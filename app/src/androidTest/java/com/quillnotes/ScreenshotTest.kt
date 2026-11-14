package com.quillnotes

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.sync.SyncProvider
import com.quillnotes.domain.LinkInfo
import com.quillnotes.domain.LinkTarget
import com.quillnotes.domain.NoteRef
import com.quillnotes.domain.NoteSearch
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
import com.quillnotes.ui.screens.home.NotebookInfo
import com.quillnotes.ui.screens.journal.JournalActions
import com.quillnotes.ui.screens.journal.JournalContent
import com.quillnotes.ui.screens.journal.JournalUiState
import com.quillnotes.ui.screens.journal.JournalView
import com.quillnotes.ui.screens.onboarding.OnboardingScreen
import com.quillnotes.ui.screens.planner.PlannerActions
import com.quillnotes.ui.screens.planner.PlannerContent
import com.quillnotes.ui.screens.planner.PlannerUiState
import com.quillnotes.ui.screens.planner.PlannerView
import com.quillnotes.ui.screens.search.SearchActions
import com.quillnotes.ui.screens.search.SearchContent
import com.quillnotes.ui.screens.search.SearchQuery
import com.quillnotes.ui.screens.search.SearchUiState
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
import com.quillnotes.ui.util.LocalTimeText
import com.quillnotes.ui.util.ResourceTimeText
import com.quillnotes.ui.util.startOfDay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Renders every screen with fixture data and writes PNGs to the app's files dir
 * (files/screenshots). CI pulls them off the emulator for visual review.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private class Shot(val name: String, val theme: String, val locale: String? = null, val content: @Composable () -> Unit)

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

    private fun localizedContext(tag: String): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(configuration)
    }

    /** Renders [content] in another language: string resources, relative-time words and date formats. */
    @Composable
    private fun WithLocale(tag: String?, content: @Composable () -> Unit) {
        if (tag == null) {
            content()
            return
        }
        val context = remember(tag) { localizedContext(tag) }
        val words = remember(tag) { ResourceTimeText(context.resources) }
        CompositionLocalProvider(
            LocalContext provides context,
            LocalConfiguration provides context.resources.configuration,
            LocalTimeText provides words,
            content = content
        )
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

    /**
     * Renders each shot and writes it to files/screenshots. A shot that throws does not stop the others:
     * the failures are written to FAILURES_<first shot>.txt and reported once at the end.
     */
    private fun capture(all: List<Shot>) {
        var current by mutableStateOf(all.first())
        compose.setContent {
            val shot = current
            key(shot.name) {
                WithLocale(shot.locale) { QuillTheme(themeKey = shot.theme) { shot.content() } }
            }
        }

        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screenshots")
        dir.mkdirs()

        val original = Locale.getDefault()
        val failures = mutableListOf<String>()
        try {
            all.forEach { shot ->
                try {
                    Locale.setDefault(shot.locale?.let { Locale.forLanguageTag(it) } ?: original)
                    compose.runOnIdle { current = shot }
                    compose.waitForIdle()
                    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                    File(dir, "${shot.name}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } catch (e: Throwable) {
                    failures += "${shot.name}: ${e::class.java.simpleName}: ${e.message?.take(300)}"
                }
            }
        } finally {
            Locale.setDefault(original)
        }
        if (failures.isNotEmpty()) {
            File(dir, "FAILURES_${all.first().name}.txt").writeText(failures.joinToString("\n"))
            throw AssertionError("Screens failed to render:\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun captureAllScreens() = capture(shots())

    @Test
    fun captureNewScreens() = capture(newShots())

    private fun newShots(): List<Shot> {
        val now = data.now
        val hour = 60 * 60_000L
        val everything = data.notes + data.journal + data.tasks

        val search = SearchUiState(
            query = SearchQuery(text = "reel"),
            results = NoteSearch.search(everything, "reel"),
            notebooks = listOf("Work", "Writing"),
            tags = listOf("work", "writing", "travel"),
            totalNotes = everything.size,
            now = now
        )
        val searchIdle = SearchUiState(tags = listOf("work", "writing", "travel"), notebooks = listOf("Work"), totalNotes = everything.size, now = now)

        val packing = NoteEntity(
            id = 8, type = NoteType.NOTE, title = "Packing list", notebook = "Personal", tags = "travel",
            content = "- [x] Passport\n- [x] Tickets\n- [ ] Charger\n- [ ] Camera", updatedAt = now - 40 * 60_000L
        )
        val home = HomeUiState(
            notes = listOf(packing) + data.notes,
            tags = listOf("writing", "work", "travel"),
            colors = listOf("yellow", "green", "blue"),
            notebooks = listOf(NotebookInfo("Personal", 1), NotebookInfo("Work", 3)),
            archivedCount = 2,
            totalCount = data.notes.size + 1,
            now = now
        )
        val journalCalendar = JournalUiState(entries = data.journal, now = now, view = JournalView.CALENDAR, selectedDay = startOfDay(now))
        val plannerWeek = PlannerUiState(tasks = data.tasks, now = now, view = PlannerView.WEEK)
        val planner = PlannerUiState(tasks = data.tasks, now = now)

        val markdown = EditorState(
            id = 91, title = "Release checklist", type = NoteType.NOTE, tags = "work", notebook = "Work", isPinned = true,
            content = "## Before we ship\n\nShip **2.3.0** this week, see [[Q3 roadmap]] and the *final* notes.\n\n" +
                "- [x] Migration dry run\n- [x] Contract tests green\n- [ ] Update the store listing\n- [ ] Tag the release\n\n" +
                "1. Build the APK\n2. Smoke-test it on a phone\n\nDetails: [release notes](https://example.com/notes)\n",
            createdAt = now - 86_400_000L, savedAt = now - 120_000L, canUndo = true
        )
        val links = LinkInfo(
            backlinks = listOf(NoteRef(2, "Q3 roadmap", NoteType.NOTE)),
            outgoing = listOf(LinkTarget("Q3 roadmap", NoteRef(2, "Q3 roadmap", NoteType.NOTE)), LinkTarget("Store listing", null))
        )
        val repeating = EditorState(
            id = 92, title = "Pay rent", type = NoteType.TASK, tags = "home", priority = 2, repeatRule = "1M",
            dueDate = now + 3 * 24 * hour, content = "Transfer before the 5th.\n- [ ] Check the amount",
            createdAt = now - 86_400_000L, savedAt = now
        )

        return listOf(
            Shot("20_search_results", ThemeKeys.PAPER) { SearchContent(search, SearchActions()) },
            Shot("21_search_idle_midnight", ThemeKeys.MIDNIGHT) { SearchContent(searchIdle, SearchActions()) },
            Shot("22_journal_calendar", ThemeKeys.PAPER) {
                WithDock(Screen.Journal.route) { JournalContent(journalCalendar, JournalActions()) }
            },
            Shot("23_planner_week", ThemeKeys.PAPER) {
                WithDock(Screen.Planner.route) { PlannerContent(plannerWeek, PlannerActions()) }
            },
            Shot("24_onboarding", ThemeKeys.PAPER) { OnboardingScreen(onFinish = {}) },
            Shot("25_editor_markdown", ThemeKeys.PAPER) { EditorContent(markdown, EditorActions(), now = now, links = links) },
            Shot("26_editor_task_repeat", ThemeKeys.PAPER) { EditorContent(repeating, EditorActions(), now = now) },
            Shot("27_home_notebooks_checklist", ThemeKeys.PAPER) {
                WithDock(Screen.Home.route) { HomeContent(home, HomeActions()) }
            },
            Shot("30_home_hi", ThemeKeys.PAPER, "hi") { WithDock(Screen.Home.route) { HomeContent(home, HomeActions()) } },
            Shot("31_planner_mr", ThemeKeys.PAPER, "mr") { WithDock(Screen.Planner.route) { PlannerContent(planner, PlannerActions()) } },
            Shot("32_journal_calendar_hi", ThemeKeys.PAPER, "hi") {
                WithDock(Screen.Journal.route) { JournalContent(journalCalendar, JournalActions()) }
            },
            Shot("33_editor_markdown_mr", ThemeKeys.PAPER, "mr") { EditorContent(markdown, EditorActions(), now = now, links = links) },
            Shot("34_settings_hi", ThemeKeys.PAPER, "hi") { SettingsContent(SettingsUiState(theme = ThemeKeys.PAPER), SettingsActions()) },
            Shot("35_sync_mr", ThemeKeys.PAPER, "mr") { SyncContent(SyncUiState(provider = SyncProvider.GOOGLE_DRIVE, isConnected = true, lastSyncTime = now - 12 * 60_000L, unsyncedCount = 3), SyncActions(), now = now) },
            Shot("36_trash_hi", ThemeKeys.PAPER, "hi") { TrashContent(data.trash, TrashActions(), now = now) },
            Shot("37_search_results_mr", ThemeKeys.PAPER, "mr") { SearchContent(search, SearchActions()) }
        )
    }
}
