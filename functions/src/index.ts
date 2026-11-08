import { onRequest } from "firebase-functions/v2/https";
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

        if (!transDoc.exists) {
            res.status(404).send("Transaction not found");
            return;
        }

        const transData = transDoc.data()!;
        if (transData.status === "COMPLETED") {
            res.status(200).send("Already processed");
            return;
        }

        const settingsDoc = await db.collection("platform").doc("settings").get();
        const settings = settingsDoc.data();

        const key = settings?.pesapalConsumerKey?.trim() || "VdiTLwH6c9jD8vVZqlvVoS29QfPv/saf";
        const secret = settings?.pesapalConsumerSecret?.trim() || "MCy2o8/hKrV7UfPSnpdYAXUFxSg=";
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

        if (statusResponse.status_code === 1) {
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
        } else {
            await transactionRef.update({ status: "FAILED", failureReason: statusResponse.payment_status_description });
        }

        res.status(200).json({ status: "200", message: "Processed" });
    } catch (error) {
        logger.error("IPN Error", error);
        res.status(500).send("Error");
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
