@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.camlite.android

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*

class MainActivity : Activity() {
    private lateinit var store: SecureStore
    private var config = AppConfig()
    private var loadFailed = false
    private var monitor = false
    private var foreground = false
    private val sessions = mutableListOf<CameraSession>()
    private var mosaic: MosaicView? = null
    private lateinit var networkMonitor: NetworkMonitor
    private var testSession: CameraSession? = null
    private var testDialog: AlertDialog? = null
    private lateinit var root: FrameLayout
    private var touchShield: TouchLockView? = null
    private var monitorDialog: AlertDialog? = null
    private val screenHandler = Handler(Looper.getMainLooper())
    private val brightnessChange = Runnable { updateBrightness() }
    private val autoLock = Runnable { if (monitorDialog == null) lockTouches() }
    private var clockRegistered = false
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { updateBrightness() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // RTSP errors can contain URLs. Suppress library logs to protect embedded credentials.
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = SecureStore(this)
        networkMonitor = NetworkMonitor(this) { available ->
            sessions.forEach { it.updateNetworks(available) }
            testSession?.updateNetworks(available)
        }
        try { config = store.load() } catch (_: Exception) { loadFailed = true }
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (monitor) {
                if (Build.VERSION.SDK_INT >= 28) {
                    val cutout = insets.displayCutout
                    v.setPadding(cutout?.safeInsetLeft ?: 0, cutout?.safeInsetTop ?: 0, cutout?.safeInsetRight ?: 0, cutout?.safeInsetBottom ?: 0)
                } else v.setPadding(0, 0, 0, 0)
            } else {
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        monitor = savedInstanceState?.getBoolean("monitor") ?: (config.settings.startMonitor || config.settings.home)
        if (loadFailed) monitor = false
        applySettings()
        showScreen()
        if (loadFailed) AlertDialog.Builder(this).setTitle("Configurações indisponíveis")
            .setMessage("Não foi possível ler ou migrar as configurações. Os arquivos foram preservados e não serão sobrescritos. Não desinstale nem limpe os dados. Reabra ou verifique uma versão compatível; sem a chave original do Android Keystore não é possível recuperar as credenciais.")
            .setPositiveButton("Entendi", null).show()
    }
    override fun onStart() {
        super.onStart(); foreground = true; networkMonitor.start()
        val filter = IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(clockReceiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(clockReceiver, filter)
        clockRegistered = true
        if (monitor) buildMonitor() else updateBrightness()
    }
    override fun onStop() {
        foreground = false
        screenHandler.removeCallbacksAndMessages(null)
        if (clockRegistered) { unregisterReceiver(clockReceiver); clockRegistered = false }
        monitorDialog?.dismiss(); monitorDialog = null
        window.attributes = window.attributes.apply { screenBrightness = -1f }
        testDialog?.dismiss(); testSession?.stop(); testSession = null
        stopSessions()
        networkMonitor.stop()
        super.onStop()
    }
    override fun onDestroy() { stopSessions(); testSession?.stop(); networkMonitor.stop(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("monitor", monitor); super.onSaveInstanceState(outState) }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); root.requestLayout() }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (monitor && touchShield == null) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) screenHandler.removeCallbacks(autoLock)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) scheduleAutoLock()
        }
        return super.dispatchTouchEvent(event)
    }
    @Deprecated("Legacy back for API 26 compatibility")
    override fun onBackPressed() { if (monitor) leaveMonitor() else super.onBackPressed() }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 16f) = TextView(this).apply { text = value; textSize = size; setTextColor(Color.WHITE); setPadding(dp(4), dp(8), dp(4), dp(8)) }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun save(): Boolean {
        if (loadFailed) { toast("Armazenamento indisponível; configurações preservadas."); return false }
        return try { store.save(config); true } catch (_: Exception) { toast("Não foi possível salvar. Verifique o armazenamento."); false }
    }
    private fun stopSessions() {
        screenHandler.removeCallbacks(autoLock)
        touchShield?.let { root.removeView(it) }; touchShield = null
        sessions.forEach { it.stop() }; sessions.clear(); mosaic = null
    }
    private fun showScreen() {
        applyOrientation()
        stopSessions()
        root.removeAllViews()
        applyWindow()
        if (monitor) { if (foreground) buildMonitor(); return }
        val list = column()
        list.addView(text("Cam Lite Android", 25f))
        list.addView(text("Até 4 câmeras • RTSP TCP • imagem completa", 14f))
        list.addView(button("Entrar no Modo Monitor") {
            if (config.cameras.isEmpty()) toast("Cadastre pelo menos uma câmera.") else { monitor = true; showScreen() }
        })
        list.addView(text("Câmeras (${config.cameras.size}/4)", 20f))
        config.cameras.forEachIndexed { index, camera ->
            list.addView(text("${index + 1}. ${camera.name}"))
            val row = LinearLayout(this)
            row.addView(button("Editar") { cameraEditor(camera) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("↑") { move(index, -1) }.apply { isEnabled = index > 0 }, LinearLayout.LayoutParams(dp(56), -2))
            row.addView(button("↓") { move(index, 1) }.apply { isEnabled = index < config.cameras.lastIndex }, LinearLayout.LayoutParams(dp(56), -2))
            row.addView(button("Remover") {
                AlertDialog.Builder(this).setMessage("Remover ${camera.name}?").setNegativeButton("Cancelar", null).setPositiveButton("Remover") { _, _ ->
                    config.cameras.removeAt(index)
                    if (save()) showScreen() else config.cameras.add(index, camera)
                }.show()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            list.addView(row)
        }
        list.addView(button("Adicionar câmera") { cameraEditor(null) }.apply { isEnabled = config.cameras.size < 4 && !loadFailed })
        list.addView(button("Configurações") { settingsDialog() })
        list.addView(button("Informações e ajuda") { helpDialog() })
        list.addView(text("Em aparelhos antigos: H.264, substream de 640 × 360/480 e 10–15 FPS.", 14f))
        root.addView(ScrollView(this).apply { addView(list) }, FrameLayout.LayoutParams(-1, -1))
    }
    private fun move(index: Int, offset: Int) {
        java.util.Collections.swap(config.cameras, index, index + offset)
        if (save()) showScreen() else java.util.Collections.swap(config.cameras, index, index + offset)
    }
    private fun buildMonitor() {
        applyOrientation()
        stopSessions(); root.removeAllViews()
        val grid = MosaicView(this, config.settings.layout)
        mosaic = grid
        root.addView(grid, FrameLayout.LayoutParams(-1, -1))
        for (i in 0 until config.settings.layout) {
            val camera = config.cameras.getOrNull(i)
            if (camera == null) {
                grid.addView(FrameLayout(this).apply { setBackgroundColor(Color.BLACK); setOnLongClickListener { monitorMenu(); true } })
            } else {
                val session = CameraSession(this, camera, config.settings.performance, onDoubleTap = { toggleCamera(i) }, onAspectRatio = { grid.setVideoAspectRatio(i, it) }) { monitorMenu() }
                session.updateNetworks(networkMonitor.networks)
                sessions.add(session); grid.addView(session.cell); session.start(i * 1000L)
            }
        }
        applyWindow()
        lockTouches()
    }
    private fun toggleCamera(index: Int) {
        if (touchShield != null) return
        val grid = mosaic ?: return
        grid.toggleCamera(index)
        sessions.forEach { session -> session.setRenderingVisible(grid.expanded < 0 || grid.getChildAt(grid.expanded) === session.cell) }
    }
    private fun monitorMenu() {
        if (touchShield != null || monitorDialog != null) return
        screenHandler.removeCallbacks(autoLock)
        monitorDialog = AlertDialog.Builder(this).setTitle("Modo Monitor")
            .setItems(arrayOf("Continuar monitorando", "Bloquear toques", "Sair do Modo Monitor", "Fechar aplicativo")) { _, which ->
                when (which) { 1 -> if (config.settings.screen.touchLock) lockTouches() else toast("Ative o bloqueio em Configurações → Tela."); 2 -> leaveMonitor(); 3 -> finish() }
            }.create().apply { setOnDismissListener { monitorDialog = null; scheduleAutoLock() }; show() }
    }
    private fun lockTouches() {
        screenHandler.removeCallbacks(autoLock)
        if (!monitor || !foreground || !config.settings.screen.touchLock || touchShield != null) return
        touchShield = TouchLockView(this) {
            touchShield?.let { root.removeView(it) }; touchShield = null
            scheduleAutoLock()
        }.also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
    }
    private fun scheduleAutoLock() {
        screenHandler.removeCallbacks(autoLock)
        val screen = config.settings.screen
        if (monitor && foreground && touchShield == null && monitorDialog == null && screen.touchLock && screen.autoLock)
            screenHandler.postDelayed(autoLock, screen.autoLockSeconds * 1000L)
    }
    private fun leaveMonitor() { monitor = false; showScreen() }
    private fun applySettings() {
        applyOrientation()
        applyWindow()
    }
    private fun applyOrientation() {
        val desired = if (monitor) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else when (config.settings.orientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (requestedOrientation != desired) requestedOrientation = desired
    }
    private fun applyWindow() {
        if (monitor && config.settings.keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateBrightness()
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (monitor) hide(WindowInsets.Type.systemBars()) else show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (monitor) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE else View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        root.requestApplyInsets()
    }
    private fun updateBrightness() {
        screenHandler.removeCallbacks(brightnessChange)
        val settings = config.settings
        val now = java.time.ZonedDateTime.now()
        val brightness = if (!monitor || !foreground) -1f else if (settings.screen.scheduledBrightness)
            BrightnessSchedule.percentage(settings.screen, now.hour * 60 + now.minute) / 100f
        else if (settings.brightness >= 0) settings.brightness.coerceAtLeast(1) / 100f else -1f
        window.attributes = window.attributes.apply { screenBrightness = brightness }
        if (monitor && foreground && settings.screen.scheduledBrightness)
            screenHandler.postDelayed(brightnessChange, BrightnessSchedule.delayUntilChange(settings.screen, now))
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus && ::root.isInitialized) applyWindow() }

    private fun field(parent: LinearLayout, label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        parent.addView(text(label, 14f))
        return EditText(this).apply { setText(value); inputType = type; setSingleLine(true); parent.addView(this) }
    }
    private fun cameraEditor(existing: CameraConfig?) {
        val form = column()
        val name = field(form, "Nome de exibição", existing?.name.orEmpty())
        val host = field(form, "IP ou hostname (sem rtsp://)", existing?.host.orEmpty())
        val port = field(form, "Porta RTSP", (existing?.port ?: 554).toString(), InputType.TYPE_CLASS_NUMBER)
        val user = field(form, "Usuário", existing?.user.orEmpty())
        val password = field(form, "Senha", existing?.password.orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val channel = field(form, "Canal", (existing?.channel ?: 1).toString(), InputType.TYPE_CLASS_NUMBER)
        val sub = CheckBox(this).apply { text = "Stream secundário (recomendado)"; isChecked = existing?.substream ?: true }
        form.addView(sub)
        val advanced = column().apply { visibility = View.GONE }
        val custom = field(advanced, "URL RTSP personalizada (opcional)", existing?.customUrl.orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        form.addView(button("Avançado: URL personalizada") { advanced.visibility = if (advanced.visibility == View.GONE) View.VISIBLE else View.GONE })
        form.addView(advanced)
        fun read(): CameraConfig? {
            val p = port.text.toString().toIntOrNull()
            val c = channel.text.toString().toIntOrNull()
            val h = host.text.toString().trim()
            val url = custom.text.toString().trim()
            if (user.text.contains(':')) { toast("O usuário RTSP não pode conter dois-pontos; a senha aceita caracteres especiais."); return null }
            if (name.text.isBlank() || p == null || p !in 1..65535 || c == null || c < 1) { toast("Informe nome, porta entre 1 e 65535 e canal positivo."); return null }
            if (url.isEmpty() && (h.isEmpty() || h.any { it.isWhitespace() || it in "/?#@" })) { toast("Informe um IP ou hostname válido."); return null }
            if (url.isNotEmpty()) {
                val parsed = Uri.parse(url)
                if (parsed.scheme?.lowercase() != "rtsp" || parsed.host.isNullOrBlank() || url.any { it.isWhitespace() } || parsed.port !in -1..65535 || parsed.port == 0) {
                    toast("Use uma URL rtsp:// válida; codifique caracteres especiais na URL."); return null
                }
            }
            return CameraConfig(existing?.id ?: java.util.UUID.randomUUID().toString(), name.text.toString().trim(), h, p, user.text.toString(), password.text.toString(), c, sub.isChecked, url)
        }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "Adicionar câmera" else "Editar câmera")
            .setView(ScrollView(this).apply { addView(form) }).setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).create()
        form.addView(button("Testar câmera") { read()?.let { testCamera(it) } })
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val camera = read() ?: return@setOnClickListener
                val duplicate = config.cameras.firstOrNull { it.id != camera.id && it.endpoint() == camera.endpoint() }
                if (duplicate != null) {
                    AlertDialog.Builder(this).setMessage("IP, porta e canal já cadastrados em ${duplicate.name}.")
                        .setNegativeButton("Voltar", null).setPositiveButton("Editar existente") { _, _ -> dialog.dismiss(); cameraEditor(duplicate) }.show()
                    return@setOnClickListener
                }
                val index = config.cameras.indexOfFirst { it.id == camera.id }
                if (index < 0 && config.cameras.size >= 4) { toast("Limite de quatro câmeras."); return@setOnClickListener }
                if (index >= 0) config.cameras[index] = camera else config.cameras.add(camera)
                if (save()) { dialog.dismiss(); showScreen() }
                else if (index >= 0 && existing != null) config.cameras[index] = existing else config.cameras.remove(camera)
            }
        }
        dialog.show()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    private fun testCamera(camera: CameraConfig) {
        testDialog?.dismiss()
        val session = CameraSession(this, camera, config.settings.performance) {}
        session.updateNetworks(networkMonitor.networks)
        testSession = session
        val content = column()
        content.addView(text("O vídeo deve aparecer abaixo. Se houver falha, confira rede, credenciais e H.264.", 14f))
        content.addView(session.cell, LinearLayout.LayoutParams(-1, dp(240)))
        testDialog = AlertDialog.Builder(this).setTitle("Testar câmera").setView(content).setPositiveButton("Fechar", null).create().apply {
            setOnDismissListener { session.stop(); if (testSession === session) testSession = null; testDialog = null }
            show(); window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        session.start(0)
    }
    private fun spinner(parent: LinearLayout, label: String, options: Array<String>, selected: Int): Spinner {
        parent.addView(text(label))
        return Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options); setSelection(selected); parent.addView(this) }
    }
    private fun settingsDialog() {
        val old = config.settings.copy(screen = config.settings.screen.copy())
        val form = column()
        val layout = spinner(form, "Layout (mostra as primeiras câmeras na ordem)", arrayOf("1 câmera", "2 câmeras", "3 câmeras", "4 câmeras"), old.layout - 1)
        val orientation = spinner(form, "Orientação das telas de configuração (Monitor sempre horizontal)", arrayOf("Automática", "Horizontal", "Vertical"), old.orientation.coerceIn(0, 2))
        val performance = spinner(form, "Desempenho", PerformanceProfile.entries.map { it.label }.toTypedArray(), old.performance.ordinal)
        form.addView(text("Para tablets de 2 GB, como Q8 Lite T810: Econômico, substream H.264 de 640 × 360/480 e 8–10 FPS configurados na câmera. Normal continua disponível. Comece com uma câmera e aumente conforme os decodificadores do aparelho. O app não muda resolução/FPS da câmera nem corta a imagem.", 14f))
        val keep = CheckBox(this).apply { text = "Manter tela ligada no Modo Monitor"; isChecked = old.keepOn }; form.addView(keep)
        val start = CheckBox(this).apply { text = "Iniciar diretamente no Modo Monitor"; isChecked = old.startMonitor }; form.addView(start)
        form.addView(text("Tela → Brilho", 20f))
        val brightnessMode = spinner(form, "Brilho automático do Cam Lite", arrayOf("Brilho manual", "Brilho programado"), if (old.screen.scheduledBrightness) 1 else 0)
        val systemBrightness = CheckBox(this).apply { text = "Usar brilho do Android"; isChecked = old.brightness < 0 }; form.addView(systemBrightness)
        val brightnessLabel = text("Brilho do app: ${if (old.brightness < 0) 50 else old.brightness}%"); form.addView(brightnessLabel)
        val brightness = SeekBar(this).apply { max = 99; progress = if (old.brightness < 0) 49 else old.brightness.coerceAtLeast(1) - 1 }; form.addView(brightness)
        brightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { brightnessLabel.text = "Brilho do app: ${progress + 1}%" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val dayTime = field(form, "Durante o dia — horário inicial (HH:mm)", timeText(old.screen.dayStart))
        val dayPercent = field(form, "Brilho durante o dia (%)", old.screen.dayBrightness.toString(), InputType.TYPE_CLASS_NUMBER)
        val nightTime = field(form, "Durante a noite — horário inicial (HH:mm)", timeText(old.screen.nightStart))
        val nightPercent = field(form, "Brilho durante a noite (%)", old.screen.nightBrightness.toString(), InputType.TYPE_CLASS_NUMBER)
        form.addView(text("Tela → Bloqueio de toques", 20f))
        val touchLock = CheckBox(this).apply { text = "Ativar bloqueio de toques no Modo Monitor"; isChecked = old.screen.touchLock }; form.addView(touchLock)
        val autoTouchLock = CheckBox(this).apply { text = "Bloquear automaticamente após inatividade"; isChecked = old.screen.autoLock }; form.addView(autoTouchLock)
        val lockSeconds = field(form, "Tempo de bloqueio automático (segundos, 5–3600)", old.screen.autoLockSeconds.toString(), InputType.TYPE_CLASS_NUMBER)
        form.addView(text("No Monitor bloqueado, toque para revelar a barra e arraste o controle da esquerda até a direita. Voltar/navegação do Android continuam disponíveis. Em acessibilidade, use a ação Desbloquear toques.", 14f))
        val home = CheckBox(this).apply { text = "Disponibilizar como Home/Launcher (aparelho dedicado)"; isChecked = old.home }; form.addView(home)
        form.addView(text("Para abrir após reiniciar: ative Home, salve e selecione Cam Lite Monitor como app de início padrão do Android. Abertura automática depende do sistema e fabricante. Toque longo permite sair do Monitor.", 14f))
        form.addView(button("Abrir escolha de app de início do Android") {
            try { startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS)) } catch (_: Exception) { toast("Escolha o app de início nas configurações do Android.") }
        })
        val dialog = AlertDialog.Builder(this).setTitle("Configurações").setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val day = parseTime(dayTime.text.toString())
                val night = parseTime(nightTime.text.toString())
                val dayValue = dayPercent.text.toString().toIntOrNull()
                val nightValue = nightPercent.text.toString().toIntOrNull()
                val seconds = lockSeconds.text.toString().toIntOrNull()
                if (day == null || night == null || day == night || dayValue == null || dayValue !in 1..100 || nightValue == null || nightValue !in 1..100 || seconds == null || seconds !in 5..3600) {
                    toast("Use horários HH:mm diferentes, brilhos de 1 a 100% e tempo de 5 a 3600 segundos."); return@setOnClickListener
                }
                config.settings.apply { this.layout = layout.selectedItemPosition + 1; this.orientation = orientation.selectedItemPosition
                    keepOn = keep.isChecked; startMonitor = start.isChecked; this.brightness = if (systemBrightness.isChecked) -1 else brightness.progress + 1; this.home = home.isChecked
                    this.performance = PerformanceProfile.entries[performance.selectedItemPosition]
                    screen = ScreenSettings(brightnessMode.selectedItemPosition == 1, day, night, dayValue, nightValue, touchLock.isChecked, autoTouchLock.isChecked, seconds) }
                if (!save()) { restoreSettings(old); return@setOnClickListener }
                try {
                    packageManager.setComponentEnabledSetting(ComponentName(this, "$packageName.DedicatedHome"),
                        if (home.isChecked) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                } catch (_: Exception) { restoreSettings(old); save(); toast("O Android não permitiu alterar a opção Home."); return@setOnClickListener }
                dialog.dismiss(); applySettings(); showScreen()
            }
        }
        dialog.show()
    }
    private fun restoreSettings(old: Settings) { config.settings.apply { layout = old.layout; orientation = old.orientation; keepOn = old.keepOn; brightness = old.brightness; startMonitor = old.startMonitor; home = old.home; performance = old.performance; screen = old.screen.copy() } }
    private fun timeText(minute: Int) = "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)
    private fun parseTime(value: String): Int? {
        val parts = value.trim().split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }
    private fun helpDialog() {
        AlertDialog.Builder(this).setTitle("Cam Lite Android 1.3").setMessage(
            "Monitor local de até 4 câmeras RTSP H.264, via TCP. Sem nuvem, conta ou gravação.\n\n" +
            "Cadastre IP, usuário, senha e canal; prefira o stream secundário. Use Testar câmera antes de salvar. Para modelos diferentes de Intelbras/Dahua, informe a URL em Avançado.\n\n" +
            "No Monitor, dê dois toques na câmera para ampliar; dê dois toques novamente para voltar ao mosaico. Toque longo abre o menu; Voltar sai do Monitor. Apenas as primeiras câmeras até o tamanho do layout são iniciadas.\n\n" +
            "Se a rede cair, o app aguarda conexão e retoma gradualmente. Wi-Fi sem internet é aceito. Desempenho Normal é padrão; Econômico reduz os limites de buffer.\n\n" +
            "Tela: configure brilho manual ou horários de dia/noite. O bloqueio de toques começa ativo no Monitor; toque e deslize a barra até o fim para desbloquear. Toque longo abre o menu quando desbloqueado. Voltar sempre sai do Monitor.\n\n" +
            "Monitor horizontal e layout responsivo. Em tablets de 2 GB, use Econômico e aumente de 1 até 4 câmeras conforme a capacidade real. Quatro decodificadores não são garantidos.\n\n" +
            "Credenciais protegidas pelo Android Keystore. Configure manualmente o limite de carga de 80% quando disponível; mantenha ventilação e brilho baixo.\n\n" +
            "Compatibilidade e quantidade de vídeos dependem dos decodificadores do celular. A estabilidade 24/7 precisa ser verificada no seu aparelho."
        ).setPositiveButton("Fechar", null).show()
    }
}
