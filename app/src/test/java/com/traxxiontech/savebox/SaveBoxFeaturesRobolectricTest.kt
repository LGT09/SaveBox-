package com.traxxiontech.savebox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.traxxiontech.savebox.data.download.DocumentFileManager
import com.traxxiontech.savebox.data.download.StorageManager
import com.traxxiontech.savebox.data.local.FavoriteEntity
import com.traxxiontech.savebox.data.local.SaveBoxDatabase
import com.traxxiontech.savebox.data.local.SettingsDataStore
import com.traxxiontech.savebox.model.BackgroundColorBlendMode
import com.traxxiontech.savebox.model.RepeatPlaybackMode
import com.traxxiontech.savebox.ui.components.RewardedDownloadPassDialog
import com.traxxiontech.savebox.ui.components.SaveBoxAdUnits
import com.traxxiontech.savebox.ui.components.ThumbnailDownloadProgressOverlay
import com.traxxiontech.savebox.ui.theme.ExtractedImageTheme
import com.traxxiontech.savebox.ui.theme.ImageColorExtractor
import com.traxxiontech.savebox.ui.theme.SaveBoxTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SaveBoxFeaturesRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: SaveBoxDatabase
    private lateinit var storageManager: StorageManager
    private lateinit var documentFileManager: DocumentFileManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SaveBoxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storageManager = StorageManager(context)
        documentFileManager = DocumentFileManager(context, storageManager)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun favoritesDao_insertsQueriesAndDeletesBookmarks() = runBlocking {
        val dao = db.favoritesDao()
        val favorite = FavoriteEntity(
            downloadId = 42L,
            title = "Harare Sunset Aerial",
            filePath = "/storage/emulated/0/Download/SaveBox/Videos/sunset.mp4",
            mediaType = "VIDEO",
            sourceHost = "youtube.com",
            durationMs = 180_000L,
            fileSizeBytes = 24_500_000L
        )
        dao.upsert(favorite)

        val all = dao.observeAllFavorites().first()
        assertEquals(1, all.size)
        assertEquals("Harare Sunset Aerial", all.first().title)
        assertEquals(42L, all.first().downloadId)
        assertTrue(dao.isFavoriteFlow(42L).first())
        assertNotNull(dao.getByDownloadId(42L))

        dao.deleteByDownloadId(42L)
        assertTrue(dao.observeAllFavorites().first().isEmpty())
        assertFalse(dao.isFavoriteFlow(42L).first())
        assertNull(dao.getByDownloadId(42L))
    }

    @Test
    fun documentFileManager_listsRenamesMovesAndDeletesFiles() = runBlocking {
        storageManager.ensureAllFolders()
        val videoFile = File(File(storageManager.getSaveBoxRootDir(), "Videos"), "clip_test.mp4")
        videoFile.writeText("sample-video-bytes")

        val createdDir = documentFileManager.createFolder("Videos", "TravelClips", isSafTree = false)
        assertTrue(createdDir)

        val listed = documentFileManager.listSaveBoxCategoryFiles("Videos")
        val targetEntry = listed.firstOrNull { it.name == "clip_test.mp4" }
        assertNotNull(targetEntry)
        assertTrue(listed.any { it.isDirectory && it.name == "TravelClips" })

        val renameResult = documentFileManager.renameEntry(targetEntry!!, "renamed_clip")
        assertTrue(renameResult.success)
        assertEquals("renamed_clip.mp4", renameResult.newName)

        val afterRename = documentFileManager.listSaveBoxCategoryFiles("Videos")
            .firstOrNull { it.name == "renamed_clip.mp4" }
        assertNotNull(afterRename)

        val moveResult = documentFileManager.moveToSaveBoxCategory(afterRename!!, "Favorites")
        assertTrue(moveResult.success)
        assertEquals("Favorites", moveResult.newFolder)

        val favoritesList = documentFileManager.listSaveBoxCategoryFiles("Favorites")
        val movedEntry = favoritesList.firstOrNull { it.name == "renamed_clip.mp4" }
        assertNotNull(movedEntry)

        val deleted = documentFileManager.deleteEntry(movedEntry!!)
        assertTrue(deleted)
        assertTrue(
            documentFileManager.listSaveBoxCategoryFiles("Favorites")
                .none { it.name == "renamed_clip.mp4" }
        )
    }

    @Test
    fun appAdsTxtAsset_matchesExactAdMobPublisherLine() {
        val assetText = context.assets.open("app-ads.txt").bufferedReader().use { it.readText().trim() }
        assertEquals(
            "google.com, pub-4601877857713565, DIRECT, f08c47fec0942fa0",
            assetText
        )
        assertEquals(SaveBoxAdUnits.APP_ADS_TXT_LINE, assetText)
    }

    @Test
    fun thumbnailDownloadProgressOverlay_rendersPercentageIndicator() {
        composeRule.setContent {
            SaveBoxTheme {
                ThumbnailDownloadProgressOverlay(
                    progress = 0.64f,
                    status = "DOWNLOADING",
                    isIndeterminate = false
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("64%").assertIsDisplayed()
    }

    @Test
    fun thumbnailDownloadProgressOverlay_rendersPausedIndicator() {
        composeRule.setContent {
            SaveBoxTheme {
                ThumbnailDownloadProgressOverlay(
                    progress = 0.40f,
                    status = "PAUSED",
                    isIndeterminate = false
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("||").assertIsDisplayed()
    }

    @Test
    fun rewardedDownloadPassDialog_displaysTimeGrantPrompt() {
        composeRule.setContent {
            SaveBoxTheme {
                RewardedDownloadPassDialog(
                    onDismiss = {},
                    onRewardGranted = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rewarded_download_pass_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Sponsored Download Pass").assertIsDisplayed()
    }

    @Test
    fun imageColorExtractor_extractsOpaqueThemeFromBitmap() {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(AndroidColor.rgb(220, 90, 45))
        val paint = android.graphics.Paint().apply { color = AndroidColor.rgb(35, 160, 145) }
        canvas.drawRect(0f, 32f, 64f, 64f, paint)

        val extracted = ImageColorExtractor.extractThemeFromBitmap(bmp)
        bmp.recycle()

        assertTrue((extracted.primaryArgb ushr 24) == 0xFFL)
        assertTrue((extracted.secondaryArgb ushr 24) == 0xFFL)
        assertTrue((extracted.surfaceArgb ushr 24) == 0xFFL)
        assertTrue((extracted.backgroundArgb ushr 24) == 0xFFL)
    }

    @Test
    fun settingsDataStore_persistsBackgroundGridColumnsAndRepeatMode() = runBlocking {
        val store = SettingsDataStore(context)
        val sampleTheme = ExtractedImageTheme(
            primaryArgb = 0xFF7A56B4L,
            secondaryArgb = 0xFF49819EL,
            surfaceArgb = 0xFF141824L,
            backgroundArgb = 0xFF0A0D14L
        )
        store.setCustomBackground("/data/user/0/com.traxxiontech.savebox/files/backgrounds/bg.jpg", sampleTheme)
        store.setBackgroundColorBlendMode(BackgroundColorBlendMode.MANUAL_PALETTE)
        store.setLibraryGridColumns(10)
        store.setDefaultRepeatMode(RepeatPlaybackMode.REPEAT_ONE)
        store.setDefaultSeekStepSec(15)

        val loaded = store.settingsFlow.first()
        assertEquals("/data/user/0/com.traxxiontech.savebox/files/backgrounds/bg.jpg", loaded.backgroundImagePath)
        assertEquals(BackgroundColorBlendMode.MANUAL_PALETTE, loaded.backgroundColorBlendMode)
        assertEquals(0xFF7A56B4L, loaded.extractedPrimaryArgb)
        assertEquals(10, loaded.libraryGridColumns)
        assertEquals(RepeatPlaybackMode.REPEAT_ONE, loaded.defaultRepeatMode)
        assertEquals(15, loaded.defaultSeekStepSec)

        store.setLibraryGridColumns(99)
        assertEquals(10, store.settingsFlow.first().libraryGridColumns)
        store.setLibraryGridColumns(1)
        assertEquals(3, store.settingsFlow.first().libraryGridColumns)

        store.clearCustomBackground()
        val cleared = store.settingsFlow.first()
        assertNull(cleared.backgroundImagePath)
        assertNull(cleared.extractedPrimaryArgb)
    }

    @Test
    fun repeatPlaybackMode_labelsMatchOnScreenControls() {
        assertEquals("No Repeat", RepeatPlaybackMode.OFF.label)
        assertEquals("Repeat ×1", RepeatPlaybackMode.REPEAT_ONE.label)
        assertEquals("Auto Repeat", RepeatPlaybackMode.REPEAT_ALL.label)
    }
}
