package com.quillnotes.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quillnotes.LaunchRequest
import com.quillnotes.ui.components.DockDestination
import com.quillnotes.ui.components.QuillDock
import com.quillnotes.ui.navigation.Screen
import com.quillnotes.ui.screens.editor.NoteEditorScreen
import com.quillnotes.ui.screens.home.HomeScreen
import com.quillnotes.ui.screens.journal.JournalScreen
import com.quillnotes.ui.screens.planner.PlannerScreen
import com.quillnotes.ui.screens.settings.SettingsScreen
import com.quillnotes.ui.screens.sync.SyncScreen
import com.quillnotes.ui.screens.trash.TrashScreen

val QuillDestinations = listOf(
    DockDestination(Screen.Home.route, "Notes", Icons.Outlined.StickyNote2, Icons.Rounded.StickyNote2),
    DockDestination(Screen.Journal.route, "Journal", Icons.Outlined.AutoStories, Icons.Rounded.AutoStories),
    DockDestination(Screen.Planner.route, "Planner", Icons.Outlined.CheckCircle, Icons.Rounded.CheckCircle)
)

private val tabRoutes = QuillDestinations.map { it.route }
private const val NAV_DURATION = 320

private fun tabIndex(route: String?): Int = tabRoutes.indexOf(route)

@Composable
fun QuillNotesMainScreen(launchRequest: LaunchRequest = LaunchRequest.NONE) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var homeSelecting by remember { mutableStateOf(false) }
    /** Text shared into the app; handed to the next editor that opens, once. */
    val pendingSharedText = remember { mutableStateOf<String?>(null) }

    val showDock = currentRoute in tabRoutes && !(currentRoute == Screen.Home.route && homeSelecting)

    // Act on whatever opened the app: a notification, a shortcut, the share sheet…
    LaunchedEffect(launchRequest.token) {
        when (launchRequest.kind) {
            LaunchRequest.Kind.NONE -> Unit
            LaunchRequest.Kind.NEW_NOTE -> navController.navigate(Screen.Editor.createRoute())
            LaunchRequest.Kind.NEW_TASK -> navController.navigate(Screen.Editor.createRoute(noteType = "task"))
            LaunchRequest.Kind.NEW_JOURNAL -> navController.navigate(Screen.Editor.createRoute(noteType = "journal"))
            LaunchRequest.Kind.OPEN_NOTE -> navController.navigate(Screen.Editor.createRoute(launchRequest.noteId))
            LaunchRequest.Kind.OPEN_TASK -> navController.navigate(Screen.Editor.createRoute(launchRequest.noteId, "task"))
            LaunchRequest.Kind.SEARCH -> Unit // handled once the search screen exists
            LaunchRequest.Kind.SHARED_TEXT -> {
                pendingSharedText.value = launchRequest.sharedText
                navController.navigate(Screen.Editor.createRoute())
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                    onNoteClick = { id -> navController.navigate(Screen.Editor.createRoute(id)) },
                    onNewNote = { navController.navigate(Screen.Editor.createRoute()) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onSelectionModeChange = { homeSelecting = it }
                )
            }

            composable(Screen.Journal.route) {
                JournalScreen(
                    onEntryClick = { id -> navController.navigate(Screen.Editor.createRoute(id, "journal")) },
                    onNewEntry = { navController.navigate(Screen.Editor.createRoute(noteType = "journal")) }
                )
            }

            composable(Screen.Planner.route) {
                PlannerScreen(
                    onTaskClick = { id -> navController.navigate(Screen.Editor.createRoute(id, "task")) },
                    onNewTask = { navController.navigate(Screen.Editor.createRoute(noteType = "task")) }
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
                // Shared text belongs to the editor that was opened for it, and only to that one.
                val initialContent = remember {
                    if (noteId == -1L) pendingSharedText.value.also { pendingSharedText.value = null } else null
                }
                NoteEditorScreen(
                    noteId = if (noteId == -1L) null else noteId,
                    noteType = noteType,
                    initialContent = initialContent,
                    onBack = { navController.popBackStack() }
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
                    navController.navigate(Screen.Editor.createRoute(noteType = type))
                },
                createLabel = when (currentRoute) {
                    Screen.Journal.route -> "New journal entry"
                    Screen.Planner.route -> "New task"
                    else -> "New note"
                }
            )
        }
    }
}
