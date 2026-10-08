package com.dwl.mutcube.storage

import android.content.Context
import com.dwl.mutcube.core.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class RemoteBackupType { WEBDAV, S3 }

data class RemoteBackupConfig(
    val type: RemoteBackupType = RemoteBackupType.WEBDAV,
    val endpoint: String = "",
    val username: String = "",
    val remotePath: String = "MutCube/mutcube-latest.zip",
    val bucket: String = "",
    val region: String = "us-east-1",
    val lastBackupAt: Long? = null,
)

class RemoteBackupConfigStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val config = state.asStateFlow()

    fun write(config: RemoteBackupConfig) {
        val normalized = config.copy(
            endpoint = config.endpoint.trim().trimEnd('/'),
            username = config.username.trim(),
            remotePath = config.remotePath.trim().trimStart('/'),
            bucket = config.bucket.trim(),
            region = config.region.trim(),
        )
        preferences.edit()
            .putString("type", normalized.type.name)
            .putString("endpoint", normalized.endpoint)
            .putString("username", normalized.username)
            .putString("remote_path", normalized.remotePath)
            .putString("bucket", normalized.bucket)
            .putString("region", normalized.region)
            .putLong("last_backup_at", normalized.lastBackupAt ?: -1L)
            .apply()
        state.value = normalized
    }

    private fun load() = RemoteBackupConfig(
        type = runCatching {
            RemoteBackupType.valueOf(preferences.getString("type", RemoteBackupType.WEBDAV.name).orEmpty())
        }.getOrDefault(RemoteBackupType.WEBDAV),
        endpoint = preferences.getString("endpoint", "").orEmpty(),
        username = preferences.getString("username", "").orEmpty(),
        remotePath = preferences.getString("remote_path", "MutCube/mutcube-latest.zip").orEmpty(),
        bucket = preferences.getString("bucket", "").orEmpty(),
        region = preferences.getString("region", "us-east-1").orEmpty(),
        lastBackupAt = preferences.getLong("last_backup_at", -1L).takeIf { it >= 0 },
    )

    companion object { const val PREFERENCES = "remote_backup" }
}

class RemoteBackupService(
    context: Context,
    private val backupService: BackupService,
    private val credentialStore: CredentialStore,
    private val configStore: RemoteBackupConfigStore,
) {
    private val appContext = context.applicationContext

    suspend fun save(config: RemoteBackupConfig, primarySecret: String, secondarySecret: String = "") {
        validate(config)
        configStore.write(config)
        when (config.type) {
            RemoteBackupType.WEBDAV -> primarySecret.takeIf(String::isNotBlank)?.let {
                credentialStore.write(WEBDAV_PASSWORD_KEY, it)
            }
            RemoteBackupType.S3 -> {
                primarySecret.takeIf(String::isNotBlank)?.let { credentialStore.write(S3_ACCESS_KEY, it) }
                secondarySecret.takeIf(String::isNotBlank)?.let { credentialStore.write(S3_SECRET_KEY, it) }
            }
        }
    }

    suspend fun test(config: RemoteBackupConfig = configStore.config.value) = withContext(Dispatchers.IO) {
        validate(config)
        when (config.type) {
            RemoteBackupType.WEBDAV -> requestWebDav(config, "OPTIONS", null)
            RemoteBackupType.S3 -> requestS3(config, "HEAD", null, bucketOnly = true)
        }
    }

    suspend fun upload(password: String, config: RemoteBackupConfig = configStore.config.value): BackupSummary = withContext(Dispatchers.IO) {
        validate(config)
        require(password.length >= 12) { "远程备份密码至少需要 12 个字符" }
        val file = temporaryFile("upload")
        try {
            val summary = file.outputStream().use { backupService.create(it, password) }
            when (config.type) {
                RemoteBackupType.WEBDAV -> requestWebDav(config, "PUT", file)
                RemoteBackupType.S3 -> requestS3(config, "PUT", file)
            }
            configStore.write(config.copy(lastBackupAt = System.currentTimeMillis()))
            summary
        } finally {
            file.delete()
        }
    }

    suspend fun restore(password: String?, config: RemoteBackupConfig = configStore.config.value): BackupSummary = withContext(Dispatchers.IO) {
        validate(config)
        val file = temporaryFile("download")
        try {
            when (config.type) {
                RemoteBackupType.WEBDAV -> downloadWebDav(config, file)
                RemoteBackupType.S3 -> downloadS3(config, file)
            }
            file.inputStream().use { backupService.restore(it, password) }
        } finally {
            file.delete()
        }
    }

    suspend fun inspect(password: String?, config: RemoteBackupConfig = configStore.config.value): BackupPreview = withContext(Dispatchers.IO) {
        validate(config)
        val file = temporaryFile("inspect")
        try {
            when (config.type) {
                RemoteBackupType.WEBDAV -> downloadWebDav(config, file)
                RemoteBackupType.S3 -> downloadS3(config, file)
            }
            file.inputStream().use { backupService.inspect(it, password) }
        } finally {
            file.delete()
        }
    }

    private suspend fun requestWebDav(config: RemoteBackupConfig, method: String, body: File?) {
        val target = if (method == "OPTIONS") config.endpoint else webDavTarget(config)
        val password = credentialStore.read(WEBDAV_PASSWORD_KEY) ?: error("请先保存 WebDAV 密码")
        val headers = mapOf("Authorization" to basic(config.username, password))
        if (method == "PUT") ensureWebDavCollections(config, headers)
        execute(URL(target), method, body, headers)
    }

    private suspend fun downloadWebDav(config: RemoteBackupConfig, target: File) {
        val password = credentialStore.read(WEBDAV_PASSWORD_KEY) ?: error("请先保存 WebDAV 密码")
        download(URL(webDavTarget(config)), target, mapOf("Authorization" to basic(config.username, password)))
    }

    private suspend fun requestS3(config: RemoteBackupConfig, method: String, body: File?, bucketOnly: Boolean = false) {
        val accessKey = credentialStore.read(S3_ACCESS_KEY) ?: error("请先保存 S3 Access Key")
        val secretKey = credentialStore.read(S3_SECRET_KEY) ?: error("请先保存 S3 Secret Key")
        val url = s3Target(config, bucketOnly)
        val payloadHash = body?.sha256() ?: EMPTY_SHA256
        execute(url, method, body, AwsV4Signer.headers(url, method, config.region, accessKey, secretKey, payloadHash))
    }

    private suspend fun downloadS3(config: RemoteBackupConfig, target: File) {
        val accessKey = credentialStore.read(S3_ACCESS_KEY) ?: error("请先保存 S3 Access Key")
        val secretKey = credentialStore.read(S3_SECRET_KEY) ?: error("请先保存 S3 Secret Key")
        val url = s3Target(config, false)
        download(url, target, AwsV4Signer.headers(url, "GET", config.region, accessKey, secretKey, EMPTY_SHA256))
    }

    private fun execute(url: URL, method: String, body: File?, headers: Map<String, String>) {
        val connection = connection(url, method, headers)
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.length())
                body.inputStream().use { input -> connection.outputStream.use(input::copyTo) }
            }
            requireSuccess(connection)
            runCatching { connection.inputStream.close() }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: URL, target: File, headers: Map<String, String>) {
        val connection = connection(url, "GET", headers)
        try {
            requireSuccess(connection)
            var total = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BACKUP_BYTES) { "远程备份超过 4 GB" }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun connection(url: URL, method: String, headers: Map<String, String>) =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = false
            headers.forEach(::setRequestProperty)
        }

    private fun requireSuccess(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status !in 200..299) {
            val detail = runCatching {
                connection.errorStream?.bufferedReader()?.use { it.readText().take(240) }
            }.getOrNull()?.replace(Regex("\\s+"), " ")?.takeIf(String::isNotBlank)
            error(buildString {
                append("远程服务返回 ").append(status)
                if (detail != null) append("：").append(detail)
            })
        }
    }

    private fun ensureWebDavCollections(config: RemoteBackupConfig, headers: Map<String, String>) {
        val segments = config.remotePath.split('/').dropLast(1).filter(String::isNotBlank)
        var current = config.endpoint
        segments.forEach { segment ->
            current += "/${AwsV4Signer.encode(segment)}"
            val connection = connection(URL(current), "MKCOL", headers)
            try {
                val status = connection.responseCode
                require(status in 200..299 || status == HttpURLConnection.HTTP_BAD_METHOD) {
                    "无法创建 WebDAV 目录，远程服务返回 $status"
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun validate(config: RemoteBackupConfig) {
        val endpoint = URI(config.endpoint)
        require(endpoint.scheme == "https" && !endpoint.host.isNullOrBlank()) { "远程地址必须使用 HTTPS" }
        require(config.remotePath.isNotBlank() && !config.remotePath.contains("..")) { "远程文件路径无效" }
        when (config.type) {
            RemoteBackupType.WEBDAV -> require(config.username.isNotBlank()) { "WebDAV 用户名不能为空" }
            RemoteBackupType.S3 -> {
                require(config.bucket.matches(Regex("[a-zA-Z0-9.-]{3,63}"))) { "S3 Bucket 无效" }
                require(config.region.matches(Regex("[a-z0-9-]{2,40}"))) { "S3 Region 无效" }
            }
        }
    }

    private fun webDavTarget(config: RemoteBackupConfig) =
        config.endpoint + "/" + config.remotePath.split('/').joinToString("/") { AwsV4Signer.encode(it) }

    private fun s3Target(config: RemoteBackupConfig, bucketOnly: Boolean): URL {
        val suffix = if (bucketOnly) "${AwsV4Signer.encode(config.bucket)}/" else
            "${AwsV4Signer.encode(config.bucket)}/${config.remotePath.split('/').joinToString("/") { AwsV4Signer.encode(it) }}"
        return URL("${config.endpoint}/$suffix")
    }

    private fun temporaryFile(operation: String): File =
        File.createTempFile("mutcube-remote-$operation-", ".zip", appContext.cacheDir)

    private fun basic(username: String, password: String) = "Basic " +
        Base64.getEncoder().encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))

    private fun File.sha256() = inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().hex()
    }

    companion object {
        private const val WEBDAV_PASSWORD_KEY = "backup.webdav.password"
        private const val S3_ACCESS_KEY = "backup.s3.access"
        private const val S3_SECRET_KEY = "backup.s3.secret"
        private const val MAX_BACKUP_BYTES = 4L * 1024 * 1024 * 1024
        private const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}

internal object AwsV4Signer {
    fun headers(
        url: URL,
        method: String,
        region: String,
        accessKey: String,
        secretKey: String,
        payloadHash: String,
        now: Date = Date(),
    ): Map<String, String> {
        val formatter = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val amzDate = formatter.format(now)
        val date = amzDate.take(8)
        val host = if (url.port == -1) url.host else "${url.host}:${url.port}"
        val canonicalUri = url.path.ifBlank { "/" }
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val canonicalHeaders = "host:$host\nx-amz-content-sha256:$payloadHash\nx-amz-date:$amzDate\n"
        val canonicalRequest = "$method\n$canonicalUri\n\n$canonicalHeaders$signedHeaders\n$payloadHash"
        val scope = "$date/$region/s3/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${canonicalRequest.sha256()}"
        val signingKey = hmac(hmac(hmac(hmac(("AWS4$secretKey").toByteArray(), date), region), "s3"), "aws4_request")
        val signature = hmac(signingKey, stringToSign).hex()
        return mapOf(
            "Host" to host,
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to payloadHash,
            "Authorization" to "AWS4-HMAC-SHA256 Credential=$accessKey/$scope, SignedHeaders=$signedHeaders, Signature=$signature",
        )
    }

    fun encode(segment: String): String = segment.toByteArray(StandardCharsets.UTF_8).joinToString("") { byte ->
        val value = byte.toInt() and 0xff
        if ((value in 'a'.code..'z'.code) || (value in 'A'.code..'Z'.code) ||
            (value in '0'.code..'9'.code) || value in listOf('-'.code, '_'.code, '.'.code, '~'.code)
        ) value.toChar().toString() else "%%%02X".format(Locale.US, value)
    }

    private fun String.sha256() = MessageDigest.getInstance("SHA-256").digest(toByteArray()).hex()
    private fun hmac(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(value.toByteArray(StandardCharsets.UTF_8))
    }
}

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
