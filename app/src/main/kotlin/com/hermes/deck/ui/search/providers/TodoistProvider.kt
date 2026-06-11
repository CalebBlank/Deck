package com.hermes.deck.ui.search.providers

import android.content.Context
import com.hermes.deck.ui.search.SearchResult
import kotlinx.coroutines.delay

/**
 * Todoist tasks. Shows active tasks matching the query (each with a checkbox to complete in place),
 * plus an "Add '<query>' to Todoist" card so the search bar doubles as quick task entry. Silent until
 * a Todoist API token is set in Settings → Search → Todoist.
 */
class TodoistProvider(private val context: Context) : SearchProvider {
    override val id = "todoist"

    override suspend fun query(q: String): List<SearchResult> {
        val raw = q.trim()
        if (raw.length < 2 || !TodoistClient.isConfigured(context)) return emptyList()
        // Only surface Todoist on explicit task intent (a leading task keyword like "todo"/"task"),
        // so a general search ("steamdeck") doesn't get a stray "add to Todoist" card. The text after
        // the keyword is the task to search for / add.
        val lower = raw.lowercase()
        val kw = TASK_WORDS.firstOrNull { lower == it || lower.startsWith("$it ") || lower.startsWith("$it:") }
            ?: return emptyList()
        val taskText = raw.drop(kw.length).trimStart(' ', ':').trim()
        delay(300)
        val matches = if (taskText.length >= 2)
            TodoistClient.search(context, taskText).take(6).map { SearchResult.TodoTaskResult(it.id, it.content) }
        else emptyList()
        return if (taskText.isNotBlank()) matches + SearchResult.TodoAddResult(taskText) else matches
    }

    private companion object {
        val TASK_WORDS = listOf("todo", "todos", "task", "tasks", "remind", "reminder", "reminders")
    }
}
