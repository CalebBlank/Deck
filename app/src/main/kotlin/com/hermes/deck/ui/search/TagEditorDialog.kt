package com.hermes.deck.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hermes.deck.data.TagRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TagEditorDialog(
    packageName: String,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tagRepo = remember { TagRepository(context) }

    var tags  by remember { mutableStateOf(tagRepo.getTags(packageName)) }
    var input by remember { mutableStateOf("") }

    fun addTag() {
        val t = input.trim().lowercase()
        if (t.isNotBlank()) {
            tags  = tags + t
            input = ""
        }
    }

    fun save() {
        // Flush any text still in the input field so the user doesn't have to
        // explicitly tap Add before pressing Done.
        val pending = input.trim().lowercase()
        val finalTags = if (pending.isNotBlank()) tags + pending else tags
        tagRepo.setTags(packageName, finalTags)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::save,
        title            = { Text("Tags — $title") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tags.isEmpty()) {
                    Text(
                        "No tags yet. Tags let you find this app or widget using extra keywords.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.sorted().forEach { tag ->
                            InputChip(
                                selected     = false,
                                onClick      = { tags = tags - tag },
                                label        = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        imageVector        = Icons.Default.Close,
                                        contentDescription = "Remove $tag",
                                        modifier           = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value          = input,
                    onValueChange  = { input = it },
                    label          = { Text("Add tag") },
                    singleLine     = true,
                    trailingIcon   = {
                        if (input.isNotBlank()) {
                            IconButton(onClick = ::addTag) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTag() }),
                    modifier        = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text("Done") } }
    )
}
