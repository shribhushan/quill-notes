package com.quillnotes.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quillnotes.ui.components.QuillBottomBar
import com.quillnotes.ui.navigation.Screen
import com.quillnotes.ui.screens.editor.NoteEditorScreen
import com.quillnotes.ui.screens.home.HomeScreen
import com.quillnotes.ui.screens.journal.JournalScreen
import com.quillnotes.ui.screens.planner.PlannerScreen
import com.quillnotes.ui.screens.settings.SettingsScreen
import com.quillnotes.ui.screens.sync.SyncScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuillNotesMainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Journal.route,
        Screen.Planner.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                QuillBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(300)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(300)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(300)
                )
            }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNoteClick = { noteId ->
                        navController.navigate(Screen.Editor.createRoute(noteId))
                    },
                    onNewNote = {
                        navController.navigate(Screen.Editor.createRoute())
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Journal.route) {
                JournalScreen(
                    onEntryClick = { noteId ->
                        navController.navigate(Screen.Editor.createRoute(noteId, "journal"))
                    },
                    onNewEntry = {
                        navController.navigate(Screen.Editor.createRoute(noteType = "journal"))
                    }
                )
            }

            composable(Screen.Planner.route) {
                PlannerScreen(
                    onTaskClick = { noteId ->
                        navController.navigate(Screen.Editor.createRoute(noteId, "task"))
                    },
                    onNewTask = {
                        navController.navigate(Screen.Editor.createRoute(noteType = "task"))
                    }
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
                )
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getLong("noteId") ?: -1L
                val noteType = backStackEntry.arguments?.getString("noteType") ?: "note"
                NoteEditorScreen(
                    noteId = if (noteId == -1L) null else noteId,
                    noteType = noteType,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSyncClick = { navController.navigate(Screen.Sync.route) }
                )
            }

            composable(Screen.Sync.route) {
                SyncScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
