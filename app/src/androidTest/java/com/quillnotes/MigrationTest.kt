package com.quillnotes

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quillnotes.data.local.MIGRATION_1_2
import com.quillnotes.data.local.MIGRATION_2_3
import com.quillnotes.data.local.MIGRATION_3_4
import com.quillnotes.data.local.QuillDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens databases written by earlier versions with the real [QuillDatabase] and every migration, so Room's
 * own schema validation runs exactly as it will on a user's phone after updating. Uses throwaway database
 * files; the app's real database is never touched.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val created = mutableListOf<String>()

    @After
    fun cleanUp() {
        created.forEach { context.deleteDatabase(it) }
    }

    private fun createOldDatabase(name: String, version: Int, statements: List<String>) {
        created += name
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            statements.forEach { db.execSQL(it) }
            db.version = version
        } finally {
            db.close()
        }
    }

    /** Opening runs the migrations and Room's schema validation; either failing throws here. */
    private fun migrate(name: String): QuillDatabase {
        val db = Room.databaseBuilder(context, QuillDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        db.openHelper.writableDatabase
        return db
    }

    private fun rows(db: QuillDatabase, sql: String): List<List<String?>> {
        val out = mutableListOf<List<String?>>()
        db.openHelper.readableDatabase.query(sql).use { c ->
            while (c.moveToNext()) {
                out += (0 until c.columnCount).map { i -> if (c.isNull(i)) null else c.getString(i) }
            }
        }
        return out
    }

    @Test
    fun upgradesAVersion2DatabaseKeepingEveryNote() {
        createOldDatabase("migrate_v2.db", 2, V2_SCHEMA + V2_SEED)
        val db = migrate("migrate_v2.db")
        try {
            assertEquals(4, db.openHelper.readableDatabase.version)
            val notes = rows(
                db,
                "SELECT title, content, type, isPinned, isDeleted, uuid, isSynced, priority, isArchived, " +
                    "isTemplate, isLocked, notebook, repeatRule, color, cloudFileId FROM notes ORDER BY id"
            )
            assertEquals(3, notes.size)

            // Stored (encrypted) text and flags are untouched.
            assertEquals(listOf("Title one", "cipher-1", "NOTE", "1", "0"), notes[0].take(5))
            assertEquals(listOf("Task", "cipher-2", "TASK", "0", "0"), notes[1].take(5))
            assertEquals(listOf("Journal", "cipher-3", "JOURNAL", "0", "1"), notes[2].take(5))
            assertEquals("yellow", notes[0][13])

            // Every note got its own stable id and will be uploaded once under the new name.
            val uuids = notes.map { it[5] }
            assertTrue("uuids must be filled in", uuids.all { !it.isNullOrBlank() })
            assertEquals("uuids must be unique", 3, uuids.toSet().size)
            assertTrue("all notes are marked for upload", notes.all { it[6] == "0" })
            assertTrue("old cloud file ids are dropped", notes.all { it[14] == null })

            // New v4 columns start switched off.
            assertTrue(notes.all { it[7] == "0" && it[8] == "0" && it[9] == "0" && it[10] == "0" && it[11] == null && it[12] == null })

            assertEquals(0, rows(db, "SELECT * FROM attachments").size)
            assertEquals(0, rows(db, "SELECT * FROM tombstones").size)
        } finally {
            db.close()
        }
    }

    @Test
    fun upgradesAVersion1DatabaseThroughEveryStep() {
        createOldDatabase("migrate_v1.db", 1, V1_SCHEMA + V1_SEED)
        val db = migrate("migrate_v1.db")
        try {
            assertEquals(4, db.openHelper.readableDatabase.version)
            val notes = rows(db, "SELECT title, uuid, color FROM notes")
            assertEquals(1, notes.size)
            assertEquals("Old note", notes[0][0])
            assertTrue(!notes[0][1].isNullOrBlank())
            assertEquals(null, notes[0][2])
        } finally {
            db.close()
        }
    }

    @Test
    fun upgradesAVersion3DatabaseAndKeepsItsUuidsAndTombstones() {
        createOldDatabase("migrate_v3.db", 3, V2_SCHEMA + V2_SEED + V3_STEP)
        val db = migrate("migrate_v3.db")
        try {
            assertEquals(4, db.openHelper.readableDatabase.version)
            val notes = rows(db, "SELECT uuid, syncedUpdatedAt, isSynced FROM notes ORDER BY id")
            assertEquals(listOf("uuid-1", "uuid-2", "uuid-3"), notes.map { it[0] })
            assertEquals("a synced note keeps its sync state", "1", notes[0][2])
            val tombstones = rows(db, "SELECT uuid, cloudFileId, isSynced FROM tombstones")
            assertEquals(listOf(listOf("gone-1", "cloud-9", "0")), tombstones)
        } finally {
            db.close()
        }
    }

    private companion object {
        /** The table Room created for database version 1 (v1.0 to v1.2): no colour column. */
        val V1_SCHEMA = listOf(
            "CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, " +
                "type TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, isPinned INTEGER NOT NULL, " +
                "isCompleted INTEGER NOT NULL, dueDate INTEGER, mood TEXT, tags TEXT NOT NULL, " +
                "isSynced INTEGER NOT NULL, cloudFileId TEXT, isDeleted INTEGER NOT NULL)"
        )
        val V1_SEED = listOf(
            "INSERT INTO notes (title, content, type, createdAt, updatedAt, isPinned, isCompleted, tags, isSynced, isDeleted) " +
                "VALUES ('Old note', 'cipher-0', 'NOTE', 1, 2, 0, 0, '', 1, 0)"
        )

        /** Version 2 (v1.3): adds the colour column. */
        val V2_SCHEMA = listOf(
            "CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, " +
                "type TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, isPinned INTEGER NOT NULL, " +
                "isCompleted INTEGER NOT NULL, dueDate INTEGER, mood TEXT, tags TEXT NOT NULL, color TEXT DEFAULT NULL, " +
                "isSynced INTEGER NOT NULL, cloudFileId TEXT, isDeleted INTEGER NOT NULL)"
        )
        val V2_SEED = listOf(
            "INSERT INTO notes (title, content, type, createdAt, updatedAt, isPinned, isCompleted, dueDate, mood, tags, color, isSynced, cloudFileId, isDeleted) " +
                "VALUES ('Title one', 'cipher-1', 'NOTE', 1000, 2000, 1, 0, NULL, NULL, 'tag', 'yellow', 1, 'cloud-123', 0)",
            "INSERT INTO notes (title, content, type, createdAt, updatedAt, isPinned, isCompleted, dueDate, mood, tags, color, isSynced, cloudFileId, isDeleted) " +
                "VALUES ('Task', 'cipher-2', 'TASK', 3000, 4000, 0, 0, 5000, NULL, '', NULL, 0, NULL, 0)",
            "INSERT INTO notes (title, content, type, createdAt, updatedAt, isPinned, isCompleted, dueDate, mood, tags, color, isSynced, cloudFileId, isDeleted) " +
                "VALUES ('Journal', 'cipher-3', 'JOURNAL', 5000, 6000, 0, 0, NULL, 'happy', '', NULL, 1, NULL, 1)"
        )

        /** What the version 3 build did on top of version 2 (stable ids, tombstones), with ids and one tombstone filled in. */
        val V3_STEP = listOf(
            "ALTER TABLE notes ADD COLUMN uuid TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE notes ADD COLUMN syncedUpdatedAt INTEGER NOT NULL DEFAULT 0",
            "UPDATE notes SET uuid = 'uuid-' || id",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_notes_uuid ON notes (uuid)",
            "CREATE TABLE IF NOT EXISTS tombstones (uuid TEXT NOT NULL, cloudFileId TEXT, deletedAt INTEGER NOT NULL, " +
                "isSynced INTEGER NOT NULL, PRIMARY KEY(uuid))",
            "INSERT INTO tombstones (uuid, cloudFileId, deletedAt, isSynced) VALUES ('gone-1', 'cloud-9', 7000, 0)"
        )
    }
}
