package com.halo.yunquevoice.memory

import org.json.JSONArray
import org.json.JSONObject

/** Tavern/SillyTavern 角色卡与云雀内部字段互转。 */
object RoleCardIO {

    fun toTavernJson(card: RoleCard): String {
        val obj = JSONObject()
        obj.put("name", card.name)
        obj.put("description", card.description)
        obj.put("personality", card.personality)
        obj.put("scenario", card.scenario)
        obj.put("first_mes", card.firstMessage)
        obj.put("mes_example", card.exampleDialogue)
        obj.put("system_prompt", card.systemPrompt)
        obj.put("post_history_instructions", card.postHistoryInstructions)
        obj.put("alternate_greetings", JSONArray().put(card.alternateGreetings))
        obj.put("creator_notes", card.creatorNotes)
        obj.put("source", card.source)
        return obj.toString(2)
    }

    /** 从 Tavern JSON 解析并写入数据库，返回新角色卡 id。 */
    fun importTavernJson(jsonText: String, db: MemoryDb): Long {
        val obj = JSONObject(jsonText)
        val name = obj.optString("name", "").ifBlank { "导入角色" }
        val alt = obj.optJSONArray("alternate_greetings")
        val altText = if (alt != null && alt.length() > 0) alt.getString(0) else ""
        val cardId = db.addRoleCard(
            name = name,
            description = obj.optString("description", ""),
            personality = obj.optString("personality", ""),
            scenario = obj.optString("scenario", ""),
            firstMessage = obj.optString("first_mes", ""),
            exampleDialogue = obj.optString("mes_example", ""),
            systemPrompt = obj.optString("system_prompt", ""),
            postHistoryInstructions = obj.optString("post_history_instructions", ""),
            alternateGreetings = altText,
            creatorNotes = obj.optString("creator_notes", ""),
            active = false,
            source = "tavern"
        )
        // 角色卡自带 character_book（世界书）也导入
        val bookObj = obj.optJSONObject("character_book")
        if (bookObj != null) {
            val bookId = db.addWorldBook(bookObj.optString("name", "角色世界书"), cardId)
            val entries = bookObj.optJSONArray("entries") ?: JSONArray()
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                val keys = e.optJSONArray("keys") ?: JSONArray()
                val keyText = (0 until keys.length()).joinToString(",") { keys.optString(it) }
                db.addWorldEntry(
                    bookId = bookId,
                    keys = keyText,
                    content = e.optString("content", ""),
                    comment = e.optString("comment", ""),
                    constant = e.optBoolean("constant", false),
                    selective = e.optBoolean("selective", false),
                    priority = e.optInt("priority", 0),
                    position = e.optString("position", "after")
                )
            }
        }
        return cardId
    }


    fun worldBookToJson(book: WorldBook, entries: List<WorldEntry>): String {
        val obj = JSONObject()
        obj.put("name", book.name)
        obj.put("entries", JSONArray().apply {
            entries.forEach { e ->
                put(JSONObject().apply {
                    put("keys", JSONArray().apply { e.keys.split(",").forEach { put(it.trim()) } })
                    put("content", e.content)
                    put("comment", e.comment)
                    put("constant", e.constant)
                    put("selective", e.selective)
                    put("priority", e.priority)
                    put("position", e.position)
                })
            }
        })
        return obj.toString(2)
    }

    fun importWorldBookJson(text: String, db: MemoryDb): Long {
        val obj = JSONObject(text)
        val bookId = db.addWorldBook(obj.optString("name", "导入世界书"))
        val entries = obj.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            val keysArr = e.optJSONArray("keys") ?: JSONArray()
            val keyText = (0 until keysArr.length()).joinToString(",") { keysArr.optString(it) }
            db.addWorldEntry(
                bookId = bookId,
                keys = keyText,
                content = e.optString("content", ""),
                comment = e.optString("comment", ""),
                constant = e.optBoolean("constant", false),
                selective = e.optBoolean("selective", false),
                priority = e.optInt("priority", 0),
                position = e.optString("position", "after")
            )
        }
        return bookId
    }
    /** 导出角色卡 + 角色世界书。 */
    fun exportWithWorldBook(card: RoleCard, worldEntries: List<WorldEntry>): String {
        val obj = JSONObject(toTavernJson(card))
        obj.put("character_book", JSONObject().apply {
            put("name", "${card.name}的世界书")
            put("entries", JSONArray().apply {
                worldEntries.forEach { e ->
                    put(JSONObject().apply {
                        put("keys", JSONArray().apply {
                            e.keys.split(",").forEach { put(it.trim()) }
                        })
                        put("content", e.content)
                        put("comment", e.comment)
                        put("constant", e.constant)
                        put("selective", e.selective)
                        put("priority", e.priority)
                        put("position", e.position)
                    })
                }
            })
        })
        return obj.toString(2)
    }
}
