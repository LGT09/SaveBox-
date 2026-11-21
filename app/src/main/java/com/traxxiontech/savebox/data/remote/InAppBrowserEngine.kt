package com.traxxiontech.savebox.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class BrowserWebLink(
    val title: String,
    val url: String,
    val snippet: String = ""
)

data class BrowserPageSnapshot(
    val url: String,
    val title: String,
    val host: String,
    val description: String,
    val paragraphs: List<String>,
    val links: List<BrowserWebLink>,
    val searchQuery: String? = null,
    val isSearchPage: Boolean = false
)

class InAppBrowserEngine(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    suspend fun navigate(rawUrlOrQuery: String): ApiResult<BrowserPageSnapshot> = withContext(Dispatchers.IO) {
        val trimmed = rawUrlOrQuery.trim()
        if (trimmed.isEmpty() || trimmed.equals("https://www.google.com", ignoreCase = true) || trimmed.equals("https://google.com", ignoreCase = true)) {
            return@withContext ApiResult.Success(buildGoogleStartSnapshot())
        }

        val parsed = trimmed.toHttpUrlOrNull()
        if (parsed != null && parsed.host.contains("google.", ignoreCase = true) && parsed.encodedPath.startsWith("/search")) {
            val query = parsed.queryParameter("q")?.trim().orEmpty()
            if (query.isNotBlank()) {
                return@withContext searchWeb(query)
            }
        }

        if (parsed == null && (!trimmed.contains(".") || trimmed.contains(" "))) {
            return@withContext searchWeb(trimmed)
        }

        val normalizedUrl = if (parsed != null) trimmed else "https://$trimmed"
        return@withContext fetchWebPage(normalizedUrl)
    }

    suspend fun searchWeb(query: String): ApiResult<BrowserPageSnapshot> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return@withContext ApiResult.Success(buildGoogleStartSnapshot())
        }
        val encoded = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.name())
        val googleSearchUrl = "https://www.google.com/search?q=$encoded"

        // 1. Try Google basic HTML view (gbv=1)
        val googleHtml = runCatching {
            fetchRawHtml("https://www.google.com/search?q=$encoded&gbv=1&hl=en")
        }.getOrNull()
        val googleResults = googleHtml?.let { parseGoogleSearchHtml(it) }.orEmpty()
        if (googleResults.isNotEmpty()) {
            return@withContext ApiResult.Success(
                BrowserPageSnapshot(
                    url = googleSearchUrl,
                    title = "$cleanQuery - Google Search",
                    host = "google.com",
                    description = "Showing ${googleResults.size} web results for \"$cleanQuery\". Tap any link to browse or fetch media.",
                    paragraphs = emptyList(),
                    links = googleResults,
                    searchQuery = cleanQuery,
                    isSearchPage = true
                )
            )
        }

        // 2. Fallback to DuckDuckGo server-rendered HTML search if Google served a JS/captcha challenge
        val ddgHtml = runCatching {
            fetchRawHtml("https://html.duckduckgo.com/html/?q=$encoded")
        }.getOrNull()
        val ddgResults = ddgHtml?.let { parseDuckDuckGoSearchHtml(it) }.orEmpty()
        if (ddgResults.isNotEmpty()) {
            return@withContext ApiResult.Success(
                BrowserPageSnapshot(
                    url = googleSearchUrl,
                    title = "$cleanQuery - Web Search",
                    host = "google.com",
                    description = "Showing ${ddgResults.size} web results for \"$cleanQuery\". Tap any result to open.",
                    paragraphs = emptyList(),
                    links = ddgResults,
                    searchQuery = cleanQuery,
                    isSearchPage = true
                )
            )
        }

        ApiResult.Error("Unable to load live search results right now. Check your internet connection and try again.")
    }

    private fun fetchWebPage(url: String): ApiResult<BrowserPageSnapshot> {
        val httpUrl = url.toHttpUrlOrNull() ?: return ApiResult.Error("Invalid URL: $url")
        return try {
            val request = Request.Builder()
                .url(httpUrl)
                .header("User-Agent", DESKTOP_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return ApiResult.Error("Page returned HTTP ${resp.code}")
                }
                val finalUrl = resp.request.url
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) {
                    return ApiResult.Error("Empty response from ${finalUrl.host}")
                }
                ApiResult.Success(parseHtmlPage(body, finalUrl))
            }
        } catch (t: Throwable) {
            ApiResult.Error(t.localizedMessage ?: "Failed to load ${httpUrl.host}")
        }
    }

    private fun fetchRawHtml(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    fun parseGoogleSearchHtml(html: String): List<BrowserWebLink> {
        val results = mutableListOf<BrowserWebLink>()
        val seen = linkedSetOf<String>()
        val anchorRegex = Regex(
            """<a\s+[^>]*href="(/url\?q=[^"]+|https?://[^"]+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        anchorRegex.findAll(html).forEach { match ->
            var href = decodeHtml(match.groupValues[1])
            if (href.startsWith("/url?q=")) {
                href = href.removePrefix("/url?q=").substringBefore("&")
                href = runCatching { URLDecoder.decode(href, StandardCharsets.UTF_8.name()) }.getOrDefault(href)
            }
            val parsed = href.toHttpUrlOrNull() ?: return@forEach
            val host = parsed.host.lowercase()
            if (host.contains("google.") || host.contains("gstatic.") || host.contains("googleusercontent.")) {
                return@forEach
            }
            val title = stripTags(match.groupValues[2]).trim()
            if (title.length < 4 || title.startsWith("http", ignoreCase = true)) return@forEach
            if (seen.add(href)) {
                results += BrowserWebLink(
                    title = title,
                    url = href,
                    snippet = host.removePrefix("www.")
                )
            }
            if (results.size >= 20) return results
        }
        return results
    }

    fun parseDuckDuckGoSearchHtml(html: String): List<BrowserWebLink> {
        val results = mutableListOf<BrowserWebLink>()
        val seen = linkedSetOf<String>()
        val linkRegex = Regex(
            """<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val snippetRegex = Regex(
            """<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</a>|<div[^>]*class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</div>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val snippets = snippetRegex.findAll(html)
            .map { stripTags(it.groupValues[1].ifBlank { it.groupValues[2] }).trim() }
            .toList()

        linkRegex.findAll(html).forEachIndexed { idx, match ->
            var href = decodeHtml(match.groupValues[1]).trim()
            if (href.startsWith("//")) href = "https:$href"
            val uddgMatch = Regex("""[?&]uddg=([^&]+)""").find(href)
            if (uddgMatch != null) {
                href = runCatching {
                    URLDecoder.decode(uddgMatch.groupValues[1], StandardCharsets.UTF_8.name())
                }.getOrDefault(href)
            }
            val parsed = href.toHttpUrlOrNull() ?: return@forEachIndexed
            val title = stripTags(match.groupValues[2]).trim()
            if (title.isBlank()) return@forEachIndexed
            if (seen.add(href)) {
                val snippet = snippets.getOrNull(idx).orEmpty().ifBlank { parsed.host.removePrefix("www.") }
                results += BrowserWebLink(
                    title = title,
                    url = href,
                    snippet = snippet
                )
            }
            if (results.size >= 20) return results
        }
        return results
    }

    fun parseHtmlPage(html: String, baseUrl: HttpUrl): BrowserPageSnapshot {
        val cleanedHtml = html
            .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<noscript[\s\S]*?</noscript>""", RegexOption.IGNORE_CASE), " ")

        val rawTitle = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(cleanedHtml)?.groupValues?.getOrNull(1)
        val title = stripTags(rawTitle.orEmpty()).ifBlank { baseUrl.host.removePrefix("www.") }

        val metaDesc = extractMeta(cleanedHtml, "description")
            ?: extractMeta(cleanedHtml, "og:description")
            ?: ""

        val paragraphs = linkedSetOf<String>()
        Regex("""<(?:h1|h2|h3|p)[^>]*>(.*?)</(?:h1|h2|h3|p)>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(cleanedHtml)
            .forEach { match ->
                val text = stripTags(match.groupValues[1]).trim()
                if (text.length >= 24 && paragraphs.size < 16) {
                    paragraphs += text
                }
            }

        val links = mutableListOf<BrowserWebLink>()
        val seenUrls = linkedSetOf<String>()
        Regex("""<a\s+[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(cleanedHtml)
            .forEach { match ->
                val rawHref = decodeHtml(match.groupValues[1]).trim()
                if (rawHref.startsWith("#") || rawHref.startsWith("javascript:", true) || rawHref.startsWith("mailto:", true)) {
                    return@forEach
                }
                val resolved = baseUrl.resolve(rawHref)?.toString() ?: return@forEach
                val label = stripTags(match.groupValues[2]).trim()
                if (label.length in 3..120 && seenUrls.add(resolved) && links.size < 25) {
                    links += BrowserWebLink(
                        title = label,
                        url = resolved,
                        snippet = resolved.toHttpUrlOrNull()?.host?.removePrefix("www.").orEmpty()
                    )
                }
            }

        return BrowserPageSnapshot(
            url = baseUrl.toString(),
            title = title,
            host = baseUrl.host.removePrefix("www."),
            description = metaDesc,
            paragraphs = paragraphs.toList(),
            links = links
        )
    }

    private fun buildGoogleStartSnapshot(): BrowserPageSnapshot {
        return BrowserPageSnapshot(
            url = "https://www.google.com",
            title = "Google",
            host = "google.com",
            description = "Type a search query or paste any website link above to browse and capture downloadable media.",
            paragraphs = listOf(
                "Browse the web safely inside SaveBox, search Google, explore pages, and tap 'Fetch Media from Page' at the bottom whenever you find a video, song, or image."
            ),
            links = listOf(
                BrowserWebLink("YouTube Mobile", "https://m.youtube.com", "Search and open videos & music"),
                BrowserWebLink("Wikimedia Commons", "https://commons.wikimedia.org", "Free HD videos, audio, and photos"),
                BrowserWebLink("Traxxion Tech", "https://traxxiontech.com", "Developer home by Vincent Ganiza")
            )
        )
    }

    private fun extractMeta(html: String, nameOrProperty: String): String? {
        val pattern = Regex(
            """<meta\s+[^>]*(?:name|property)=["']${Regex.escape(nameOrProperty)}["'][^>]*content=["']([^"']+)["'][^>]*>|<meta\s+[^>]*content=["']([^"']+)["'][^>]*(?:name|property)=["']${Regex.escape(nameOrProperty)}["'][^>]*>""",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.find(html) ?: return null
        val value = match.groupValues[1].ifBlank { match.groupValues[2] }
        return decodeHtml(value).trim().takeIf { it.isNotBlank() }
    }

    private fun stripTags(raw: String): String {
        val noTags = raw.replace(Regex("""<[^>]+>"""), " ")
        return decodeHtml(noTags).replace(Regex("""\s+"""), " ").trim()
    }

    private fun decodeHtml(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#x2F;", "/")
            .replace("&#x3D;", "=")
    }
}
