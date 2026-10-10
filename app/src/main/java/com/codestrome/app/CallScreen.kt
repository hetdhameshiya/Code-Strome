package com.codestrome.app

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun CallScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // ----- Call warning on/off -----
    fun warningsOn(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val roles = context.getSystemService(RoleManager::class.java)
        return roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    var enabled by remember { mutableStateOf(warningsOn()) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { enabled = warningsOn() }

    fun requestRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            }
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { requestRole() }

    // ----- Check and report numbers -----
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var reportCount by remember { mutableStateOf<Long?>(null) }

    fun checkNumber() {
        error = null
        message = null
        reportCount = null
        val number = CallCache.normalize(input)
        if (!CallCache.isValid(number)) {
            error = "Enter a valid phone number."
            return
        }
        loading = true
        FirebaseFirestore.getInstance()
            .collection("reportedNumbers").document(number)
            .get()
            .addOnSuccessListener { doc ->
                loading = false
                reportCount = doc.getLong("count") ?: 0L
            }
            .addOnFailureListener {
                loading = false
                error = "Could not check this number. Check your internet connection."
            }
    }

    fun reportNumber() {
        error = null
        message = null
        reportCount = null
        val number = CallCache.normalize(input)
        when {
            !CallCache.isValid(number) -> error = "Enter a valid phone number."
            CallCache.hasReportedByMe(context, number) -> error = "You have already reported this number."
            else -> {
                loading = true
                val db = FirebaseFirestore.getInstance()
                val ref = db.collection("reportedNumbers").document(number)
                db.runTransaction { tx ->
                    val snap = tx.get(ref)
                    if (snap.exists()) {
                        tx.update(
                            ref,
                            mapOf(
                                "count" to ((snap.getLong("count") ?: 0L) + 1L),
                                "lastReportedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        tx.set(
                            ref,
                            mapOf(
                                "count" to 1L,
                                "lastReportedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    null
                }
                    .addOnSuccessListener {
                        loading = false
                        CallCache.markReportedByMe(context, number)
                        CallCache.addReported(context, number)
                        message = "Thanks. This number is now flagged for people using the app."
                    }
                    .addOnFailureListener {
                        loading = false
                        error = "Could not send the report. Check your internet connection and try again."
                    }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text(
            text = "Call protection",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Get a warning when a number reported by other users calls you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        // ----- Status card -----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (enabled) "Call warnings are on" else "Call warnings are off",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                if (Build.VERSION.SDK_INT < 29) {
                    Text(
                        "Call warnings need Android 10 or newer.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (enabled) {
                    Text(
                        "Incoming calls are checked on your phone against the list of reported numbers. Calls always ring as normal.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "Android will ask you to choose Code Strome as your Caller ID and spam app. This replaces your current spam filter. Call numbers are checked on your phone and are not sent anywhere.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                            if (needsPermission) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                requestRole()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Turn on call warnings") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ----- Check / report -----
        Text("Check or report a number", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Phone number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { checkNumber() },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) { Text("Check number") }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { reportNumber() },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) { Text("Report as scam") }

        Spacer(Modifier.height(4.dp))
        Text(
            "Only report numbers that really made scam or spam calls.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }

        reportCount?.let { count ->
            val reported = count > 0
            val scheme = MaterialTheme.colorScheme
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (reported) scheme.error else scheme.surfaceVariant,
                    contentColor = if (reported) scheme.onError else scheme.onSurfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (reported) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (reported) "Reported $count time(s)" else "No reports yet",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (reported) "Other users reported this number. Be careful, and never share an OTP or password."
                        else "No one has reported this number. That does not mean it is safe.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
