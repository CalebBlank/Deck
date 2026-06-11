# Reverb articles in Deck search — design + plan

Surface **recent Reverb RSS articles** in Deck's universal search. "Reverb" is the
separate Android RSS reader (`Projects/Reverb`, package `com.caleb.reverb`) that syncs
from a self-hosted **FreshRSS** server over the **Google Reader API**.

Status: design + initial scaffold. Not built, not wired for production. Starting point for review.

---

## TL;DR recommendation

**Use Deck's existing plugin ContentProvider contract, with the provider living *inside the
Reverb app*.** Reverb already holds the FreshRSS credentials and already does the GReader
auth/fetch; it exposes a `content://com.hermes.deck.plugin.reverb/search` provider that returns
recent reading-list articles as plugin result rows. Deck discovers it automatically and renders
them as `PluginResult` cards — **no Deck-side code changes at all**.

This is the path that answers the task's central question (how does Deck get the FreshRSS
token?) the best way: **it never does.** The token stays in Reverb's private storage.

---

## How Deck's search-provider contract works (reverse-engineered)

Two distinct extension paths exist. Files below are in the Deck repo.

### Path A — native in-app provider (Tandoor / Plex / Home Assistant style)

- `ui/search/providers/SearchProvider.kt` — the interface: `val id: String` +
  `suspend fun query(q: String): List<SearchResult>`.
- `ui/search/SearchResult.kt` — a **sealed class**; every native provider adds its own
  `data class XResult(...) : SearchResult()`.
- A provider is a tiny class (see `TandoorProvider.kt`) that gates on config
  (`Client.isConfigured(context)`), calls a raw-HTTP `object` client (see `TandoorClient.kt`,
  reads `deck_prefs` SharedPreferences for `*_base_url` / `*_token`), and maps the client's
  data class to its `SearchResult` subclass.
- Registered by hand in `SearchViewModel.kt` `companion object factory` → `staticProviders` list
  (~line 707). The view model debounces 200 ms and fans out to all providers in parallel; each
  provider's results trickle in independently (`runCatching` per provider, so a slow/throwing one
  can't break search).
- Rendering + tap are in `ui/search/LauncherSearchBar.kt`. Adding a native result type means
  touching **five exhaustive `when` blocks** (no `else` branch — it won't compile until all are
  updated):
  1. tap handler `onResultClick` (~line 437) — what tapping does
  2. card dispatch `SearchResultRow` (~line 487) — which composable renders it
  3. group label (~line 1470) — section header
  4. `providerIdForResult` (~line 1690) — maps result → settings id
  5. dedup key (~line 3560)
  Plus a new `XResultCard` composable + an `openX(...)` function, and a Settings entry
  (`SettingsScreen.kt`: `SearchProviderMeta` list ~line 585, state vars ~line 909, settings UI
  block ~line 1456).

### Path B — plugin ContentProvider (third-party APK contract) — RECOMMENDED here

- `plugin/PluginContract.kt` — the cross-app contract:
  - Authority must start with `com.hermes.deck.plugin.`
  - Query URI: `content://<authority>/search?q=<query>`
  - Cursor columns: `title`, `subtitle`, `icon_uri` (content:// or android.resource://, nullable),
    `action_uri` (an **intent URI** via `Intent.toUri(URI_INTENT_SCHEME)`), `result_type`
    (free-text label, used as the group header).
  - Provider `<provider>` element must declare meta-data `com.hermes.deck.plugin.NAME`.
- `plugin/PluginRepository.kt` — Deck **auto-discovers** any installed package exposing such a
  provider (scans `getInstalledPackages(GET_PROVIDERS | GET_META_DATA)`), and on each query does
  `contentResolver.query(...)` on `Dispatchers.IO`, mapping rows → `SearchResult.PluginResult`.
  Discovery refreshes on package add/remove. Disable per-plugin via `disabled_plugins` pref.
- `SearchResult.PluginResult(pluginId, pluginName, title, subtitle, iconUri, actionUri, resultType)`
  is **already wired through all five `when` sites**. On tap, Deck runs
  `Intent.parseUri(actionUri, URI_INTENT_SCHEME)` + `startActivity` (LauncherSearchBar ~line 437).

Auto-discovery works with **zero Deck changes** because Deck already holds `QUERY_ALL_PACKAGES`
(see Deck's permissions table) — that's the dependency that lets it see Reverb's provider. Without
it, Android 11+ package visibility would hide the provider and Deck would need a `<queries>` entry.

**The plugin contract maps onto an Article almost 1:1**, so Path B needs **zero** changes in Deck:

| Plugin column | Article field |
|---|---|
| `title`       | `article.title` |
| `subtitle`    | `article.feedTitle` (or `article.source`) |
| `icon_uri`    | favicon — see open question |
| `action_uri`  | `Intent.ACTION_VIEW` on `article.link`, serialized with `Intent.toUri(URI_INTENT_SCHEME)` |
| `result_type` | `"Reverb"` (becomes the group header) |

---

## Data-source decision

The data is "recent articles already synced into Reverb." Three options:

### (a) Deck queries FreshRSS GReader directly (port Reverb's `GReaderClient`)
Reverb's `GReaderClient.kt` + `parseItems` are intentionally Android-free, so they port cleanly
into a Deck `ReverbClient.kt`. But Deck is a *separate app* and **cannot read Reverb's private
SharedPreferences** (the `gr_token`). So the user would have to **re-enter FreshRSS URL + username
+ password in Deck**, Deck would run ClientLogin itself, and store its **own** token in
`deck_prefs`. Credentials are duplicated in two apps; a server password change must be redone in
both. Touches ~10 Deck sites (full Path A checklist above).

### (b) Reverb exposes a Deck plugin ContentProvider — RECOMMENDED
Reverb already has the token and the fetch logic. It adds one ContentProvider
(`com.hermes.deck.plugin.reverb`) that, on `query`, loads its own account
(`GReaderAccountStore.load(getSharedPreferences("reverb_prefs", …))`), calls
`GReaderClient.streamItems(acc, READING_LIST)`, filters by the query, and returns cursor rows.
**Deck never sees the credentials.** All new code is in Reverb (one provider class + a manifest
`<provider>` entry). **Zero Deck-side changes.** Requires Reverb installed — but the data *is*
Reverb's, so that's not a real cost.

### (c) Shared store (file / shared prefs across apps)
Cross-app SharedPreferences are deprecated/unreliable; a shared file needs a ContentProvider
anyway. Strictly worse than (b). Rejected.

**Chosen: (b).** It is the most minimal on Deck, keeps the FreshRSS token in exactly one place
(the app that owns it), and reuses Reverb's existing, tested GReader code with no porting.

### Trade-off summary

| | (a) Deck-direct (native) | (b) Reverb plugin (chosen) |
|---|---|---|
| Where token lives | duplicated into `deck_prefs` | Reverb only |
| User re-enters creds | yes (URL+user+pass in Deck) | no |
| Deck code changes | ~10 sites (full Path A) | none |
| New code location | Deck | Reverb (1 provider + manifest) |
| Requires Reverb installed | no | yes (acceptable) |
| Reuses Reverb's tested fetch | re-port | as-is, same process |

---

## What "recent articles" means here (important nuance)

Deck providers are **query-driven** — `query(q)` gets the typed string. The GReader
`stream/contents` endpoint has **no server-side text search**. So the v1 behavior is:

> Fetch a recent window of the reading-list (e.g. the latest ~50–100 items, briefly cached),
> then **filter client-side** by title/feed against the typed query, and return the matches as
> result cards.

A *pure* "show my recent articles regardless of query" feed is a different feature — that's the
**Deck contextual-feed** idea (blank-query surfacing) noted as future work, not search. The scaffold
implements query-filtered recent articles; if `q` is blank it can optionally return the newest N.

---

## Scaffold added (this task)

Path B, so the new code is **in Reverb**, following Deck's plugin contract:

- `Reverb/app/src/main/java/com/caleb/reverb/data/DeckPluginProvider.kt` — a `ContentProvider`
  with authority `com.hermes.deck.plugin.reverb`. On `query` of the `search` path it loads the
  GReader account from `reverb_prefs`, fetches `READING_LIST` (with a short in-memory cache),
  filters by `q`, and returns a `MatrixCursor` with the five plugin columns. Article tap →
  `action_uri` = an `ACTION_VIEW` intent URI on `article.link` (browser; safe default).
  **The network fetch is currently stubbed/guarded** — it returns empty until reviewed (see TODOs
  in the file) so it can't ship half-done or block search on a slow binder call.
- `Reverb/app/src/main/AndroidManifest.xml` — added the `<provider>` declaration (exported,
  with the `com.hermes.deck.plugin.NAME` meta-data) inside `<application>`.

No Deck files were modified. No build run, no commit.

> If the user prefers Path A (no dependency on Reverb being installed, at the cost of duplicated
> credentials), the full Deck-side checklist is in "Path A" above; `ReverbClient.kt` would be a
> near-copy of Reverb's `GReaderClient.kt`.

---

## Open questions / decisions for the user

1. **Approach.** Confirm Path B (plugin in Reverb, no credential duplication, requires Reverb
   installed) vs Path A (native in Deck, user re-enters FreshRSS creds in Deck). Recommendation: B.
2. **What does tapping an article do?** Reverb's `MainActivity` has *only* a LAUNCHER
   intent-filter — there is **no deep link to open a specific article**. Options:
   - (default, scaffolded) open `article.link` in the browser via `ACTION_VIEW`;
   - or add an article deep-link to Reverb (`reverb://article?link=…` intent-filter + handling in
     `MainActivity` → open `ReaderPane`) so taps land back in the reader. This is extra Reverb work.
   Which?
3. **"Recent + filtered" vs "recent feed".** Confirm v1 = type-to-filter recent reading-list items
   (search-shaped). A pure recent feed belongs to the contextual-feed feature instead.
4. **Favicon / `icon_uri`.** GReader items don't carry a favicon. Options: omit (Deck shows a
   fallback), use the article image (`imageUrl`) if present, or derive a favicon URL from the
   source host. Reverb has `FaviconColors.kt` / `SourceLogos.kt` — reuse? Note: `icon_uri` must be
   a `content://`/`android.resource://` URI a *separate* app can read, not an https URL, so a
   remote favicon would need a Reverb image ContentProvider or just be skipped for v1.
5. **Read vs unread / starred.** Reading-list = all items; should the provider prefer unread, or
   include starred? Default: newest reading-list items.
6. **How many / freshness.** Window size (50? 100?) and cache TTL for the fetched batch.
7. **Content exposure (privacy).** Path B's privacy win is *credential* isolation — the FreshRSS
   token never leaves Reverb. But Deck's plugin contract requires the provider to be `exported`
   with **no `android:permission`**, so once `FETCH_ENABLED` is flipped on, **any installed app can
   query `content://com.hermes.deck.plugin.reverb/search?q=` and read recent article titles/links/
   feed names** (not the credentials). Acceptable for this data, or should Deck's `PluginContract`
   grow a signature/custom permission so only Deck can query plugins? (That's a Deck-contract
   change, out of scope here, but worth flagging.)
