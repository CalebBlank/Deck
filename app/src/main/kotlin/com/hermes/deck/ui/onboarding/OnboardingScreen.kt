package com.hermes.deck.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    // Skip notification step if already granted
    fun advanceFromUsageAccess() {
        val notifGranted = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
        if (notifGranted) onFinish() else step = 2
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                0 -> PermissionStep(
                    icon        = Icons.Outlined.Accessibility,
                    title       = "Enable Accessibility",
                    body        = "Deck uses the Accessibility Service to capture app screenshots for your card strip. Enable \"Deck\" in the Accessibility settings.",
                    actionLabel = "Open Accessibility Settings",
                    onAction    = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onNext = { step = 1 }
                )
                1 -> PermissionStep(
                    icon        = Icons.Outlined.History,
                    title       = "Allow Usage Access",
                    body        = "Deck needs usage access to know which apps you've used recently and show them as cards. Enable \"Deck\" in Usage access settings.",
                    actionLabel = "Open Usage Access Settings",
                    onAction    = {
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onNext = { advanceFromUsageAccess() }
                )
                2 -> PermissionStep(
                    icon        = Icons.Outlined.Notifications,
                    title       = "Allow Notification Access",
                    body        = "Deck can surface your notifications in search results. Enable \"Deck\" in Notification access settings. You can skip this if you prefer.",
                    actionLabel = "Open Notification Settings",
                    onAction    = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onNext = onFinish
                )
            }
        }
    }
}

@Composable
private fun PermissionStep(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    onNext: () -> Unit
) {
    Icon(
        imageVector        = icon,
        contentDescription = null,
        modifier           = Modifier.size(72.dp),
        tint               = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(28.dp))
    Text(
        text      = title,
        style     = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text      = body,
        style     = MaterialTheme.typography.bodyMedium,
        color     = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(40.dp))
    Button(
        onClick  = onAction,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(actionLabel)
    }
    Spacer(Modifier.height(12.dp))
    TextButton(
        onClick  = onNext,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Continue")
    }
}
