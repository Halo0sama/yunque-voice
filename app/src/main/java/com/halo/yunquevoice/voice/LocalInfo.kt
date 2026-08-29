package com.halo.yunquevoice.voice

import android.content.Context
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地基础信息：时间、日期、星期、电量。
 * 让云雀不需要联网也能准确回答这些基本问题。
 */
object LocalInfo {

    private val timeFmt = SimpleDateFormat("HH点mm分", Locale.CHINA)
    private val dateFmt = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    private val weekdayFmt = SimpleDateFormat("EEEE", Locale.CHINA)
    private val fullFmt = SimpleDateFormat("yyyy年M月d日 HH:mm EEEE", Locale.CHINA)

    fun nowLine(): String = "当前时间：${fullFmt.format(Date())}"

    /** 无 Context 版本：时间/日期/星期。 */
    fun answerBasic(question: String): String? = answerBasic(null, question)

    /** 带 Context 版本：额外支持电池电量等需要系统服务的功能。 */
    fun answerBasic(context: Context?, question: String): String? {
        val q = question.trim()
        val now = Date()
        return when {
            q.contains("几点") || q.contains("现在时间") || q.contains("当前时间") ->
                "现在是${timeFmt.format(now)}。"
            q.contains("几号") || q.contains("日期") || q.contains("今天几月") ->
                "今天是${dateFmt.format(now)}，${weekdayFmt.format(now)}。"
            q.contains("星期") || q.contains("周几") ->
                "今天是${weekdayFmt.format(now)}。"
            q.contains("电量") || q.contains("电池") || q.contains("还有多少电") -> {
                val bm = context?.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                if (level >= 0) "当前手机电量约${level}%。" else "我没法读到电量。"
            }
            else -> null
        }
    }
}
