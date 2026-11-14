package com.quillnotes.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Journal : Screen("journal")
    data object Planner : Screen("planner")
    data object Settings : Screen("settings")
    data object Sync : Screen("sync")
    data object Trash : Screen("trash")

    data object Search : Screen("search")
    data object Onboarding : Screen("onboarding")

    data object Editor : Screen("editor?noteId={noteId}&noteType={noteType}&voice={voice}") {
        /** [voice] starts dictation as soon as the editor opens (the "voice note" shortcut). */
        fun createRoute(noteId: Long? = null, noteType: String = "note", voice: Boolean = false): String {
            return "editor?noteId=${noteId ?: -1L}&noteType=$noteType&voice=$voice"
        }
    }
}
