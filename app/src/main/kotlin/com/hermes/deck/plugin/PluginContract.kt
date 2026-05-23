package com.hermes.deck.plugin

/**
 * Contract between Deck and third-party search plugin APKs.
 *
 * A plugin declares a ContentProvider with an authority that starts with AUTHORITY_PREFIX,
 * responds to queries at the "search" path, and returns a Cursor with the columns below.
 *
 * Example authority: "com.example.myplugin.deck"
 * Example query URI: content://com.example.myplugin.deck/search?q=weather
 *
 * The plugin must also declare the META_PLUGIN_NAME meta-data entry on its provider.
 */
object PluginContract {
    const val AUTHORITY_PREFIX = "com.hermes.deck.plugin."
    const val PATH_SEARCH      = "search"
    const val PARAM_QUERY      = "q"

    // Cursor column names plugins must return
    const val COL_TITLE       = "title"
    const val COL_SUBTITLE    = "subtitle"
    const val COL_ICON_URI    = "icon_uri"    // content:// or android.resource:// URI, nullable
    const val COL_ACTION_URI  = "action_uri"  // intent URI (Intent.toUri / Intent.parseUri)
    const val COL_RESULT_TYPE = "result_type" // arbitrary string label for analytics/display

    // Meta-data keys the plugin's <provider> element must declare
    const val META_PLUGIN_NAME    = "com.hermes.deck.plugin.NAME"
    const val META_PLUGIN_VERSION = "com.hermes.deck.plugin.VERSION"
}
