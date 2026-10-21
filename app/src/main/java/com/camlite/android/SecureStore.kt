package com.camlite.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "cameras.enc"))
    private val staged = AtomicFile(File(context.filesDir, "cameras.enc.pending"))
    private val previous = AtomicFile(File(context.filesDir, "cameras.enc.previous"))
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("cam-lite-v1", null) as? SecretKey)?.let { return it }
        check(create) { "Chave de criptografia existente indisponível" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("cam-lite-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): AppConfig {
        val bytes = try { file.readFully() } catch (e: java.io.FileNotFoundException) {
            try { previous.readFully() } catch (_: java.io.FileNotFoundException) { return AppConfig() }
        }
        val json = decrypt(bytes)
        val config = AppConfig.from(json)
        if (json.optInt("schemaVersion", 1) < AppConfig.SCHEMA_VERSION) save(config)
        return config
    }
    private fun decrypt(bytes: ByteArray): JSONObject {
        require(bytes.size > 28 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13))) }
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8))
    }
    fun save(config: AppConfig) {
        config.validate()
        val original = try { file.readFully().also { AppConfig.from(decrypt(it)) } } catch (_: java.io.FileNotFoundException) { null }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)) }
        val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(config.json().toString().toByteArray(Charsets.UTF_8))
        // Validate a durable encrypted candidate while the original is untouched.
        write(staged, encrypted)
        check(AppConfig.from(decrypt(staged.readFully())) == config)
        if (original != null) { write(previous, original); check(previous.readFully().contentEquals(original)) }
        try {
            write(file, encrypted)
            check(AppConfig.from(decrypt(file.readFully())) == config)
        } catch (e: Exception) {
            if (original != null) try { write(file, original) } catch (_: Exception) { /* encrypted previous copy remains */ }
            throw e
        }
    }
    private fun write(target: AtomicFile, bytes: ByteArray) {
        val output = target.startWrite()
        try { output.write(bytes); output.fd.sync(); target.finishWrite(output) } catch (e: Exception) { target.failWrite(output); throw e }
    }
}
