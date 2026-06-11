package com.hermes.deck.ui.search.providers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Talks to the Transistor internet-radio app through its MediaBrowserService (the Android Auto
 * interface). Transistor accepts a 3rd-party client and exposes the user's saved stations under
 * browse root `__ROOT__` — but it does NOT implement search() — so a "radio" search is just a local
 * filter over the browsed station list. Playback works via the session token + playFromMediaId.
 * Needs no setup beyond the app being installed with stations added. Same shape as [SymfoniumClient].
 */
object TransistorClient {
    const val PKG = "org.y20k.transistor"
    private const val SERVICE = "org.y20k.transistor.PlayerService"

    data class Station(
        val mediaId: String,     // station UUID
        val title: String,
        val art: Bitmap?         // station favicon — Transistor ships it as MediaItem.iconBitmap (iconUri is null)
    )

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val connectMutex = Mutex()
    @Volatile private var browser: MediaBrowserCompat? = null
    // Browsed station list, cached briefly so we don't re-browse on every keystroke (debounced anyway).
    @Volatile private var cache: List<Station> = emptyList()
    @Volatile private var cacheAt = 0L

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PKG, 0); true
    }.getOrDefault(false)

    private suspend fun connected(context: Context): MediaBrowserCompat? {
        browser?.let { if (it.isConnected) return it }
        return connectMutex.withLock {
            browser?.let { if (it.isConnected) return@withLock it }
            withContext(Dispatchers.Main.immediate) {
                val def = CompletableDeferred<Boolean>()
                val cb = object : MediaBrowserCompat.ConnectionCallback() {
                    override fun onConnected() { if (!def.isCompleted) def.complete(true) }
                    override fun onConnectionFailed() { if (!def.isCompleted) def.complete(false) }
                    override fun onConnectionSuspended() { browser = null }
                }
                val b = MediaBrowserCompat(context.applicationContext, ComponentName(PKG, SERVICE), cb, null)
                if (!runCatching { b.connect() }.isSuccess) return@withContext null
                if (def.await()) { browser = b; b } else null
            }
        }
    }

    /** Browse the user's saved stations (browse root). One-shot: resolves on the first children load and
     *  unsubscribes. Cached for 15s so rapid typing reuses the list; picks up newly-added stations after. */
    private suspend fun stations(context: Context): List<Station> {
        if (cache.isNotEmpty() && System.currentTimeMillis() - cacheAt < 15_000) return cache
        val b = connected(context) ?: return cache
        val loaded = withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<List<Station>> { cont ->
                val root = b.root
                b.subscribe(root, object : MediaBrowserCompat.SubscriptionCallback() {
                    override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
                        b.unsubscribe(root, this)
                        if (!cont.isActive) return
                        cont.resumeWith(Result.success(children.mapNotNull {
                            val id = it.mediaId ?: return@mapNotNull null
                            if (!it.isPlayable) return@mapNotNull null
                            Station(id, it.description.title?.toString() ?: "Station", it.description.iconBitmap)
                        }))
                    }
                    override fun onError(parentId: String) {
                        b.unsubscribe(root, this)
                        if (cont.isActive) cont.resumeWith(Result.success(emptyList()))
                    }
                })
            }
        }
        if (loaded.isNotEmpty()) { cache = loaded; cacheAt = System.currentTimeMillis() }
        return loaded
    }

    /** "Search" = local filter over the saved stations by title (Transistor has no real search). */
    suspend fun search(context: Context, query: String): List<Station> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return stations(context).filter { it.title.contains(q, ignoreCase = true) }
    }

    /** Start a station playing in Transistor, then surface its now-playing UI. Same BAL caveat as
     *  [SymfoniumClient.play]: on Android 14+ the foreground sender must grant the sessionActivity
     *  PendingIntent permission to start an activity from the background, or the launch is dropped. */
    fun play(context: Context, mediaId: String) {
        scope.launch {
            val b = connected(context) ?: return@launch
            runCatching {
                val controller = MediaControllerCompat(context.applicationContext, b.sessionToken)
                controller.transportControls.playFromMediaId(mediaId, null)
                val sa = controller.sessionActivity
                val opened = sa != null && runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        val opts = android.app.ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        sa.send(context, 0, null, null, null, null, opts.toBundle())
                    } else sa.send()
                }.isSuccess
                if (!opened) {
                    context.packageManager.getLaunchIntentForPackage(PKG)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let { runCatching { context.startActivity(it) } }
                }
            }
        }
    }
}
