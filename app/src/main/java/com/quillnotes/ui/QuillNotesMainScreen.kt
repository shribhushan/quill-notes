package com.quillnotes.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quillnotes.CrashReporter
import com.quillnotes.LaunchRequest
import com.quillnotes.R
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.media.ImageLoader
import com.quillnotes.ui.components.DockDestination
import com.quillnotes.ui.components.LocalImageLoader
import com.quillnotes.ui.components.QuillDock
import com.quillnotes.ui.navigation.Screen
import com.quillnotes.ui.screens.editor.EditorViewModel
import com.quillnotes.ui.screens.editor.NoteEditorScreen
import com.quillnotes.ui.screens.home.HomeScreen
import com.quillnotes.ui.screens.journal.JournalScreen
import com.quillnotes.ui.screens.onboarding.OnboardingScreen
import com.quillnotes.ui.screens.planner.PlannerScreen
import com.quillnotes.ui.screens.search.SearchScreen
import com.quillnotes.ui.screens.settings.SettingsScreen
import com.quillnotes.ui.screens.sync.SyncScreen
import com.quillnotes.ui.screens.trash.TrashScreen
import com.quillnotes.ui.util.LocalTimeText
import com.quillnotes.ui.util.rememberTimeText

val QuillDestinations = listOf(
    DockDestination(Screen.Home.route, R.string.nav_notes, Icons.Outlined.StickyNote2, Icons.Rounded.StickyNote2),
    DockDestination(Screen.Journal.route, R.string.nav_journal, Icons.Outlined.AutoStories, Icons.Rounded.AutoStories),
    DockDestination(Screen.Planner.route, R.string.nav_planner, Icons.Outlined.CheckCircle, Icons.Rounded.CheckCircle)
)

private val tabRoutes = QuillDestinations.map { it.route }
private const val NAV_DURATION = 320

/** Phones and folded foldables get one pane; tablets and unfolded foldables get list + editor side by side. */
private const val TWO_PANE_MIN_WIDTH_DP = 840
private val ListPaneWidth = 400.dp

private fun tabIndex(route: String?): Int = tabRoutes.indexOf(route)

/** A note shown in the editor pane of the two-pane layout. */
private data class PaneNote(val id: Long?, val type: String, val voice: Boolean = false)

/**
 * What the activity shows: the first-run introduction until it has been seen, then the app. Also
 * offers to share the last crash report (nothing is sent without the user choosing to).
 */
@Composable
fun QuillAppRoot(imageLoader: ImageLoader, launchRequest: LaunchRequest = LaunchRequest.NONE) {
    val main: MainViewModel = hiltViewModel()
    val onboardingDone by main.onboardingDone.collectAsState()
    val context = LocalContext.current
    var crashReport by remember { mutableStateOf(CrashReporter.pending(context)) }

    CompositionLocalProvider(LocalImageLoader provides imageLoader, LocalTimeText provides rememberTimeText()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (onboardingDone) {
                null -> Unit
                false -> OnboardingScreen(onFinish = main::finishOnboarding)
                true -> QuillNotesMainScreen(launchRequest)
            }
        }

        val report = crashReport
        if (report != null && onboardingDone == true) {
            AlertDialog(
                onDismissRequest = { CrashReporter.clear(context); crashReport = null },
                title = { Text(stringResource(R.string.crash_prompt_title)) },
                text = { Text(stringResource(R.string.crash_prompt_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_subject))
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                        CrashReporter.clear(context)
                        crashReport = null
                    }) { Text(stringResource(R.string.crash_prompt_share)) }
                },
                dismissButton = {
                    TextButton(onClick = { CrashReporter.clear(context); crashReport = null }) {
                        Text(stringResource(R.string.crash_prompt_dismiss))
                    }
                }
            )
        }
    }
}

@Composable
fun QuillNotesMainScreen(launchRequest: LaunchRequest = LaunchRequest.NONE) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var homeSelecting by remember { mutableStateOf(false) }
    /** Text shared into the app; handed to the next editor that opens, once. */
    val pendingSharedText = remember { mutableStateOf<String?>(null) }
    val expanded = LocalConfiguration.current.screenWidthDp >= TWO_PANE_MIN_WIDTH_DP
    var pane by remember { mutableStateOf<PaneNote?>(null) }

    val showDock = currentRoute in tabRoutes && !(currentRoute == Screen.Home.route && homeSelecting)

    /** Opens a note: in the side pane on a wide screen, as a full screen otherwise. */
    val openEditor: (id: Long?, type: String, voice: Boolean) -> Unit = { id, type, voice ->
        if (expanded) pane = PaneNote(id, type, voice)
        else navController.navigate(Screen.Editor.createRoute(id, type, voice))
    }

    // Act on whatever opened the app: a notification, a shortcut, the share sheet…
    LaunchedEffect(launchRequest.token) {
        when (launchRequest.kind) {
            LaunchRequest.Kind.NONE -> Unit
            LaunchRequest.Kind.NEW_NOTE -> openEditor(null, "note", false)
            LaunchRequest.Kind.NEW_TASK -> openEditor(null, "task", false)
            LaunchRequest.Kind.NEW_JOURNAL -> openEditor(null, "journal", false)
            LaunchRequest.Kind.NEW_VOICE -> openEditor(null, "note", true)
            LaunchRequest.Kind.OPEN_PLANNER -> navController.navigate(Screen.Planner.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            LaunchRequest.Kind.OPEN_NOTE -> openEditor(launchRequest.noteId, "note", false)
            LaunchRequest.Kind.OPEN_TASK -> openEditor(launchRequest.noteId, "task", false)
            LaunchRequest.Kind.SEARCH -> navController.navigate(Screen.Search.route)
            LaunchRequest.Kind.SHARED_TEXT -> {
                pendingSharedText.value = launchRequest.sharedText
                openEditor(null, "note", false)
            }
        }
    }

    val navigation: @Composable (Modifier) -> Unit = { modifier ->
        NavigationArea(
            navController = navController,
            currentRoute = currentRoute,
            showDock = showDock,
            pendingSharedText = pendingSharedText,
            openEditor = openEditor,
            onHomeSelectionChange = { homeSelecting = it },
            modifier = modifier
        )
    }

    if (!expanded) {
        navigation(Modifier.fillMaxSize())
    } else {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            navigation(Modifier.width(ListPaneWidth).fillMaxHeight())
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                val current = pane
                if (current == null) {
                    EditorPanePlaceholder()
                } else {
                    // One view-model per open note, so switching notes never mixes their edits.
                    val key = "pane/${current.id}/${current.type}/${current.voice}"
                    val viewModel: EditorViewModel = hiltViewModel(key = key)
                    androidx.compose.runtime.DisposableEffect(key) { onDispose { viewModel.flush() } }
                    NoteEditorScreen(
                        noteId = current.id,
                        noteType = current.type,
                        initialContent = remember(key) { if (current.id == null) pendingSharedText.value.also { pendingSharedText.value = null } else null },
                        startVoice = current.voice,
                        onBack = { pane = null },
                        onOpenNote = { id -> pane = PaneNote(id, "note") },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorPanePlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.pane_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The navigation graph plus the floating dock: the whole app on a phone, the list pane on a tablet. */
@Composable
private fun NavigationArea(
    navController: NavHostController,
    currentRoute: String?,
    showDock: Boolean,
    pendingSharedText: androidx.compose.runtime.MutableState<String?>,
    openEditor: (id: Long?, type: String, voice: Boolean) -> Unit,
    onHomeSelectionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val from = tabIndex(initialState.destination.route)
                val to = tabIndex(targetState.destination.route)
                if (from >= 0 && to >= 0) {
                    // Between tabs: a short lateral drift in the direction of travel.
                    slideInHorizontally(tween(NAV_DURATION, easing = FastOutSlowInEasing)) { w -> if (to > from) w / 8 else -w / 8 } +
                        fadeIn(tween(NAV_DURATION))
                } else {
                    slideInHorizontally(tween(NAV_DURATION, easing = FastOutSlowInEasing)) { w -> w / 4 } +
                        fadeIn(tween(NAV_DURATION))
                }
            },
            exitTransition = {
                val from = tabIndex(initialState.destination.route)
                val to = tabIndex(targetState.destination.route)
                if (from >= 0 && to >= 0) {
                    slideOutHorizontally(tween(NAV_DURATION, easing = FastOutSlowInEasing)) { w -> if (to > from) -w / 8 else w / 8 } +
                        fadeOut(tween(NAV_DURATION / 2))
                } else {
                    slideOutHorizontally(tween(NAV_DURATION, easing = FastOutSlowInEasing)) { w -> -w / 10 } +
                        fadeOut(tween(NAV_DURATION))
                }
            },
            popEnterTransition = {
                slideInHorizontally(tween(NAV_DURATION, easing = FastOutSlowInEasing)) { w -> -w / 10 } +
                    fadeIn(tween(NAV_DURATION))
            },
            popExitTransition = {
                slideOutHorizontally(tween(NAV_DURATION, easing = FastOutSlowInEasing)) { w -> w / 4 } +
                    fadeOut(tween(NAV_DURATION / 2))
            }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNoteClick = { id -> openEditor(id, "note", false) },
                    onNewNote = { openEditor(null, "note", false) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onSelectionModeChange = onHomeSelectionChange,
                    onSearchClick = { navController.navigate(Screen.Search.route) }
                )
            }

            composable(Screen.Journal.route) {
                JournalScreen(
                    onEntryClick = { id -> openEditor(id, "journal", false) },
                    onNewEntry = { openEditor(null, "journal", false) }
                )
            }

            composable(Screen.Planner.route) {
                PlannerScreen(
                    onTaskClick = { id -> openEditor(id, "task", false) },
                    onNewTask = { openEditor(null, "task", false) }
                )
            }

            composable(
                route = Screen.Editor.route,
                arguments = listOf(
                    navArgument("noteId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("noteType") {
                        type = NavType.StringType
                        defaultValue = "note"
                    },
                    navArgument("voice") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                ),
                enterTransition = {
                    slideInVertically(tween(NAV_DURATION, easing = FastOutSlowInEasing)) { h -> h / 6 } +
                        fadeIn(tween(NAV_DURATION))
                },
                popExitTransition = {
                    slideOutVertically(tween(NAV_DURATION, easing = FastOutSlowInEasing)) { h -> h / 6 } +
                        fadeOut(tween(NAV_DURATION / 2))
                }
            ) { entry ->
                val noteId = entry.arguments?.getLong("noteId") ?: -1L
                val noteType = entry.arguments?.getString("noteType") ?: "note"
                val startVoice = entry.arguments?.getBoolean("voice") ?: false
                // Shared text belongs to the editor that was opened for it, and only to that one.
                val initialContent = remember {
                    if (noteId == -1L) pendingSharedText.value.also { pendingSharedText.value = null } else null
                }
                NoteEditorScreen(
                    noteId = if (noteId == -1L) null else noteId,
                    noteType = noteType,
                    initialContent = initialContent,
                    startVoice = startVoice,
                    onBack = { navController.popBackStack() },
                    onOpenNote = { id -> navController.navigate(Screen.Editor.createRoute(id)) }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { id, type ->
                        val kind = when (type) {
                            NoteType.JOURNAL -> "journal"
                            NoteType.TASK -> "task"
                            NoteType.NOTE -> "note"
                        }
                        openEditor(id, kind, false)
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSyncClick = { navController.navigate(Screen.Sync.route) },
                    onTrashClick = { navController.navigate(Screen.Trash.route) }
                )
            }

            composable(Screen.Sync.route) {
                SyncScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Trash.route) {
                TrashScreen(onBack = { navController.popBackStack() })
            }
        }

        AnimatedVisibility(
            visible = showDock,
            enter = slideInVertically(tween(NAV_DURATION)) { it } + fadeIn(),
            exit = slideOutVertically(tween(NAV_DURATION)) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            QuillDock(
                destinations = QuillDestinations,
                currentRoute = currentRoute,
                onNavigate = { route ->
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onCreate = {
                    val type = when (currentRoute) {
                        Screen.Journal.route -> "journal"
                        Screen.Planner.route -> "task"
                        else -> "note"
                    }
                    openEditor(null, type, false)
                },
                createLabel = when (currentRoute) {
                    Screen.Journal.route -> stringResource(R.string.fab_new_journal)
                    Screen.Planner.route -> stringResource(R.string.fab_new_task)
                    else -> stringResource(R.string.fab_new_note)
                }
            )
        }
    }
}
