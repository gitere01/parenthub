package com.joseph.cbchubpro.child

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.joseph.cbchubpro.child.ui.ChildHomeScreen
import com.joseph.cbchubpro.child.ui.LessonViewScreen
import com.joseph.cbchubpro.child.ui.PointsLeaderboardScreen
import com.joseph.cbchubpro.child.ui.QuizViewScreen
import com.joseph.cbchubpro.child.ui.TeacherDetailsScreen
import com.joseph.cbchubpro.child.ui.pairing.PairingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Bridge both versions of LocalLifecycleOwner to prevent crashes with mismatched library versions
            CompositionLocalProvider(
                androidx.lifecycle.compose.LocalLifecycleOwner provides this,
                @Suppress("DEPRECATION")
                androidx.compose.ui.platform.LocalLifecycleOwner provides this
            ) {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()

    // Setting startDestination based on current state
    val startDestination = if (mainViewModel.isPairedSync()) "home" else "pairing"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("pairing") {
            PairingScreen(onDevicePaired = {
                mainViewModel.updatePairingStatus()
                navController.navigate("permissions") { 
                    popUpTo("pairing") { inclusive = true } 
                }
            })
        }
        composable("permissions") {
            PermissionsHubScreen(onAllPermissionsGranted = {
                navController.navigate("home") { 
                    popUpTo("permissions") { inclusive = true } 
                }
            })
        }
        composable("home") {
            ChildHomeScreen(
                onNavigateToLesson = { lessonId ->
                    navController.navigate("lesson/$lessonId")
                },
                onNavigateToQuiz = { quizId ->
                    navController.navigate("quiz/$quizId")
                },
                onNavigateToTeacher = { teacherId ->
                    navController.navigate("teacher_details/$teacherId")
                },
                onNavigateToLeaderboard = {
                    navController.navigate("leaderboard")
                },
                onLogout = {
                    mainViewModel.updatePairingStatus()
                    navController.navigate("pairing") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "teacher_details/{teacherId}",
            arguments = listOf(navArgument("teacherId") { type = NavType.StringType })
        ) { backStackEntry ->
            val teacherId = backStackEntry.arguments?.getString("teacherId") ?: ""
            TeacherDetailsScreen(
                teacherId = teacherId,
                onBack = { navController.popBackStack() },
                onNavigateToLesson = { lessonId ->
                    navController.navigate("lesson/$lessonId")
                },
                onNavigateToQuiz = { quizId ->
                    navController.navigate("quiz/$quizId")
                }
            )
        }
        composable(
            route = "lesson/{lessonId}",
            arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
        ) { backStackEntry ->
            val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
            LessonViewScreen(
                lessonId = lessonId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "quiz/{quizId}",
            arguments = listOf(navArgument("quizId") { type = NavType.StringType })
        ) { backStackEntry ->
            val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
            QuizViewScreen(
                quizId = quizId,
                onBack = { navController.popBackStack() }
            )
        }
        composable("leaderboard") {
            PointsLeaderboardScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
