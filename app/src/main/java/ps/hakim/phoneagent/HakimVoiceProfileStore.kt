package ps.hakim.phoneagent

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * خزنة محلية لعينة صوت مالك حكيم.
 *
 * العينة ليست قالبًا بيومتريًا ولا تُرفع إلى خدمة خارجية. تحفظ داخل مساحة
 * no-backup الخاصة بالتطبيق ومشفرة بمفتاح AES-GCM مولد في Android Keystore.
 */
object HakimVoiceProfileStore {
    const val VERSION = "VOICE-PROFILE-VAULT-2026-10-06-v1"

    private const val KEY_ALIAS = "hakim_voice_profile_v1"
    private const val PREFS = "hakim_voice_profile_meta_v1"
    private const val DIR = "voice-profile"
    private const val FILE = "sample.hvp"
    private const val MAX_BYTES = 16 * 1024 * 1024
    private val MAGIC = byteArrayOf(
        'H'.code.toByte(), 'V'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte()
    )

    data class Metadata(
        val sizeBytes: Int,
        val sha256: String,
        val mimeType: String,
        val updatedAt: Long
    )

    fun hasProfile(context: Context): Boolean =
        profileFile(context).isFile && metadata(context) != null

    fun metadata(context: Context): Metadata? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hash = p.getString("sha256", null) ?: return null
        val size = p.getInt("size_bytes", -1)
        val mime = p.getString("mime_type", "audio/*").orEmpty()
        val updated = p.getLong("updated_at", 0L)
        if (size <= 0 || updated <= 0L || !hash.matches(Regex("^[0-9a-f]{64}$"))) return null
        return Metadata(size, hash, mime, updated)
    }

    fun saveFromUri(context: Context, uri: Uri): Metadata {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty().ifBlank { "audio/*" }
        require(
            mime.startsWith("audio/") ||
                mime == "application/ogg" ||
                mime == "application/octet-stream"
        ) { "VOICE_PROFILE_NOT_AUDIO" }
        val input = resolver.openInputStream(uri) ?: error("VOICE_PROFILE_OPEN_FAILED")
        return input.use { save(context, it, mime) }
    }

    fun delete(context: Context) {
        runCatching { profileFile(context).delete() }
        runCatching { backupFile(context).delete() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        }
    }

    /**
     * يفك التشفير إلى ملف cache خاص بالتطبيق للتشغيل المحلي فقط.
     * يجب على المستدعي حذف الملف بعد انتهاء التشغيل.
     */
    fun materializePreview(context: Context): File {
        val source = profileFile(context)
        require(source.isFile) { "VOICE_PROFILE_MISSING" }
        val plain = decrypt(source)
        val out = File(context.cacheDir, "hakim-voice-preview-${System.nanoTime()}.audio")
        try {
            FileOutputStream(out).use { stream ->
                stream.write(plain)
                stream.fd.sync()
            }
            return out
        } finally {
            plain.fill(0)
        }
    }

    private fun save(context: Context, input: InputStream, mimeType: String): Metadata {
        val bytes = readBounded(input)
        require(bytes.size >= 1024) { "VOICE_PROFILE_TOO_SMALL" }

        val hash = sha256(bytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(bytes)

        val dir = File(context.noBackupFilesDir, DIR).apply {
            if (!exists()) require(mkdirs()) { "VOICE_PROFILE_DIR_FAILED" }
        }
        val target = File(dir, FILE)
        val temp = File(dir, "$FILE.tmp")
        val backup = File(dir, "$FILE.bak")
        temp.delete()

        try {
            val raw = FileOutputStream(temp)
            val out = DataOutputStream(raw)
            try {
                out.write(MAGIC)
                out.writeInt(iv.size)
                out.write(iv)
                out.writeInt(ciphertext.size)
                out.write(ciphertext)
                out.flush()
                raw.fd.sync()
            } finally {
                out.close()
            }
            require(temp.isFile && temp.length() > 0L) { "VOICE_PROFILE_WRITE_FAILED" }

            if (backup.exists()) require(backup.delete()) { "VOICE_PROFILE_STALE_BACKUP" }
            val hadOld = target.exists()
            if (hadOld) require(target.renameTo(backup)) { "VOICE_PROFILE_BACKUP_FAILED" }

            if (!temp.renameTo(target)) {
                if (hadOld && backup.exists()) backup.renameTo(target)
                error("VOICE_PROFILE_REPLACE_FAILED")
            }

            val meta = Metadata(
                sizeBytes = bytes.size,
                sha256 = hash,
                mimeType = mimeType.take(80),
                updatedAt = System.currentTimeMillis()
            )
            val committed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("size_bytes", meta.sizeBytes)
                .putString("sha256", meta.sha256)
                .putString("mime_type", meta.mimeType)
                .putLong("updated_at", meta.updatedAt)
                .commit()

            if (!committed) {
                target.delete()
                if (hadOld && backup.exists()) backup.renameTo(target)
                error("VOICE_PROFILE_META_FAILED")
            }

            backup.delete()
            return meta
        } finally {
            bytes.fill(0)
            temp.delete()
        }
    }

    private fun decrypt(source: File): ByteArray {
        DataInputStream(FileInputStream(source)).use { input ->
            val magic = ByteArray(4)
            input.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "VOICE_PROFILE_FORMAT" }

            val ivSize = input.readInt()
            require(ivSize in 12..32) { "VOICE_PROFILE_IV" }
            val iv = ByteArray(ivSize)
            input.readFully(iv)

            val cipherSize = input.readInt()
            require(cipherSize in 1..(MAX_BYTES + 64 * 1024)) { "VOICE_PROFILE_SIZE" }
            val encrypted = ByteArray(cipherSize)
            input.readFully(encrypted)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            return cipher.doFinal(encrypted)
        }
    }

    private fun readBounded(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_BYTES) { "VOICE_PROFILE_TOO_LARGE" }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun profileFile(context: Context): File =
        File(File(context.noBackupFilesDir, DIR), FILE)

    private fun backupFile(context: Context): File =
        File(File(context.noBackupFilesDir, DIR), "$FILE.bak")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
