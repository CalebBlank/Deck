package com.hermes.deck.ui.search

import android.content.Context
import java.text.Normalizer

/**
 * Minimal BERT WordPiece tokenizer (uncased) matching HuggingFace's bert-base-uncased /
 * all-MiniLM-L6-v2 tokenizer for English text:
 *   clean → whitespace-split → lowercase + strip accents → split on punctuation → greedy WordPiece
 *   (## continuation, whole-token [UNK] fallback) → wrap with [CLS] … [SEP].
 * Produces the input_ids / attention_mask the ONNX model expects. (CJK/surrogate handling omitted —
 * search queries are short English; mismatches there only soften the embedding, never crash.)
 */
class WordPieceTokenizer private constructor(private val vocab: Map<String, Int>) {
    private val unkId = vocab["[UNK]"] ?: 100
    private val clsId = vocab["[CLS]"] ?: 101
    private val sepId = vocab["[SEP]"] ?: 102

    class Encoded(val ids: LongArray, val mask: LongArray)

    fun encode(text: String, maxLen: Int = 64): Encoded {
        val pieces = ArrayList<Int>(maxLen)
        pieces.add(clsId)
        outer@ for (tok in basicTokenize(text)) {
            for (id in wordpiece(tok)) {
                if (pieces.size >= maxLen - 1) break@outer   // keep room for [SEP]
                pieces.add(id)
            }
        }
        pieces.add(sepId)
        val ids = LongArray(pieces.size) { pieces[it].toLong() }
        val mask = LongArray(pieces.size) { 1L }
        return Encoded(ids, mask)
    }

    private fun wordpiece(token: String): List<Int> {
        if (token.length > 100) return listOf(unkId)
        val out = ArrayList<Int>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var found: Int? = null
            while (start < end) {
                val piece = (if (start > 0) "##" else "") + token.substring(start, end)
                val id = vocab[piece]
                if (id != null) { found = id; break }
                end--
            }
            if (found == null) return listOf(unkId)   // any unmatchable char → whole token is [UNK]
            out.add(found)
            start = end
        }
        return out
    }

    private fun basicTokenize(text: String): List<String> {
        val out = ArrayList<String>()
        for (ws in clean(text).trim().split(Regex("\\s+"))) {
            if (ws.isEmpty()) continue
            val norm = stripAccents(ws.lowercase())
            val sb = StringBuilder()
            for (c in norm) {
                if (isPunct(c)) {
                    if (sb.isNotEmpty()) { out.add(sb.toString()); sb.setLength(0) }
                    out.add(c.toString())
                } else sb.append(c)
            }
            if (sb.isNotEmpty()) out.add(sb.toString())
        }
        return out
    }

    private fun clean(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            if (c.code == 0 || c.code == 0xFFFD || isControl(c)) continue
            sb.append(if (c.isWhitespace()) ' ' else c)
        }
        return sb.toString()
    }

    private fun stripAccents(s: String): String {
        val nfd = Normalizer.normalize(s, Normalizer.Form.NFD)
        val sb = StringBuilder(nfd.length)
        for (c in nfd) if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) sb.append(c)
        return sb.toString()
    }

    private fun isControl(c: Char): Boolean {
        if (c == '\t' || c == '\n' || c == '\r') return false
        val t = Character.getType(c)
        return t == Character.CONTROL.toInt() || t == Character.FORMAT.toInt()
    }

    private fun isPunct(c: Char): Boolean {
        val cp = c.code
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
        return when (Character.getType(c)) {
            Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt() -> true
            else -> false
        }
    }

    companion object {
        fun fromAsset(context: Context, name: String): WordPieceTokenizer {
            val vocab = HashMap<String, Int>(32000)
            context.assets.open(name).bufferedReader().useLines { lines ->
                var i = 0
                for (line in lines) { vocab[line] = i; i++ }
            }
            return WordPieceTokenizer(vocab)
        }
    }
}
