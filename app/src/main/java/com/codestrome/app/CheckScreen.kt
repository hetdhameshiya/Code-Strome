package com.codestrome.app

import android.util.Patterns
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.codestrome.app.ui.theme.RiskTone
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

internal fun normalizeUrl(input: String): String {
    val text = input.trim()
    return if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
}

internal fun saveScan(url: String, stats: VtStats, tone: RiskTone) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val data = hashMapOf(
        "url" to url,
        "level" to tone.name,
        "malicious" to stats.malicious,
        "suspicious" to stats.suspicious,
        "harmless" to stats.harmless,
        "createdAt" to FieldValue.serverTimestamp()
    )
    FirebaseFirestore.getInstance()
        .collection("users").document(uid)
        .collection("scans")
        .add(data)
}

/** Set autoScan = true to open the QR scanner as soon as the screen opens. */
@Composable
fun CheckScreen(autoScan: Boolean = false, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var input by remember { mutableStateOf("") }
    var checkedUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var stats by remember { mutableStateOf<VtStats?>(null) }
    var notFound by remember { mutableStateOf(false) }

    fun finish(result: VtResult) {
        loading = false
        status = null
        when (result) {
            is VtResult.Done -> {
                stats = result.stats
                saveScan(checkedUrl, result.stats, result.stats.toTone())
            }
            is VtResult.NotFound -> notFound = true
            is VtResult.Failed -> error = result.message
        }
    }

    fun runCheck(raw: String) {
        stats = null
        error = null
        notFound = false
        val url = normalizeUrl(raw)
        when {
            raw.isBlank() -> error = "Paste a link first."
            !Patterns.WEB_URL.matcher(url).matches() -> error = "That does not look like a valid link."
            !VirusTotalApi.hasKey() ->
                error = "No VirusTotal key found. Add VT_API_KEY to local.properties and rebuild."
            else -> {
                checkedUrl = url
                loading = true
                status = "Checking VirusTotal..."
                scope.launch { finish(VirusTotalApi.lookup(url)) }
            }
        }
    }

    fun scanQr() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val value = (barcode.rawValue ?: "").trim()
                input = value
                stats = null
                notFound = false
                error = null
                when {
                    value.isBlank() -> error = "The QR code is empty."
                    value.startsWith("upi:", ignoreCase = true) ->
                        error = "This is a payment QR code (UPI), not a web link. Only pay people you trust, and never scan a QR code to receive money."
                    !Patterns.WEB_URL.matcher(normalizeUrl(value)).matches() ->
                        error = "This QR code does not contain a web link. It contains: ${value.take(80)}"
                    else -> runCheck(value)
                }
            }
            .addOnFailureListener {
                error = "Could not scan the QR code. Check your internet connection and that Google Play services is up to date."
            }
    }

    LaunchedEffect(Unit) {
        if (autoScan) scanQr()
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
            text = if (autoScan) "Scan a QR code" else "Check a link",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Paste a link or scan a QR code to see whether security engines flag it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Link (for example example.com)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { runCheck(input) },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text("Check link")
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { scanQr() },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) { Text("Scan QR code") }

        Spacer(Modifier.height(16.dp))

        status?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        if (notFound) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Not scanned before", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "VirusTotal has no report for this link yet. Scanning it sends the link to VirusTotal, which can share it with security vendors. Only scan links you are happy to share. A scan takes about a minute.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            notFound = false
                            loading = true
                            status = "Scanning, this takes about a minute..."
                            scope.launch { finish(VirusTotalApi.scan(checkedUrl)) }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Scan this link") }
                }
            }
        }

        stats?.let { ResultCard(url = checkedUrl, stats = it) }
    }
}

@Composable
internal fun ResultCard(url: String, stats: VtStats) {
    val tone = stats.toTone()
    val scheme = MaterialTheme.colorScheme

    // Two brand colours only. Colour is never the only signal: every level also has an icon and a word.
    val container = if (tone == RiskTone.DANGEROUS) scheme.error else scheme.surfaceVariant
    val content = when (tone) {
        RiskTone.SAFE -> scheme.onSurfaceVariant
        RiskTone.SUSPICIOUS -> scheme.error
        RiskTone.DANGEROUS -> scheme.onError
    }
    val border = if (tone == RiskTone.SUSPICIOUS) BorderStroke(2.dp, scheme.error) else null
    val icon = if (tone == RiskTone.SAFE) Icons.Filled.CheckCircle else Icons.Filled.Warning
    val title = when (tone) {
        RiskTone.SAFE -> "Safe"
        RiskTone.SUSPICIOUS -> "Suspicious"
        RiskTone.DANGEROUS -> "Dangerous"
    }
    val advice = when (tone) {
        RiskTone.SAFE -> "No security engine flagged this link. This is not a guarantee."
        RiskTone.SUSPICIOUS -> "Some engines flagged this link. Be careful and avoid entering any details."
        RiskTone.DANGEROUS -> "Many engines flagged this link. Do not open it."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = border
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(8.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(advice, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "${stats.malicious} malicious, ${stats.suspicious} suspicious, ${stats.harmless} harmless",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(url, style = MaterialTheme.typography.bodySmall)
        }
    }
}
