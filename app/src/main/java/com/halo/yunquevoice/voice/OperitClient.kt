package com.halo.yunquevoice.voice

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Operit 本地 HTTP API 客户端：获取工具清单并执行工具。 */
object OperitClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isConfigured(context: Context): Boolean =
        Store.operitUrl(context).isNotBlank() && Store.operitToken(context).isNotBlank()

    suspend fun listTools(context: Context): JSONArray? {
        val base = Store.operitUrl(context).trimEnd('/')
        if (base.isBlank()) return null
        return try {
            val req = Request.Builder()
                .url("$base/v1/tools")
                .header("Authorization", "Bearer ${Store.operitToken(context)}")
                .get()
                .build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                val text = r.body?.string() ?: return null
                val obj = JSONObject(text)
                if (obj.has("tools")) obj.getJSONArray("tools") else JSONArray(text)
            }
        } catch (e: Throwable) {
            VoiceMvpLog.e("OPERIT", "listTools failed: ${e.message}", e)
            null
        }
    }

    suspend fun invoke(context: Context, name: String, args: JSONObject): String {
        val base = Store.operitUrl(context).trimEnd('/')
        val body = JSONObject()
            .put("name", name)
            .put("arguments", args)
        try {
            val req = Request.Builder()
                .url("$base/v1/tools/invoke")
                .header("Authorization", "Bearer ${Store.operitToken(context)}")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { r ->
                val text = r.body?.string() ?: ""
                if (r.isSuccessful) return text
                return JSONObject().put("error", "HTTP ${r.code}: $text").toString()
            }
        } catch (e: Throwable) {
            return JSONObject().put("error", e.message).toString()
        }
    }
}
