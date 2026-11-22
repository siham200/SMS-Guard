
package com.smsguard.app

import android.content.Context
import android.util.Log

object TokenizerTest {

    fun run(context: Context) {
        try {
            val result = HuggingFaceTokenizerHelper.encode(
                context,
                "Votre colis est en attente de livraison."
            )

            Log.d("TOKENIZER_TEST", "Tokenizer fonctionne")
            Log.d(
                "TOKENIZER_TEST",
                "Nombre de tokens : ${result.inputIds.count { it != 1L }}"
            )
            Log.d(
                "TOKENIZER_TEST",
                "Input IDs : ${result.inputIds.joinToString()}"
            )
            Log.d(
                "TOKENIZER_TEST",
                "Attention mask : ${result.attentionMask.joinToString()}"
            )

        } catch (e: Exception) {
            Log.e("TOKENIZER_TEST", "Erreur tokenizer", e)
        }
    }
}
