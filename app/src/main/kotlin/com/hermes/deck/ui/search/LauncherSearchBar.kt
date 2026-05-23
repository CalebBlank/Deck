package com.hermes.deck.ui.search

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

private val PLACEHOLDER_WORDS = listOf(
    "groove", "vibe", "fix", "jam", "move",
    "people", "thing", "song", "place", "answer"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun LauncherSearchBar(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.factory(context))
    val query   by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val expanded = query.isNotEmpty()

    var wordIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            wordIndex = (wordIndex + 1) % PLACEHOLDER_WORDS.size
        }
    }

    DockedSearchBar(
        modifier = modifier,
        inputField = {
            SearchBarDefaults.InputField(
                query            = query,
                onQueryChange    = vm::onQueryChange,
                onSearch         = {},
                expanded         = expanded,
                onExpandedChange = {},
                placeholder      = {
                    Row {
                        Text("Find your… ")
                        AnimatedContent(
                            targetState = PLACEHOLDER_WORDS[wordIndex],
                            transitionSpec = {
                                slideInVertically { it } + fadeIn() togetherWith
                                slideOutVertically { -it } + fadeOut()
                            },
                            label = "placeholder_word"
                        ) { word ->
                            Text(
                                text       = word,
                                fontStyle  = FontStyle.Italic,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                trailingIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Apps, contentDescription = "All apps")
                    }
                }
            )
        },
        expanded         = expanded,
        onExpandedChange = { if (!it) vm.clearQuery() }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier       = Modifier.heightIn(max = 360.dp)
        ) {
            items(results, key = { resultKey(it) }) { result ->
                SearchResultRow(result = result, onDismiss = vm::clearQuery)
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    when (result) {
        is SearchResult.AppResult -> ListItem(
            headlineContent = { Text(result.app.label) },
            leadingContent  = { Icon(Icons.Default.Apps, contentDescription = null) },
            modifier        = Modifier.clickable {
                context.packageManager.getLaunchIntentForPackage(result.app.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let { context.startActivity(it) }
                onDismiss()
            }
        )

        is SearchResult.ContactResult -> ListItem(
            headlineContent  = { Text(result.name) },
            supportingContent = result.phoneNumber?.let { { Text(it) } },
            leadingContent   = { Icon(Icons.Default.Call, contentDescription = null) },
            modifier         = Modifier.clickable {
                result.phoneNumber?.let { num ->
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                onDismiss()
            }
        )

        is SearchResult.CalculatorResult -> ListItem(
            headlineContent  = { Text(result.result, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(result.expression) },
            leadingContent   = { Icon(Icons.Default.Calculate, contentDescription = null) }
        )

        is SearchResult.PluginResult -> ListItem(
            headlineContent  = { Text(result.title) },
            supportingContent = result.subtitle?.let { { Text(it) } },
            leadingContent   = { Icon(Icons.Default.Extension, contentDescription = null) },
            modifier         = Modifier.clickable {
                result.actionUri?.let { uri ->
                    runCatching {
                        context.startActivity(
                            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                onDismiss()
            }
        )
    }
}

private fun resultKey(result: SearchResult): Any = when (result) {
    is SearchResult.AppResult        -> "app:${result.app.packageName}"
    is SearchResult.ContactResult    -> "contact:${result.name}"
    is SearchResult.CalculatorResult -> "calc:${result.expression}"
    is SearchResult.PluginResult     -> "plugin:${result.pluginId}:${result.title}"
}
