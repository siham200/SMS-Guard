
package com.smsguard.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class TokenizedInput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokens: List<String>
)

class WordPieceTokenizer(
    context: Context,
    private val maxLength: Int = 128
) {
    private val vocab = mutableMapOf<String, Int>()

    private val clsToken = "[CLS]"
    private val sepToken = "[SEP]"
    private val unkToken = "[UNK]"
    private val padToken = "[PAD]"

    init {
        context.assets.open("vocab.txt").use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines {
                    lines ->
                lines.forEachIndexed { index, token ->
                    vocab[token] = index
                }
            }
        }

        require(vocab.containsKey(clsToken))
        require(vocab.containsKey(sepToken))
        require(vocab.containsKey(unkToken))
        require(vocab.containsKey(padToken))
    }

    private fun isWhitespace(cp: Int): Boolean {
        return Character.isWhitespace(cp) ||
                Character.isSpaceChar(cp)
    }

    private fun isControl(cp: Int): Boolean {
        val type = Character.getType(cp)
        return type == Character.CONTROL.toInt() ||
                type == Character.FORMAT.toInt()
    }

    private fun isChinese(cp: Int): Boolean {
        return cp in 0x4E00..0x9FFF ||
                cp in 0x3400..0x4DBF ||
                cp in 0x20000..0x2A6DF ||
                cp in 0x2A700..0x2B73F ||
                cp in 0x2B740..0x2B81F ||
                cp in 0x2B820..0x2CEAF ||
                cp in 0xF900..0xFAFF ||
                cp in 0x2F800..0x2FA1F
    }

    private fun isPunctuation(cp: Int): Boolean {
        val type = Character.getType(cp)

        return type == Character.CONNECTOR_PUNCTUATION.toInt() ||
                type == Character.DASH_PUNCTUATION.toInt() ||
                type == Character.START_PUNCTUATION.toInt() ||
                type == Character.END_PUNCTUATION.toInt() ||
                type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.OTHER_PUNCTUATION.toInt() ||
                type == Character.MATH_SYMBOL.toInt() ||
                type == Character.CURRENCY_SYMBOL.toInt() ||
                type == Character.MODIFIER_SYMBOL.toInt() ||
                type == Character.OTHER_SYMBOL.toInt()
    }

    private fun basicTokenize(text: String): List<String> {
        val cleaned = StringBuilder()

        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val char = String(Character.toChars(cp))

            when {
                cp == 0 || cp == 0xFFFD -> Unit
                isWhitespace(cp) -> cleaned.append(' ')
                isControl(cp) -> Unit
                isChinese(cp) -> cleaned.append(" $char ")
                else -> cleaned.append(char)
            }

            i += Character.charCount(cp)
        }

        val result = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                result.add(current.toString())
                current.clear()
            }
        }

        i = 0
        val normalized = cleaned.toString()

        while (i < normalized.length) {
            val cp = normalized.codePointAt(i)
            val char = String(Character.toChars(cp))

            when {
                isWhitespace(cp) -> flush()
                isPunctuation(cp) -> {
                    flush()
                    result.add(char)
                }
                else -> current.append(char)
            }

            i += Character.charCount(cp)
        }

        flush()
        return result
    }

    private fun wordPiece(word: String): List<String> {
        val characters = word.codePoints()
            .toArray()
            .map { String(Character.toChars(it)) }

        if (characters.size > 100) {
            return listOf(unkToken)
        }

        val pieces = mutableListOf<String>()
        var start = 0

        while (start < characters.size) {
            var end = characters.size
            var found: String? = null

            while (start < end) {
                val part = characters.subList(start, end)
                    .joinToString("")

                val candidate = if (start == 0) {
                    part
                } else {
                    "##$part"
                }

                if (vocab.containsKey(candidate)) {
                    found = candidate
                    break
                }

                end--
            }

            if (found == null) {
                return listOf(unkToken)
            }

            pieces.add(found)
            start = end
        }

        return pieces
    }

    fun encode(text: String): TokenizedInput {
        require(maxLength >= 2)

        val basicTokens = basicTokenize(text)
        val wordPieces = mutableListOf<String>()

        for (word in basicTokens) {
            wordPieces.addAll(wordPiece(word))
        }

        val availableLength = maxLength - 2
        val truncated = wordPieces.take(availableLength)

        val finalTokens = listOf(clsToken) +
                truncated +
                listOf(sepToken)

        val ids = LongArray(maxLength)
        val mask = LongArray(maxLength)

        finalTokens.forEachIndexed { index, token ->
            ids[index] = vocab[token]?.toLong()
                ?: vocab[unkToken]!!.toLong()

            mask[index] = 1L
        }

        val padId = vocab[padToken]!!.toLong()

        for (i in finalTokens.size until maxLength) {
            ids[i] = padId
            mask[i] = 0L
        }

        return TokenizedInput(
            inputIds = ids,
            attentionMask = mask,
            tokens = finalTokens
        )
    }
}
