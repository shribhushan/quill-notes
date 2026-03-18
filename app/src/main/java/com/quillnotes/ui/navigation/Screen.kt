package com.quillnotes.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Journal : Screen("journal")
    data object Planner : Screen("planner")
    data object Settings : Screen("settings")
    data object Sync : Screen("sync")

    data object Editor : Screen("editor?noteId={noteId}&noteType={noteType}") {
        fun createRoute(noteId: Long? = null, noteType: String = "note"): String {
            return "editor?noteId=${noteId ?: -1L}&noteType=$noteType"
        }
    }
}
