package com.dwl.mutcube.template.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.URI
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** GET-only, bounded network access for a domain explicitly granted to the installed package. */
internal suspend fun templateFetch(url: String, allowedDomains: Set<String>): JSONObject = withContext(Dispatchers.IO) {
    val host = requireNotNull(validateTemplateNetworkUrl(url, allowedDomains).host).lowercase()
    val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .dns(Dns { requestedHost ->
            require(requestedHost.lowercase() == host)
            Dns.SYSTEM.lookup(requestedHost).also { addresses ->
                require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) { "目标地址不允许访问" }
            }
        })
        .build()
    client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
        requireNonRedirect(response.code)
        val contentType = response.header("Content-Type").orEmpty().lowercase()
        require(contentType.startsWith("text/") || contentType.contains("json")) { "只支持文本或 JSON 响应" }
        val bytes = response.body.byteStream().use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 128 * 1024) { "响应超过 128 KB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        JSONObject().put("status", response.code).put("body", bytes.toString(Charsets.UTF_8))
            .put("contentType", contentType)
    }
}

internal fun requireNonRedirect(statusCode: Int) {
    require(statusCode !in 300..399) { "不允许重定向" }
}

internal fun validateTemplateNetworkUrl(url: String, allowedDomains: Set<String>): URI {
    require(url.length in 1..2_000)
    val uri = URI(url)
    val host = requireNotNull(uri.host)?.lowercase()
    require(uri.scheme == "https" && uri.port == -1 && uri.userInfo == null && uri.fragment == null)
    require(host in allowedDomains) { "域名未获授权" }
    return uri
}

internal fun isPublicAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress) return false
    val bytes = address.address
    if (bytes.size == 4) {
        val first = bytes[0].toInt() and 255
        val second = bytes[1].toInt() and 255
        if (first == 0 || first == 10 || first == 127 || first >= 224 ||
            first == 169 && second == 254 || first == 172 && second in 16..31 ||
            first == 192 && second == 168 || first == 100 && second in 64..127 ||
            first == 198 && second in 18..19) return false
    } else if (bytes.size == 16) {
        val first = bytes[0].toInt() and 255
        if (first and 0xfe == 0xfc || first == 0xfe) return false
        // IPv4-mapped IPv6 addresses must be checked as IPv4 as well.
        if (bytes.take(10).all { it.toInt() == 0 } && bytes[10].toInt() == -1 && bytes[11].toInt() == -1) {
            return isPublicAddress(InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
        }
    }
    return true
}
