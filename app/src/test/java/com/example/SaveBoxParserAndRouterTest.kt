package com.example

import com.example.data.remote.MediaApiService
import com.example.data.remote.MediaResponseParser
import com.example.data.remote.SaveBoxApiManager
import com.example.model.MediaPlatform
import com.example.model.MediaType
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SaveBoxParserAndRouterTest {

    private fun createApiManager(): SaveBoxApiManager {
        val client = OkHttpClient.Builder().build()
        val service = Retrofit.Builder()
            .baseUrl("https://nayan-video-downloader.vercel.app/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MediaApiService::class.java)
        return SaveBoxApiManager(client, service)
    }

    @Test
    fun detectPlatform_identifiesAllSupportedDomains() {
        val manager = createApiManager()
        assertEquals(MediaPlatform.YOUTUBE, manager.detectPlatform("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(MediaPlatform.YOUTUBE_PLAYLIST, manager.detectPlatform("https://www.youtube.com/playlist?list=PL123"))
        assertEquals(MediaPlatform.TIKTOK, manager.detectPlatform("https://www.tiktok.com/@user/video/12345"))
        assertEquals(MediaPlatform.INSTAGRAM, manager.detectPlatform("https://www.instagram.com/reel/abc/"))
        assertEquals(MediaPlatform.FACEBOOK, manager.detectPlatform("https://fb.watch/xyz"))
        assertEquals(MediaPlatform.TWITTER, manager.detectPlatform("https://x.com/user/status/123"))
        assertEquals(MediaPlatform.THREADS, manager.detectPlatform("https://www.threads.net/@user/post/123"))
        assertEquals(MediaPlatform.SPOTIFY, manager.detectPlatform("https://open.spotify.com/track/123"))
        assertEquals(MediaPlatform.SOUNDCLOUD, manager.detectPlatform("https://soundcloud.com/artist/track"))
        assertEquals(MediaPlatform.PINTEREST, manager.detectPlatform("https://pin.it/abc123"))
        assertEquals(MediaPlatform.LIKEE, manager.detectPlatform("https://likee.video/@user/video/1"))
        assertEquals(MediaPlatform.TERABOX, manager.detectPlatform("https://terabox.com/s/1abc"))
        assertEquals(MediaPlatform.GOOGLE_DRIVE, manager.detectPlatform("https://drive.google.com/file/d/abc/view"))
    }

    @Test
    fun parseAnalyzedMedia_extractsFormatsAndMetadata() {
        val sampleJson = """
            {
              "data": {
                "title": "Sample Sunset Clip",
                "author": "Traxxion Studio",
                "duration": "03:15",
                "thumbnail": "https://cdn.example.com/thumb.jpg",
                "medias": [
                  { "quality": "1080p", "url": "https://cdn.example.com/video_1080.mp4", "ext": "mp4", "size": "24.5 MB" },
                  { "quality": "Audio 128kbps", "url": "https://cdn.example.com/audio.mp3", "ext": "mp3", "size": "3.2 MB" }
                ]
              }
            }
        """.trimIndent()

        val element = JsonParser.parseString(sampleJson)
        val parsed = MediaResponseParser.parseAnalyzedMedia(
            root = element,
            sourceUrl = "https://youtu.be/test",
            platform = MediaPlatform.YOUTUBE
        )

        assertNotNull(parsed)
        assertEquals("Sample Sunset Clip", parsed!!.title)
        assertEquals("Traxxion Studio", parsed.author)
        assertEquals(195_000L, parsed.durationMs)
        assertTrue(parsed.formats.size >= 2)
        assertEquals(MediaType.VIDEO, parsed.formats[0].mediaType)
        assertEquals(MediaType.AUDIO, parsed.formats[1].mediaType)
    }

    @Test
    fun parseSearchResults_extractsTracksAndUrls() {
        val searchJson = """
            {
              "results": [
                {
                  "id": "yt_1",
                  "title": "Harare Skyline 4K",
                  "channel": "Zimbabwe Aerials",
                  "duration": "4:20",
                  "thumbnail": "https://img.youtube.com/vi/abc/hqdefault.jpg",
                  "url": "https://www.youtube.com/watch?v=abc"
                }
              ]
            }
        """.trimIndent()

        val items = MediaResponseParser.parseSearchResults(
            root = JsonParser.parseString(searchJson),
            platform = MediaPlatform.YOUTUBE
        )
        assertEquals(1, items.size)
        assertEquals("Harare Skyline 4K", items[0].title)
        assertEquals("https://www.youtube.com/watch?v=abc", items[0].url)
    }
}
