package com.joseph.cbchubpro.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.joseph.cbchubpro.ui.auth.AuthViewModel
import com.joseph.cbchubpro.ui.auth.AuthState
import com.joseph.cbchubpro.ui.management.*

@Composable
fun DashboardRoleRouter(
    authViewModel: AuthViewModel,
    onLogoutClicked: () -> Unit,
    onCreateFamilyClicked: () -> Unit,
    onJoinFamilyClicked: () -> Unit,
    onManageFamilyClicked: (String) -> Unit,
    onChildControlClicked: () -> Unit,
    onCareerGuardianClicked: () -> Unit,
    onMarketplaceClicked: () -> Unit,
    onSwapHubClicked: () -> Unit,
    onMySwapsClicked: () -> Unit,
    onMyOrdersClicked: () -> Unit,
    onJobBoardClicked: () -> Unit,
    onPropertyListingClicked: () -> Unit = {},
    onRestaurantBookingClicked: () -> Unit = {}
) {
    val authState by authViewModel.authState.collectAsState()

    when (val state = authState) {
        is AuthState.Success -> {
            val role = state.role.lowercase().trim()
            val email = state.user.email?.lowercase()?.trim() ?: ""

            // Master Override for Platform Owner and all Admins
            if (email == "gitere01@gmail.com" || role == "super_admin" || role == "admin") {
                AdminManagementScreen(onLogoutClicked = onLogoutClicked)
            } else {
                when (role) {
                    "institution" -> {
                        InstitutionManagementScreen(onLogoutClicked = onLogoutClicked)
                    }
                    "vendor" -> {
                        VendorManagementScreen(onLogoutClicked = onLogoutClicked)
                    }
                    "property_agent" -> {
                        PropertyManagementScreen(onLogoutClicked = onLogoutClicked)
                    }
                    "parent/guardian" -> {
                        if (state.studentId != null && state.schoolId != null) {
                            SchoolParentDashboard(
                                schoolId = state.schoolId,
                                studentId = state.studentId,
                                onLogoutClicked = onLogoutClicked,
                                onMarketplaceClicked = onMarketplaceClicked,
                                onPropertyListingClicked = onPropertyListingClicked,
                                onRestaurantBookingClicked = onRestaurantBookingClicked,
                                onSwapHubClicked = onSwapHubClicked
                            )
                        } else {
                            ParentDashboard(
                                username = state.user.displayName ?: "",
                                onLogoutClicked = onLogoutClicked,
                                onCreateFamilyClicked = onCreateFamilyClicked,
                                onJoinFamilyClicked = onJoinFamilyClicked,
                                onManageFamilyClicked = onManageFamilyClicked,
                                onChildControlClicked = onChildControlClicked,
                                onCareerGuardianClicked = onCareerGuardianClicked,
                                onMarketplaceClicked = onMarketplaceClicked,
                                onSwapHubClicked = onSwapHubClicked,
                                onMySwapsClicked = onMySwapsClicked,
                                onMyOrdersClicked = onMyOrdersClicked,
                                onJobBoardClicked = onJobBoardClicked,
                                onPropertyListingClicked = onPropertyListingClicked,
                                onRestaurantBookingClicked = onRestaurantBookingClicked
                            )
                        }
                    }
                    "professional", "service provider" -> {
                        ServiceManagementScreen(onLogoutClicked = onLogoutClicked)
                    }
                    "bursar" -> {
                        BursarDashboard(onLogoutClicked = onLogoutClicked)
                    }
                    "teacher" -> {
                        if (state.isStaff) {
                            SchoolManagementScreen(onLogoutClicked = onLogoutClicked)
                        } else {
                            TeacherManagementScreen(onLogoutClicked = onLogoutClicked)
                        }
                    }
                    "school", "security guard", "receptionist", "principal", "deputy principal", "secretary", "admin assistant", "driver", "cleaner" -> {
                        SchoolManagementScreen(onLogoutClicked = onLogoutClicked)
                    }
                    else -> {
                        ParentDashboard(
                            username = state.user.displayName ?: "User",
                            onLogoutClicked = onLogoutClicked,
                            onCreateFamilyClicked = onCreateFamilyClicked,
                            onJoinFamilyClicked = onJoinFamilyClicked,
                            onManageFamilyClicked = onManageFamilyClicked,
                            onChildControlClicked = onChildControlClicked,
                            onCareerGuardianClicked = onCareerGuardianClicked,
                            onMarketplaceClicked = onMarketplaceClicked,
                            onSwapHubClicked = onSwapHubClicked,
                            onMySwapsClicked = onMySwapsClicked,
                            onMyOrdersClicked = onMyOrdersClicked,
                            onJobBoardClicked = onJobBoardClicked,
                            onPropertyListingClicked = onPropertyListingClicked,
                            onRestaurantBookingClicked = onRestaurantBookingClicked
                        )
                    }
                }
            }
        }
        is AuthState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
