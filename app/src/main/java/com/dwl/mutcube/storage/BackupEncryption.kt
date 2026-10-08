package com.dwl.mutcube.storage

import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable, password-protected envelope. The password is never stored in the archive or device preferences. */
internal object BackupEncryption {
    private val magic = "MUTCUBE-BACKUP-ENC-1\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val ITERATIONS = 600_000

    fun output(output: OutputStream, password: String?): OutputStream {
        if (password.isNullOrEmpty()) return output
        require(password.length >= 12) { "备份密码至少需要 12 个字符" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        output.write(magic)
        output.write(salt)
        output.write(nonce)
        val cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, nonce)
        return CipherOutputStream(output, cipher)
    }

    fun input(input: InputStream, password: String?): Pair<InputStream, Boolean> {
        val source = PushbackInputStream(input.buffered(), magic.size)
        val prefix = ByteArray(magic.size)
        var count = 0
        while (count < prefix.size) {
            val next = source.read(prefix, count, prefix.size - count)
            if (next < 0) break
            count += next
        }
        if (count != magic.size || !prefix.contentEquals(magic)) {
            if (count > 0) source.unread(prefix, 0, count)
            return source to false
        }
        require(!password.isNullOrEmpty()) { "此备份已加密，请输入备份密码" }
        val salt = ByteArray(SALT_BYTES)
        val nonce = ByteArray(NONCE_BYTES)
        require(source.readFully(salt) && source.readFully(nonce)) { "加密备份头部不完整" }
        return CipherInputStream(source, cipher(Cipher.DECRYPT_MODE, password, salt, nonce)) to true
    }

    private fun cipher(mode: Int, password: String, salt: ByteArray, nonce: ByteArray): Cipher {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val encoded = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(encoded, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(magic)
                updateAAD(salt)
                updateAAD(nonce)
            }
        } finally { encoded.fill(0) }
    }

    private fun InputStream.readFully(bytes: ByteArray): Boolean {
        var offset = 0
        while (offset < bytes.size) {
            val count = read(bytes, offset, bytes.size - offset)
            if (count < 0) return false
            offset += count
        }
        return true
    }
}
