package com.halo.yunquevoice.voice

import java.util.Calendar
import java.util.GregorianCalendar

/**
 * WakeUp 课程表（.ics）解析：VEVENT → (课程名, 星期, 上课分钟, 下课分钟)。
 * 同名同时段的多条事件（学期分段）自动合并为一条；RRULE 细节（UNTIL/单双周）按"每周"简化处理。
 */
object IcsParser {

    data class ParsedCourse(
        val name: String,
        val dayIso: Int,      // 1=一 … 7=日
        val startMin: Int,
        val endMin: Int
    )

    /** 解析 .ics 全文，返回去重后的课程时段列表。 */
    fun parse(text: String): List<ParsedCourse> {
        // 1) 展开折行（续行以空格/Tab开头）
        val lines = mutableListOf<String>()
        for (raw in text.lines()) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.size - 1] = lines.last() + raw.trim()
            } else {
                lines.add(raw)
            }
        }
        // 2) 逐 VEVENT 提取
        val out = mutableListOf<ParsedCourse>()
        var name = ""
        var start: Calendar? = null
        var end: Calendar? = null
        var inEvent = false
        for (line in lines) {
            val upper = line.uppercase()
            if (upper.startsWith("BEGIN:VEVENT")) {
                inEvent = true; name = ""; start = null; end = null
            } else if (upper.startsWith("END:VEVENT")) {
                if (inEvent && start != null && end != null && name.isNotBlank()) {
                    val iso = ((start.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                    out.add(
                        ParsedCourse(
                            name = name.trim(),
                            dayIso = iso,
                            startMin = start.get(Calendar.HOUR_OF_DAY) * 60 + start.get(Calendar.MINUTE),
                            endMin = end.get(Calendar.HOUR_OF_DAY) * 60 + end.get(Calendar.MINUTE)
                        )
                    )
                }
                inEvent = false
            } else if (inEvent) {
                when {
                    upper.startsWith("SUMMARY:") ->
                        name = line.substring(8).replace("\\,", ",").replace("\\;", ";")
                    upper.startsWith("DTSTART") -> parseIcsDate(line)?.let { start = it }
                    upper.startsWith("DTEND") -> parseIcsDate(line)?.let { end = it }
                }
            }
        }
        // 3) 去重（同名同时段；学期分段的多条合并）
        return out.distinctBy { it.name to "${it.dayIso}-${it.startMin}-${it.endMin}" }
    }

    /** 支持 20260831T101500（本地）与 20260831T021500Z（UTC，转+8）。 */
    private fun parseIcsDate(line: String): Calendar? {
        val value = line.substringAfterLast(":").trim()
        val m = Regex("^(\\d{8})T(\\d{6})(Z?)$").find(value) ?: return null
        val (d, t, z) = m.destructured
        val cal = GregorianCalendar()
        cal.clear()
        cal.set(
            d.substring(0, 4).toInt(), d.substring(4, 6).toInt() - 1, d.substring(6, 8).toInt(),
            t.substring(0, 2).toInt(), t.substring(2, 4).toInt(), t.substring(4, 6).toInt()
        )
        if (z == "Z") cal.add(Calendar.HOUR_OF_DAY, 8)
        return cal
    }
}
