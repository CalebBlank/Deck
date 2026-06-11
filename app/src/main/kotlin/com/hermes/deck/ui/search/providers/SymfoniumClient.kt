package com.hermes.deck.ui.search.providers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
import kotlin.coroutines.resume

/**
 * Talks to the Symfonium music player through its MediaBrowserService — the same interface Android
 * Auto uses. Symfonium hands us its `auto_root` browse tree and accepts our transport commands as a
 * full controller, so we can search its library and start playback with no account/token setup; it
 * just needs to be installed. Mirrors the singleton shape of [PlexClient] / [TandoorClient].
 *
 * MediaBrowserCompat must be constructed/connected and have its callbacks delivered on the main
 * thread, so all browser interaction is marshalled onto [Dispatchers.Main]; suspension (not blocking)
 * keeps the main thread free to deliver the callbacks we're waiting on.
 */
object SymfoniumClient {
    const val PKG = "app.symfonik.music.player"
    private const val SERVICE = "app.symfonik.core.playback.service.PlayerService"
    private const val MAX_RESULTS = 8

    data class SymfoniumItem(
        val mediaId: String,     // e.g. "song/216043", "browse_album_songs/24262", "browse_albums_artists/3810"
        val title: String,
        val subtitle: String?,   // artist · album, as Symfonium formats it
        val artUri: String?,
        val type: String         // "song" | "album" | "artist"
    )

    /** Symfonium's search returns songs plus album/artist containers, distinguished by mediaId prefix. */
    private fun typeOf(mediaId: String): String? = when {
        // Top-level search uses "song/"; an album's track children are "album_song/<album>/<i>" and an
        // artist's are "artist_song/…" — all playable songs.
        mediaId.startsWith("song/") || mediaId.startsWith("album_song/") || mediaId.startsWith("artist_song/") -> "song"
        mediaId.startsWith("browse_album_songs/") -> "album"
        mediaId.startsWith("browse_albums_artists/") -> "artist"
        else -> null   // e.g. "album_random/…" (the Shuffle action) — not a content row
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val connectMutex = Mutex()
    @Volatile private var browser: MediaBrowserCompat? = null

    /** True if Symfonium is installed — keeps the provider silent on devices without it. */
    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PKG, 0); true
    }.getOrDefault(false)

    /** Connect (or reuse an existing connection) on the main thread; null if Symfonium refused. */
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

    /** Search Symfonium's library — songs plus album/artist containers. Returns a capped, variety-balanced
     *  set (some songs + a couple albums + an artist) so containers aren't crowded out by track matches.
     *  Empty on any failure. Can't cancel an in-flight MediaBrowser.search, so on coroutine cancellation we
     *  just abandon the continuation (the trickle model already supersedes stale queries). */
    suspend fun search(context: Context, query: String): List<SymfoniumItem> {
        val b = connected(context) ?: return emptyList()
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { cont ->
                b.search(query, null, object : MediaBrowserCompat.SearchCallback() {
                    override fun onSearchResult(q: String, extras: Bundle?, items: MutableList<MediaBrowserCompat.MediaItem>) {
                        if (!cont.isActive) return
                        val all = items.mapNotNull { mi ->
                            val id = mi.mediaId ?: return@mapNotNull null
                            val t = typeOf(id) ?: return@mapNotNull null
                            val d = mi.description
                            SymfoniumItem(
                                mediaId  = id,
                                title    = d.title?.toString() ?: "Unknown",
                                subtitle = d.subtitle?.toString()?.takeIf { s -> s.isNotBlank() },
                                artUri   = d.iconUri?.toString(),
                                type     = t
                            )
                        }
                        val albums  = all.filter { it.type == "album" }.take(2)
                        val artists = all.filter { it.type == "artist" }.take(1)
                        val songs   = all.filter { it.type == "song" }.take(MAX_RESULTS - albums.size - artists.size)
                        cont.resume(songs + albums + artists)
                    }
                    override fun onError(q: String, extras: Bundle?) { if (cont.isActive) cont.resume(emptyList()) }
                })
            }
        }
    }

    /** Browse the children of a container mediaId. The search mediaIds ARE the browse paths, so
     *  "browse_album_songs/<id>" → that album's songs, "browse_albums_artists/<id>" → that artist's
     *  albums. Used to expand a rich album/artist card. Empty on failure. */
    suspend fun children(context: Context, mediaId: String): List<SymfoniumItem> {
        val b = connected(context) ?: return emptyList()
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { cont ->
                val cb = object : MediaBrowserCompat.SubscriptionCallback() {
                    override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
                        runCatching { b.unsubscribe(mediaId) }
                        if (!cont.isActive) return
                        cont.resume(children.mapNotNull { mi ->
                            val id = mi.mediaId ?: return@mapNotNull null
                            // Skip Symfonium's "Shuffle" / "Play all" ACTION rows (ids like
                            // "album_random/…" / "artist_random/…") — they aren't tracks or albums. Keep
                            // the safe "song" default for any other id so the artist card (whose album
                            // children may use an id prefix we haven't enumerated) doesn't regress.
                            if (id.contains("_random/")) return@mapNotNull null
                            val d = mi.description
                            SymfoniumItem(
                                mediaId  = id,
                                title    = d.title?.toString() ?: "Unknown",
                                subtitle = d.subtitle?.toString()?.takeIf { s -> s.isNotBlank() },
                                artUri   = d.iconUri?.toString(),
                                type     = typeOf(id) ?: "song"
                            )
                        })
                    }
                    override fun onError(parentId: String) {
                        runCatching { b.unsubscribe(mediaId) }
                        if (cont.isActive) cont.resume(emptyList())
                    }
                }
                cont.invokeOnCancellation { runCatching { b.unsubscribe(mediaId) } }
                b.subscribe(mediaId, cb)
            }
        }
    }

    /** Start playing a song (by Symfonium mediaId) in Symfonium, then surface Symfonium's now-playing
     *  screen. Fire-and-forget; reconnects if needed. The session's `sessionActivity` is the PendingIntent
     *  Symfonium registers for "open now playing" (the same one its notification uses) — sending it opens
     *  the player UI even though that activity isn't exported. Falls back to the app's launcher entry. */
    fun play(context: Context, mediaId: String, openNowPlaying: Boolean = true) {
        scope.launch {
            val b = connected(context) ?: return@launch
            runCatching {
                val controller = MediaControllerCompat(context.applicationContext, b.sessionToken)
                controller.transportControls.playFromMediaId(mediaId, null)
                if (openNowPlaying) openNowPlaying(context, controller)
            }
        }
    }

    /** Bring Symfonium's now-playing UI forward. Its `sessionActivity` is the PendingIntent Symfonium
     *  registers for "open now playing" (what its notification taps). On Android 14+ a foreground app
     *  must explicitly grant a PendingIntent permission to start an activity from the background — without
     *  it, send() *succeeds* but the launch is silently dropped (observed). Falls back to the launcher. */
    private fun openNowPlaying(context: Context, controller: MediaControllerCompat) {
        val sa = controller.sessionActivity
        val opened = sa != null && runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                val opts = android.app.ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                sa.send(context, 0, null, null, null, null, opts.toBundle())
            } else {
                sa.send()
            }
        }.isSuccess
        if (!opened) {
            context.packageManager.getLaunchIntentForPackage(PKG)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { runCatching { context.startActivity(it) } }
        }
    }

    /** Load a result's album art, if Symfonium exposed a readable uri for it. Null → music-note fallback. */
    suspend fun fetchArt(context: Context, uri: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri)).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
}
