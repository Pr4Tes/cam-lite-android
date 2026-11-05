package com.camlite.android.updates

import java.time.Instant

object UpdateCheckPresentation {
    fun summary(busy: Boolean, failed: Boolean, release: ReleaseManifest?, installed: Long, now: Instant = Instant.now()): String {
        if (busy) return "Consultando o GitHub…"
        if (failed) return "Não foi possível confirmar. Veja o resultado da tentativa abaixo."
        if (release == null) return "Ainda não verificada. Toque em Verificar agora."
        if (!release.expires.isAfter(now)) return "Consulta anterior vencida. Verifique novamente."
        return if (release.code > installed) "${release.version} (${release.code})" else
            "Você já usa a versão publicada: ${release.version} (${release.code})."
    }
}
