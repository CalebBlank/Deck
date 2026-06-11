package com.hermes.deck.ui.search

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * Sentence embedder: all-MiniLM-L6-v2 (a similarity-tuned sentence-transformer) run via ONNX Runtime.
 * Tokenize → model → mean-pool token vectors over the attention mask → L2-normalize → 384-d unit
 * vector. Because outputs are unit vectors, cosine similarity is just the dot product.
 */
class MiniLmEmbedder private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val tokenizer: WordPieceTokenizer
) {
    private val inputNames = session.inputNames

    fun embed(text: String): FloatArray? = runCatching {
        val enc = tokenizer.encode(text)
        val n = enc.ids.size
        val shape = longArrayOf(1, n.toLong())
        val ids   = OnnxTensor.createTensor(env, LongBuffer.wrap(enc.ids), shape)
        val mask  = OnnxTensor.createTensor(env, LongBuffer.wrap(enc.mask), shape)
        val types = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(n) { 0L }), shape)
        val feed = HashMap<String, OnnxTensor>()
        if ("input_ids" in inputNames) feed["input_ids"] = ids
        if ("attention_mask" in inputNames) feed["attention_mask"] = mask
        if ("token_type_ids" in inputNames) feed["token_type_ids"] = types

        val pooled: FloatArray = session.run(feed).use { result ->
            @Suppress("UNCHECKED_CAST")
            val out = result[0].value as Array<Array<FloatArray>>   // [1, n, dim]
            val dim = out[0][0].size
            val acc = FloatArray(dim)
            var count = 0
            for (t in 0 until n) {
                if (enc.mask[t] == 0L) continue
                val tok = out[0][t]
                for (d in 0 until dim) acc[d] += tok[d]
                count++
            }
            if (count > 0) for (d in acc.indices) acc[d] /= count
            var norm = 0f
            for (v in acc) norm += v * v
            norm = sqrt(norm)
            if (norm > 0f) for (d in acc.indices) acc[d] /= norm
            acc
        }
        ids.close(); mask.close(); types.close()
        pooled
    }.getOrNull()

    companion object {
        fun create(context: Context, modelAsset: String, vocabAsset: String): MiniLmEmbedder {
            val env = OrtEnvironment.getEnvironment()
            val bytes = context.assets.open(modelAsset).use { it.readBytes() }
            val session = env.createSession(bytes, OrtSession.SessionOptions())
            val tok = WordPieceTokenizer.fromAsset(context, vocabAsset)
            return MiniLmEmbedder(env, session, tok)
        }
    }
}
