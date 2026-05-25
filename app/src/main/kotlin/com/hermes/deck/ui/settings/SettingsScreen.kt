package com.hermes.deck.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("deck_prefs", Context.MODE_PRIVATE) }

    var darkMode       by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }
    var materialYou    by remember { mutableStateOf(prefs.getBoolean("material_you", true)) }
    var hideSelfCards      by remember { mutableStateOf(prefs.getBoolean("hide_self_from_cards", true)) }
    var hideSystemSettings by remember { mutableStateOf(prefs.getBoolean("hide_system_settings", true)) }
    var gridColumns    by remember { mutableIntStateOf(prefs.getInt("grid_columns", 4)) }
    var hiddenApps     by remember {
        mutableStateOf(
            prefs.getString("hidden_apps", "")!!.split(",").filter { it.isNotEmpty() }.toSet()
        )
    }
    var drawerStyle         by remember { mutableStateOf(prefs.getString("drawer_view_mode", "Grid") ?: "Grid") }
    var showColumnPicker    by remember { mutableStateOf(false) }
    var showStylePicker     by remember { mutableStateOf(false) }
    var showResetConfirm    by remember { mutableStateOf(false) }
    var showClearPinConfirm by remember { mutableStateOf(false) }
    var showHiddenApps      by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("—")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("Appearance")
            ListItem(
                headlineContent   = { Text("Dark theme") },
                supportingContent = { Text(if (darkMode) "Always dark" else "Follow system") },
                trailingContent   = {
                    Switch(
                        checked         = darkMode,
                        onCheckedChange = { checked ->
                            darkMode = checked
                            prefs.edit().putBoolean("dark_mode", checked).apply()
                        }
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent   = { Text("Material You colors") },
                supportingContent = { Text(if (materialYou) "Using wallpaper colors" else "Using default palette") },
                trailingContent   = {
                    Switch(
                        checked         = materialYou,
                        onCheckedChange = { checked ->
                            materialYou = checked
                            prefs.edit().putBoolean("material_you", checked).apply()
                        }
                    )
                }
            )
            HorizontalDivider()
            SettingsItem(
                title    = "Change wallpaper",
                subtitle = "Open system wallpaper picker",
                onClick  = {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
            HorizontalDivider()
            SectionHeader("Preferences")
            ListItem(
                headlineContent   = { Text("Hide Deck from cards") },
                supportingContent = { Text(if (hideSelfCards) "Deck won't appear in recent cards" else "Deck appears in recent cards") },
                trailingContent   = {
                    Switch(
                        checked         = hideSelfCards,
                        onCheckedChange = { checked ->
                            hideSelfCards = checked
                            prefs.edit().putBoolean("hide_self_from_cards", checked).apply()
                        }
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent   = { Text("Hide Settings from cards") },
                supportingContent = { Text(if (hideSystemSettings) "System Settings won't appear in recent cards" else "System Settings appears in recent cards") },
                trailingContent   = {
                    Switch(
                        checked         = hideSystemSettings,
                        onCheckedChange = { checked ->
                            hideSystemSettings = checked
                            prefs.edit().putBoolean("hide_system_settings", checked).apply()
                        }
                    )
                }
            )
            HorizontalDivider()
            SettingsItem(
                title    = "Hidden apps",
                subtitle = if (hiddenApps.isEmpty()) "None hidden"
                           else "${hiddenApps.size} app${if (hiddenApps.size == 1) "" else "s"} hidden",
                onClick  = { showHiddenApps = true }
            )
            HorizontalDivider()
            SettingsItem(
                title    = "Grid columns",
                subtitle = "$gridColumns columns",
                onClick  = { showColumnPicker = true }
            )
            HorizontalDivider()
            SettingsItem(
                title    = "App drawer style",
                subtitle = if (drawerStyle == "List") "List" else "Grid",
                onClick  = { showStylePicker = true }
            )
            HorizontalDivider()
            SectionHeader("About")
            SettingsItem(
                title    = "Reset onboarding",
                subtitle = "Show permission setup screens on next launch"
            ) {
                showResetConfirm = true
            }
            HorizontalDivider()
            SettingsItem(
                title    = "Clear pinned apps",
                subtitle = "Remove all apps from the dock bar"
            ) {
                showClearPinConfirm = true
            }
            HorizontalDivider()
            SettingsItem(
                title    = "Version",
                subtitle = versionName ?: "—",
                onClick  = null
            )
        }
    }

    if (showHiddenApps) {
        HiddenAppsScreen(
            hiddenApps = hiddenApps,
            onSave     = { updated ->
                hiddenApps = updated
                prefs.edit().putString("hidden_apps", updated.joinToString(",")).apply()
                showHiddenApps = false
            },
            onBack     = { showHiddenApps = false }
        )
    }

    if (showColumnPicker) {
        AlertDialog(
            onDismissRequest = { showColumnPicker = false },
            title = { Text("Grid columns") },
            text = {
                Column {
                    listOf(3, 4, 5).forEach { n ->
                        Row(
                            modifier            = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = gridColumns == n,
                                    onClick  = {
                                        gridColumns = n
                                        prefs.edit().putInt("grid_columns", n).apply()
                                        showColumnPicker = false
                                    }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment   = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = gridColumns == n, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text("$n columns")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showStylePicker) {
        AlertDialog(
            onDismissRequest = { showStylePicker = false },
            title = { Text("App drawer style") },
            text = {
                Column {
                    listOf("Grid", "List").forEach { style ->
                        Row(
                            modifier            = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = drawerStyle == style,
                                    onClick  = {
                                        drawerStyle = style
                                        prefs.edit().putString("drawer_view_mode", style).apply()
                                        showStylePicker = false
                                    }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment   = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = drawerStyle == style, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(style)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset onboarding?") },
            text  = { Text("The permission setup screens will appear on next launch.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().remove("onboarding_done").apply()
                    showResetConfirm = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearPinConfirm) {
        AlertDialog(
            onDismissRequest = { showClearPinConfirm = false },
            title = { Text("Clear dock?") },
            text  = { Text("All pinned apps will be removed from the dock bar.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().remove("pinned").apply()
                    showClearPinConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearPinConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent   = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        modifier          = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenAppsScreen(
    hiddenApps: Set<String>,
    onSave: (Set<String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var filterQuery by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(hiddenApps) }

    val allApps: List<Pair<String, String>> = remember {
        val pm = context.packageManager
        pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .mapNotNull { info ->
                runCatching {
                    val label = pm.getApplicationLabel(info).toString()
                    label to info.packageName
                }.getOrNull()
            }
            .sortedBy { it.first.lowercase() }
    }

    val filtered = remember(filterQuery, allApps) {
        if (filterQuery.isBlank()) allApps
        else allApps.filter { it.first.contains(filterQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hidden apps") },
                navigationIcon = {
                    IconButton(onClick = { onSave(pending) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value         = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder   = { Text("Search apps") },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.second }) { (label, pkg) ->
                    val checked = pkg in pending
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pending = if (checked) pending - pkg else pending + pkg
                            }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked         = checked,
                            onCheckedChange = { isChecked ->
                                pending = if (isChecked) pending + pkg else pending - pkg
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
