package com.camlite.android.updates

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import com.camlite.android.BuildConfig
import com.camlite.android.MainActivity
import com.camlite.android.R
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

/** Application-scoped updater; it has no reference to camera configuration or player sessions. */
class UpdateManager private constructor(private val context: Context) {
    val preferences = context.getSharedPreferences("updates-v1", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "updates").apply { mkdirs() }
    private val main = Handler(Looper.getMainLooper())
    private val executor = ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS, LinkedBlockingQueue())
    private val listeners = mutableSetOf<() -> Unit>()
    private val protocol = UpdateProtocol(UpdateProtocol.publicKey(context.resources.openRawResource(R.raw.update_public_key).bufferedReader().use { it.readText() }))
    private val transport = UpdateTransport()
    private var publication: Pair<ByteArray,ByteArray>? = null
    var cached: ReleaseManifest? = null; private set
    var ready: ReleaseManifest? = null; private set
    var busy = false; private set
    var downloaded = 0L; private set
    var downloadSize = 0L; private set
    private val cancelled = AtomicBoolean()
    private var activeCount = 0
    private var monitoring = false
    private var attemptStorageFailed = false
    var installerIntent: Intent? = null; private set
    private val automatic = Runnable { if (activeCount > 0) check(false) }
    val available: ReleaseManifest? get() = cached?.takeIf { it.code > BuildConfig.VERSION_CODE && it.expires.isAfter(Instant.now()) }
    val automaticEnabled get() = preferences.getBoolean("automatic",true)
    val notificationsEnabled get() = preferences.getBoolean("notify",true)
    init {
        File(directory,"download.part").delete()
        File(directory,"candidate.apk").delete()
        try { publication = readPublication("last-publication.bin"); cached = publication?.let { verify(it, false) } } catch (_: Exception) { }
        try { ready = readPublication("ready-publication.bin")?.let { verify(it, false) }?.takeIf { readyFile(it).isFile && it.code > BuildConfig.VERSION_CODE } } catch (_: Exception) { }
        if (preferences.getString("download_status","") == "Baixando...") preferences.edit().putString("download_status","Download interrompido. Você pode tentar novamente.").apply()
        val expected = preferences.getLong("install_expected",0)
        if (expected > 0) {
            val installed = installedCode()
            if (installed >= expected) preferences.edit().putString("install_status","Versão $installed observada no Android após a atualização.").putLong("install_expected",0).apply()
            else if (!preferences.contains("install_session")) preferences.edit().putString("install_status","Instalação não confirmada; versão anterior ainda em uso.").apply()
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Atualizações do Cam Lite",NotificationManager.IMPORTANCE_LOW).apply { description="Novas versões; sem avisos sobre o mosaico"; setSound(null,null) })
    }
    fun observe(listener: () -> Unit) { if(listeners.add(listener)) activeCount++; main.removeCallbacks(automatic); main.post(automatic) }
    fun unobserve(listener: () -> Unit) { if (listeners.remove(listener)) activeCount--; if (activeCount == 0) main.removeCallbacks(automatic) }
    fun monitor(value: Boolean) { monitoring = value; if (!value) notifyIfNeeded() }
    fun settings(automatic: Boolean, notify: Boolean) {
        preferences.edit().putBoolean("automatic",automatic).putBoolean("notify",notify).apply()
        main.removeCallbacks(this.automatic)
        if (activeCount > 0) main.post(this.automatic)
        if (!notify) context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
    private fun boot() = try { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) } catch (_: Exception) { -1 }
    private fun due() = UpdatePolicy.automaticDue(automaticEnabled && !attemptStorageFailed,System.currentTimeMillis(),SystemClock.elapsedRealtime(),boot(),preferences.getLong("last_attempt",0),preferences.getLong("last_elapsed",0),preferences.getInt("last_boot",-2))
    private fun scheduleAutomatic() {
        main.removeCallbacks(automatic)
        if (activeCount <= 0 || !automaticEnabled || attemptStorageFailed) return
        val elapsed = SystemClock.elapsedRealtime()
        val lastElapsed = preferences.getLong("last_elapsed",0)
        val remaining = if (boot() >= 0 && boot() == preferences.getInt("last_boot",-2) && elapsed >= lastElapsed)
            UpdateSource.DAY_MS - (elapsed-lastElapsed) else UpdateSource.DAY_MS - (System.currentTimeMillis()-preferences.getLong("last_attempt",0))
        main.postDelayed(automatic, remaining.coerceIn(1000, UpdateSource.DAY_MS))
    }
    private fun changed() { main.post { listeners.toList().forEach { it() }; notifyIfNeeded() } }
    fun check(manual: Boolean, download: Boolean = false) {
        if (busy) return
        if (!manual && !due()) { scheduleAutomatic(); return }
        busy = true; cancelled.set(false); downloaded=0; downloadSize=0; changed()
        executor.execute {
            var checkingPublication = true
            try {
                if (!preferences.edit().putLong("last_attempt",System.currentTimeMillis()).putLong("last_elapsed",SystemClock.elapsedRealtime()).putInt("last_boot",boot()).commit()) {
                    attemptStorageFailed=true
                    throw UpdateRejected("Não foi possível registrar a tentativa. Consulta automática suspensa; tente manualmente após liberar armazenamento.")
                }
                attemptStorageFailed=false
                val pair = transport.read(UpdateSource.MANIFEST_URL,65536) to transport.read(UpdateSource.MANIFEST_URL + ".sig",4096)
                val release = verify(pair, true)
                savePublication("last-publication.bin",pair)
                publication=pair; cached=release
                preferences.edit().putLong("highest_seen",maxOf(preferences.getLong("highest_seen",0),release.code))
                    .putString("check_status",if (release.code > BuildConfig.VERSION_CODE) "Nova versão autenticada: ${release.version}." else "Você já usa a versão disponível.").commit()
                checkingPublication=false
                if (download) {
                    if (release.code <= BuildConfig.VERSION_CODE) throw UpdateRejected("Esta versão já está instalada.")
                    if (directory.usableSpace < release.size * 2 + 32L*1024*1024) throw UpdateRejected("Espaço insuficiente para baixar e instalar.")
                    preferences.edit().putString("download_status","Baixando...").commit()
                    downloadSize=release.size
                    val part = File(directory,"download.part")
                    var lastProgress = 0L
                    transport.download(release,part,cancelled) { bytes ->
                        downloaded=bytes
                        val now=SystemClock.elapsedRealtime()
                        if (now-lastProgress>=250) { lastProgress=now; changed() }
                    }
                    val candidate=File(directory,"candidate.apk")
                    try {
                        if(candidate.exists()) candidate.delete()
                        if(!part.renameTo(candidate)) throw java.io.IOException()
                        validateApk(candidate,release)
                        if (cancelled.get()) throw CancellationException()
                        val target=readyFile(release)
                        if (target.exists() && !target.delete()) throw java.io.IOException()
                        if (!candidate.renameTo(target)) throw java.io.IOException()
                        savePublication("ready-publication.bin",pair)
                        ready=release
                        directory.listFiles()?.filter { it.name.matches(Regex("ready-[0-9]+\\.apk")) && it != target }?.forEach { it.delete() }
                        preferences.edit().putString("download_status","APK autenticado e validado. Instalação depende de confirmação.").commit()
                    } finally { part.delete(); candidate.delete() }
                }
            } catch (e: Exception) {
                val message = when (e) { is CancellationException -> "Download cancelado; versão atual preservada."; is UpdateRejected -> e.message.orEmpty(); else -> "Não foi possível acessar a fonte ou concluir a operação. Tente manualmente mais tarde." }
                val result=preferences.edit()
                if(checkingPublication || !download) result.putString("check_status",message)
                if(download) result.putString("download_status",message)
                result.commit()
                File(directory,"download.part").delete()
            } finally { main.post { busy=false; changed(); scheduleAutomatic() } }
        }
    }
    fun cancelDownload() { cancelled.set(true) }
    fun later() {
        cached?.let { preferences.edit().putLong("postponed",maxOf(it.code,preferences.getLong("postponed",0))).apply() }
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
    private fun verify(pair: Pair<ByteArray,ByteArray>, current: Boolean): ReleaseManifest = protocol.verify(pair.first,pair.second,Instant.now(),Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS.toSet(),if (current) BuildConfig.VERSION_CODE.toLong() else 0,if(current) preferences.getLong("highest_seen",0) else 0)
    private fun savePublication(name: String, pair: Pair<ByteArray,ByteArray>) {
        val bytes=ByteArrayOutputStream().apply { DataOutputStream(this).use { it.writeInt(pair.first.size); it.write(pair.first); it.writeInt(pair.second.size); it.write(pair.second) } }.toByteArray()
        val file=AtomicFile(File(directory,name)); val output=file.startWrite()
        try { output.write(bytes); output.fd.sync(); file.finishWrite(output) } catch(e:Exception) { file.failWrite(output); throw e }
    }
    private fun readPublication(name:String):Pair<ByteArray,ByteArray>? {
        val f=File(directory,name); if(!f.exists()) return null
        if(f.length()>71000) throw UpdateRejected("Cache inválido.")
        return DataInputStream(ByteArrayInputStream(AtomicFile(f).readFully())).use { input ->
            val rawSize=input.readInt(); require(rawSize in 1..65536)
            val raw=ByteArray(rawSize); input.readFully(raw)
            val sigSize=input.readInt(); require(sigSize in 1..4096)
            val sig=ByteArray(sigSize); input.readFully(sig); require(input.available()==0)
            raw to sig
        }
    }
    private fun readyFile(release: ReleaseManifest)=File(directory,"ready-${release.code}.apk")
    @Suppress("DEPRECATION")
    private fun validateApk(file:File,release:ReleaseManifest) {
        if (file.length()!=release.size) throw UpdateRejected("Tamanho do APK incompatível.")
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(65536); while(true) { val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) } }
        if(digest.digest().joinToString("") { "%02x".format(it) }!=release.sha256) throw UpdateRejected("SHA-256 do APK incompatível.")
        if(!release.expires.isAfter(Instant.now()) || release.code<=installedCode()) throw UpdateRejected("APK vencido, já instalado ou downgrade.")
        val flags=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive=context.packageManager.getPackageArchiveInfo(file.absolutePath,flags) ?: throw UpdateRejected("APK inválido.")
        val code=if(Build.VERSION.SDK_INT>=28) archive.longVersionCode else archive.versionCode.toLong()
        val signatures=if(Build.VERSION.SDK_INT>=28) archive.signingInfo?.apkContentsSigners else archive.signatures
        val installed=context.packageManager.getPackageInfo(context.packageName,flags)
        val current=if(Build.VERSION.SDK_INT>=28) installed.signingInfo?.apkContentsSigners else installed.signatures
        ZipFile(file).use { zip ->
            val nativeAbis=zip.entries().asSequence().filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }.map { it.name.split('/')[1] }.toSet()
            UpdateIdentity.validate(release,archive.packageName,code,archive.versionName,archive.applicationInfo?.minSdkVersion,
                signatures?.map { UpdateProtocol.hash(it.toByteArray()) }?.toSet() ?: emptySet(), current?.map { UpdateProtocol.hash(it.toByteArray()) }?.toSet() ?: emptySet(),
                nativeAbis,Build.SUPPORTED_ABIS.toSet(),Build.VERSION.SDK_INT,installedCode())
        }
    }
    @Suppress("DEPRECATION")
    private fun installedCode():Long { val p=context.packageManager.getPackageInfo(context.packageName,0); return if(Build.VERSION.SDK_INT>=28) p.longVersionCode else p.versionCode.toLong() }
    fun beginInstall() {
        if(busy || monitoring) return
        val release=ready ?: return
        busy=true; changed()
        executor.execute {
            var sessionId=-1
            try {
                val pair=readPublication("ready-publication.bin") ?: throw UpdateRejected("Baixe novamente o APK.")
                val authenticated=verify(pair,true)
                if(authenticated!=release) throw UpdateRejected("Publicação do download mudou. Baixe novamente.")
                val file=readyFile(release); validateApk(file,release)
                if(!context.packageManager.canRequestPackageInstalls()) throw UpdateRejected("Autorize a instalação desta origem nas configurações do Android.")
                if(directory.usableSpace<release.size+16L*1024*1024) throw UpdateRejected("Espaço insuficiente para a instalação.")
                val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName); setSize(release.size)
                    if(Build.VERSION.SDK_INT>=31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                val installer=context.packageManager.packageInstaller
                val oldSession=preferences.getInt("install_session",-1)
                if(oldSession>=0) try { installer.abandonSession(oldSession) } catch (_: Exception) { }
                installerIntent=null
                preferences.edit().remove("install_session").apply()
                sessionId=installer.createSession(params)
                preferences.edit().putInt("install_session",sessionId).putLong("install_expected",release.code).putString("install_status","Preparando confirmação oficial do Android...").commit()
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk",0,release.size).use { output -> file.inputStream().use { it.copyTo(output,65536) }; session.fsync(output) }
                    val callback=Intent(context,InstallResultReceiver::class.java).setAction(context.packageName+".INSTALL_RESULT").setPackage(context.packageName)
                    val pending=PendingIntent.getBroadcast(context,sessionId,callback,PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(pending.intentSender)
                }
            } catch(e:Exception) {
                if(sessionId>=0) try { context.packageManager.packageInstaller.abandonSession(sessionId) } catch (_: Exception) { }
                preferences.edit().remove("install_session").remove("install_expected").putString("install_status",if(e is UpdateRejected) e.message else "Não foi possível iniciar o instalador. A versão atual foi preservada.").commit()
            } finally { main.post { busy=false; changed() } }
        }
    }
    fun installResult(intent:Intent) {
        val session=intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1)
        if(session!=preferences.getInt("install_session",-2)) return
        val status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
        @Suppress("DEPRECATION") val confirmation=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent::class.java) else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION && confirmation!=null) {
            installerIntent=confirmation
            preferences.edit().putString("install_status","Pronto para abrir a confirmação oficial do Android. Use Continuar instalação.").apply()
        } else {
            installerIntent=null
            if(status!=PackageInstaller.STATUS_SUCCESS) preferences.edit().remove("install_expected").apply()
            preferences.edit().remove("install_session").putString("install_status",when(status) {
                PackageInstaller.STATUS_SUCCESS -> if(installedCode()>=preferences.getLong("install_expected",Long.MAX_VALUE)) "Nova versão observada no Android." else "Instalador informou sucesso; versão será conferida na próxima abertura."
                PackageInstaller.STATUS_FAILURE_ABORTED -> "Instalação cancelada; dados e versão atual preservados."
                PackageInstaller.STATUS_FAILURE_STORAGE -> "Instalação não concluída: espaço insuficiente."
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Instalação não concluída: APK incompatível."
                else -> "Instalação não concluída. A versão atual permanece utilizável."
            }).apply()
        }
        changed()
    }
    fun consumeInstallerIntent():Intent? { val result=installerIntent; installerIntent=null; return result }
    private fun notifyIfNeeded() {
        val release=available ?: return
        if(ready?.code==release.code) return
        val manager=context.getSystemService(NotificationManager::class.java)
        val permission=(Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
        if(!UpdatePolicy.shouldNotify(release.code,BuildConfig.VERSION_CODE.toLong(),notificationsEnabled,permission,monitoring,preferences.getLong("postponed",0),preferences.getLong("notified",0))) return
        val intent=Intent(context,MainActivity::class.java).putExtra("open_updates",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            manager.notify(NOTIFICATION_ID,Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_camera).setContentTitle("Cam Lite ${release.version} disponível")
                .setContentText("Veja as novidades em Configurações → Atualizações.").setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build())
            preferences.edit().putLong("notified",release.code).apply()
        } catch (_: SecurityException) { }
    }
    companion object {
        private const val CHANNEL="cam_lite_updates"
        private const val NOTIFICATION_ID=1400
        @Volatile private var instance:UpdateManager?=null
        fun get(context:Context):UpdateManager = instance ?: synchronized(this) { instance ?: UpdateManager(context.applicationContext).also { instance=it } }
    }
}
