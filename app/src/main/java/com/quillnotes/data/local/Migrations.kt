package com.quillnotes.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/** v1 → v2: adds the `color` accent-label column used by note color labels. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN color TEXT DEFAULT NULL")
    }
}

/**
 * v2 → v3: stable per-note UUIDs and delete tombstones for two-way sync.
 *
 * Every existing note gets a fresh UUID and is marked un-synced with no cloud file,
 * so the first v2 sync uploads it under its new `note_<uuid>.qnote` name.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE notes ADD COLUMN syncedUpdatedAt INTEGER NOT NULL DEFAULT 0")

        val ids = mutableListOf<Long>()
        db.query("SELECT id FROM notes").use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        for (id in ids) {
            db.execSQL("UPDATE notes SET uuid = ? WHERE id = ?", arrayOf<Any>(UUID.randomUUID().toString(), id))
        }
        db.execSQL("UPDATE notes SET isSynced = 0, cloudFileId = NULL")

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notes_uuid ON notes (uuid)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS tombstones (" +
                "uuid TEXT NOT NULL, cloudFileId TEXT, deletedAt INTEGER NOT NULL, " +
                "isSynced INTEGER NOT NULL, PRIMARY KEY(uuid))"
        )
    }
}
