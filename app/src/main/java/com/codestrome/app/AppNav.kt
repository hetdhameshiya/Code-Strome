package com.codestrome.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AppNav() {
    val nav = rememberNavController()

    // If someone is already signed in, skip the login screen.
    val start = if (FirebaseAuth.getInstance().currentUser != null) "home" else "login"

    NavHost(navController = nav, startDestination = start) {
        composable("login") {
            LoginScreen(
                onLoggedIn = {
                    nav.navigate("home") { popUpTo("login") { inclusive = true } }
                },
                onSignUp = { nav.navigate("signup") },
                onForgot = { nav.navigate("forgot") }
            )
        }
        composable("signup") {
            SignUpScreen(
                onSignedUp = {
                    nav.navigate("home") { popUpTo("login") { inclusive = true } }
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable("forgot") {
            ForgotPasswordScreen(onBack = { nav.popBackStack() })
        }
        composable("home") {
            HomeScreen(
                onCheck = { nav.navigate("check") },
                onQr = { nav.navigate("qr") },
                onSms = { nav.navigate("sms") },
                onCall = { nav.navigate("call") },
                onSignOut = {
                    FirebaseAuth.getInstance().signOut()
                    nav.navigate("login") { popUpTo("home") { inclusive = true } }
                }
            )
        }
        composable("check") {
            CheckScreen(autoScan = false, onBack = { nav.popBackStack() })
        }
        composable("qr") {
            CheckScreen(autoScan = true, onBack = { nav.popBackStack() })
        }
        composable("sms") {
            SmsScreen(onBack = { nav.popBackStack() })
        }
        composable("call") {
            CallScreen(onBack = { nav.popBackStack() })
        }
    }
}
