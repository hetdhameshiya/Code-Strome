package com.codestrome.app

import android.util.Base64
import com.codestrome.app.ui.theme.RiskTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class VtStats(
    val malicious: Int,
    val suspicious: Int,
    val harmless: Int,
    val undetected: Int
)

sealed class VtResult {
    data class Done(val stats: VtStats) : VtResult()
    object NotFound : VtResult()
    data class Failed(val message: String) : VtResult()
}

/** Proposed mapping from VirusTotal counts to the app's three risk levels. Tune as needed. */
fun VtStats.toTone(): RiskTone = when {
    malicious == 0 && suspicious == 0 -> RiskTone.SAFE
    malicious >= 3 -> RiskTone.DANGEROUS
    else -> RiskTone.SUSPICIOUS
}

object VirusTotalApi {
    private const val BASE = "https://www.virustotal.com/api/v3"
    private val apiKey: String = BuildConfig.VT_API_KEY

    fun hasKey(): Boolean = apiKey.isNotBlank()

    private class Response(val code: Int, val body: String)

    private fun request(method: String, path: String, form: String? = null): Response {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("x-apikey", apiKey)
            conn.setRequestProperty("accept", "application/json")
            if (form != null) {
                conn.doOutput = true
                conn.setRequestProperty("content-type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(form.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Response(code, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun urlId(url: String): String =
        Base64.encodeToString(
            url.toByteArray(),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )

    private fun parseStats(json: JSONObject) = VtStats(
        malicious = json.optInt("malicious"),
        suspicious = json.optInt("suspicious"),
        harmless = json.optInt("harmless"),
        undetected = json.optInt("undetected")
    )

    private fun errorText(r: Response): String = when (r.code) {
        429 -> "VirusTotal rate limit reached (free key: 4 requests per minute, 500 per day). Wait a minute and try again."
        401 -> "VirusTotal rejected the API key. Check VT_API_KEY in local.properties."
        400 -> "VirusTotal could not read that link. Check it and try again."
        else -> "VirusTotal error (code ${r.code}). Try again later."
    }

    private const val NETWORK_ERROR = "Could not reach VirusTotal. Check your internet connection."

    /** Looks up a link VirusTotal has already seen. Does not send the link for a new scan. */
    suspend fun lookup(url: String): VtResult = withContext(Dispatchers.IO) {
        try {
            val r = request("GET", "/urls/${urlId(url)}")
            when (r.code) {
                200 -> {
                    val attrs = JSONObject(r.body).getJSONObject("data").getJSONObject("attributes")
                    VtResult.Done(parseStats(attrs.getJSONObject("last_analysis_stats")))
                }
                404 -> VtResult.NotFound
                else -> VtResult.Failed(errorText(r))
            }
        } catch (e: Exception) {
            VtResult.Failed(NETWORK_ERROR)
        }
    }

    /** Sends the link to VirusTotal for a new scan, then waits for the result. */
    suspend fun scan(url: String): VtResult = withContext(Dispatchers.IO) {
        try {
            val submit = request("POST", "/urls", "url=" + URLEncoder.encode(url, "UTF-8"))
            if (submit.code !in 200..299) {
                return@withContext VtResult.Failed(errorText(submit))
            }
            val id = JSONObject(submit.body).getJSONObject("data").getString("id")

            // Few polls, spaced out, because the free key allows only 4 requests per minute.
            repeat(3) {
                delay(20_000)
                val r = request("GET", "/analyses/$id")
                if (r.code != 200) {
                    return@withContext VtResult.Failed(errorText(r))
                }
                val attrs = JSONObject(r.body).getJSONObject("data").getJSONObject("attributes")
                if (attrs.optString("status") == "completed") {
                    return@withContext VtResult.Done(parseStats(attrs.getJSONObject("stats")))
                }
            }
            VtResult.Failed("The scan is still running. Try checking the link again in a minute.")
        } catch (e: Exception) {
            VtResult.Failed(NETWORK_ERROR)
        }
    }
}
