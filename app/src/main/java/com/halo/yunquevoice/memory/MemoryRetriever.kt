package com.halo.yunquevoice.memory

/**
 * 轻量记忆检索：按当前问题与记忆的相关度打分，自动挑选最相关的记忆。
 * 中文按双字组切分，英文按词切分，叠加时间衰减（越新权重越高）。
 */
object MemoryRetriever {

    fun select(memories: List<MemoryEntry>, query: String, limit: Int = 8): List<MemoryEntry> {
        if (memories.isEmpty()) return emptyList()
        val q = grams(query)
        val now = System.currentTimeMillis()
        val maxAge = memories.maxOfOrNull { now - it.ts }?.coerceAtLeast(1L) ?: 1L
        val scored = memories.map { m ->
            val mg = grams(m.content)
            val overlap = q.intersect(mg).size.toDouble()
            val recency = 1.0 - ((now - m.ts).toDouble() / maxAge).coerceIn(0.0, 1.0)
            m to (overlap + recency * 0.3)
        }
        return scored.sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun grams(text: String): Set<String> {
        val clean = text.replace(Regex("[^\\u4e00-\\u9fa5A-Za-z0-9]"), "")
        if (clean.isEmpty()) return emptySet()
        val result = mutableSetOf<String>()
        if (clean.any { it.code in 0x4e00..0x9fa5 }) {
            // 中文双字组
            if (clean.length == 1) result.add(clean)
            for (i in 0 until clean.length - 1) {
                result.add(clean.substring(i, i + 2))
            }
        } else {
            for (w in clean.split(Regex("(?=[A-Z])"))) {
                if (w.length >= 2) result.add(w.lowercase())
            }
        }
        return result
    }
}
