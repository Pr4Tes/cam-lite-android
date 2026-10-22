package com.camlite.android.updates

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.*
import com.camlite.android.BuildConfig
import java.text.DateFormat
import java.util.Date

class UpdateScreen(private val activity: Activity, private val manager: UpdateManager, private val monitor: () -> Boolean) {
    private var dialog: AlertDialog? = null
    private var details: TextView? = null
    private var progress: ProgressBar? = null
    private var checkButton: Button? = null
    private var updateButton: Button? = null
    private var installButton: Button? = null
    private var cancelButton: Button? = null
    fun show() {
        if (monitor() || activity.isFinishing) return
        if (dialog?.isShowing == true) { refresh(); return }
        val form=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(24,12,24,12) }
        val auto=CheckBox(activity).apply { text="Verificar automaticamente (a cada 24 horas)"; isChecked=manager.automaticEnabled }
        val notify=CheckBox(activity).apply { text="Notificar novas versões"; isChecked=manager.notificationsEnabled }
        form.addView(auto); form.addView(notify)
        auto.setOnCheckedChangeListener { _,value -> manager.settings(value,notify.isChecked) }
        notify.setOnCheckedChangeListener { _,value -> manager.settings(auto.isChecked,value); refresh() }
        details=TextView(activity).apply { setTextIsSelectable(true); textSize=14f; setPadding(0,12,0,12) }; form.addView(details)
        progress=ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal).apply { max=100 }; form.addView(progress)
        fun button(label:String, action:()->Unit)=Button(activity).apply { text=label; isAllCaps=false; setOnClickListener { action() }; form.addView(this) }
        checkButton=button("Verificar agora") { manager.check(true) }
        button("Ver novidades") {
            if (!monitor()) AlertDialog.Builder(activity).setTitle("Notas da versão").setMessage(manager.cached?.notes ?: "Nenhuma publicação autenticada disponível ainda.").setPositiveButton("Fechar",null).show()
        }
        updateButton=button("Atualizar agora — baixar APK") { manager.check(true,download=true) }
        cancelButton=button("Cancelar download") { manager.cancelDownload() }
        installButton=button("Instalar APK validado") { install() }
        if(Build.VERSION.SDK_INT>=33) button("Permitir notificações no Android") { activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1400) }
        button("Mais tarde") { manager.later(); dialog?.dismiss() }
        dialog=AlertDialog.Builder(activity).setTitle("Atualizações — canal estável").setView(ScrollView(activity).apply { addView(form) }).setNegativeButton("Fechar",null).create().apply {
            setOnDismissListener { dialog=null; details=null; progress=null; checkButton=null; updateButton=null; installButton=null; cancelButton=null }
            show()
        }
        refresh()
    }
    fun refresh() {
        if(monitor()) return
        val prefs=manager.preferences
        val last=prefs.getLong("last_attempt",0)
        val attempt=if(last==0L) "Ainda não realizada" else DateFormat.getDateTimeInstance().format(Date(last))
        val permission=Build.VERSION.SDK_INT<33 || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
        details?.text="Instalada: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n"+
            "Disponível: ${manager.available?.let { "${it.version} (${it.code})" } ?: "nenhuma versão mais nova autenticada"}\n"+
            "Canal: estável\nÚltima tentativa: $attempt\n${prefs.getString("check_status","Não verificado.")}\n\n"+
            "Download: ${prefs.getString("download_status","Não solicitado.")}\n"+
            (if(manager.downloadSize>0) "${manager.downloaded} / ${manager.downloadSize} bytes\n" else "")+
            "Instalação: ${prefs.getString("install_status","Não solicitada.")}\n\n"+
            (if(!permission) "Notificação do Android não autorizada; o recurso permanece acessível aqui.\n" else "")+
            "Download e instalação exigem sua ação. O Android pode fechar o app durante a instalação. Câmeras e configurações não são apagadas."
        progress?.apply { visibility=if(manager.downloadSize>0 && manager.busy) View.VISIBLE else View.GONE; progress=if(manager.downloadSize>0) (manager.downloaded*100/manager.downloadSize).toInt().coerceIn(0,100) else 0 }
        checkButton?.isEnabled=!manager.busy
        updateButton?.isEnabled=!manager.busy && manager.available!=null
        cancelButton?.visibility=if(manager.busy && manager.downloadSize>0) View.VISIBLE else View.GONE
        installButton?.apply { isEnabled=!manager.busy && (manager.ready!=null || manager.installerIntent!=null); text=if(manager.installerIntent!=null) "Continuar instalação no Android" else "Instalar APK validado" }
    }
    private fun install() {
        if(monitor() || manager.busy) return
        manager.consumeInstallerIntent()?.let { confirmation ->
            try { activity.startActivity(confirmation) } catch (_: Exception) { Toast.makeText(activity,"Não foi possível abrir a confirmação. Tente novamente.",Toast.LENGTH_LONG).show() }
            refresh(); return
        }
        if(!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity).setTitle("Permitir esta origem")
                .setMessage("Para instalar uma atualização baixada pelo Cam Lite, autorize esta origem nas configurações oficiais do Android. Ao voltar, toque novamente em Instalar; o APK será revalidado.")
                .setNegativeButton("Mais tarde",null).setPositiveButton("Abrir configuração") { _,_ ->
                    try { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${activity.packageName}"))) }
                    catch (_: Exception) { Toast.makeText(activity,"Abra no Android: Apps → Acesso especial → Instalar apps desconhecidos → Cam Lite.",Toast.LENGTH_LONG).show() }
                }.show()
            return
        }
        AlertDialog.Builder(activity).setTitle("Confirmar atualização")
            .setMessage("O APK será revalidado antes de abrir o fluxo oficial do Android. Durante a instalação autorizada, o Android pode encerrar o Cam Lite e interromper os streams. Deseja preparar a instalação?")
            .setNegativeButton("Cancelar",null).setPositiveButton("Continuar") { _,_ -> manager.beginInstall() }.show()
    }
    fun close() { dialog?.dismiss() }
}
