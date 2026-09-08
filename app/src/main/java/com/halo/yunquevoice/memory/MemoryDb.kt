package com.halo.yunquevoice.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class SpeakerProfile(
    val id: String,
    var name: String,
    val feature: String,
    val createdAt: Long,
    var updatedAt: Long,
    var sampleCount: Int,
    var description: String = "",
    var cloudSpeakerId: String? = null,
    var canonical: Boolean = true
)

data class RelationshipRecord(
    val id: Long,
    val sourceId: String,
    val sourceName: String,
    val targetId: String,
    val targetName: String,
    val relation: String,
    val evidence: String,
    val ts: Long
)

data class ConversationRecord(
    val id: Long,
    val ts: Long,
    val speakerId: String?,
    val speakerName: String?,
    val text: String,
    val origin: String, // "user" / "other" / "assistant"
    val missedText: String? = null
)

data class MemoryEntry(
    val id: Long,
    val ts: Long,
    var content: String,
    var pinned: Boolean
)

data class AboutMeEntry(
    val id: Long,
    val ts: Long,
    var content: String,
    var source: String, // "ai" / "manual"
    val speakerId: String? = null
)

data class RoleCard(
    val id: Long,
    var name: String,
    var description: String,
    var personality: String,
    var scenario: String,
    var firstMessage: String,
    var exampleDialogue: String,
    var systemPrompt: String,
    var postHistoryInstructions: String,
    var alternateGreetings: String,
    var creatorNotes: String,
    var active: Boolean,
    var source: String
)

data class WorldBook(
    val id: Long,
    var name: String,
    val roleCardId: Long?
)

data class WorldEntry(
    val id: Long,
    val bookId: Long,
    var keys: String,
    var content: String,
    var comment: String,
    var constant: Boolean,
    var selective: Boolean,
    var priority: Int,
    var position: String
)

class MemoryDb(context: Context) : SQLiteOpenHelper(context, "yunque_memory.db", null, 10) {

    private var defaultsChecked = false

    init {
        runCatching {
            writableDatabase // 触发创建/升级
            if (!defaultsChecked) {
                ensureDefaultRoleCard()
                updateDefaultRoleCardWording()
                defaultsChecked = true
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE speakers (" +
                "id TEXT PRIMARY KEY, name TEXT, feature TEXT, " +
                "created_at INTEGER, updated_at INTEGER, sample_count INTEGER, description TEXT DEFAULT '', " +
                "cloud_speaker_id TEXT, canonical INTEGER DEFAULT 1)"
        )
        db.execSQL(
            "CREATE TABLE conversations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, " +
                "speaker_id TEXT, speaker_name TEXT, text TEXT, origin TEXT, missed_text TEXT)"
        )
        db.execSQL("CREATE INDEX idx_conv_ts ON conversations(ts)")
        db.execSQL("CREATE INDEX idx_conv_text ON conversations(text)")
        db.execSQL(
            "CREATE TABLE memories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, content TEXT, pinned INTEGER DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX idx_mem_text ON memories(content)")
        createRelationships(db)
        createGraphPositions(db)
        createAboutMe(db)
        createRoleCards(db)
        createSessionState(db)
        createDailyStats(db)
        createProfileDoc(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching { db.execSQL("ALTER TABLE speakers ADD COLUMN description TEXT DEFAULT ''") }
            createRelationships(db)
        }
        if (oldVersion < 3) {
            createGraphPositions(db)
        }
        if (oldVersion < 4) {
            runCatching { db.execSQL("ALTER TABLE conversations ADD COLUMN missed_text TEXT") }
        }
        if (oldVersion < 5) {
            createAboutMe(db)
        }
        if (oldVersion < 6) {
            runCatching { db.execSQL("ALTER TABLE about_me ADD COLUMN speaker_id TEXT") }
        }
        if (oldVersion < 7) {
            createRoleCards(db)
        }
        if (oldVersion < 8) {
            runCatching { db.execSQL("ALTER TABLE speakers ADD COLUMN cloud_speaker_id TEXT") }
            runCatching { db.execSQL("ALTER TABLE speakers ADD COLUMN canonical INTEGER DEFAULT 1") }
        }
        if (oldVersion < 9) {
            createSessionState(db)
            createDailyStats(db)
        }
        if (oldVersion < 10) {
            createProfileDoc(db)
        }
    }

    private fun createSessionState(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS session_state (" +
                "id INTEGER PRIMARY KEY CHECK (id = 1), " +
                "summary TEXT DEFAULT '', summarized_until INTEGER DEFAULT 0, " +
                "last_compaction_day TEXT DEFAULT '', updated_at INTEGER DEFAULT 0)"
        )
    }

    private fun createDailyStats(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS daily_stats (" +
                "date TEXT PRIMARY KEY, stats TEXT, updated_at INTEGER)"
        )
    }

    private fun createProfileDoc(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS profile_doc (" +
                "id INTEGER PRIMARY KEY CHECK (id = 1), " +
                "content TEXT DEFAULT '', budget INTEGER DEFAULT 800, updated_at INTEGER DEFAULT 0)"
        )
    }

    /* ─────────── 用户画像文档（关于我） ─────────── */

    data class ProfileDoc(val content: String, val budget: Int)

    fun loadProfileDoc(): ProfileDoc {
        readableDatabase.query("profile_doc", null, "id = 1", null, null, null, null).use { c ->
            if (c.moveToFirst()) {
                return ProfileDoc(content = c.getString(1) ?: "", budget = c.getInt(2))
            }
        }
        return ProfileDoc("", 800)
    }

    fun saveProfileDoc(content: String, budget: Int) {
        val values = ContentValues().apply {
            put("id", 1)
            put("content", content)
            put("budget", budget)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("profile_doc", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** 导出前调用：把 WAL 合回主文件，保证复制的 DB 完整。 */
    fun walCheckpoint() {
        writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
    }

    /* ─────────── 工作记忆（会话状态） ─────────── */

    data class SessionState(
        val summary: String,
        val summarizedUntilTs: Long,
        val lastCompactionDay: String
    )

    fun loadSessionState(): SessionState {
        readableDatabase.query("session_state", null, "id = 1", null, null, null, null).use { c ->
            if (c.moveToFirst()) {
                return SessionState(
                    summary = c.getString(1) ?: "",
                    summarizedUntilTs = c.getLong(2),
                    lastCompactionDay = c.getString(3) ?: ""
                )
            }
        }
        return SessionState("", 0, "")
    }

    fun saveSessionState(state: SessionState) {
        val values = ContentValues().apply {
            put("id", 1)
            put("summary", state.summary)
            put("summarized_until", state.summarizedUntilTs)
            put("last_compaction_day", state.lastCompactionDay)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("session_state", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /* ─────────── 每日运行统计（试用调优用） ─────────── */

    fun statAdd(date: String, key: String, delta: Double) {
        val o = readDayStats(date)
        o.put(key, o.optDouble(key, 0.0) + delta)
        val values = ContentValues().apply {
            put("date", date)
            put("stats", o.toString())
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("daily_stats", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun readDayStats(date: String): JSONObject {
        readableDatabase.query("daily_stats", null, "date = ?", arrayOf(date), null, null, null).use { c ->
            if (c.moveToFirst()) {
                return runCatching { JSONObject(c.getString(1)) }.getOrElse { JSONObject() }
            }
        }
        return JSONObject()
    }

    fun recentDailyStats(days: Int = 14): List<Pair<String, JSONObject>> {
        val result = mutableListOf<Pair<String, JSONObject>>()
        readableDatabase.query("daily_stats", null, null, null, null, null, "date DESC", days.toString()).use { c ->
            while (c.moveToNext()) {
                val stats = runCatching { JSONObject(c.getString(1)) }.getOrElse { JSONObject() }
                result.add(c.getString(0) to stats)
            }
        }
        return result
    }

    private fun createRelationships(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS relationships (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, source_id TEXT, source_name TEXT, " +
                "target_id TEXT, target_name TEXT, relation TEXT, evidence TEXT, ts INTEGER)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_rel_source ON relationships(source_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_rel_target ON relationships(target_id)")
    }

    private fun createGraphPositions(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS graph_positions (" +
                "speaker_id TEXT PRIMARY KEY, x REAL, y REAL)"
        )
    }

    private fun createRoleCards(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS role_cards (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, description TEXT, personality TEXT, " +
                "scenario TEXT, first_message TEXT, example_dialogue TEXT, system_prompt TEXT, " +
                "post_history_instructions TEXT, alternate_greetings TEXT, creator_notes TEXT, " +
                "active INTEGER, source TEXT)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS world_books (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, role_card_id INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS world_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, book_id INTEGER, keys TEXT, content TEXT, " +
                "comment TEXT, constant INTEGER, selective INTEGER, priority INTEGER, position TEXT)"
        )
    }

    private fun ensureDefaultRoleCard() {
        if (listRoleCards().isEmpty()) {
            addRoleCard(
                name = "云雀",
                description = "云雀·私人助理，一个越来越懂你的身边助理。",
                personality = "温柔、敏锐、有分寸；不轻易打扰，但值得开口时一定会开口。",
                scenario = "你是一个全天候陪伴用户的私人助理，既懂人情又可靠。",
                firstMessage = "我在呢。",
                exampleDialogue = "主人：帮我记住我下周要去上海。\n云雀：好，我记住了，下周去上海。",
                systemPrompt = "你是云雀，主人的私人助理。回答简短、口语化、适合直接朗读。",
                postHistoryInstructions = "继续以云雀的身份自然回应，不要脱离角色。",
                active = true,
                source = "builtin"
            )
        }
    }

    private fun createAboutMe(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS about_me (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, content TEXT, source TEXT, speaker_id TEXT)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_about_content ON about_me(content)")
    }

    /* ─────────── speakers ─────────── */

    fun getSpeakers(): List<SpeakerProfile> {
        val result = mutableListOf<SpeakerProfile>()
        readableDatabase.query("speakers", null, null, null, null, null, "updated_at DESC").use { c ->
            while (c.moveToNext()) {
                result.add(
                    SpeakerProfile(
                        id = c.getString(0),
                        name = c.getString(1),
                        feature = c.getString(2),
                        createdAt = c.getLong(3),
                        updatedAt = c.getLong(4),
                        sampleCount = c.getInt(5),
                        description = if (c.columnCount > 6) c.getString(6) ?: "" else "",
                        cloudSpeakerId = if (c.columnCount > 7) c.getString(7) else null,
                        canonical = if (c.columnCount > 8) c.getInt(8) == 1 else true
                    )
                )
            }
        }
        return result
    }

    fun getSpeaker(id: String): SpeakerProfile? =
        getSpeakers().firstOrNull { it.id == id }

    fun upsertSpeaker(speaker: SpeakerProfile) {
        val values = ContentValues().apply {
            put("id", speaker.id)
            put("name", speaker.name)
            put("feature", speaker.feature)
            put("created_at", speaker.createdAt)
            put("updated_at", speaker.updatedAt)
            put("sample_count", speaker.sampleCount)
            put("description", speaker.description)
            put("cloud_speaker_id", speaker.cloudSpeakerId)
            put("canonical", if (speaker.canonical) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("speakers", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun renameSpeaker(id: String, name: String) {
        val values = ContentValues().apply {
            put("name", name)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("speakers", values, "id=?", arrayOf(id))
        // 同步更新历史对话里的显示名，用户改名后搜索/记录应立即可见
        val convValues = ContentValues().apply { put("speaker_name", name) }
        writableDatabase.update("conversations", convValues, "speaker_id=?", arrayOf(id))
        // 同名合并：如果已经存在另一个同名的 canonical 说话人，把当前这条并进去
        val existing = listCanonicalSpeakers().firstOrNull { it.id != id && it.name == name }
        if (existing != null) {
            mergeSpeaker(id, existing.id)
        }
    }

    fun setCanonical(speakerId: String, canonical: Boolean) {
        val values = ContentValues().apply {
            put("canonical", if (canonical) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("speakers", values, "id=?", arrayOf(speakerId))
    }

    fun setCloudSpeakerId(speakerId: String, cloudId: String?) {
        val values = ContentValues().apply {
            put("cloud_speaker_id", cloudId)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("speakers", values, "id=?", arrayOf(speakerId))
    }

    /** 把旧 speaker 的数据全部迁移到主 speaker（本地→云端）。 */
    fun mergeSpeaker(mergeFromId: String, intoId: String) {
        // conversations
        val cv = ContentValues().apply {
            put("speaker_id", intoId)
        }
        writableDatabase.update("conversations", cv, "speaker_id=?", arrayOf(mergeFromId))
        // about_me
        writableDatabase.update("about_me", ContentValues().apply { put("speaker_id", intoId) }, "speaker_id=?", arrayOf(mergeFromId))
        // relationships source/target
        writableDatabase.update("relationships", ContentValues().apply { put("source_id", intoId) }, "source_id=?", arrayOf(mergeFromId))
        writableDatabase.update("relationships", ContentValues().apply { put("target_id", intoId) }, "target_id=?", arrayOf(mergeFromId))
        // mark old non-canonical
        setCanonical(mergeFromId, false)
        setCloudSpeakerId(mergeFromId, intoId)
    }

    fun listCanonicalSpeakers(): List<SpeakerProfile> = getSpeakers().filter { it.canonical }

    fun deleteSpeaker(id: String) {
        writableDatabase.delete("speakers", "id=?", arrayOf(id))
    }

    /* ─────────── relationships ─────────── */

    fun listRelationships(): List<RelationshipRecord> {
        val result = mutableListOf<RelationshipRecord>()
        readableDatabase.query("relationships", null, null, null, null, null, "ts DESC").use { c ->
            while (c.moveToNext()) {
                result.add(
                    RelationshipRecord(
                        id = c.getLong(0),
                        sourceId = c.getString(1),
                        sourceName = c.getString(2),
                        targetId = c.getString(3),
                        targetName = c.getString(4),
                        relation = c.getString(5) ?: "",
                        evidence = c.getString(6) ?: "",
                        ts = c.getLong(7)
                    )
                )
            }
        }
        return result
    }

    fun relationshipsFor(speakerId: String): List<RelationshipRecord> =
        listRelationships().filter { it.sourceId == speakerId || it.targetId == speakerId }

    fun addRelationship(sourceId: String, sourceName: String, targetId: String, targetName: String, relation: String, evidence: String): Long {
        val values = ContentValues().apply {
            put("source_id", sourceId)
            put("source_name", sourceName)
            put("target_id", targetId)
            put("target_name", targetName)
            put("relation", relation)
            put("evidence", evidence)
            put("ts", System.currentTimeMillis())
        }
        return writableDatabase.insert("relationships", null, values)
    }

    fun deleteRelationship(id: Long) {
        writableDatabase.delete("relationships", "id=?", arrayOf(id.toString()))
    }

    /* ─────────── graph positions ─────────── */

    fun loadGraphPositions(): Map<String, Pair<Float, Float>> {
        val result = HashMap<String, Pair<Float, Float>>()
        readableDatabase.query("graph_positions", null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                result[c.getString(0)] = c.getFloat(1) to c.getFloat(2)
            }
        }
        return result
    }

    fun saveGraphPosition(speakerId: String, x: Float, y: Float) {
        val values = ContentValues().apply {
            put("speaker_id", speakerId)
            put("x", x)
            put("y", y)
        }
        writableDatabase.insertWithOnConflict("graph_positions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /* ─────────── conversations ─────────── */

    fun addConversation(record: ConversationRecord): Long {
        val values = ContentValues().apply {
            put("ts", record.ts)
            put("speaker_id", record.speakerId)
            put("speaker_name", record.speakerName)
            put("text", record.text)
            put("origin", record.origin)
            put("missed_text", record.missedText)
        }
        return writableDatabase.insert("conversations", null, values)
    }

    fun updateConversationMissed(convId: Long, missedText: String?) {
        val values = ContentValues().apply { put("missed_text", missedText) }
        writableDatabase.update("conversations", values, "id=?", arrayOf(convId.toString()))
    }

    fun recentConversations(limit: Int = 200): List<ConversationRecord> {
        val result = mutableListOf<ConversationRecord>()
        readableDatabase.query(
            "conversations", null, null, null, null, null, "ts DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) {
                result.add(fromConversation(c))
            }
        }
        return result
    }

    /** [fromTs, toTs] 内的对话，按时间正序，最多 limit 条（超出时保留最新的）。 */
    fun conversationsBetween(fromTs: Long, toTs: Long, limit: Int = 500): List<ConversationRecord> {
        val result = mutableListOf<ConversationRecord>()
        readableDatabase.query(
            "conversations", null, "ts >= ? AND ts <= ?",
            arrayOf(fromTs.toString(), toTs.toString()), null, null, "ts DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) {
                result.add(fromConversation(c))
            }
        }
        return result.asReversed()
    }

    fun updateConversationSpeaker(convId: Long, speakerId: String?, speakerName: String?) {
        val values = ContentValues().apply {
            put("speaker_id", speakerId)
            put("speaker_name", speakerName)
        }
        writableDatabase.update("conversations", values, "id=?", arrayOf(convId.toString()))
    }

    fun searchConversations(query: String, limit: Int = 100): List<ConversationRecord> {
        val result = mutableListOf<ConversationRecord>()
        readableDatabase.query(
            "conversations",
            null,
            "text LIKE ?",
            arrayOf("%$query%"),
            null,
            null,
            "ts DESC",
            limit.toString()
        ).use { c ->
            while (c.moveToNext()) result.add(fromConversation(c))
        }
        return result
    }

    private fun fromConversation(c: android.database.Cursor): ConversationRecord =
        ConversationRecord(
            id = c.getLong(0),
            ts = c.getLong(1),
            speakerId = c.getString(2),
            speakerName = c.getString(3),
            text = c.getString(4) ?: "",
            origin = c.getString(5) ?: "other",
            missedText = if (c.columnCount > 6) c.getString(6) else null
        )

    /* ─────────── memories ─────────── */

    fun addMemory(content: String, pinned: Boolean = false): Long {
        val values = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("content", content)
            put("pinned", if (pinned) 1 else 0)
        }
        return writableDatabase.insert("memories", null, values)
    }

    fun listMemories(): List<MemoryEntry> {
        val result = mutableListOf<MemoryEntry>()
        readableDatabase.query("memories", null, null, null, null, null, "pinned DESC, ts DESC").use { c ->
            while (c.moveToNext()) {
                result.add(
                    MemoryEntry(
                        id = c.getLong(0),
                        ts = c.getLong(1),
                        content = c.getString(2) ?: "",
                        pinned = c.getInt(3) == 1
                    )
                )
            }
        }
        return result
    }

    fun updateMemory(id: Long, content: String) {
        val values = ContentValues().apply { put("content", content) }
        writableDatabase.update("memories", values, "id=?", arrayOf(id.toString()))
    }

    fun deleteMemory(id: Long) {
        writableDatabase.delete("memories", "id=?", arrayOf(id.toString()))
    }

    /** 云端迁移成功后清空本地 memories 表（云端为唯一真相源）。 */
    fun clearMemories() {
        writableDatabase.delete("memories", null, null)
    }

    /** 全部记忆内容清空（保留声纹档案、角色卡/世界书、设置）：对话时间线、工作记忆、画像、关于我、关系、统计。 */
    fun wipeMemoryContent(speakersToo: Boolean = false) {
        listOf(
            "conversations", "memories", "about_me", "relationships",
            "graph_positions", "daily_stats", "session_state", "profile_doc"
        ).forEach { table ->
            runCatching { writableDatabase.delete(table, null, null) }
        }
        if (speakersToo) {
            writableDatabase.delete("speakers", null, null)
        }
    }

    /* ─────────── about_me ─────────── */

    fun addAboutMe(content: String, source: String = "manual", speakerId: String? = null): Long {
        val values = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("content", content)
            put("source", source)
            if (speakerId != null) put("speaker_id", speakerId)
        }
        return writableDatabase.insert("about_me", null, values)
    }

    fun listAboutMe(speakerId: String? = null): List<AboutMeEntry> {
        val result = mutableListOf<AboutMeEntry>()
        val selection = if (speakerId == null) "speaker_id IS NULL" else "speaker_id = ?"
        val args = if (speakerId == null) null else arrayOf(speakerId)
        readableDatabase.query("about_me", null, selection, args, null, null, "ts DESC").use { c ->
            while (c.moveToNext()) {
                result.add(
                    AboutMeEntry(
                        id = c.getLong(0),
                        ts = c.getLong(1),
                        content = c.getString(2) ?: "",
                        source = c.getString(3) ?: "manual",
                        speakerId = c.getString(4)
                    )
                )
            }
        }
        return result
    }

    fun updateAboutMe(id: Long, content: String) {
        val values = ContentValues().apply { put("content", content) }
        writableDatabase.update("about_me", values, "id=?", arrayOf(id.toString()))
    }


    fun deleteAboutMe(id: Long) {
        writableDatabase.delete("about_me", "id=?", arrayOf(id.toString()))
    }

    private fun updateDefaultRoleCardWording() {
        val card = listRoleCards().firstOrNull { it.name == "云雀" && it.source == "builtin" } ?: return
        if (card.systemPrompt.contains("主人")) return
        updateRoleCard(
            card.id,
            card.copy(
                description = "云雀·私人助理，一个越来越懂主人的身边助理。",
                scenario = "你是一个全天候陪伴主人的私人助理，既懂人情又可靠。",
                exampleDialogue = "主人：帮我记住我下周要去上海。\n云雀：好，我记住了，下周去上海。",
                systemPrompt = "你是云雀，主人的私人助理。回答简短、口语化、适合直接朗读。"
            )
        )
    }

    /* ─────────── role_cards / world_books ─────────── */

    fun addRoleCard(
        name: String,
        description: String = "",
        personality: String = "",
        scenario: String = "",
        firstMessage: String = "",
        exampleDialogue: String = "",
        systemPrompt: String = "",
        postHistoryInstructions: String = "",
        alternateGreetings: String = "",
        creatorNotes: String = "",
        active: Boolean = false,
        source: String = "manual"
    ): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("description", description)
            put("personality", personality)
            put("scenario", scenario)
            put("first_message", firstMessage)
            put("example_dialogue", exampleDialogue)
            put("system_prompt", systemPrompt)
            put("post_history_instructions", postHistoryInstructions)
            put("alternate_greetings", alternateGreetings)
            put("creator_notes", creatorNotes)
            put("active", if (active) 1 else 0)
            put("source", source)
        }
        return writableDatabase.insert("role_cards", null, values)
    }

    fun listRoleCards(): List<RoleCard> {
        val result = mutableListOf<RoleCard>()
        readableDatabase.query("role_cards", null, null, null, null, null, "active DESC, id DESC").use { c ->
            while (c.moveToNext()) result.add(fromRoleCard(c))
        }
        return result
    }

    fun getRoleCard(id: Long): RoleCard? =
        readableDatabase.query("role_cards", null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) fromRoleCard(c) else null
        }

    fun getActiveRoleCard(): RoleCard? =
        readableDatabase.query("role_cards", null, "active=1", null, null, null, "id DESC", "1").use { c ->
            if (c.moveToFirst()) fromRoleCard(c) else null
        }

    fun updateRoleCard(id: Long, card: RoleCard) {
        val values = ContentValues().apply {
            put("name", card.name)
            put("description", card.description)
            put("personality", card.personality)
            put("scenario", card.scenario)
            put("first_message", card.firstMessage)
            put("example_dialogue", card.exampleDialogue)
            put("system_prompt", card.systemPrompt)
            put("post_history_instructions", card.postHistoryInstructions)
            put("alternate_greetings", card.alternateGreetings)
            put("creator_notes", card.creatorNotes)
            put("active", if (card.active) 1 else 0)
            put("source", card.source)
        }
        writableDatabase.update("role_cards", values, "id=?", arrayOf(id.toString()))
    }

    fun setActiveRoleCard(id: Long?) {
        writableDatabase.execSQL("UPDATE role_cards SET active=0")
        if (id != null) {
            val values = ContentValues().apply { put("active", 1) }
            writableDatabase.update("role_cards", values, "id=?", arrayOf(id.toString()))
        }
    }

    fun deleteRoleCard(id: Long) {
        writableDatabase.delete("role_cards", "id=?", arrayOf(id.toString()))
    }

    private fun fromRoleCard(c: android.database.Cursor): RoleCard =
        RoleCard(
            id = c.getLong(0),
            name = c.getString(1) ?: "",
            description = c.getString(2) ?: "",
            personality = c.getString(3) ?: "",
            scenario = c.getString(4) ?: "",
            firstMessage = c.getString(5) ?: "",
            exampleDialogue = c.getString(6) ?: "",
            systemPrompt = c.getString(7) ?: "",
            postHistoryInstructions = c.getString(8) ?: "",
            alternateGreetings = c.getString(9) ?: "",
            creatorNotes = c.getString(10) ?: "",
            active = c.getInt(11) == 1,
            source = c.getString(12) ?: "manual"
        )

    fun addWorldBook(name: String, roleCardId: Long? = null): Long {
        val values = ContentValues().apply {
            put("name", name)
            if (roleCardId != null) put("role_card_id", roleCardId)
        }
        return writableDatabase.insert("world_books", null, values)
    }

    fun listWorldBooks(): List<WorldBook> {
        val result = mutableListOf<WorldBook>()
        readableDatabase.query("world_books", null, null, null, null, null, "id DESC").use { c ->
            while (c.moveToNext()) result.add(WorldBook(c.getLong(0), c.getString(1) ?: "", c.getLong(2)))
        }
        return result
    }

    fun deleteWorldBook(id: Long) {
        writableDatabase.delete("world_entries", "book_id=?", arrayOf(id.toString()))
        writableDatabase.delete("world_books", "id=?", arrayOf(id.toString()))
    }

    fun addWorldEntry(bookId: Long, keys: String, content: String, comment: String = "", constant: Boolean = false, selective: Boolean = false, priority: Int = 0, position: String = "after"): Long {
        val values = ContentValues().apply {
            put("book_id", bookId)
            put("keys", keys)
            put("content", content)
            put("comment", comment)
            put("constant", if (constant) 1 else 0)
            put("selective", if (selective) 1 else 0)
            put("priority", priority)
            put("position", position)
        }
        return writableDatabase.insert("world_entries", null, values)
    }

    fun listWorldEntries(bookId: Long? = null): List<WorldEntry> {
        val result = mutableListOf<WorldEntry>()
        val where = if (bookId == null) null else "book_id=?"
        val args = if (bookId == null) null else arrayOf(bookId.toString())
        readableDatabase.query("world_entries", null, where, args, null, null, "priority DESC, id DESC").use { c ->
            while (c.moveToNext()) {
                result.add(
                    WorldEntry(
                        id = c.getLong(0), bookId = c.getLong(1), keys = c.getString(2) ?: "",
                        content = c.getString(3) ?: "", comment = c.getString(4) ?: "",
                        constant = c.getInt(5) == 1, selective = c.getInt(6) == 1,
                        priority = c.getInt(7), position = c.getString(8) ?: "after"
                    )
                )
            }
        }
        return result
    }

    fun deleteWorldEntry(id: Long) {
        writableDatabase.delete("world_entries", "id=?", arrayOf(id.toString()))
    }

    /** 根据文本关键词返回命中的世界书内容（含 constant）。 */
    fun matchWorldEntries(text: String, roleCardId: Long? = null): List<WorldEntry> {
        val entries = mutableListOf<WorldEntry>()
        val books = listWorldBooks()
        val bookIds = if (roleCardId == null) books.map { it.id } else books.filter { it.roleCardId == null || it.roleCardId == roleCardId }.map { it.id }
        for (bookId in bookIds) {
            for (e in listWorldEntries(bookId)) {
                if (e.constant || e.keys.split(",").any { it.isNotBlank() && text.contains(it.trim()) }) {
                    entries.add(e)
                }
            }
        }
        return entries.sortedByDescending { it.priority }
    }
}

/** 把特征数组与 JSON 互转，便于 speakers.feature 存储 */
fun encodeFeature(feature: DoubleArray): String {
    val arr = JSONArray()
    for (v in feature) arr.put(v)
    return JSONObject().put("v", arr).toString()
}

fun decodeFeature(s: String): DoubleArray = runCatching {
    val arr = JSONObject(s).getJSONArray("v")
    DoubleArray(arr.length()) { arr.getDouble(it) }
}.getOrDefault(DoubleArray(0))
