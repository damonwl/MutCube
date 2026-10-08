package com.dwl.mutcube.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val version: String?, val pageUrl: String, val message: String)

object UpdateChecker {
    private const val API_URL = "https://gitee.com/api/v5/repos/wlwanglei/mutcube/releases/latest"
    const val RELEASES_URL = "https://gitee.com/wlwanglei/mutcube/releases"

    suspend fun check(): ReleaseInfo = withContext(Dispatchers.IO) {
        val connection = URL(API_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MutCube-Android")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> ReleaseInfo(null, RELEASES_URL, "仓库尚未发布 Release")
                in 200..299 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val version = json.optString("tag_name").ifBlank { json.optString("name") }.takeIf(String::isNotBlank)
                    ReleaseInfo(
                        version = version,
                        pageUrl = json.optString("html_url", RELEASES_URL).ifBlank { RELEASES_URL },
                        message = version?.let { "最新版本 $it" } ?: "Release 信息不完整",
                    )
                }
                else -> error("更新服务返回 ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }
}
