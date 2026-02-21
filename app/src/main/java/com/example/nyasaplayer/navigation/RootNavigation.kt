package com.example.nyasaplayer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nyasaplayer.screens.NyasaPlayerApp
import com.example.nyasaplayer.screens.auth.LoginScreen
import com.example.nyasaplayer.screens.auth.SignUpScreen

const val LoginRoute = "login"
const val SignUpRoute = "sign_up"
const val MainAppRoute = "main_app"

@UnstableApi
@Composable
fun RootNavHost(
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(LoginRoute) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(MainAppRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                },
                onNavigateToSignUp = { navController.navigate(SignUpRoute) },
            )
        }
        composable(SignUpRoute) {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.navigate(MainAppRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }
        composable(MainAppRoute) {
            NyasaPlayerApp(
                onSignOut = {
                    navController.navigate(LoginRoute) {
                        popUpTo(MainAppRoute) { inclusive = true }
                    }
                },
            )
        }
    }
}
