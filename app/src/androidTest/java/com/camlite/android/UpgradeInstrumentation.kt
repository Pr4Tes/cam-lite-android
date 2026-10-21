package com.camlite.android

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Run only on a disposable emulator. Uses Android APIs so seed also works against 1.2. */
class UpgradeInstrumentation : Instrumentation() {
    private var phase = ""
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); phase = arguments?.getString("phase").orEmpty(); start() }
    private fun example() = JSONObject("""{"schemaVersion":2,"cameras":[
      {"id":"upgrade-b","name":"Exemplo B","host":"192.0.2.2","port":8554,"user":"ficticio","password":"falsa:@/%","channel":2,"substream":false,"customUrl":"rtsp://192.0.2.2:8554/custom?channel=2"},
      {"id":"upgrade-a","name":"Exemplo A","host":"camera.example","port":554,"user":"exemplo","password":"falsa2","channel":1,"substream":true,"customUrl":""}],
      "settings":{"layout":2,"orientation":1,"keepOn":true,"brightness":23,"startMonitor":true,"home":false,"performance":"ECONOMIC",
      "screen":{"scheduledBrightness":false,"dayStart":480,"nightStart":1320,"dayBrightness":35,"nightBrightness":9,"touchLock":false,"autoLock":false,"autoLockSeconds":45}}}""")
    private fun key(create: Boolean): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("cam-lite-v1", null) as? SecretKey)?.let { return it }
        check(create)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("cam-lite-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun decrypt(file: File): JSONObject {
        val bytes = file.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13))) }
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8))
    }
    override fun onStart() {
        try {
            val file = File(targetContext.filesDir, "cameras.enc")
            if (phase == "seed") {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)) }
                file.writeBytes(byteArrayOf(1) + cipher.iv + cipher.doFinal(example().toString().toByteArray(Charsets.UTF_8)))
                File(targetContext.filesDir, "upgrade-test.sha256").writeText(java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })
                check(decrypt(file).getJSONArray("cameras").length() == 2)
            } else {
                check(phase == "check")
                val activity = startActivitySync(Intent().setClassName(targetContext.packageName, "${targetContext.packageName}.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                waitForIdleSync()
                val upgraded = decrypt(file)
                val old = example()
                check(upgraded.getInt("schemaVersion") == 2)
                check(upgraded.getJSONArray("cameras").toString() == old.getJSONArray("cameras").toString())
                val oldSettings = old.getJSONObject("settings")
                oldSettings.keys().forEach { check(upgraded.getJSONObject("settings").get(it).toString() == oldSettings.get(it).toString()) }
                check(!upgraded.getJSONObject("settings").getJSONObject("screen").getBoolean("scheduledBrightness"))
                val hash = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                check(hash == File(targetContext.filesDir, "upgrade-test.sha256").readText())
                check(activity.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                runOnMainSync { activity.finish() }
            }
            finish(0, Bundle().apply { putString("resultado", "Upgrade fixture: $phase OK") })
        } catch (e: Throwable) { finish(1, Bundle().apply { putString("falha", e.stackTraceToString()) }) }
    }
}
