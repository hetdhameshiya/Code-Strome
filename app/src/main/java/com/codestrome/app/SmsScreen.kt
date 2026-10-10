package com.codestrome.app

import android.util.Patterns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Words that scam messages often use. A hint only, not proof.
private val warningWords = listOf(
    "urgent", "immediately", "verify", "blocked", "suspended", "kyc", "otp",
    "password", "prize", "winner", "lottery", "claim", "refund", "expire",
    "gift", "click", "limited time", "last chance", "free"
)

private fun findLinks(text: String): List<String> {
    val matcher = Patterns.WEB_URL.matcher(text)
    val links = mutableListOf<String>()
    while (matcher.find()) {
        links.add(normalizeUrl(matcher.group().trimEnd('.', ',', ')', '!', '?')))
    }
    // Only the first 3 links, because the free VirusTotal key allows 4 requests per minute.
    return links.distinct().take(3)
}

private fun findWarningWords(text: String): List<String> {
    val lower = text.lowercase()
    return warningWords.filter { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(lower) }
}

@Composable
fun SmsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var flags by remember { mutableStateOf<List<String>>(emptyList()) }
    var links by remember { mutableStateOf<List<String>>(emptyList()) }
    var results by remember { mutableStateOf<List<Pair<String, VtResult>>>(emptyList()) }

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
            text = "Check an SMS",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Copy a text message you received and paste it here. The app checks any links in it and looks for common scam wording.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Paste the message") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                error = null
                results = emptyList()
                checked = false
                if (message.isBlank()) {
                    error = "Paste a message first."
                } else {
                    val foundLinks = findLinks(message)
                    flags = findWarningWords(message)
                    links = foundLinks
                    if (foundLinks.isEmpty()) {
                        checked = true
                    } else if (!VirusTotalApi.hasKey()) {
                        error = "No VirusTotal key found. Add VT_API_KEY to local.properties and rebuild."
                    } else {
                        loading = true
                        scope.launch {
                            val out = mutableListOf<Pair<String, VtResult>>()
                            for (link in foundLinks) {
                                val result = VirusTotalApi.lookup(link)
                                out.add(link to result)
                                if (result is VtResult.Done) {
                                    saveScan(link, result.stats, result.stats.toTone())
                                }
                            }
                            results = out
                            loading = false
                            checked = true
                        }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text("Check message")
            }
        }

        Spacer(Modifier.height(16.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        if (checked) {
            if (flags.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Warning signs in the text", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(flags.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Scam messages often create urgency or ask for codes and passwords. Never share an OTP or password, even if the message says it is from a bank.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (links.isEmpty()) {
                Text(
                    "No links found in this message.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            results.forEach { (url, result) ->
                when (result) {
                    is VtResult.Done -> ResultCard(url = url, stats = result.stats)
                    is VtResult.NotFound -> Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Not scanned before", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(url, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "VirusTotal has no report for this link. Treat it with care, or use Check a link to scan it.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is VtResult.Failed -> Text(
                        "${url}: ${result.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
