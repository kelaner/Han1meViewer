package com.yenaly.han1meviewer.util

import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/** The small subset of HanimeVideo needed for local browsing and restoring downloads. */
data class LocalDownloadMetadata(
    val title: String = "",
    val coverUrl: String = "",
    val author: String? = null,
    val genre: String? = null,
    val uploadTime: String? = null,
    val videoUrls: Map<String, String> = emptyMap(),
)

/** Does not require unrelated HanimeVideo fields to be present in legacy info.json files. */
fun parseLocalDownloadMetadata(text: String): LocalDownloadMetadata {
    val root = Json.parseToJsonElement(text) as? JsonObject
        ?: throw IOException("info.json root must be an object")
    fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)
        ?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    // HanimeVideo.artist is Artist?, serialized as { name, avatarUrl, genre }.
    val artist = root["artist"] as? JsonObject
    val urls = (root["videoUrls"] as? JsonObject)?.mapNotNull { (quality, value) ->
        (value as? JsonObject)?.string("link")?.let { quality to it }
    }?.toMap().orEmpty()
    return LocalDownloadMetadata(
        // Preserve title whitespace: maintenance reconstructs exact on-disk names from it.
        title = (root["title"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty(),
        coverUrl = root.string("coverUrl").orEmpty(),
        author = artist?.string("name"),
        genre = artist?.string("genre"),
        uploadTime = root.string("uploadTime"),
        videoUrls = urls,
    )
}

/** Reads at most 2 MiB on the caller's dispatcher. The caller owns and closes [input]. */
suspend fun readLocalDownloadMetadata(input: InputStream): LocalDownloadMetadata {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        coroutineContext.ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size() + count > 2 * 1024 * 1024) throw IOException("info.json too large")
        output.write(buffer, 0, count)
    }
    coroutineContext.ensureActive()
    return parseLocalDownloadMetadata(output.toString("UTF-8"))
}
