package com.camlite.android

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import java.io.File

/** One bounded smoke pass, using the real Android Keystore and real Activity. No camera required. */
class SmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val passed = mutableListOf<String>()
        var activity: MainActivity? = null
        try {
            val cameras = (1..4).map { CameraConfig(name = "Teste $it", host = "127.0.0.1", port = 9, user = "a@ /%", password = "p:@/%?# +", channel = it) }.toMutableList()
            val uri = cameras[0].uri()
            check(uri.host == "127.0.0.1" && uri.port == 9)
            check(uri.userInfo == "a@ /%:p:@/%?# +")
            check(uri.getQueryParameter("subtype") == "1")
            check(!uri.encodedAuthority!!.substringAfter('@').contains('@'))
            check(CameraConfig(name = "dup", host = "ignored", channel = 99, customUrl = "rtsp://127.0.0.1:9/cam/realmonitor?channel=1&subtype=0").endpoint() == cameras[0].endpoint())
            passed.add("URI: caracteres especiais e duplicata IP/porta/canal")
            val store = SecureStore(targetContext)
            store.save(AppConfig(cameras, Settings(screen = ScreenSettings(touchLock = false))))
            val loaded = store.load()
            check(loaded.cameras == cameras && loaded.settings.keepOn)
            val encrypted = File(targetContext.filesDir, "cameras.enc").readBytes().toString(Charsets.ISO_8859_1)
            check(!encrypted.contains(cameras[0].password) && !encrypted.contains("127.0.0.1") && !encrypted.contains("Teste 1"))
            passed.add("Keystore AES-GCM: persistência e ausência de senha/IP em texto puro")
            val launched = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = launched
            waitForIdleSync()
            runOnMainSync {
                val root = launched.window.decorView
                check(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "Cam Lite Android" })
                check(descendants(root).filterIsInstance<TextView>().any { it.text.toString().contains("Teste 4") })
                descendants(root).filterIsInstance<Button>().first { it.text.toString() == "Entrar no Modo Monitor" }.performClick()
            }
            waitForIdleSync()
            runOnMainSync {
                check(launched.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
                check(launched.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                val mosaic = descendants(launched.window.decorView).filterIsInstance<MosaicView>().single()
                check(mosaic.childCount == 4)
                check(mosaic.getChildAt(0).width == mosaic.getChildAt(1).width)
                check(mosaic.getChildAt(2).top >= mosaic.height / 2)
            }
            passed.add("Activity abre, carrega câmeras, entra no Monitor e ativa KEEP_SCREEN_ON")
            val configField = MainActivity::class.java.getDeclaredField("config").apply { isAccessible = true }
            val build = MainActivity::class.java.getDeclaredMethod("buildMonitor").apply { isAccessible = true }
            for (count in 1..3) {
                runOnMainSync {
                    (configField.get(launched) as AppConfig).settings.layout = count
                    build.invoke(launched)
                }
                waitForIdleSync()
                runOnMainSync {
                    val mosaic = descendants(launched.window.decorView).filterIsInstance<MosaicView>().single()
                    check(mosaic.childCount == count)
                    for (i in 0 until count) {
                        val cell = mosaic.getChildAt(i)
                        check(cell.width > 0 && cell.height > 0 && cell.left >= 0 && cell.right <= mosaic.width && cell.bottom <= mosaic.height)
                        check(descendants(cell).filterIsInstance<androidx.media3.ui.PlayerView>().single().resizeMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
                    }
                    if (count == 1) { val cell = mosaic.getChildAt(0); check(cell.width == mosaic.width && cell.height == mosaic.height) }
                    if (count == 3) check(mosaic.getChildAt(2).width == mosaic.width)
                }
            }
            passed.add("Layouts 1/2/3/4 com células dentro da tela e FIT")
            runOnMainSync {
                val grid = descendants(launched.window.decorView).filterIsInstance<MosaicView>().single()
                val cells = (0 until grid.childCount).map { grid.getChildAt(it) }
                val sessionsField = MainActivity::class.java.getDeclaredField("sessions").apply { isAccessible = true }
                val before = (sessionsField.get(launched) as List<*>).toList()
                val toggle = MainActivity::class.java.getDeclaredMethod("toggleCamera", Int::class.javaPrimitiveType).apply { isAccessible = true }
                toggle.invoke(launched, 1)
                check(grid.expanded == 1 && cells[0].visibility == View.INVISIBLE && cells[1].visibility == View.VISIBLE)
                toggle.invoke(launched, 1)
                check(grid.expanded == -1 && cells.all { it.visibility == View.VISIBLE })
                check((sessionsField.get(launched) as List<*>).zip(before).all { (a, b) -> a === b })
                check(cells.indices.all { grid.getChildAt(it) === cells[it] })
            }
            passed.add("Ampliação/retorno preserva células e sessões sem recriação")
            // Observe offline retry, not camera playback. Allow 1 initial transport failure and retry.
            Thread.sleep(1800)
            runOnMainSync {
                val messages = descendants(launched.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
                check(messages.any { it.contains("Reconectando") || it.contains("Conectando") })
                launched.onBackPressed()
                check(launched.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0)
                check(descendants(launched.window.decorView).none { it is MosaicView })
            }
            passed.add("Endpoint offline tratado sem crash; Voltar sai do Monitor e limpa KEEP_SCREEN_ON")
            runOnMainSync { launched.finish() }
            waitForIdleSync()
            activity = null
            store.save(AppConfig(cameras, Settings(startMonitor = true, brightness = 20, performance = PerformanceProfile.ECONOMIC)))
            check(store.load().settings.performance == PerformanceProfile.ECONOMIC)
            val restarted = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = restarted
            waitForIdleSync()
            runOnMainSync {
                check(descendants(restarted.window.decorView).any { it is MosaicView })
                check(kotlin.math.abs(restarted.window.attributes.screenBrightness - 0.2f) < 0.01f)
            }
            passed.add("Reabertura automática no Monitor e brilho específico da janela")
            finish(0, Bundle().apply { putString("resultado", passed.joinToString("\n")); putInt("checks", passed.size) })
        } catch (e: Throwable) {
            finish(1, Bundle().apply { putString("falha", e.stackTraceToString()); putString("passou", passed.joinToString("\n")) })
        } finally {
            activity?.let { runOnMainSync { it.finish() } }
            SecureStore(targetContext).save(AppConfig())
        }
    }
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(descendants(view.getChildAt(i)))
    }
}
