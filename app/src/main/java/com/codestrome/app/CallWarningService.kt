package com.codestrome.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat

/**
 * Android calls this for every incoming call once the user makes Code Strome
 * their "Caller ID & spam app". It always lets the call ring, and shows a warning
 * notification when the number has been reported.
 */
class CallWarningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val incoming = Build.VERSION.SDK_INT < 29 ||
                callDetails.callDirection == Call.Details.DIRECTION_INCOMING

        if (incoming) {
            val raw = callDetails.handle?.schemeSpecificPart
            if (raw != null) {
                val number = CallCache.normalize(raw)
                if (CallCache.isValid(number) && CallCache.isReported(this, number)) {
                    showWarning(raw)
                }
            }
        }

        // An empty response allows the call as normal.
        respondToCall(callDetails, CallResponse.Builder().build())
    }

    private fun showWarning(number: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val channelId = "call_warnings"

        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Call warnings", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Possible scam call")
            .setContentText("$number was reported by other users. Do not share OTPs or passwords.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$number was reported by other users. Do not share OTPs or passwords, and hang up if you feel pressured.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        manager.notify(number.hashCode(), notification)
    }
}
