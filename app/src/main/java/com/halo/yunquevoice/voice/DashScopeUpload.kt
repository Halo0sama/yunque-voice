package com.halo.yunquevoice.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * DashScope 官方文件上传通道：
 * 1. GET /api/v1/uploads 获取临时上传凭证
 * 2. multipart/form-data 上传文件到 upload_host
 * 3. 生成 oss:// 临时 URL，供模型调用（需带 X-DashScope-OssResourceResolve）
 */
object DashScopeUpload {

    private const val UPLOAD_POLICY_URL = "https://dashscope.aliyuncs.com/api/v1/uploads"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun upload(apiKey: String, file: File, model: String): String = withContext(Dispatchers.IO) {
        VoiceMvpLog.i("UPLOAD", "开始获取上传凭证 model=$model file=${file.name} size=${file.length()}")
        val policy = fetchPolicy(apiKey, model)
        VoiceMvpLog.i("UPLOAD", "凭证获取成功 host=${policy.uploadHost}")

        val fileName = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val key = "${policy.uploadDir.trimEnd('/')}/$fileName"
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("OSSAccessKeyId", policy.ossAccessKeyId)
            .addFormDataPart("policy", policy.policy)
            .addFormDataPart("Signature", policy.signature)
            .addFormDataPart("key", key)
            .addFormDataPart("x-oss-object-acl", policy.xOssObjectAcl)
            .addFormDataPart("x-oss-forbid-overwrite", policy.xOssForbidOverwrite)
            .addFormDataPart("success_action_status", "200")
            .addFormDataPart("file", fileName, file.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        val req = Request.Builder()
            .url(policy.uploadHost)
            .post(multipart)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                VoiceMvpLog.e("UPLOAD", "上传失败 HTTP ${resp.code}: ${body.take(300)}")
                throw IllegalStateException("上传失败 HTTP ${resp.code}: ${body.take(200)}")
            }
        }
        val ossUrl = "oss://$key"
        VoiceMvpLog.i("UPLOAD", "上传成功 ossUrl=$ossUrl")
        ossUrl
    }

    private fun fetchPolicy(apiKey: String, model: String): UploadPolicy {
        val url = "$UPLOAD_POLICY_URL?action=getPolicy&model=${model}"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("获取上传凭证失败 HTTP ${resp.code}: ${text.take(200)}")
            val root = JSONObject(text)
            val data = root.getJSONObject("data")
            return UploadPolicy(
                uploadHost = data.getString("upload_host"),
                policy = data.getString("policy"),
                signature = data.getString("signature"),
                ossAccessKeyId = data.getString("oss_access_key_id"),
                xOssObjectAcl = data.optString("x_oss_object_acl", "private"),
                xOssForbidOverwrite = data.optString("x_oss_forbid_overwrite", "true"),
                uploadDir = data.getString("upload_dir")
            )
        }
    }

    private data class UploadPolicy(
        val uploadHost: String,
        val policy: String,
        val signature: String,
        val ossAccessKeyId: String,
        val xOssObjectAcl: String,
        val xOssForbidOverwrite: String,
        val uploadDir: String
    )
}
