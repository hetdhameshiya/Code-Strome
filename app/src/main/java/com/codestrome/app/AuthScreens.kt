package com.codestrome.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// ---------- Shared pieces ----------

@Composable
private fun AuthLayout(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        content()
    }
}

@Composable
private fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun MainButton(text: String, loading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text(text)
        }
    }
}

@Composable
private fun MessageText(message: String?, isError: Boolean) {
    if (message != null) {
        Text(
            text = message,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
    }
}

private fun friendlyError(e: Exception?): String {
    if (e is FirebaseNetworkException) {
        return "No internet connection. Check your network and try again."
    }
    return when ((e as? FirebaseAuthException)?.errorCode) {
        "ERROR_INVALID_EMAIL" -> "That email address is not valid."
        "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" ->
            "Wrong email or password."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists."
        "ERROR_WEAK_PASSWORD" -> "Password is too weak. Use at least 6 characters."
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please try again later."
        else -> e?.localizedMessage ?: "Something went wrong. Please try again."
    }
}

// ---------- Login ----------

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onSignUp: () -> Unit,
    onForgot: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AuthLayout(title = "Welcome back", subtitle = "Log in to Code Strome") {
        AuthField(email, { email = it.trim() }, "Email", KeyboardType.Email)
        AuthField(password, { password = it }, "Password", KeyboardType.Password, password = true)
        MessageText(error, isError = true)

        MainButton("Log in", loading) {
            if (email.isBlank() || password.isBlank()) {
                error = "Enter your email and password."
            } else {
                loading = true
                error = null
                FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        loading = false
                        if (task.isSuccessful) {
                            onLoggedIn()
                        } else {
                            error = friendlyError(task.exception)
                        }
                    }
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onForgot) { Text("Forgot password?") }
        TextButton(onClick = onSignUp) { Text("New here? Create an account") }
    }
}

// ---------- Sign up ----------

@Composable
fun SignUpScreen(
    onSignedUp: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AuthLayout(title = "Create account", subtitle = "Join Code Strome") {
        AuthField(name, { name = it }, "Full name")
        AuthField(email, { email = it.trim() }, "Email", KeyboardType.Email)
        AuthField(password, { password = it }, "Password (6+ characters)", KeyboardType.Password, password = true)
        AuthField(confirm, { confirm = it }, "Confirm password", KeyboardType.Password, password = true)
        MessageText(error, isError = true)

        MainButton("Sign up", loading) {
            when {
                name.isBlank() || email.isBlank() -> error = "Enter your name and email."
                password.length < 6 -> error = "Password must be at least 6 characters."
                password != confirm -> error = "Passwords do not match."
                else -> {
                    loading = true
                    error = null
                    val auth = FirebaseAuth.getInstance()
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val uid = auth.currentUser?.uid
                                if (uid == null) {
                                    loading = false
                                    onSignedUp()
                                } else {
                                    // Save the user record. Matches the Firestore rule for users/{uid}.
                                    val data = hashMapOf(
                                        "name" to name.trim(),
                                        "email" to email,
                                        "createdAt" to FieldValue.serverTimestamp()
                                    )
                                    FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .document(uid)
                                        .set(data)
                                        .addOnCompleteListener {
                                            loading = false
                                            onSignedUp()
                                        }
                                }
                            } else {
                                loading = false
                                error = friendlyError(task.exception)
                            }
                        }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Already have an account? Log in") }
    }
}

// ---------- Forgot password ----------

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    AuthLayout(title = "Reset password", subtitle = "We will email you a reset link") {
        AuthField(email, { email = it.trim() }, "Email", KeyboardType.Email)
        MessageText(error, isError = true)
        MessageText(info, isError = false)

        MainButton("Send reset link", loading) {
            if (email.isBlank()) {
                error = "Enter your email."
                info = null
            } else {
                loading = true
                error = null
                info = null
                FirebaseAuth.getInstance()
                    .sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        loading = false
                        if (task.isSuccessful) {
                            info = "If an account exists for this email, a reset link has been sent. Check your inbox and spam folder."
                        } else {
                            error = friendlyError(task.exception)
                        }
                    }
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Back to log in") }
    }
}
