package com.codestrome.app

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Keeps a small list of reported scam numbers on the phone, so the call warning
 * service can check an incoming call instantly, even with a weak connection.
 */
object CallCache {
    private const val PREFS = "call_cache"
    private const val KEY_REPORTED = "reported_numbers"
    private const val KEY_MINE = "my_reports"

    // How many reports a number needs before it triggers a warning. Raise this (for example to 3)
    // to reduce false alarms once many people use the app.
    private const val MIN_REPORTS = 1L

    /** Keeps the last 10 digits, so +91 98765 43210 and 09876543210 match each other. */
    fun normalize(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    fun isValid(number: String): Boolean = number.length in 7..10

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context, key: String): Set<String> =
        prefs(context).getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun write(context: Context, key: String, values: Set<String>) {
        prefs(context).edit().putStringSet(key, values).apply()
    }

    fun isReported(context: Context, number: String): Boolean =
        read(context, KEY_REPORTED).contains(number)

    fun addReported(context: Context, number: String) {
        write(context, KEY_REPORTED, read(context, KEY_REPORTED) + number)
    }

    fun hasReportedByMe(context: Context, number: String): Boolean =
        read(context, KEY_MINE).contains(number)

    fun markReportedByMe(context: Context, number: String) {
        write(context, KEY_MINE, read(context, KEY_MINE) + number)
    }

    /** Downloads the reported numbers from Firestore into the local cache. */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        FirebaseFirestore.getInstance()
            .collection("reportedNumbers")
            .limit(1000)
            .get()
            .addOnSuccessListener { snap ->
                val numbers = snap.documents
                    .filter { (it.getLong("count") ?: 0L) >= MIN_REPORTS }
                    .map { it.id }
                    .toSet()
                write(appContext, KEY_REPORTED, numbers)
            }
    }
}
