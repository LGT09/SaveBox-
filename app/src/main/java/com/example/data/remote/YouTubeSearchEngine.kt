package com.example.data.remote

import com.example.model.SearchResultItem
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Self-contained YouTube search engine built directly inside SaveBox using OkHttp + JSON tree parsing
 * (no external third-party wrapper APIs).
 */
class YouTubeSearchEngine(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }

    suspend fun search(query: String): ApiResult<List<SearchResultItem>> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) {
            return@withContext ApiResult.Error("Enter keywords to search YouTube.")
        }

        // 1. Primary: Query YouTube public results page and parse ytInitialData
        val htmlResults = runCatching { fetchFromHtmlSearch(clean) }.getOrNull()
        if (!htmlResults.isNullOrEmpty()) {
            return@withContext ApiResult.Success(htmlResults)
        }

        // 2. Fallback: YouTube public web search JSON endpoint
        return@withContext try {
            val apiResults = fetchFromInnertubeSearch(clean)
            ApiResult.Success(apiResults)
        } catch (t: Throwable) {
            ApiResult.Error(t.localizedMessage ?: "Unable to search YouTube right now.", t)
        }
    }

    private fun fetchFromHtmlSearch(query: String): List<SearchResultItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("https://www.youtube.com/results?search_query=$encoded&sp=EgIQAQ%253D%253D")
            .header("User-Agent", DESKTOP_UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val html = resp.body?.string().orEmpty()
            val jsonObj = extractJsonObjectAfterMarker(html, "var ytInitialData = ")
                ?: extractJsonObjectAfterMarker(html, "ytInitialData = ")
                ?: return emptyList()
            return parseYtInitialData(jsonObj)
        }
    }

    private fun fetchFromInnertubeSearch(query: String): List<SearchResultItem> {
        val payload = JSONObject().apply {
            put("query", query)
            put("params", "EgIQAQ%3D%3D")
            put(
                "context",
                JSONObject().apply {
                    put(
                        "client",
                        JSONObject().apply {
                            put("clientName", "WEB")
                            put("clientVersion", "2.20240726.00.00")
                            put("hl", "en")
                            put("gl", "US")
                        }
                    )
                }
            )
        }
        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", DESKTOP_UA)
            .build()

        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            val jsonObj = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
            return parseYtInitialData(jsonObj)
        }
    }

    internal fun parseYtInitialData(root: JsonObject): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()
        collectVideoRenderers(root, results)
        return results.distinctBy { it.id }
    }

    private fun collectVideoRenderers(element: JsonElement?, out: MutableList<SearchResultItem>) {
        if (element == null || element.isJsonNull) return
        if (element.isJsonArray) {
            element.asJsonArray.forEach { collectVideoRenderers(it, out) }
            return
        }
        if (!element.isJsonObject) return

        val obj = element.asJsonObject
        val renderer = obj.getAsJsonObject("videoRenderer")
        if (renderer != null) {
            parseVideoRenderer(renderer)?.let { out.add(it) }
        }
        for (entry in obj.entrySet()) {
            if (entry.key != "videoRenderer") {
                collectVideoRenderers(entry.value, out)
            }
        }
    }

    private fun parseVideoRenderer(renderer: JsonObject): SearchResultItem? {
        val videoId = renderer.get("videoId")?.asString?.takeIf { it.isNotBlank() } ?: return null
        val title = extractText(renderer.get("title")) ?: return null
        val channel = extractText(renderer.get("ownerText"))
            ?: extractText(renderer.get("longBylineText"))
            ?: extractText(renderer.get("shortBylineText"))
        val duration = extractText(renderer.get("lengthText"))
        val views = extractText(renderer.get("shortViewCountText"))
            ?: extractText(renderer.get("viewCountText"))

        val thumb = renderer.getAsJsonObject("thumbnail")
            ?.getAsJsonArray("thumbnails")
            ?.lastOrNull()
            ?.asJsonObject
            ?.get("url")?.asString
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return SearchResultItem(
            id = videoId,
            title = title.trim(),
            channel = channel?.trim(),
            viewCountText = views?.trim(),
            thumbnailUrl = thumb,
            durationText = duration?.trim(),
            url = "https://www.youtube.com/watch?v=$videoId"
        )
    }

    private fun extractText(el: JsonElement?): String? {
        if (el == null || el.isJsonNull || !el.isJsonObject) return null
        val obj = el.asJsonObject
        obj.get("simpleText")?.takeIf { it.isJsonPrimitive }?.asString?.let {
            if (it.isNotBlank()) return it
        }
        val runs: JsonArray? = obj.getAsJsonArray("runs")
        if (runs != null) {
            val joined = runs.mapNotNull { r ->
                if (r.isJsonObject) r.asJsonObject.get("text")?.asString else null
            }.joinToString("")
            if (joined.isNotBlank()) return joined
        }
        return null
    }

    private fun extractJsonObjectAfterMarker(source: String, marker: String): JsonObject? {
        val idx = source.indexOf(marker)
        if (idx == -1) return null
        val start = source.indexOf('{', idx + marker.length)
        if (start == -1) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until source.length) {
            val c = source[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        val jsonStr = source.substring(start, i + 1)
                        return runCatching {
                            val el = JsonParser.parseString(jsonStr)
                            if (el.isJsonObject) el.asJsonObject else null
                        }.getOrNull()
                    }
                }
            }
        }
        return null
    }
}
