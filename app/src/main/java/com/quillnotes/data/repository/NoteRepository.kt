package com.quillnotes.data.repository

import com.quillnotes.data.encryption.NoteCipher
import com.quillnotes.data.local.dao.AttachmentDao
import com.quillnotes.data.local.dao.NoteDao
import com.quillnotes.data.local.entity.AttachmentEntity
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.local.entity.TombstoneEntity
import com.quillnotes.data.sync.AttachmentRef
import com.quillnotes.data.sync.NotePayload
import com.quillnotes.data.sync.SyncMerge
import com.quillnotes.data.sync.SyncNote
import com.quillnotes.data.sync.SyncStore
import com.quillnotes.data.sync.noteType
import com.quillnotes.data.sync.toRef
import com.quillnotes.domain.RepeatRule
import com.quillnotes.text.Checklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository providing encrypted note operations.
 * All data passing through this layer is encrypted/decrypted transparently.
 */
@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val attachmentDao: AttachmentDao,
    private val encryption: NoteCipher,
    private val files: AttachmentFiles
) : SyncStore {
    // ── Read Operations ────────────────────────────────────────

    fun getAllNotes(): Flow<List<NoteEntity>> =
        noteDao.getAllNotes().map { notes -> notes.map { decryptNote(it) } }.flowOn(Dispatchers.Default)

    fun getQuickNotes(): Flow<List<NoteEntity>> =
        noteDao.getQuickNotes().map { notes -> notes.map { decryptNote(it) } }.flowOn(Dispatchers.Default)

    fun getJournalEntries(): Flow<List<NoteEntity>> =
        noteDao.getJournalEntries().map { notes -> notes.map { decryptNote(it) } }.flowOn(Dispatchers.Default)

    fun getTasks(): Flow<List<NoteEntity>> =
        noteDao.getTasks().map { notes -> notes.map { decryptNote(it) } }.flowOn(Dispatchers.Default)

    fun getTemplates(): Flow<List<NoteEntity>> =
        noteDao.getTemplates().map { notes -> notes.map { decryptNote(it) } }.flowOn(Dispatchers.Default)

    override suspend fun getNoteById(id: Long): NoteEntity? =
        noteDao.getNoteById(id)?.let { decryptNote(it) }

    /**
     * Note text is encrypted with a randomised AEAD, so it can't be matched in SQL —
     * search the decrypted notes in memory instead.
     */
    fun searchNotes(query: String): Flow<List<NoteEntity>> =
        getAllNotes().map { notes ->
            val q = query.trim()
            if (q.isEmpty()) notes
            else notes.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.content.contains(q, ignoreCase = true) ||
                    it.tags.contains(q, ignoreCase = true)
            }
        }

    fun getCountByType(type: NoteType): Flow<Int> = noteDao.getCountByType(type)

    // ── Write Operations ───────────────────────────────────────

    suspend fun createNote(note: NoteEntity): Long {
        // Ciphertext is bound to the note's id, which only exists once the row is inserted:
        // reserve the row first, then write the content encrypted under its real id.
        val id = noteDao.insertNote(note.copy(id = 0, title = "", content = "", tags = "", mood = null, notebook = null))
        noteDao.updateNote(encryptNote(note.copy(id = id)))
        return id
    }

    suspend fun updateNote(note: NoteEntity) {
        // Callers hold a cached copy of the row; sync may have moved its bookkeeping on
        // since, so keep the stored sync state rather than writing the stale one back.
        val stored = noteDao.getNoteById(note.id)
        val encrypted = encryptNote(
            note.copy(
                uuid = stored?.uuid ?: note.uuid,
                cloudFileId = stored?.cloudFileId ?: note.cloudFileId,
                syncedUpdatedAt = stored?.syncedUpdatedAt ?: note.syncedUpdatedAt,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        noteDao.updateNote(encrypted)
    }

    suspend fun deleteNote(id: Long) {
        noteDao.softDeleteNote(id)
    }

    /**
     * Ticks or unticks a task. Completing a recurring task does not finish it: it starts its next
     * round (due date moved on, sub-items unticked). Returns the task as stored afterwards so
     * the caller can re-arm its reminder.
     */
    suspend fun setTaskCompleted(id: Long, completed: Boolean, now: Long = System.currentTimeMillis()): NoteEntity? {
        val raw = noteDao.getNoteById(id) ?: return null
        val rule = RepeatRule.parse(raw.repeatRule)
        val due = raw.dueDate
        if (completed && rule != null && due != null) {
            noteDao.advanceRecurring(id, rule.nextAfter(due, now), now)
            val advanced = noteDao.getNoteById(id)?.let { decryptNote(it) }
            if (advanced != null && Checklist.resetAll(advanced.content) != advanced.content) {
                noteDao.updateNote(encryptNote(advanced.copy(content = Checklist.resetAll(advanced.content))))
            }
        } else {
            noteDao.toggleTaskComplete(id, completed, now)
        }
        return getNoteById(id)
    }

    suspend fun toggleTaskComplete(id: Long, completed: Boolean) {
        noteDao.toggleTaskComplete(id, completed)
    }

    suspend fun togglePin(id: Long, pinned: Boolean) {
        noteDao.togglePin(id, pinned)
    }

    suspend fun setArchived(ids: Collection<Long>, archived: Boolean) {
        ids.forEach { noteDao.setArchived(it, archived) }
    }

    suspend fun setLocked(id: Long, locked: Boolean) {
        noteDao.setLocked(id, locked)
    }

    suspend fun setTemplate(id: Long, template: Boolean) {
        noteDao.setTemplate(id, template)
    }

    /**
     * Ticks a checklist line in a note's text (from a card or widget) and saves it. Returns the
     * new content, or null if the note is gone or the line is not a checklist item.
     */
    suspend fun toggleChecklistItem(id: Long, lineIndex: Int): String? {
        val note = getNoteById(id) ?: return null
        val updated = Checklist.toggle(note.content, lineIndex)
        if (updated == note.content) return null
        updateNote(note.copy(content = updated))
        return updated
    }

    /** New note from a template (same text, tags, colour, type and due time; not itself a template). */
    suspend fun createFromTemplate(template: NoteEntity, now: Long = System.currentTimeMillis()): Long =
        createNote(
            NoteEntity(
                title = template.title, content = template.content, type = template.type,
                tags = template.tags, color = template.color, notebook = template.notebook,
                priority = template.priority, repeatRule = template.repeatRule, mood = null,
                createdAt = now, updatedAt = now
            )
        )

    // ── Notebooks ──────────────────────────────────────────────

    /** Moves notes into [name] (case preserved), or out of any notebook when null/blank. */
    suspend fun moveToNotebook(ids: Collection<Long>, name: String?) {
        val target = name?.trim()?.takeIf { it.isNotEmpty() }
        val now = System.currentTimeMillis()
        for (id in ids) {
            val raw = noteDao.getNoteById(id) ?: continue
            noteDao.updateNote(
                raw.copy(
                    notebook = target?.let { encryption.encrypt(it, contextOf(id)) },
                    isSynced = false,
                    updatedAt = now
                )
            )
        }
    }

    /** Renames a notebook across all notes. Merging into an existing name is allowed. */
    suspend fun renameNotebook(from: String, to: String) {
        val target = to.trim().takeIf { it.isNotEmpty() } ?: return
        val now = System.currentTimeMillis()
        for (raw in noteDao.getNotesWithNotebook()) {
            val current = raw.notebook?.let { decryptField(it, contextOf(raw.id)) } ?: continue
            if (!current.equals(from, ignoreCase = true)) continue
            noteDao.updateNote(raw.copy(notebook = encryption.encrypt(target, contextOf(raw.id)), isSynced = false, updatedAt = now))
        }
    }

    /** Removes a notebook; its notes stay, just unfiled. */
    suspend fun deleteNotebook(name: String) {
        val now = System.currentTimeMillis()
        for (raw in noteDao.getNotesWithNotebook()) {
            val current = raw.notebook?.let { decryptField(it, contextOf(raw.id)) } ?: continue
            if (current.equals(name, ignoreCase = true)) {
                noteDao.updateNote(raw.copy(notebook = null, isSynced = false, updatedAt = now))
            }
        }
    }

    // ── Attachments ────────────────────────────────────────────

    fun observeAttachments(noteUuid: String): Flow<List<AttachmentEntity>> = attachmentDao.observeForNote(noteUuid)

    /** Attachments of every note, by note uuid (cards show the first as a cover). */
    fun observeAllAttachments(): Flow<Map<String, List<AttachmentEntity>>> =
        attachmentDao.observeAll().map { all -> all.groupBy { it.noteUuid } }

    suspend fun addAttachment(
        noteUuid: String,
        bytes: ByteArray,
        mimeType: String,
        width: Int,
        height: Int
    ): AttachmentEntity {
        val position = attachmentDao.getForNote(noteUuid).size
        val attachment = AttachmentEntity(
            noteUuid = noteUuid, mimeType = mimeType, width = width, height = height,
            sizeBytes = bytes.size.toLong(), position = position
        )
        withContext(Dispatchers.IO) { files.write(attachment.uuid, bytes) }
        attachmentDao.insert(attachment)
        noteDao.touchByUuid(noteUuid)
        return attachment
    }

    suspend fun removeAttachment(noteUuid: String, uuid: String) {
        attachmentDao.delete(noteUuid, uuid)
        if (attachmentDao.countByUuid(uuid) == 0) withContext(Dispatchers.IO) { files.delete(uuid) }
        noteDao.touchByUuid(noteUuid)
    }

    suspend fun readAttachment(uuid: String): ByteArray? = withContext(Dispatchers.IO) { files.read(uuid) }

    /** For callers already on a background thread (bulk export). */
    fun readAttachmentBlocking(uuid: String): ByteArray? = files.read(uuid)

    private suspend fun dropAttachmentsOf(noteUuid: String) {
        val owned = attachmentDao.getForNote(noteUuid)
        attachmentDao.deleteForNote(noteUuid)
        owned.map { it.uuid }.distinct().forEach { if (attachmentDao.countByUuid(it) == 0) files.delete(it) }
    }

    // ── Trash Operations ───────────────────────────────────────

    fun getDeletedNotes(): Flow<List<NoteEntity>> =
        noteDao.getDeletedNotes().map { notes -> notes.map { decryptNote(it) } }.flowOn(Dispatchers.Default)

    suspend fun restoreNote(id: Long) {
        noteDao.restoreNote(id)
    }

    suspend fun permanentlyDeleteNote(id: Long) {
        noteDao.getNoteById(id)?.let { leaveTombstone(it) }
        noteDao.permanentlyDeleteNote(id)
    }

    suspend fun purgeOldDeletedNotes() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        noteDao.getDeletedBefore(cutoff).forEach { leaveTombstone(it); noteDao.permanentlyDeleteNote(it.id) }
    }

    suspend fun purgeAllDeletedNotes() {
        noteDao.getDeletedBefore(Long.MAX_VALUE).forEach { leaveTombstone(it); noteDao.permanentlyDeleteNote(it.id) }
    }

    /** Recorded before the row is removed so the deletion reaches the cloud and other phones. */
    private suspend fun leaveTombstone(note: NoteEntity) {
        noteDao.insertTombstone(
            TombstoneEntity(uuid = note.uuid, cloudFileId = note.cloudFileId, deletedAt = System.currentTimeMillis())
        )
        dropAttachmentsOf(note.uuid)
    }

    // ── Task Reminder Operations ───────────────────────────────

    suspend fun getTasksWithDueDateAfter(millis: Long): List<NoteEntity> =
        noteDao.getTasksWithDueDateAfter(millis).map { decryptNote(it) }

    // ── Sync Operations ────────────────────────────────────────

    suspend fun countUnsynced(): Int = noteDao.countUnsynced()

    /** Every note including trashed ones, decrypted, with attachment lists. */
    override suspend fun getAllForSync(): List<SyncNote> {
        val attachments = attachmentDao.getAll().groupBy { it.noteUuid }
        return noteDao.getAllForSync().map { raw ->
            val note = decryptNote(raw)
            SyncNote(note, attachments[note.uuid].orEmpty().sortedBy { it.position }.map { it.toRef() })
        }
    }

    override suspend fun getTombstones(): List<TombstoneEntity> = noteDao.getAllTombstones()

    override suspend fun markTombstoneSynced(uuid: String, cloudFileId: String) =
        noteDao.markTombstoneSynced(uuid, cloudFileId)

    override suspend fun dropTombstone(uuid: String) = noteDao.deleteTombstone(uuid)

    /**
     * Records a successful upload. Ignored if the note was edited after [updatedAt] was
     * read, leaving that newer edit flagged for the next sync.
     */
    override suspend fun markSynced(id: Long, cloudFileId: String, updatedAt: Long, syncedUpdatedAt: Long) {
        noteDao.markAsSynced(id, cloudFileId, updatedAt, syncedUpdatedAt)
    }

    /** Creates a local note from a cloud copy. Returns the new row id. */
    override suspend fun insertFromSync(payload: NotePayload, cloudFileId: String): Long {
        noteDao.getNoteByUuid(payload.uuid)?.let { return it.id }
        val entity = payload.toEntity(cloudFileId)
        // Content is bound to the row id, which exists only after insert (see createNote).
        val id = noteDao.insertNote(entity.copy(title = "", content = "", tags = "", mood = null, notebook = null))
        noteDao.updateNote(encryptNote(entity.copy(id = id)))
        reconcileAttachments(payload.uuid, payload.attachments, uploaded = true)
        return id
    }

    /** Overwrites a local note with a cloud copy, keeping the cloud timestamp. */
    override suspend fun applyFromSync(existing: NoteEntity, payload: NotePayload, cloudFileId: String) {
        noteDao.updateNote(encryptNote(payload.toEntity(cloudFileId).copy(id = existing.id)))
        reconcileAttachments(payload.uuid, payload.attachments, uploaded = true)
    }

    override suspend fun deleteFromSync(uuid: String) {
        dropAttachmentsOf(uuid)
        noteDao.deleteByUuid(uuid)
    }

    /** Keeps the losing side of an edit conflict as its own note, flagged to upload. */
    override suspend fun addConflictCopy(loser: NotePayload) {
        val id = createNote(
            NoteEntity(
                title = SyncMerge.conflictTitle(loser.title),
                content = loser.content,
                type = loser.noteType(),
                createdAt = loser.createdAt,
                isPinned = false,
                isCompleted = loser.isCompleted,
                dueDate = loser.dueDate,
                mood = loser.mood,
                tags = loser.tags,
                color = loser.color,
                priority = loser.priority,
                repeatRule = loser.repeatRule,
                notebook = loser.notebook,
                isLocked = loser.isLocked
            )
        )
        val copy = noteDao.getNoteById(id) ?: return
        // The copy shares the original's image files; upload status is re-checked on the next sync.
        reconcileAttachments(copy.uuid, loser.attachments, uploaded = false)
    }

    // ── Attachments for sync ──

    override suspend fun attachmentsToUpload(): List<AttachmentRef> =
        attachmentDao.pendingUploads().map { it.toRef() }

    override suspend fun readAttachmentBytes(uuid: String): ByteArray? = withContext(Dispatchers.IO) { files.read(uuid) }

    override suspend fun markAttachmentUploaded(uuid: String) = attachmentDao.markUploaded(uuid)

    override suspend fun attachmentsToDownload(): List<AttachmentRef> =
        attachmentDao.missingFiles().map { it.toRef() }

    override suspend fun storeDownloadedAttachment(uuid: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) { files.write(uuid, bytes) }
        attachmentDao.markDownloaded(uuid)
    }

    /** Makes the attachment rows of [noteUuid] match [refs]. */
    private suspend fun reconcileAttachments(noteUuid: String, refs: List<AttachmentRef>, uploaded: Boolean) {
        val existing = attachmentDao.getForNote(noteUuid)
        val wanted = refs.map { it.uuid }.toSet()
        for (gone in existing.filter { it.uuid !in wanted }) {
            attachmentDao.delete(noteUuid, gone.uuid)
            if (attachmentDao.countByUuid(gone.uuid) == 0) files.delete(gone.uuid)
        }
        val have = existing.map { it.uuid }.toSet()
        refs.forEachIndexed { index, ref ->
            if (ref.uuid in have) return@forEachIndexed
            attachmentDao.insert(
                AttachmentEntity(
                    uuid = ref.uuid, noteUuid = noteUuid, mimeType = ref.mime, width = ref.width, height = ref.height,
                    sizeBytes = ref.size, position = index, createdAt = ref.createdAt,
                    hasLocalFile = files.exists(ref.uuid), isUploaded = uploaded
                )
            )
        }
    }

    // ── Import (backup file) ───────────────────────────────────

    /** Adds a note from a backup. It is flagged as changed so the next cloud sync uploads it. */
    suspend fun importNew(payload: NotePayload): Long {
        noteDao.getNoteByUuid(payload.uuid)?.let { return it.id } // never replace an existing note
        val entity = payload.toEntity(cloudFileId = "").copy(isSynced = false, syncedUpdatedAt = 0, cloudFileId = null)
        val id = noteDao.insertNote(entity.copy(title = "", content = "", tags = "", mood = null, notebook = null))
        noteDao.updateNote(encryptNote(entity.copy(id = id)))
        reconcileAttachments(payload.uuid, payload.attachments, uploaded = false)
        return id
    }

    /** Replaces a local note with a newer backed-up version. */
    suspend fun importOver(existing: NoteEntity, payload: NotePayload) {
        val stored = noteDao.getNoteById(existing.id)
        val entity = payload.toEntity(cloudFileId = "").copy(
            id = existing.id, isSynced = false,
            cloudFileId = stored?.cloudFileId, syncedUpdatedAt = stored?.syncedUpdatedAt ?: 0
        )
        noteDao.updateNote(encryptNote(entity))
        reconcileAttachments(payload.uuid, payload.attachments, uploaded = false)
    }

    /** Stores an attachment's bytes from a backup and marks it present. */
    suspend fun importAttachmentFile(uuid: String, bytes: ByteArray) {
        if (attachmentDao.countByUuid(uuid) == 0) return // belongs to a note that was not imported
        withContext(Dispatchers.IO) { files.write(uuid, bytes) }
        attachmentDao.markFilePresent(uuid)
    }

    suspend fun getNoteByUuid(uuid: String): NoteEntity? = noteDao.getNoteByUuid(uuid)?.let { decryptNote(it) }

    private fun NotePayload.toEntity(cloudFileId: String) = NoteEntity(
        uuid = uuid, title = title, content = content, type = noteType(),
        createdAt = createdAt, updatedAt = updatedAt, isPinned = isPinned, isCompleted = isCompleted,
        dueDate = dueDate, mood = mood, tags = tags, color = color,
        isSynced = true, syncedUpdatedAt = updatedAt, cloudFileId = cloudFileId, isDeleted = isTrashed,
        priority = priority, repeatRule = repeatRule, notebook = notebook, isArchived = isArchived,
        isTemplate = isTemplate, isLocked = isLocked
    )

    // ── Encryption Helpers ─────────────────────────────────────

    private fun contextOf(id: Long) = "note_$id"

    private fun encryptNote(note: NoteEntity): NoteEntity {
        val context = contextOf(note.id)
        return note.copy(
            title = encryption.encrypt(note.title, context),
            content = encryption.encrypt(note.content, context),
            tags = encryption.encrypt(note.tags, context),
            mood = note.mood?.let { encryption.encrypt(it, context) },
            notebook = note.notebook?.let { encryption.encrypt(it, context) }
        )
    }

    private fun decryptNote(note: NoteEntity): NoteEntity {
        val context = contextOf(note.id)
        return note.copy(
            title = decryptField(note.title, context),
            content = decryptField(note.content, context),
            tags = decryptField(note.tags, context),
            mood = note.mood?.let { decryptField(it, context) },
            notebook = note.notebook?.let { decryptField(it, context) }
        )
    }

    /**
     * Earlier builds encrypted brand-new notes before they had an id (context "note_0"),
     * so fall back to that context when the note's own one doesn't authenticate.
     * [NoteCipher.decrypt] returns its input unchanged on failure.
     */
    private fun decryptField(value: String, context: String): String {
        val plain = encryption.decrypt(value, context)
        return if (plain == value && value.isNotEmpty()) encryption.decrypt(value, LEGACY_NEW_NOTE_CONTEXT) else plain
    }

    private companion object {
        const val LEGACY_NEW_NOTE_CONTEXT = "note_0"
    }
}
