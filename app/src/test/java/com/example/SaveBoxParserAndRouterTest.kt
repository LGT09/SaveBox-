package com.example

import com.example.data.remote.UrlMediaBot
import com.example.data.remote.YouTubeSearchEngine
import com.example.model.MediaType
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveBoxParserAndRouterTest {

    private fun createClient() = OkHttpClient.Builder().build()

    @Test
    fun extractYouTubeVideoId_parsesWatchShortsAndShortLinks() {
        val bot = UrlMediaBot(createClient())
        assertEquals(
            "dQw4w9WgXcQ",
            bot.extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ".toHttpUrl())
        )
        assertEquals(
            "abcDEF12345",
            bot.extractYouTubeVideoId("https://www.youtube.com/watch?v=abcDEF12345".toHttpUrl())
        )
        assertEquals(
            "shorts998877",
            bot.extractYouTubeVideoId("https://www.youtube.com/shorts/shorts998877".toHttpUrl())
        )
        assertNull(
            bot.extractYouTubeVideoId("https://example.com/video/clip.mp4".toHttpUrl())
        )
    }

    @Test
    fun parseYouTubePlayerResponse_extractsVideoAudioImageQualitiesAndSizes() {
        val bot = UrlMediaBot(createClient())
        val samplePlayerJson = """
            {
              "videoDetails": {
                "videoId": "vid123456",
                "title": "Victoria Falls Aerial Sunset",
                "author": "Traxxion Media",
                "lengthSeconds": "195",
                "thumbnail": {
                  "thumbnails": [
                    { "url": "https://i.ytimg.com/vi/vid123456/hqdefault.jpg" },
                    { "url": "https://i.ytimg.com/vi/vid123456/maxresdefault.jpg" }
                  ]
                }
              },
              "streamingData": {
                "formats": [
                  {
                    "itag": 22,
                    "url": "https://redirector.googlevideo.com/videoplayback?itag=22",
                    "mimeType": "video/mp4; codecs=\"avc1.64001F, mp4a.40.2\"",
                    "qualityLabel": "720p",
                    "width": 1280,
                    "height": 720,
                    "contentLength": "25690112"
                  }
                ],
                "adaptiveFormats": [
                  {
                    "itag": 137,
                    "url": "https://redirector.googlevideo.com/videoplayback?itag=137",
                    "mimeType": "video/mp4; codecs=\"avc1.640028\"",
                    "qualityLabel": "1080p",
                    "width": 1920,
                    "height": 1080,
                    "contentLength": "52428800"
                  },
                  {
                    "itag": 140,
                    "url": "https://redirector.googlevideo.com/videoplayback?itag=140",
                    "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                    "averageBitrate": 128000,
                    "contentLength": "3145728"
                  }
                ]
              }
            }
        """.trimIndent()

        val playerJson = JsonParser.parseString(samplePlayerJson).asJsonObject
        val analyzed = bot.parseYouTubePlayerResponse(
            root = playerJson,
            videoId = "vid123456",
            originalUrl = "https://www.youtube.com/watch?v=vid123456",
            host = "youtube.com"
        )

        assertNotNull(analyzed)
        assertEquals("Victoria Falls Aerial Sunset", analyzed!!.title)
        assertEquals("Traxxion Media", analyzed.author)
        assertEquals(195_000L, analyzed.durationMs)
        assertEquals("3:15", analyzed.durationText)
        assertTrue(analyzed.formats.any { it.mediaType == MediaType.VIDEO && it.quality == "1080p" && it.estimatedSizeBytes == 52428800L })
        assertTrue(analyzed.formats.any { it.mediaType == MediaType.VIDEO && it.quality == "720p" && it.sizeDisplay == "24.5 MB" })
        assertTrue(analyzed.formats.any { it.mediaType == MediaType.AUDIO && it.estimatedSizeBytes == 3145728L })
        assertTrue(analyzed.formats.any { it.mediaType == MediaType.IMAGE })
    }

    @Test
    fun parseYouTubePlayerResponse_returnsNullWhenOnlyCoverArtWithoutStreams() {
        val bot = UrlMediaBot(createClient())
        val coverOnlyJson = """
            {
              "playabilityStatus": {
                "status": "LOGIN_REQUIRED"
              },
              "videoDetails": {
                "videoId": "locked123",
                "title": "Restricted Video",
                "thumbnail": {
                  "thumbnails": [
                    { "url": "https://i.ytimg.com/vi/locked123/maxresdefault.jpg" }
                  ]
                }
              }
            }
        """.trimIndent()

        val playerJson = JsonParser.parseString(coverOnlyJson).asJsonObject
        val analyzed = bot.parseYouTubePlayerResponse(
            root = playerJson,
            videoId = "locked123",
            originalUrl = "https://www.youtube.com/watch?v=locked123",
            host = "youtube.com"
        )

        assertNull(analyzed)
    }

    @Test
    fun parseHtmlForMedia_extractsEmbeddedVideosAndImages() = runBlocking {
        val bot = UrlMediaBot(createClient())
        val html = """
            <html>
              <head>
                <title>Sunset Documentary Clip</title>
                <meta property="og:title" content="Sunset Documentary Clip" />
                <meta property="og:image" content="https://cdn.example.org/poster.jpg" />
                <meta property="og:video" content="https://cdn.example.org/video_1080p.mp4" />
              </head>
              <body>
                <video poster="https://cdn.example.org/poster.jpg">
                  <source src="https://cdn.example.org/video_720p.mp4" label="720p HD" type="video/mp4" />
                </video>
                <img src="https://cdn.example.org/photo_hd.png" srcset="https://cdn.example.org/photo_1200.png 1200w" />
              </body>
            </html>
        """.trimIndent()

        val parsed = bot.parseHtmlForMedia(
            html = html,
            baseUrl = "https://example.org/watch".toHttpUrl(),
            originalUrl = "https://example.org/watch",
            host = "example.org"
        )

        assertNotNull(parsed)
        assertEquals("Sunset Documentary Clip", parsed!!.title)
        assertTrue(parsed.formats.any { it.mediaType == MediaType.VIDEO && it.downloadUrl.contains("video_1080p.mp4") })
        assertTrue(parsed.formats.any { it.mediaType == MediaType.VIDEO && it.downloadUrl.contains("video_720p.mp4") })
        assertTrue(parsed.formats.any { it.mediaType == MediaType.IMAGE && it.downloadUrl.contains("photo_1200.png") })
    }

    @Test
    fun youtubeSearchEngine_parsesYtInitialDataRenderers() {
        val engine = YouTubeSearchEngine(createClient())
        val sampleInitialData = """
            {
              "contents": {
                "twoColumnSearchResultsRenderer": {
                  "primaryContents": {
                    "sectionListRenderer": {
                      "contents": [
                        {
                          "itemSectionRenderer": {
                            "contents": [
                              {
                                "videoRenderer": {
                                  "videoId": "harare4k",
                                  "title": { "runs": [{ "text": "Harare Skyline 4K Drone" }] },
                                  "ownerText": { "runs": [{ "text": "Zimbabwe Aerials" }] },
                                  "lengthText": { "simpleText": "4:20" },
                                  "viewCountText": { "simpleText": "45K views" },
                                  "thumbnail": {
                                    "thumbnails": [
                                      { "url": "https://i.ytimg.com/vi/harare4k/hqdefault.jpg" }
                                    ]
                                  }
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val items = engine.parseYtInitialData(JsonParser.parseString(sampleInitialData).asJsonObject)
        assertEquals(1, items.size)
        assertEquals("harare4k", items[0].id)
        assertEquals("Harare Skyline 4K Drone", items[0].title)
        assertEquals("Zimbabwe Aerials", items[0].channel)
        assertEquals("4:20", items[0].durationText)
        assertEquals("https://www.youtube.com/watch?v=harare4k", items[0].url)
    }

    @Test
    fun inAppBrowserEngine_parsesSearchAndPageLinks() {
        val engine = com.example.data.remote.InAppBrowserEngine(createClient())
        val searchHtml = """
            <html><body>
              <a class="result__a" href="https://duckduckgo.com/l/?uddg=https%3A%2F%2Ftraxxiontech.com%2Fsavebox">Traxxion Tech SaveBox</a>
              <a class="result__snippet">Official SaveBox page by Vincent Ganiza.</a>
            </body></html>
        """.trimIndent()
        val results = engine.parseDuckDuckGoSearchHtml(searchHtml)
        assertEquals(1, results.size)
        assertEquals("Traxxion Tech SaveBox", results[0].title)
        assertEquals("https://traxxiontech.com/savebox", results[0].url)

        val pageHtml = """
            <html>
              <head>
                <title>Traxxion Tech Hub</title>
                <meta name="description" content="Zimbabwe software engineering studio." />
              </head>
              <body>
                <p>SaveBox enables smart media capturing and offline playback with Material 3 glassmorphism.</p>
                <a href="/downloads">Browse Downloads</a>
              </body>
            </html>
        """.trimIndent()
        val snapshot = engine.parseHtmlPage(pageHtml, "https://traxxiontech.com".toHttpUrl())
        assertEquals("Traxxion Tech Hub", snapshot.title)
        assertEquals("Zimbabwe software engineering studio.", snapshot.description)
        assertTrue(snapshot.paragraphs.any { it.contains("SaveBox enables smart media capturing") })
        assertTrue(snapshot.links.any { it.url == "https://traxxiontech.com/downloads" })
    }
}
