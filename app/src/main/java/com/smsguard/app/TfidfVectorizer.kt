
package com.smsguard.app

import android.content.Context
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.ln
import kotlin.math.sqrt

class TfidfVectorizer(context: Context) {

    private val vocabulary = HashMap<String, Int>()
    private val idf: FloatArray

    private val sublinearTf: Boolean
    private val normalizeL2: Boolean
    private val lowercase: Boolean

    private val minNgram: Int
    private val maxNgram: Int

    private val tokenPattern: Pattern

    // Détecte les URL commençant par http://, https:// ou www.
    private val urlPattern =
        Regex("""https?://\S+|www\.\S+""")

    init {
        val json = context.assets
            .open("tfidf_config.json")
            .bufferedReader(Charsets.UTF_8)
            .use { JSONObject(it.readText()) }

        val featureCount = json.getInt("n_features")

        val vocabJson = json.getJSONObject("vocabulary")
        val keys = vocabJson.keys()

        while (keys.hasNext()) {
            val word = keys.next()
            vocabulary[word] = vocabJson.getInt(word)
        }

        require(vocabulary.size == featureCount) {
            "Taille du vocabulaire incorrecte"
        }

        val idfJson = json.getJSONArray("idf")

        require(idfJson.length() == featureCount) {
            "Taille du tableau IDF incorrecte"
        }

        idf = FloatArray(featureCount) { index ->
            idfJson.getDouble(index).toFloat()
        }

        sublinearTf = json.getBoolean("sublinear_tf")
        normalizeL2 = json.getString("norm") == "l2"
        lowercase = json.optBoolean("lowercase", true)

        val ngramRange = json.getJSONArray("ngram_range")
        minNgram = ngramRange.getInt(0)
        maxNgram = ngramRange.getInt(1)


        tokenPattern = Pattern.compile("[\\p{L}\\p{N}_]{2,}")
    }

    fun transform(message: String): FloatArray {

        // 1. Mettre le texte en minuscules.
        var cleaned = if (lowercase) {
            message.lowercase(Locale.ROOT)
        } else {
            message
        }

        // 2. Remplacer les URL par le token "url".
        cleaned = cleaned.replace(urlPattern, " url ")

        // 3. Remplacer les espaces multiples par un seul espace.
        cleaned = cleaned
            .replace(Regex("\\s+"), " ")
            .trim()

        // 4. Extraire les tokens.
        val matcher = tokenPattern.matcher(cleaned)
        val tokens = mutableListOf<String>()

        while (matcher.find()) {
            tokens.add(matcher.group())
        }

        // 5. Construire les unigrammes et bigrammes.
        val counts = HashMap<Int, Int>()

        for (n in minNgram..maxNgram) {

            if (tokens.size < n) continue

            for (i in 0..tokens.size - n) {

                val term = tokens
                    .subList(i, i + n)
                    .joinToString(" ")

                val index = vocabulary[term] ?: continue

                counts[index] = (counts[index] ?: 0) + 1
            }
        }

        // 6. Calculer le vecteur TF-IDF.
        val vector = FloatArray(idf.size)

        for ((index, count) in counts) {

            val tf = if (sublinearTf) {
                1.0 + ln(count.toDouble())
            } else {
                count.toDouble()
            }

            vector[index] = (tf * idf[index]).toFloat()
        }

        // 7. Normalisation L2.
        if (normalizeL2) {

            var sum = 0.0

            for (value in vector) {
                sum += value.toDouble() * value.toDouble()
            }

            val norm = sqrt(sum)

            if (norm > 0.0) {
                for (i in vector.indices) {
                    vector[i] = (vector[i] / norm).toFloat()
                }
            }
        }

        return vector
    }
}
