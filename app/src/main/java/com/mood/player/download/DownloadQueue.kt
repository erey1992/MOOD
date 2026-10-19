package com.mood.player.download

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class DownloadPhase {
    PENDING, METADATA, WAITING_CHOICE, DOWNLOADING, CONVERTING, SAVING,
    COMPLETED, SKIPPED, FAILED, CANCELLED;

    val active: Boolean get() = this in setOf(PENDING, METADATA, WAITING_CHOICE, DOWNLOADING, CONVERTING, SAVING)
    val retryable: Boolean get() = this == FAILED || this == CANCELLED
}

data class DownloadConflict(val requestId: String, val fileName: String)

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val folderUri: String,
    val folderName: String,
    val title: String? = null,
    val author: String? = null,
    val durationMs: Long? = null,
    val phase: DownloadPhase = DownloadPhase.PENDING,
    val progress: Float? = null,
    val message: String? = null,
    val savedUri: String? = null,
    val conflict: DownloadConflict? = null,
) {
    val targetKey: String get() = "$folderUri|$url"
}

data class DownloadState(
    val items: List<DownloadItem> = emptyList(),
    val resolving: Boolean = false,
    val message: String = "",
    val inputError: String? = null,
    val failedInputUrl: String? = null,
    val failedInputFolder: String? = null,
    val completionVersion: Long = 0,
) {
    val busy: Boolean get() = resolving || items.any { it.phase.active }
}

object DownloadStatus {
    internal val mutable = MutableStateFlow(DownloadState())
    val state = mutable.asStateFlow()
    private var preferences: android.content.SharedPreferences? = null

    @Synchronized
    fun initialize(context: Context) {
        if (preferences != null) return
        val prefs = context.getSharedPreferences("download_queue", Context.MODE_PRIVATE)
        preferences = prefs
        val restored = runCatching {
            val entries = JSONArray(prefs.getString("items", "[]"))
            (0 until entries.length()).map { index ->
                val entry = entries.getJSONObject(index)
                val phase = DownloadPhase.valueOf(entry.getString("phase"))
                DownloadItem(
                    id = entry.getString("id"), url = entry.getString("url"),
                    folderUri = entry.getString("folder"), folderName = entry.getString("folderName"),
                    title = entry.text("title"), author = entry.text("author"),
                    durationMs = entry.optLong("duration", -1).takeIf { it >= 0 },
                    phase = if (phase.active) DownloadPhase.FAILED else phase,
                    progress = if (phase == DownloadPhase.COMPLETED) 1f else null,
                    message = if (phase.active) "La descarga se interrumpió. Pulsa Reintentar." else entry.text("message"),
                    savedUri = entry.text("savedUri"),
                )
            }
        }.getOrDefault(emptyList())
        mutable.value = DownloadState(items = restored)
    }

    internal fun changeItem(id: String, persist: Boolean = true, transform: (DownloadItem) -> DownloadItem) {
        mutable.update { state -> state.copy(items = state.items.map { if (it.id == id) transform(it) else it }) }
        if (persist) save()
    }

    internal fun completed(id: String, uri: String) {
        mutable.update { state -> state.copy(
            items = state.items.map { if (it.id == id) it.copy(phase = DownloadPhase.COMPLETED, progress = 1f, savedUri = uri, message = null, conflict = null) else it },
            completionVersion = state.completionVersion + 1,
        ) }
        save()
    }

    /** Never serialize progress callbacks: only metadata, queue changes and terminal results. */
    @Synchronized
    internal fun save() {
        val entries = JSONArray()
        state.value.items.forEach { item ->
            entries.put(JSONObject().put("id", item.id).put("url", item.url)
                .put("folder", item.folderUri).put("folderName", item.folderName)
                .put("title", item.title).put("author", item.author).put("duration", item.durationMs)
                .put("phase", item.phase.name).put("message", item.message).put("savedUri", item.savedUri))
        }
        preferences?.edit()?.putString("items", entries.toString())?.apply()
    }

    internal fun clearAll() {
        // Keep the resolver busy until its cancellation finishes, and retain library revisions.
        mutable.update { it.copy(items = emptyList(), message = "", inputError = null, failedInputUrl = null, failedInputFolder = null) }
        save()
    }
}

internal fun JSONObject.text(key: String): String? = optString(key)
    .trim().takeIf { it.isNotEmpty() && it != "null" }
