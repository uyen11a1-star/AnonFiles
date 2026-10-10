package com.anon.filemanager.crypto

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    // Layout file mã hóa (không có magic rõ ràng — nhìn hex giống rác):
    // [16B salt][12B IV][4B XOR seed][ciphertext (AES-GCM)]
    // GCM tag 16B đã gộp trong ciphertext.
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val SEED_LEN = 4
    private const val TAG_BITS = 128
    private const val PBKDF2_ITER = 120_000
    private const val KEY_BITS = 256
    private const val HEADER_LEN = SALT_LEN + IV_LEN + SEED_LEN

    private const val ALGO_AES = "AES"
    private const val MODE = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"

    private val rng = SecureRandom()

    fun encryptFile(src: File, dst: File, password: CharArray, obfuscate: Boolean): String {
        return try {
            val plain = src.readBytes()
            val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
            val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
            val seed = ByteArray(SEED_LEN).also { rng.nextBytes(it) }

            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance(MODE)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            val cipherText = cipher.doFinal(plain)

            // Build payload = header + ciphertext
            val payload = ByteArray(HEADER_LEN + cipherText.size)
            System.arraycopy(salt, 0, payload, 0, SALT_LEN)
            System.arraycopy(iv, 0, payload, SALT_LEN, IV_LEN)
            System.arraycopy(seed, 0, payload, SALT_LEN + IV_LEN, SEED_LEN)
            System.arraycopy(cipherText, 0, payload, HEADER_LEN, cipherText.size)

            // XOR obfuscate (byte rác) — dùng seed làm base cho keystream
            val out = if (obfuscate) xorStream(payload, seed) else payload
            dst.writeBytes(out)
            ""
        } catch (e: Exception) { "Lỗi mã hóa: ${e.message}" }
    }

    fun decryptFile(src: File, dst: File, password: CharArray, obfuscated: Boolean): String {
        return try {
            val raw = src.readBytes()
            if (raw.size < HEADER_LEN + 16) return "File quá nhỏ để là file mã hóa"

            // Nếu obfuscated: 4 byte seed nằm ở vị trí SALT_LEN+IV_LEN
            // (seed không bị XOR để có thể đọc trước khi un-obfuscate)
            val seed = if (obfuscated) {
                val s = ByteArray(SEED_LEN)
                System.arraycopy(raw, SALT_LEN + IV_LEN, s, 0, SEED_LEN)
                s
            } else ByteArray(SEED_LEN)

            val payload = if (obfuscated) xorStream(raw, seed) else raw

            val salt = ByteArray(SALT_LEN).also { System.arraycopy(payload, 0, it, 0, SALT_LEN) }
            val iv = ByteArray(IV_LEN).also { System.arraycopy(payload, SALT_LEN, it, 0, IV_LEN) }
            val cipherText = ByteArray(payload.size - HEADER_LEN).also {
                System.arraycopy(payload, HEADER_LEN, it, 0, it.size)
            }

            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance(MODE)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            val plain = cipher.doFinal(cipherText)
            dst.writeBytes(plain)
            ""
        } catch (e: javax.crypto.AEADBadTagException) {
            "Sai mật khẩu hoặc file hỏng (GCM tag không khớp)"
        } catch (e: Exception) {
            "Lỗi giải mã: ${e.message}"
        }
    }

    // XOR obfuscation: wrap 4-byte seed lặp lại
    private fun xorStream(data: ByteArray, seed: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        for (i in data.indices) {
            out[i] = (data[i].toInt() xor seed[i % SEED_LEN].toInt()).toByte()
        }
        // Seed không XOR ở vị trí header (giữ nguyên để decrypt đọc được)
        if (data.size >= SALT_LEN + IV_LEN + SEED_LEN) {
            System.arraycopy(data, SALT_LEN + IV_LEN, out, SALT_LEN + IV_LEN, SEED_LEN)
        }
        return out
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITER, KEY_BITS)
        val factory = SecretKeyFactory.getInstance(KDF)
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, ALGO_AES)
    }

    // Check nhanh: file này có phải là file AnonFiles encrypt không?
    // Không thể chắc chắn 100% nếu không có mật khẩu, nhưng check theo extension .aef.
    fun isEncryptedByExtension(name: String): Boolean = name.lowercase().endsWith(".aef")
}
