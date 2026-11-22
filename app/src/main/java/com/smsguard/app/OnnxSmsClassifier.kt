
package com.smsguard.app

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File

data class SmsPrediction(
    val label: String,
    val isSuspicious: Boolean
)

class OnnxSmsClassifier(context: Context) {

    companion object {
        private const val TAG = "SMS_EMBEDDINGS"
        private const val ASSET_TAG = "ASSET_TEST"
        private const val EMBEDDING_SIZE = 384
    }

    private val appContext = context.applicationContext
    private val environment = OrtEnvironment.getEnvironment()

    private val encoderSession: OrtSession
    private val classifierSession: OrtSession

    init {
        Log.d(TAG, "Initialisation du nouveau modèle")

        // TEST 1 : Vérification des assets
        testAssets()

        // TEST 2 : Copie des modèles ONNX depuis la racine de assets
        val encoderFile = copyAssetModel(
            "sentence_encoder_int8.onnx",
            "sentence_encoder_int8.onnx"
        )

        val classifierFile = copyAssetModel(
            "sms_emb_svm.onnx",
            "sms_emb_svm.onnx"
        )

        // TEST 3 : Chargement des sessions ONNX
        encoderSession = environment.createSession(
            encoderFile.absolutePath
        )

        classifierSession = environment.createSession(
            classifierFile.absolutePath
        )

        // Vérification des entrées et sorties de l'encodeur
        require("input_ids" in encoderSession.inputNames) {
            "Entrée input_ids absente : ${encoderSession.inputNames}"
        }

        require("attention_mask" in encoderSession.inputNames) {
            "Entrée attention_mask absente : ${encoderSession.inputNames}"
        }

        require("embedding" in encoderSession.outputNames) {
            "Sortie embedding absente : ${encoderSession.outputNames}"
        }

        // Vérification du classifieur
        require("input" in classifierSession.inputNames) {
            "Entrée SVM absente : ${classifierSession.inputNames}"
        }

        require("label" in classifierSession.outputNames) {
            "Sortie label absente : ${classifierSession.outputNames}"
        }

        Log.d(TAG, "Encodeur chargé : sentence_encoder_int8.onnx")
        Log.d(TAG, "Classifieur chargé : sms_emb_svm.onnx")
        Log.d(TAG, "Entrées et sorties ONNX vérifiées")
    }

    // Diagnostic des fichiers dans la racine de assets
    private fun testAssets() {

        Log.d(ASSET_TAG, "========== DEBUT TEST ASSETS ==========")

        try {
            // 1. Vérifier les fichiers de la racine
            val rootFiles = appContext.assets.list("")

            Log.d(
                ASSET_TAG,
                "Assets racine : ${rootFiles?.joinToString()}"
            )

            // 2. Vérifier directement l'encodeur
            appContext.assets.open(
                "sentence_encoder_int8.onnx"
            ).use {
                Log.d(
                    ASSET_TAG,
                    "SUCCES : sentence_encoder_int8.onnx accessible"
                )
            }

            // 3. Vérifier le classifieur
            appContext.assets.open(
                "sms_emb_svm.onnx"
            ).use {
                Log.d(
                    ASSET_TAG,
                    "SUCCES : sms_emb_svm.onnx accessible"
                )
            }

            // 4. Vérifier la configuration
            appContext.assets.open(
                "embedding_config.json"
            ).use {
                Log.d(
                    ASSET_TAG,
                    "SUCCES : embedding_config.json accessible"
                )
            }

            // 5. Vérifier le tokenizer
            appContext.assets.open(
                "tokenizer/tokenizer.json"
            ).use {
                Log.d(
                    ASSET_TAG,
                    "SUCCES : tokenizer.json accessible"
                )
            }

            appContext.assets.open(
                "tokenizer/tokenizer_config.json"
            ).use {
                Log.d(
                    ASSET_TAG,
                    "SUCCES : tokenizer_config.json accessible"
                )
            }

            Log.d(
                ASSET_TAG,
                "Tous les fichiers nécessaires sont accessibles."
            )

        } catch (e: Exception) {

            Log.e(
                ASSET_TAG,
                "ERREUR ASSETS : ${e.message}",
                e
            )
        }

        Log.d(ASSET_TAG, "========== FIN TEST ASSETS ==========")
    }

    @Synchronized
    fun analyze(message: String): SmsPrediction {

        require(message.isNotBlank()) {
            "Le SMS est vide."
        }

        Log.d(TAG, "Début de l'analyse Embeddings + SVM")

        // 1. Tokenisation du SMS
        val tokens = HuggingFaceTokenizerHelper.encode(
            appContext,
            message
        )

        Log.d(TAG, "Tokenisation terminée")

        // 2. Création des tenseurs d'entrée
        val idsTensor = OnnxTensor.createTensor(
            environment,
            arrayOf(tokens.inputIds)
        )

        val maskTensor = OnnxTensor.createTensor(
            environment,
            arrayOf(tokens.attentionMask)
        )

        val embedding: FloatArray

        try {

            // 3. Exécution du Sentence Encoder
            encoderSession.run(
                mapOf(
                    "input_ids" to idsTensor,
                    "attention_mask" to maskTensor
                )
            ).use { encoderResult ->

                val embeddingOutput = encoderResult
                    .get("embedding")
                    .orElseThrow {
                        IllegalStateException(
                            "Sortie embedding absente"
                        )
                    }

                embedding = extractFloatArray(
                    embeddingOutput.value
                )

                require(embedding.size == EMBEDDING_SIZE) {
                    "Dimension embedding incorrecte : ${embedding.size}"
                }
            }

        } finally {
            idsTensor.close()
            maskTensor.close()
        }

        Log.d(
            TAG,
            "Embedding obtenu : ${embedding.size} valeurs"
        )

        // 4. Préparation de l'entrée du SVM
        val embeddingTensor = OnnxTensor.createTensor(
            environment,
            arrayOf(embedding)
        )

        try {

            // 5. Classification SVM
            classifierSession.run(
                mapOf("input" to embeddingTensor)
            ).use { classifierResult ->

                val labelOutput = classifierResult
                    .get("label")
                    .orElseThrow {
                        IllegalStateException(
                            "Sortie label absente"
                        )
                    }

                val predictedClass = extractNumber(
                    labelOutput.value
                ).toInt()

                Log.d(
                    TAG,
                    "Classe prédite : $predictedClass"
                )

                return when (predictedClass) {

                    0 -> SmsPrediction(
                        "Normal",
                        false
                    )

                    1 -> SmsPrediction(
                        "Suspect",
                        true
                    )

                    else -> throw IllegalStateException(
                        "Classe inconnue : $predictedClass"
                    )
                }
            }

        } finally {
            embeddingTensor.close()
        }
    }

    // Copie d'un modèle depuis assets vers filesDir
    private fun copyAssetModel(
        assetPath: String,
        destinationName: String
    ): File {

        val destination = File(
            appContext.filesDir,
            destinationName
        )

        // Toujours recopier pour éviter d'utiliser une ancienne version
        Log.d(TAG, "Copie du fichier : $assetPath")

        appContext.assets.open(assetPath).use { input ->

            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        Log.d(
            TAG,
            "Copie terminée : ${destination.absolutePath}"
        )

        return destination
    }

    // Extraction du vecteur d'embedding
    private fun extractFloatArray(value: Any?): FloatArray {

        return when (value) {

            is FloatArray -> value

            is Array<*> -> {
                require(value.isNotEmpty()) {
                    "Embedding vide"
                }

                extractFloatArray(value[0])
            }

            else -> throw IllegalStateException(
                "Format embedding non reconnu : ${value?.javaClass}"
            )
        }
    }

    // Extraction de la classe prédite
    private fun extractNumber(value: Any?): Number {

        return when (value) {

            is Number -> value

            is LongArray -> value.first()
            is IntArray -> value.first()
            is FloatArray -> value.first()
            is DoubleArray -> value.first()

            is Array<*> -> {
                require(value.isNotEmpty()) {
                    "Sortie ONNX vide"
                }

                extractNumber(value.first())
            }

            else -> throw IllegalStateException(
                "Format de sortie non reconnu : ${value?.javaClass}"
            )
        }
    }

    fun close() {
        encoderSession.close()
        classifierSession.close()
    }
}
