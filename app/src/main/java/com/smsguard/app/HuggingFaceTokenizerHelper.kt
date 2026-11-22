
package com.smsguard.app

import android.content.Context
import android.util.Log
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer

data class TokenizedSms(
    val inputIds: LongArray,
    val attentionMask: LongArray
)

object HuggingFaceTokenizerHelper {

    private const val TAG = "HF_TOKENIZER"

    private const val MAX_LENGTH = 128
    private const val PAD_TOKEN_ID = 1L

    private var tokenizer: HuggingFaceTokenizer? = null

    @Synchronized
    fun initialize(context: Context) {

        if (tokenizer != null) {
            return
        }

        Log.d(TAG, "Initialisation du tokenizer Hugging Face")

        try {
            val inputStream = context.applicationContext.assets
                .open("tokenizer/tokenizer.json")

            tokenizer = inputStream.use { stream ->
                HuggingFaceTokenizer.newInstance(stream, null)
            }

            Log.d(TAG, "Tokenizer chargé avec succès")

        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation tokenizer", e)
            throw e
        }
    }

    @Synchronized
    fun encode(
        context: Context,
        message: String
    ): TokenizedSms {

        initialize(context)

        require(message.isNotBlank()) {
            "Le SMS est vide."
        }

        // Même prétraitement que pendant l'entraînement
        val cleaned = message
            .lowercase()
            .replace(
                Regex("https?://\\S+|www\\.\\S+"),
                " URL "
            )
            .replace(Regex("\\s+"), " ")
            .trim()

        Log.d(TAG, "Prétraitement terminé")

        val currentTokenizer = tokenizer
            ?: throw IllegalStateException("Tokenizer non initialisé")

        // Tokenisation avec les tokens spéciaux
        val encoding = currentTokenizer.encode(
            cleaned,
            true,
            false
        )

        val ids = encoding.getIds()
        val mask = encoding.getAttentionMask()

        require(ids.size == mask.size) {
            "Les dimensions des tokens et du masque sont différentes"
        }

        // Préparation des tableaux de longueur fixe
        val finalIds = LongArray(MAX_LENGTH) {
            PAD_TOKEN_ID
        }

        val finalMask = LongArray(MAX_LENGTH) {
            0L
        }

        val count = minOf(ids.size, MAX_LENGTH)

        for (i in 0 until count) {
            finalIds[i] = ids[i]
            finalMask[i] = mask[i]
        }

        Log.d(
            TAG,
            "Tokenisation réussie : $count tokens"
        )

        return TokenizedSms(
            inputIds = finalIds,
            attentionMask = finalMask
        )
    }
}
