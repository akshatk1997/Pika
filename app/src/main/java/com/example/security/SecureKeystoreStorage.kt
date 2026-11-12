package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive browser settings and site-permission policies at rest
 * using Android Keystore (AES-256-GCM) in private app filesDir.
 */
class SecureKeystoreStorage(private val context: Context) {

  companion object {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "feather_browser_master_aes_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_BITS = 128
  }

  private fun getOrCreateKey(): SecretKey? {
    return try {
      val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
      val existing = try {
        ks.getKey(KEY_ALIAS, null) as? SecretKey
      } catch (_: Throwable) {
        try {
          ks.deleteEntry(KEY_ALIAS)
        } catch (_: Throwable) {
        }
        null
      }
      if (existing != null) return existing

      val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
      val spec = KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
      keyGen.init(spec)
      keyGen.generateKey()
    } catch (_: Throwable) {
      null
    }
  }

  fun writeEncryptedString(fileName: String, plaintext: String): Boolean {
    val safeName = UrlValidator.sanitizeFilename(fileName)
    val targetFile = File(context.filesDir, safeName)
    val key = getOrCreateKey() ?: return false
    return try {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, key)
      val iv = cipher.iv
      val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
      val payload = ByteArray(1 + iv.size + ciphertext.size)
      payload[0] = iv.size.toByte()
      System.arraycopy(iv, 0, payload, 1, iv.size)
      System.arraycopy(ciphertext, 0, payload, 1 + iv.size, ciphertext.size)
      targetFile.writeBytes(payload)
      true
    } catch (_: Throwable) {
      false
    }
  }

  fun readEncryptedString(fileName: String): String? {
    val safeName = UrlValidator.sanitizeFilename(fileName)
    val targetFile = File(context.filesDir, safeName)
    if (!targetFile.exists()) return null
    val key = getOrCreateKey() ?: return null
    return try {
      val payload = targetFile.readBytes()
      if (payload.size < 2) return null
      val ivLen = payload[0].toInt() and 0xFF
      if (ivLen != GCM_IV_LEN || payload.size <= 1 + ivLen) return null
      val iv = payload.copyOfRange(1, 1 + ivLen)
      val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
      String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    } catch (_: Throwable) {
      null
    }
  }

  fun deleteEncryptedFile(fileName: String) {
    val safeName = UrlValidator.sanitizeFilename(fileName)
    val targetFile = File(context.filesDir, safeName)
    if (targetFile.exists()) {
      // Overwrite bytes before unlinking
      try {
        val len = targetFile.length().toInt().coerceAtMost(65536)
        if (len > 0) {
          val zeros = ByteArray(len)
          SecureRandom().nextBytes(zeros)
          targetFile.writeBytes(zeros)
        }
      } catch (_: Throwable) {
      }
      targetFile.delete()
    }
  }
}
