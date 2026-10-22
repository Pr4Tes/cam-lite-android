package com.camlite.android.updates

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

object VerifiedDownload {
    fun copy(input:InputStream,target:File,release:ReleaseManifest,cancel:AtomicBoolean,progress:(Long)->Unit) {
        var success=false
        try {
            val digest=MessageDigest.getInstance("SHA-256")
            var total=0L
            val start=System.nanoTime()
            target.outputStream().use { output ->
                val buffer=ByteArray(65536)
                while(true) {
                    if(cancel.get()) throw CancellationException()
                    if((System.nanoTime()-start)/1000000000>600) throw java.io.IOException("Download excedeu o tempo permitido")
                    val n=input.read(buffer); if(n<0) break
                    total+=n
                    if(total>release.size) throw UpdateRejected("Download maior que o autorizado.")
                    digest.update(buffer,0,n); output.write(buffer,0,n); progress(total)
                }
                output.fd.sync()
            }
            if(cancel.get()) throw CancellationException()
            if(total!=release.size || digest.digest().joinToString("") { "%02x".format(it) }!=release.sha256) throw UpdateRejected("Download incompleto ou SHA-256 incompatível.")
            success=true
        } finally { if(!success) target.delete() }
    }
}

object UpdateIdentity {
    fun validate(release:ReleaseManifest,packageName:String,code:Long,version:String?,minSdk:Int?,signers:Set<String>,installedSigners:Set<String>,nativeAbis:Set<String>,deviceAbis:Set<String>,sdk:Int,installedCode:Long) {
        if(packageName!="com.camlite.android" || code!=release.code || version!=release.version || code<=installedCode) throw UpdateRejected("Pacote ou versão do APK incompatível.")
        if(minSdk!=release.minSdk || release.minSdk>sdk) throw UpdateRejected("Android incompatível com o APK.")
        if(signers!=setOf(UpdateSource.APK_SIGNER) || installedSigners!=setOf(UpdateSource.APK_SIGNER)) throw UpdateRejected("Assinatura permanente do APK incompatível.")
        if(nativeAbis.isNotEmpty() && (nativeAbis.intersect(deviceAbis).isEmpty() || !nativeAbis.containsAll(release.architectures))) throw UpdateRejected("Arquitetura do APK incompatível.")
    }
}
