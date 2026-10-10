package com.codestrome.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

private data class ScanItem(val url: String, val level: String)

@Composable
fun HomeScreen(
    onCheck: () -> Unit,
    onQr: () -> Unit,
    onSms: () -> Unit,
    onCall: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val user = FirebaseAuth.getInstance().currentUser
    var name by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<ScanItem>>(emptyList()) }

    // Read the saved name (users/{uid}) and the last 5 scans (users/{uid}/scans).
    LaunchedEffect(user?.uid) {
        val uid = user?.uid
        if (uid != null) {
            CallCache.refresh(context)
            val userDoc = FirebaseFirestore.getInstance().collection("users").document(uid)
            userDoc.get().addOnSuccessListener { doc -> name = doc.getString("name") }
            userDoc.collection("scans")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener { snap ->
                    history = snap.documents.map {
                        ScanItem(it.getString("url") ?: "", it.getString("level") ?: "")
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Hi, ${name ?: "there"}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = user?.email ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Safety tools", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Check links, QR codes and text messages with VirusTotal, and get warned about reported scam callers.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                    Text("Check a link")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onQr, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan a QR code")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onSms, modifier = Modifier.fillMaxWidth()) {
                    Text("Check an SMS")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCall, modifier = Modifier.fillMaxWidth()) {
                    Text("Call protection")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Recent checks", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                if (history.isEmpty()) {
                    Text("Your past checks will appear here.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    history.forEach { item ->
                        Text(
                            text = "${item.level} - ${item.url}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Sign out")
        }
    }
}
