
package com.smsguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import java.util.concurrent.Executors

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SMS_GUARD"
        private val executor = Executors.newSingleThreadExecutor()
    }

    override fun onReceive(context: Context, intent: Intent) {

        Log.d(TAG, "Broadcast SMS reçu par SmsReceiver")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "Action ignorée : ${intent.action}")
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        executor.execute {
            try {
                val messages =
                    Telephony.Sms.Intents.getMessagesFromIntent(intent)

                if (messages.isEmpty()) {
                    Log.e(TAG, "Aucun SMS trouvé dans le broadcast")
                    return@execute
                }

                val sender =
                    messages.firstOrNull()?.originatingAddress ?: "Inconnu"

                val messageBody =
                    messages.joinToString("") { it.messageBody ?: "" }

                if (messageBody.isBlank()) {
                    Log.e(TAG, "Le contenu du SMS est vide")
                    return@execute
                }

                Log.d(TAG, "SMS reçu, début de l'analyse")

                val prediction = SmsClassifierEngine.analyze(
                    appContext,
                    messageBody
                )

                Log.d(TAG, "Expéditeur : $sender")
                Log.d(TAG, "Résultat : ${prediction.label}")
                Log.d(TAG, "Suspect : ${prediction.isSuspicious}")

                val historyItem = SmsHistoryItem(
                    id = System.currentTimeMillis(),
                    sender = sender,
                    message = messageBody,
                    label = prediction.label,
                    timestamp = System.currentTimeMillis()
                )

                SmsHistoryRepository.save(appContext, historyItem)

                Log.d(TAG, "SMS enregistré dans l'historique")

                if (prediction.isSuspicious) {
                    NotificationHelper.showSuspiciousNotification(
                        appContext,
                        sender
                    )
                    Log.d(TAG, "Notification suspecte demandée")
                } else {
                    Log.d(TAG, "SMS normal : aucune notification")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erreur pendant l'analyse du SMS", e)

            } finally {
                pendingResult.finish()
            }
        }
    }
}
