package com.constrakr.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.constrakr.domain.FaceEmbedding
import com.constrakr.domain.FacePose
import org.json.JSONArray
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Port of iOS [EmbeddingCrypto.swift] — AES-GCM with Android Keystore.
 * Never log or expose raw 512-d vectors.
 */
class SecureFaceTemplateStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun encryptValues(values: FloatArray): ByteArray {
        val plain = JSONArray().apply { values.forEach { put(it.toDouble()) } }.toString().toByteArray()
        return seal(plain)
    }

    fun decryptValues(ciphertext: ByteArray): FloatArray {
        val json = String(open(ciphertext))
        val arr = JSONArray(json)
        return FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
    }

    fun encryptEmbeddings(embeddings: List<FaceEmbedding>): ByteArray {
        val json = JSONArray()
        for (emb in embeddings) {
            json.put(JSONArray().apply {
                put(emb.pose.raw)
                put(JSONArray().apply { emb.values.forEach { put(it.toDouble()) } })
            })
        }
        return seal(json.toString().toByteArray())
    }

    fun decryptEmbeddings(ciphertext: ByteArray): List<FaceEmbedding> {
        val arr = JSONArray(String(open(ciphertext)))
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONArray(i)
                val pose = FacePose.entries.first { it.raw == item.getString(0) }
                val valuesArr = item.getJSONArray(1)
                val values = FloatArray(valuesArr.length()) { valuesArr.getDouble(it).toFloat() }
                add(FaceEmbedding(pose, values))
            }
        }
    }

    private fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        return iv + encrypted
    }

    private fun open(combined: ByteArray): ByteArray {
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val payload = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(payload)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "constrakr.secure-templates"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "com.constrakr.embedding-aes-key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
