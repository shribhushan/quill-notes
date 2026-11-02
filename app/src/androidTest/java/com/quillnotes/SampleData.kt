package com.quillnotes

import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.ui.util.shiftDays
import com.quillnotes.ui.util.startOfDay

/** Realistic fixture content for screenshot tests, relative to [now]. */
class SampleData(val now: Long = System.currentTimeMillis()) {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val today = startOfDay(now)
    private fun daysAgo(days: Int, atHour: Int = 9) = shiftDays(today, -days) + atHour * hour
    private fun inDays(days: Int, atHour: Int = 9) = shiftDays(today, days) + atHour * hour

    val notes = listOf(
        NoteEntity(
            id = 1, type = NoteType.NOTE, isPinned = true, color = "yellow",
            title = "Chapter 12 — the reel",
            content = "She finds the old film reel in his bag. Keep the scene quiet: rain on the tin roof, the projector humming, neither of them saying what they both know.",
            tags = "writing, draft", updatedAt = now - 25 * minute
        ),
        NoteEntity(
            id = 2, type = NoteType.NOTE, isPinned = true,
            title = "Q3 roadmap",
            content = "1. Migrate billing service\n2. Contract tests for the web API\n3. Pilot AI coding assistants with two teams",
            tags = "work", updatedAt = now - 3 * hour
        ),
        NoteEntity(
            id = 3, type = NoteType.NOTE, color = "blue",
            title = "Architecture review notes",
            content = "Event sourcing for the ledger looks promising, but we need a clear story for replays and schema evolution before committing.",
            tags = "work, architecture", updatedAt = now - 26 * hour
        ),
        NoteEntity(
            id = 4, type = NoteType.NOTE, color = "green",
            title = "Weekend in the hills",
            content = "Fort walk at sunrise, breakfast at the old café, the palace museum after lunch. Pack the good camera.",
            tags = "travel", updatedAt = now - 2 * 24 * hour
        ),
        NoteEntity(
            id = 5, type = NoteType.NOTE,
            title = "",
            content = "“What we observe is not nature itself, but nature exposed to our method of questioning.” — Heisenberg",
            updatedAt = now - 3 * 24 * hour
        ),
        NoteEntity(
            id = 6, type = NoteType.NOTE, color = "purple",
            title = "Story idea: the long watch",
            content = "A station keeper notices the supply logs don't add up. Keep the real antagonist offstage until act three.",
            tags = "writing, ideas", updatedAt = now - 5 * 24 * hour
        ),
        NoteEntity(
            id = 7, type = NoteType.NOTE, color = "pink",
            title = "Gift ideas",
            content = "Fountain pen, a film camera, that cookbook from the corner bookstore.",
            tags = "personal", updatedAt = now - 9 * 24 * hour
        )
    )

    val journal = listOf(
        NoteEntity(
            id = 20, type = NoteType.JOURNAL, mood = "happy", color = null,
            title = "Shipped the migration",
            content = "The cut-over went cleanly. The team stayed calm through the rollback drill and nobody was paged overnight.",
            createdAt = today + 8 * hour + 40 * minute, updatedAt = today + 9 * hour
        ),
        NoteEntity(
            id = 21, type = NoteType.JOURNAL, mood = "calm",
            title = "Evening walk",
            content = "Long walk by the river. Worked out the ending for chapter 12 without even trying.",
            createdAt = daysAgo(1, 19), updatedAt = daysAgo(1, 19)
        ),
        NoteEntity(
            id = 22, type = NoteType.JOURNAL, mood = "anxious",
            title = "Big presentation tomorrow",
            content = "Slides are done. Still nervous about the Q&A, so I rehearsed it twice.",
            createdAt = daysAgo(1, 8), updatedAt = daysAgo(1, 8)
        ),
        NoteEntity(
            id = 23, type = NoteType.JOURNAL, mood = "neutral",
            title = "Slow Sunday",
            content = "Read, napped, made too much chai.",
            createdAt = daysAgo(2, 17), updatedAt = daysAgo(2, 17)
        ),
        NoteEntity(
            id = 24, type = NoteType.JOURNAL, mood = "happy",
            title = "Writing streak",
            content = "Two thousand words before breakfast.",
            createdAt = daysAgo(4, 7), updatedAt = daysAgo(4, 7)
        )
    )

    val tasks = listOf(
        NoteEntity(id = 40, type = NoteType.TASK, content = "", title = "Send Q3 roadmap", dueDate = daysAgo(1, 18), tags = "work", updatedAt = now - 2 * hour),
        NoteEntity(id = 41, type = NoteType.TASK, title = "Prep for 1:1", content = "Goals, on-call load, feedback", dueDate = today + 15 * hour, tags = "work", color = "blue", updatedAt = now - 4 * hour),
        NoteEntity(id = 42, type = NoteType.TASK, content = "", title = "Book weekend hotel", dueDate = today + 18 * hour, color = "green", updatedAt = now - 5 * hour),
        NoteEntity(id = 43, type = NoteType.TASK, content = "", title = "Revise chapter 12 opening", dueDate = inDays(2, 9), tags = "writing", color = "yellow", updatedAt = now - 6 * hour),
        NoteEntity(id = 44, type = NoteType.TASK, content = "", title = "Renew passport", dueDate = inDays(9, 9), updatedAt = now - 30 * hour),
        NoteEntity(id = 45, type = NoteType.TASK, content = "", title = "Try the new pour-over recipe", updatedAt = now - 50 * hour),
        NoteEntity(id = 46, type = NoteType.TASK, content = "", title = "Review rollout plan", isCompleted = true, dueDate = today + 10 * hour, tags = "work", updatedAt = now - hour),
        NoteEntity(id = 47, type = NoteType.TASK, content = "", title = "Pay electricity bill", isCompleted = true, updatedAt = now - 20 * hour)
    )

    val trash = listOf(
        NoteEntity(id = 60, type = NoteType.NOTE, title = "Old grocery list", content = "", isDeleted = true, updatedAt = now - 2 * 24 * hour),
        NoteEntity(id = 61, type = NoteType.TASK, title = "Call the plumber", content = "", isDeleted = true, updatedAt = now - 12 * 24 * hour),
        NoteEntity(id = 62, type = NoteType.JOURNAL, title = "Rainy Tuesday", content = "", isDeleted = true, updatedAt = now - 28 * 24 * hour)
    )
}
