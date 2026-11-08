package com.joseph.cbchubpro

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.joseph.cbchubpro.ui.auth.AuthViewModel
import com.joseph.cbchubpro.ui.auth.SignInScreen
import com.joseph.cbchubpro.ui.auth.SignUpScreen
import com.joseph.cbchubpro.ui.auth.AuthState
import com.joseph.cbchubpro.ui.childcontrol.ChildControlScreen
import com.joseph.cbchubpro.ui.dashboard.DashboardRoleRouter
import com.joseph.cbchubpro.ui.discovery.*
import com.joseph.cbchubpro.ui.family.*
import com.joseph.cbchubpro.ui.management.CartViewModel
import com.joseph.cbchubpro.ui.management.VendorManagementScreen
import com.joseph.cbchubpro.ui.management.LiveLocationScreen
import com.joseph.cbchubpro.ui.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val cartViewModel: CartViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(
                onGetStartedClicked = {
                    navController.navigate("signin")
                }
            )
        }

        composable("signup") {
            SignUpScreen(
                onSignUpClicked = { name, email, password, role, extraData ->
                    authViewModel.signUp(name, email, password, role, extraData)
                },
                onSignInClicked = { navController.navigate("signin") }
            )
        }
        
        composable("signin") {
            SignInScreen(
                onSignInClicked = { email, password ->
                    authViewModel.signIn(email, password)
                },
                onSignUpClicked = { navController.navigate("signup") }
            )
        }
        composable("dashboard") {
            DashboardRoleRouter(
                authViewModel = authViewModel,
                onLogoutClicked = {
                    authViewModel.signOut()
                    navController.navigate("signin") { popUpTo("dashboard") { inclusive = true } }
                },
                onCreateFamilyClicked = { navController.navigate("create_family") },
                onJoinFamilyClicked = { navController.navigate("join_family") },
                onManageFamilyClicked = { familyId ->
                    navController.navigate("family_management/$familyId")
                },
                onChildControlClicked = { navController.navigate("child_control") },
                onCareerGuardianClicked = { navController.navigate("career_guardian") },
                onMarketplaceClicked = { navController.navigate("marketplace") },
                onSwapHubClicked = { navController.navigate("swap_hub") },
                onMySwapsClicked = { navController.navigate("my_swaps") },
                onMyOrdersClicked = { navController.navigate("my_orders") },
                onJobBoardClicked = { navController.navigate("job_board") },
                onPropertyListingClicked = { navController.navigate("property_listings") },
                onRestaurantBookingClicked = { navController.navigate("hospitality_discovery") }
            )
        }
        composable("create_family") {
            CreateFamilyScreen { familyId ->
                navController.navigate("family_management/$familyId") { popUpTo("create_family") { inclusive = true } }
            }
        }
        composable("join_family") {
            JoinFamilyScreen(
                onBackClicked = { navController.popBackStack() },
                onJoinedSuccessfully = { familyId ->
                    navController.navigate("family_management/$familyId") { popUpTo("join_family") { inclusive = true } }
                }
            )
        }
        composable(
            "family_management/{familyId}",
            arguments = listOf(navArgument("familyId") { type = NavType.StringType })
        ) {
            val familyId = it.arguments?.getString("familyId") ?: ""
            FamilyManagementScreen(familyId = familyId) { childId ->
                navController.navigate("child_management/$childId")
            }
        }
        composable(
            "child_management/{childId}",
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) {
            val childId = it.arguments?.getString("childId") ?: ""
            ChildManagementScreen(
                childId = childId,
                onBackClicked = { navController.popBackStack() },
                onLiveLocationClicked = { id ->
                    navController.navigate("live_location/$id")
                }
            )
        }
        composable(
            "live_location/{childId}",
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) {
            val childId = it.arguments?.getString("childId") ?: ""
            LiveLocationScreen(childId = childId)
        }
        composable("child_control") {
            ChildControlScreen(navController = navController)
        }
        composable("career_guardian") {
            ProfessionalDiscoveryScreen(
                onBackClicked = { navController.popBackStack() },
                onProfessionalClicked = { profId ->
                    navController.navigate("professional_detail/$profId")
                },
                onViewJobsClicked = { navController.navigate("job_board") }
            )
        }
        composable(
            "professional_detail/{profId}",
            arguments = listOf(navArgument("profId") { type = NavType.StringType })
        ) {
            val profId = it.arguments?.getString("profId") ?: ""
            ProfessionalDetailScreen(
                professionalId = profId,
                onBackClicked = { navController.popBackStack() }
            )
        }
        composable("job_board") {
            JobsScreen(
                onBackClicked = { navController.popBackStack() }
            )
        }
        composable("marketplace") {
            MarketplaceScreen(
                cartViewModel = cartViewModel,
                onBackClicked = { navController.popBackStack() },
                onVendorClicked = { vendorId ->
                    navController.navigate("vendor_detail/$vendorId")
                },
                onCartClicked = { navController.navigate("cart") }
            )
        }
        composable("swap_hub") {
            SwapHubScreen(
                onBackClicked = { navController.popBackStack() },
                onParentClicked = { parentId ->
                    navController.navigate("vendor_detail/$parentId")
                }
            )
        }
        composable(
            "vendor_detail/{vendorId}",
            arguments = listOf(navArgument("vendorId") { type = NavType.StringType })
        ) {
            val vendorId = it.arguments?.getString("vendorId") ?: ""
            VendorDetailScreen(
                vendorId = vendorId,
                cartViewModel = cartViewModel,
                onBackClicked = { navController.popBackStack() }
            )
        }
        composable("my_swaps") {
            VendorManagementScreen(
                onLogoutClicked = {
                    authViewModel.signOut()
                    navController.navigate("signin") { popUpTo("dashboard") { inclusive = true } }
                }
            )
        }
        composable("my_orders") {
            BuyerOrdersScreen(
                onBackClicked = { navController.popBackStack() }
            )
        }
        composable("cart") {
            CartScreen(
                viewModel = cartViewModel,
                onBackClicked = { navController.popBackStack() },
                onCheckoutSuccess = {
                    navController.navigate("my_orders") {
                        popUpTo("marketplace") { inclusive = true }
                    }

                    Toast.makeText(context, "Order placed! Collect and pay at the agent on delivery.", Toast.LENGTH_LONG).show()
                }
            )
        }
        composable("property_listings") {
            PropertyDiscoveryScreen(
                onBackClicked = { navController.popBackStack() }
            )
        }
        composable("hospitality_discovery") {
            HospitalityDiscoveryScreen(
                onBackClicked = { navController.popBackStack() }
            )
        }
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                navController.navigate("dashboard") { popUpTo("signin") { inclusive = true } }
            }
            AuthState.SignUpSuccess -> {
                Toast.makeText(context, "Registration successful! Please sign in.", Toast.LENGTH_LONG).show()
                navController.navigate("signin") {
                    popUpTo("signup") { inclusive = true }
                }
                authViewModel.resetAuthState()
            }
            is AuthState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }
}
