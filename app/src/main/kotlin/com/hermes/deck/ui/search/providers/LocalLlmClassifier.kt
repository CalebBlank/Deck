package com.hermes.deck.ui.search.providers

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Query-intent classifier that runs a small instruction LLM (Qwen2.5-0.5B-Instruct) fully on-device
 * via MediaPipe's LLM Inference. This is the fallback for devices where AICore won't serve Gemini Nano
 * (rooted / beta builds, like Caleb's Pixel) — it ships nothing in the APK and downloads the ~547 MB
 * `.task` model on demand the first time the feature is enabled.
 *
 * Same contract as [GeminiNanoClassifier]: [classify] returns a subset of [GeminiNanoClassifier.DOMAINS]
 * or null (model not ready / inference failed) so ranking falls back to the heuristic. Everything is
 * best-effort and never blocks search; the caller runs it as a late re-rank.
 */
object LocalLlmClassifier {
    private const val TAG = "LocalLlm"

    // Qwen2.5-0.5B-Instruct, q8, MediaPipe .task — Apache-2.0, NON-gated (verified public download).
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
        "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    private const val MODEL_FILE = "qwen2.5-0.5b-instruct-q8.task"
    private const val MODEL_BYTES = 546_660_344L

    /** UI-facing lifecycle for the model, surfaced in Settings so the user sees the download. */
    sealed class ModelState {
        object Idle : ModelState()
        object Loading : ModelState()                       // engine spinning up
        data class Downloading(val percent: Int) : ModelState()
        object Ready : ModelState()
        data class Failed(val message: String) : ModelState()
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.Idle)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    @Volatile private var engine: LlmInference? = null
    @Volatile private var failedPermanently = false
    private val initMutex = Mutex()
    // generateResponse is a synchronous, UNINTERRUPTIBLE native call on a single shared engine.
    // Fast typing settles queries faster than inference completes, and overlapping native calls CRASH
    // the app (a native abort runCatching can't catch). Serialize them: a superseded query's coroutine
    // is cancelled while it waits here and drops out cleanly; only one inference ever runs at a time.
    private val inferenceMutex = Mutex()

    private val cache = object : LinkedHashMap<String, Set<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<String>>?) = size > 64
    }

    private fun modelFile(context: Context): File =
        File(context.getExternalFilesDir("llm"), MODEL_FILE)

    /** True once the .task is present at full size; otherwise downloads it (with progress). */
    private suspend fun ensureModel(context: Context): Boolean = withContext(Dispatchers.IO) {
        val file = modelFile(context)
        if (file.exists() && file.length() == MODEL_BYTES) return@withContext true
        runCatching { download(context, file) }
            .onFailure { Log.w(TAG, "model download failed: ${it.message}"); _state.value = ModelState.Failed("download failed") }
            .getOrDefault(false)
    }

    private fun download(context: Context, dest: File): Boolean {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "$MODEL_FILE.part")
        _state.value = ModelState.Downloading(0)
        val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000; readTimeout = 60_000; instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) { _state.value = ModelState.Failed("HTTP ${conn.responseCode}"); return false }
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else MODEL_BYTES
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var read: Int; var done = 0L; var lastPct = -1
                    while (input.read(buf).also { read = it } >= 0) {
                        out.write(buf, 0, read); done += read
                        val pct = ((done * 100) / total).toInt()
                        if (pct != lastPct) { lastPct = pct; _state.value = ModelState.Downloading(pct) }
                    }
                }
            }
        } finally { conn.disconnect() }
        if (tmp.length() != MODEL_BYTES) { tmp.delete(); _state.value = ModelState.Failed("incomplete download"); return false }
        if (dest.exists()) dest.delete()
        return tmp.renameTo(dest)
    }

    /** Get (or build) the inference engine. null when the model isn't ready yet / engine failed. */
    private suspend fun ensureEngine(context: Context): LlmInference? {
        engine?.let { return it }
        if (failedPermanently) return null
        return initMutex.withLock {
            engine?.let { return it }
            if (!ensureModel(context)) return null
            _state.value = ModelState.Loading
            withContext(Dispatchers.IO) {
                runCatching {
                    val opts = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile(context).absolutePath)
                        .setMaxTokens(1024)
                        .setMaxTopK(64)
                        .build()
                    LlmInference.createFromOptions(context, opts)
                }.onFailure {
                    Log.w(TAG, "engine init failed: ${it.message}")
                    failedPermanently = true
                    _state.value = ModelState.Failed("model load failed")
                }.getOrNull()?.also { engine = it; _state.value = ModelState.Ready }
            }
        }
    }

    /** Classify [query] into a subset of [GeminiNanoClassifier.DOMAINS]; null if not ready / failed. */
    suspend fun classify(context: Context, query: String): Set<String>? {
        val q = query.trim().lowercase()
        if (q.length < 2 || failedPermanently) return null
        synchronized(cache) { cache[q] }?.let { return it }
        val llm = ensureEngine(context) ?: return null
        val raw = withContext(Dispatchers.IO) {
            inferenceMutex.withLock {
                runCatching { llm.generateResponse(buildPrompt(q)) }
                    .onFailure { Log.w(TAG, "inference failed: ${it.message}") }
                    .getOrNull()
            }
        } ?: return null
        val domains = parse(raw)
        Log.i(TAG, "classify '$q' -> raw='${raw.take(120)}' domains=$domains")
        if (domains.isNotEmpty()) synchronized(cache) { cache[q] = domains }
        return domains.ifEmpty { null }
    }

    /** True once the model is downloaded and the engine is loaded — gates the offline-answer card so
     *  it only offers to answer when the model is actually usable (without forcing a download). */
    fun isReady(): Boolean = engine != null

    /** Free-form generation for the offline-answer card. Reuses the SAME [LlmInference] engine and
     *  [inferenceMutex] as [classify] — never a second engine (a second ~550MB load, and concurrent
     *  generateResponse was the native crash fixed in v671). null if the model isn't ready / failed.
     *  [maxChars] trims the answer (the model can ramble). */
    suspend fun generate(context: Context, prompt: String, maxChars: Int = 600): String? {
        if (failedPermanently) return null
        val llm = ensureEngine(context) ?: return null
        val out = withContext(Dispatchers.IO) {
            inferenceMutex.withLock {
                runCatching { llm.generateResponse(prompt) }
                    .onFailure { Log.w(TAG, "generate failed: ${it.message}") }
                    .getOrNull()
            }
        } ?: return null
        return out.trim().take(maxChars).ifBlank { null }
    }

    // Qwen2.5-Instruct chat template (ChatML) for reliable instruction-following.
    private fun buildPrompt(q: String): String =
        "<|im_start|>system\nYou label what a phone search query is about.<|im_end|>\n" +
        "<|im_start|>user\nChoose only from these categories: " +
        GeminiNanoClassifier.DOMAINS.joinToString(", ") + ". " +
        "Reply with ONLY the matching categories as a comma-separated list, most relevant first, no other words. " +
        "A superhero or fictional character is comic, movie, tv. A song, album, band, or musician is music. " +
        "If none fit, reply none.\n" +
        "Query: \"" + q + "\"<|im_end|>\n<|im_start|>assistant\n"

    private fun parse(raw: String): Set<String> =
        raw.lowercase().split(Regex("[^a-z]+")).filter { it in GeminiNanoClassifier.DOMAINS }.toSet()
}
