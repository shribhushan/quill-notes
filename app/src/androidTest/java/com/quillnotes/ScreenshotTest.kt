package com.quillnotes

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.sync.SyncProvider
import com.quillnotes.ui.QuillDestinations
import com.quillnotes.ui.components.LockedScreen
import com.quillnotes.ui.components.QuillDock
import com.quillnotes.ui.navigation.Screen
import com.quillnotes.ui.screens.editor.EditorActions
import com.quillnotes.ui.screens.editor.EditorContent
import com.quillnotes.ui.screens.editor.EditorState
import com.quillnotes.ui.screens.home.HomeActions
import com.quillnotes.ui.screens.home.HomeContent
import com.quillnotes.ui.screens.home.HomeUiState
import com.quillnotes.ui.screens.journal.JournalActions
import com.quillnotes.ui.screens.journal.JournalContent
import com.quillnotes.ui.screens.journal.JournalUiState
import com.quillnotes.ui.screens.planner.PlannerActions
import com.quillnotes.ui.screens.planner.PlannerContent
import com.quillnotes.ui.screens.planner.PlannerUiState
import com.quillnotes.ui.screens.settings.SettingsActions
import com.quillnotes.ui.screens.settings.SettingsContent
import com.quillnotes.ui.screens.settings.SettingsUiState
import com.quillnotes.ui.screens.sync.SyncActions
import com.quillnotes.ui.screens.sync.SyncContent
import com.quillnotes.ui.screens.sync.SyncUiState
import com.quillnotes.ui.screens.trash.TrashActions
import com.quillnotes.ui.screens.trash.TrashContent
import com.quillnotes.ui.theme.QuillTheme
import com.quillnotes.ui.theme.ThemeKeys
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders every screen with fixture data and writes PNGs to the app's files dir
 * (files/screenshots). CI pulls them off the emulator for visual review.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private class Shot(val name: String, val theme: String, val content: @Composable () -> Unit)

    private val data = SampleData()

    @Composable
    private fun WithDock(route: String, content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            content()
            QuillDock(
                destinations = QuillDestinations,
                currentRoute = route,
                onNavigate = {},
                onCreate = {},
                createLabel = "New",
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    private fun shots(): List<Shot> {
        val now = data.now
        val home = HomeUiState(
            notes = data.notes,
            tags = listOf("writing", "work", "travel", "personal"),
            colors = listOf("yellow", "green", "blue", "purple", "pink"),
            totalCount = data.notes.size,
            now = now
        )
        val journal = JournalUiState(entries = data.journal, now = now)
        val planner = PlannerUiState(tasks = data.tasks, now = now)
        val note = data.notes.first()
        val noteEditor = EditorState(
            id = note.id, title = note.title, content = note.content + "\n\nThe projector light catches the dust between them. He reaches for the reel; she doesn't let go.",
            type = NoteType.NOTE, tags = note.tags, color = note.color, isPinned = true,
            createdAt = now - 86_400_000L, savedAt = now - 60_000L, canUndo = true
        )
        val entry = data.journal.first()
        val journalEditor = EditorState(
            id = entry.id, title = entry.title, content = entry.content, type = NoteType.JOURNAL,
            mood = entry.mood, tags = "work, gratitude", createdAt = entry.createdAt, savedAt = now
        )
        val task = data.tasks[1]
        val taskEditor = EditorState(
            id = task.id, title = task.title, content = "• Goals for next quarter\n• On-call load\n• Feedback from the last review",
            type = NoteType.TASK, dueDate = task.dueDate, tags = task.tags, color = task.color,
            createdAt = now - 3_600_000L, savedAt = now
        )
        val sync = SyncUiState(
            provider = SyncProvider.GOOGLE_DRIVE, isConnected = true,
            lastSyncTime = now - 12 * 60_000L, unsyncedCount = 3
        )

        return listOf(
            Shot("01_home_grid", ThemeKeys.PAPER) {
                WithDock(Screen.Home.route) { HomeContent(home, HomeActions()) }
            },
            Shot("02_home_list", ThemeKeys.PAPER) {
                WithDock(Screen.Home.route) { HomeContent(home.copy(viewMode = "list"), HomeActions()) }
            },
            Shot("03_home_grid_midnight", ThemeKeys.MIDNIGHT) {
                WithDock(Screen.Home.route) { HomeContent(home, HomeActions()) }
            },
            Shot("04_home_selection", ThemeKeys.PAPER) {
                HomeContent(home.copy(selectedIds = setOf(3L, 4L)), HomeActions())
            },
            Shot("05_home_empty", ThemeKeys.PAPER) {
                WithDock(Screen.Home.route) { HomeContent(HomeUiState(now = now), HomeActions()) }
            },
            Shot("06_journal", ThemeKeys.PAPER) {
                WithDock(Screen.Journal.route) { JournalContent(journal, JournalActions()) }
            },
            Shot("07_journal_midnight", ThemeKeys.MIDNIGHT) {
                WithDock(Screen.Journal.route) { JournalContent(journal, JournalActions()) }
            },
            Shot("08_planner", ThemeKeys.PAPER) {
                WithDock(Screen.Planner.route) { PlannerContent(planner, PlannerActions()) }
            },
            Shot("09_planner_aurora", ThemeKeys.AURORA) {
                WithDock(Screen.Planner.route) { PlannerContent(planner, PlannerActions()) }
            },
            Shot("10_editor_note", ThemeKeys.PAPER) {
                EditorContent(noteEditor, EditorActions(), now = now)
            },
            Shot("11_editor_journal", ThemeKeys.PAPER) {
                EditorContent(journalEditor, EditorActions(), now = now)
            },
            Shot("12_editor_task_midnight", ThemeKeys.MIDNIGHT) {
                EditorContent(taskEditor, EditorActions(), now = now)
            },
            Shot("13_settings", ThemeKeys.PAPER) {
                SettingsContent(SettingsUiState(theme = ThemeKeys.PAPER, biometricLock = true), SettingsActions())
            },
            Shot("14_settings_midnight", ThemeKeys.MIDNIGHT) {
                SettingsContent(SettingsUiState(theme = ThemeKeys.MIDNIGHT, fontSize = "large"), SettingsActions())
            },
            Shot("15_sync", ThemeKeys.PAPER) {
                SyncContent(sync, SyncActions(), now = now)
            },
            Shot("16_sync_disconnected_midnight", ThemeKeys.MIDNIGHT) {
                SyncContent(SyncUiState(), SyncActions(), now = now)
            },
            Shot("17_trash", ThemeKeys.PAPER) {
                TrashContent(data.trash, TrashActions(), now = now)
            },
            Shot("18_locked", ThemeKeys.PAPER) {
                LockedScreen(onUnlock = {})
            }
        )
    }

    @Test
    fun captureAllScreens() {
        val all = shots()
        var current by mutableStateOf(all.first())
        compose.setContent {
            val shot = current
            key(shot.name) {
                QuillTheme(themeKey = shot.theme) { shot.content() }
            }
        }

        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screenshots")
        dir.deleteRecursively()
        dir.mkdirs()

        all.forEach { shot ->
            compose.runOnIdle { current = shot }
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "${shot.name}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
