import { HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { initializeApp } from "firebase-admin/app";
import * as logger from "firebase-functions/logger";

initializeApp();

export const pesapalIPN = onRequest({ region: "us-central1", cors: true }, async (req, res) => {
    // Lazy load heavy modules inside the handler to avoid deployment timeouts
    const { getFirestore, Timestamp } = await import("firebase-admin/firestore");
    const axios = (await import("axios")).default;

    const trackingId = req.query.OrderTrackingId as string;
    const merchantRef = req.query.OrderMerchantReference as string;

    // Return OK for verification or browser hits
    if (!trackingId || !merchantRef) {
        res.status(200).send("OK - Verified");
        return;
    }

    try {
        const db = getFirestore();
        const transactionRef = db.collection("point_transactions").doc(merchantRef);
        const transDoc = await transactionRef.get();
        const boostRef = db.collection("payment_requests").doc(merchantRef);
        const boostDoc = await boostRef.get();
        if (!transDoc.exists && !boostDoc.exists) {
            res.status(404).send("Transaction not found");
            return;
        }

        const transData = transDoc.data();
        const boostData = boostDoc.data();
        if (transData?.status === "COMPLETED" || boostData?.status === "COMPLETED") {
            res.status(200).send("Already processed");
            return;
        }

        const settingsDoc = await db.collection("settings").doc("platform").get();
        const legacySettingsDoc = await db.collection("platform").doc("settings").get();
        const settings = settingsDoc.data() || legacySettingsDoc.data();

        const key = settings?.pesapalConsumerKey?.trim();
        const secret = settings?.pesapalConsumerSecret?.trim();
        if (!key || !secret) throw new Error("PesaPal credentials are not configured.");
        const isLive = settings?.liveMode === true;
        const baseUrl = isLive ? "https://pay.pesapal.com/v3" : "https://cybqa.pesapal.com/pesapalv3";

        // 1. Get Token
        const authRes = await axios.post(`${baseUrl}/api/Auth/RequestToken`, { consumer_key: key, consumer_secret: secret });
        const token = authRes.data.token;

        // 2. Get Status
        const statusRes = await axios.get(`${baseUrl}/api/Transactions/GetTransactionStatus?orderTrackingId=${trackingId}`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        const statusResponse = statusRes.data;

        if (statusResponse.status_code === 1 && transDoc.exists && transData) {
            await db.runTransaction(async (t) => {
                const userBalanceRef = db.collection("point_balances").doc(transData.userId);
                const balanceDoc = await t.get(userBalanceRef);
                const currentPoints = balanceDoc.exists ? (balanceDoc.data()?.totalPoints || 0) : 0;

                t.set(userBalanceRef, {
                    totalPoints: currentPoints + transData.pointsAllocated,
                    lastUpdated: Timestamp.now()
                }, { merge: true });

                t.update(transactionRef, {
                    status: "COMPLETED",
                    pesapalNotificationData: statusResponse,
                    completedAt: Timestamp.now()
                });
            });
        } else if (transDoc.exists) {
            await transactionRef.update({ status: "FAILED", failureReason: statusResponse.payment_status_description });
        } else if (boostData?.type === "PRODUCT_AD_BOOST") {
            const verifiedBoostPayment = statusResponse.status_code === 1
                && String(statusResponse.merchant_reference) === merchantRef
                && String(boostData.pesapalOrderTrackingId || "") === trackingId
                && Number(statusResponse.amount) === Number(boostData.amount)
                && String(statusResponse.currency) === "KES";
            if (verifiedBoostPayment) {
                await db.runTransaction(async (t) => {
                    const request = await t.get(boostRef);
                    if (!request.exists || request.data()?.status === "COMPLETED") return;
                    const data = request.data()!;
                    const productRef = db.collection("users").doc(data.userId).collection("products").doc(data.productId);
                    const product = await t.get(productRef);
                    if (!product.exists) throw new Error("Boost listing no longer exists.");
                    const expiresAt = Timestamp.fromMillis(Date.now() + Number(data.durationDays) * 24 * 60 * 60 * 1000);
                    t.update(boostRef, { status: "COMPLETED", pesapalNotificationData: statusResponse, completedAt: Timestamp.now() });
                    t.update(productRef, {
                        adLevel: data.adLevel,
                        adStatus: "ACTIVE",
                        adExpiresAt: expiresAt,
                        promotionRequestId: merchantRef,
                        promotionDurationDays: data.durationDays
                    });
                });
            } else {
                await boostRef.update({ status: "FAILED", failureReason: statusResponse.payment_status_description || "Payment was not completed." });
            }
        } else {
            await boostRef.update({ status: "FAILED", failureReason: statusResponse.payment_status_description || "Payment was not completed." });
        }

        res.status(200).json({ status: "200", message: "Processed" });
    } catch (error) {
        logger.error("IPN Error", error);
        res.status(500).send("Error");
    }
});

export const createBoostCheckout = onCall({ region: "us-central1" }, async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in before buying a listing boost.");
    const productId = String(request.data?.productId || "");
    const adLevel = String(request.data?.adLevel || "");
    const durationDays = Number(request.data?.durationDays || 0);
    const priceByLevel: Record<string, number> = { BASIC: 50, PREMIUM: 150, FEATURED: 500 };
    if (!productId || !priceByLevel[adLevel] || ![7, 30].includes(durationDays)) {
        throw new HttpsError("invalid-argument", "Choose a valid boost and duration.");
    }

    const { getFirestore, Timestamp } = await import("firebase-admin/firestore");
    const axios = (await import("axios")).default;
    const db = getFirestore();
    const uid = request.auth.uid;
    const productRef = db.collection("users").doc(uid).collection("products").doc(productId);
    const productDoc = await productRef.get();
    if (!productDoc.exists) throw new HttpsError("not-found", "Listing not found.");
    const existing = await db.collection("payment_requests").where("userId", "==", uid).get();
    if (existing.docs.some((doc) => doc.get("productId") === productId && doc.get("type") === "PRODUCT_AD_BOOST" && doc.get("status") === "PENDING")) {
        throw new HttpsError("failed-precondition", "A boost payment is already pending for this listing.");
    }

    const settingsRef = db.collection("settings").doc("platform");
    const settingsDoc = await settingsRef.get();
    const settings = settingsDoc.data();
    const key = settings?.pesapalConsumerKey?.trim();
    const secret = settings?.pesapalConsumerSecret?.trim();
    if (!key || !secret) throw new HttpsError("failed-precondition", "PesaPal is not configured by the administrator.");
    const isLive = settings?.liveMode === true;
    const baseUrl = isLive ? "https://pay.pesapal.com/v3" : "https://cybqa.pesapal.com/pesapalv3";
    const ipnUrl = "https://pesapalipn-vs7fnli2hq-uc.a.run.app";
    const authRes = await axios.post(`${baseUrl}/api/Auth/RequestToken`, { consumer_key: key, consumer_secret: secret });
    const token = authRes.data.token as string;
    let ipnId = String(settings?.pesapalIpnId || "");
    if (!ipnId) {
        const registered = await axios.post(`${baseUrl}/api/URLSetup/RegisterIPN`,
            { url: ipnUrl, ipn_notification_type: "GET" }, { headers: { Authorization: `Bearer ${token}`, Accept: "application/json", "Content-Type": "application/json" } });
        ipnId = String(registered.data?.ipn_id || "");
        if (!ipnId) throw new Error("PesaPal did not return an IPN registration ID.");
        await settingsRef.set({ pesapalIpnId: ipnId }, { merge: true });
    }

    const amount = priceByLevel[adLevel] * (durationDays === 30 ? 3 : 1);
    const requestRef = db.collection("payment_requests").doc();
    const profile = await db.collection("users").doc(uid).get();
    const profileData = profile.data() || {};
    const phone = String(profileData.contact || profileData.phone || request.auth.token.phone_number || "");
    const email = String(request.auth.token.email || profileData.email || "");
    if (!phone && !email) throw new HttpsError("failed-precondition", "Add a phone number or email to your account for PesaPal checkout.");
    const name = String(profileData.name || request.auth.token.name || "Vendor").trim().split(/\s+/);
    await requestRef.set({
        id: requestRef.id, userId: uid, amount, phone, type: "PRODUCT_AD_BOOST", productId,
        productName: productDoc.get("name") || "Product listing", adLevel, durationDays,
        status: "PENDING", timestamp: Timestamp.now()
    });
    try {
        const order = await axios.post(`${baseUrl}/api/Transactions/SubmitOrderRequest`, {
            id: requestRef.id, currency: "KES", amount, description: `${adLevel} listing boost for ${durationDays} days`,
            callback_url: ipnUrl, notification_id: ipnId,
            billing_address: {
                email_address: email, phone_number: phone, country_code: "KE", first_name: name[0] || "Vendor",
                middle_name: "", last_name: name.slice(1).join(" ") || "", line_1: "", line_2: "", city: "", state: "", postal_code: "", zip_code: ""
            }
        }, { headers: { Authorization: `Bearer ${token}`, Accept: "application/json", "Content-Type": "application/json" } });
        const redirectUrl = String(order.data?.redirect_url || "");
        if (!redirectUrl) throw new Error("PesaPal did not return a checkout URL.");
        await requestRef.update({ pesapalOrderTrackingId: order.data.order_tracking_id || "", redirectUrl });
        return { redirectUrl, paymentRequestId: requestRef.id };
    } catch (error) {
        await requestRef.update({ status: "FAILED", failureReason: "Could not create PesaPal checkout." });
        logger.error("Boost checkout creation failed", error);
        throw new HttpsError("unavailable", "Could not start PesaPal checkout. Please try again.");
    }
});

export const createPointsCheckout = onCall({ region: "us-central1" }, async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in before purchasing points.");
    const amount = Number(request.data?.amount || 0);
    const email = String(request.data?.email || request.auth.token.email || "");
    const phone = String(request.data?.phone || request.auth.token.phone_number || "");
    if (!Number.isFinite(amount) || amount < 10 || amount > 50000 || (!email && !phone)) {
        throw new HttpsError("invalid-argument", "Enter a valid amount (KES 10–50,000) and an email or phone number.");
    }
    const { getFirestore, Timestamp } = await import("firebase-admin/firestore");
    const axios = (await import("axios")).default;
    const db = getFirestore();
    const settingsRef = db.collection("settings").doc("platform");
    const settingsDoc = await settingsRef.get();
    const settings = settingsDoc.data();
    const key = settings?.pesapalConsumerKey?.trim();
    const secret = settings?.pesapalConsumerSecret?.trim();
    if (!key || !secret) throw new HttpsError("failed-precondition", "PesaPal is not configured by the administrator.");
    const baseUrl = settings?.liveMode === true ? "https://pay.pesapal.com/v3" : "https://cybqa.pesapal.com/pesapalv3";
    const ipnUrl = "https://pesapalipn-vs7fnli2hq-uc.a.run.app";
    const authRes = await axios.post(`${baseUrl}/api/Auth/RequestToken`, { consumer_key: key, consumer_secret: secret });
    const token = authRes.data.token as string;
    let ipnId = String(settings?.pesapalIpnId || "");
    if (!ipnId) {
        const registered = await axios.post(`${baseUrl}/api/URLSetup/RegisterIPN`,
            { url: ipnUrl, ipn_notification_type: "GET" }, { headers: { Authorization: `Bearer ${token}`, Accept: "application/json", "Content-Type": "application/json" } });
        ipnId = String(registered.data?.ipn_id || "");
        if (!ipnId) throw new Error("PesaPal did not return an IPN registration ID.");
        await settingsRef.set({ pesapalIpnId: ipnId }, { merge: true });
    }

    const transactionRef = db.collection("point_transactions").doc();
    const pointsAllocated = amount * 0.85;
    const user = await db.collection("users").doc(request.auth.uid).get();
    const firstName = String(request.data?.firstName || user.get("name") || "Parent").trim().split(/\s+/)[0];
    const lastName = String(request.data?.lastName || "");
    await transactionRef.set({
        id: transactionRef.id, userId: request.auth.uid, amountPaid: amount, pointsAllocated,
        platformRevenue: amount - pointsAllocated, status: "PENDING", type: "PURCHASE", timestamp: Timestamp.now()
    });
    try {
        const order = await axios.post(`${baseUrl}/api/Transactions/SubmitOrderRequest`, {
            id: transactionRef.id, currency: "KES", amount, description: "Purchase of gift points",
            callback_url: ipnUrl, notification_id: ipnId,
            billing_address: {
                email_address: email, phone_number: phone, country_code: "KE", first_name: firstName || "Parent",
                middle_name: "", last_name: lastName, line_1: "", line_2: "", city: "", state: "", postal_code: "", zip_code: ""
            }
        }, { headers: { Authorization: `Bearer ${token}`, Accept: "application/json", "Content-Type": "application/json" } });
        const redirectUrl = String(order.data?.redirect_url || "");
        if (!redirectUrl) throw new Error("PesaPal did not return a checkout URL.");
        await transactionRef.update({ pesapalOrderTrackingId: String(order.data?.order_tracking_id || "") });
        return { redirectUrl, transactionId: transactionRef.id };
    } catch (error) {
        await transactionRef.update({ status: "FAILED" });
        logger.error("Points checkout creation failed", error);
        throw new HttpsError("unavailable", "Could not start PesaPal checkout. Please try again.");
    }
});

export const onSMSPurchaseCreated = onDocumentCreated("sms_purchases/{purchaseId}", async (event) => {
    const { getFirestore, Timestamp } = await import("firebase-admin/firestore");
    const snapshot = event.data;
    if (!snapshot) return;
    const data = snapshot.data();
    if (data.status === "PENDING") {
        const db = getFirestore();
        const schoolRef = db.collection("users").doc(data.schoolId);
        await db.runTransaction(async (transaction) => {
            const schoolDoc = await transaction.get(schoolRef);
            const currentCredits = schoolDoc.data()?.smsCredits || 0;
            transaction.update(schoolRef, { smsCredits: currentCredits + data.credits });
            transaction.update(snapshot.ref, { status: "COMPLETED", processedAt: Timestamp.now() });
        });
    }
});

export const onSMSLogCreated = onDocumentCreated("schools/{schoolId}/sms_logs/{logId}", async (event) => {
    const { getFirestore, Timestamp } = await import("firebase-admin/firestore");
    const snapshot = event.data;
    if (!snapshot) return;
    const data = snapshot.data();
    const db = getFirestore();
    const schoolDoc = await db.collection("users").doc(event.params.schoolId).get();
    const currentCredits = schoolDoc.data()?.smsCredits || 0;
    if (currentCredits < data.recipients.length) {
        await snapshot.ref.update({ status: "FAILED", reason: "Insufficient Credits" });
        return;
    }
    try {
        await db.runTransaction(async (transaction) => {
            transaction.update(schoolDoc.ref, { smsCredits: currentCredits - data.recipients.length });
            transaction.update(snapshot.ref, { status: "SENT", sentAt: Timestamp.now() });
        });
    } catch (error) {
        logger.error("SMS Error", error);
    }
});

export const onBusLocationUpdate = onDocumentUpdated("schools/{schoolId}/transport/{busId}", async (event) => {
    const { getFirestore } = await import("firebase-admin/firestore");
    const newData = event.data?.after.data();
    const oldData = event.data?.before.data();
    if (!newData || !oldData || (newData.currentLat === oldData.currentLat && newData.currentLng === oldData.currentLng)) return;
    const db = getFirestore();

    const studentsSnapshot = await db.collection("schools").doc(event.params.schoolId).collection("students").where("busRouteId", "==", event.params.busId).get();
    const parentAlerts = studentsSnapshot.docs.map(async (studentDoc) => {
        const student = studentDoc.data();
        if (!student.parentId) return;
        const parentDoc = await db.collection("users").doc(student.parentId).get();
        const parent = parentDoc.data();
        if (parent?.fcmToken) {
            logger.info("Ready to notify parent", { student: student.name });
        }
    });
    await Promise.all(parentAlerts);
});
