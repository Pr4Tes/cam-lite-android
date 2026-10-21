# Validação da entrega 1.0 — 11/10/2026

## Verificado neste ambiente

- Projeto Kotlin/Gradle compilado com JDK 17, Gradle 8.11.1 e Android SDK 35.
- `assembleDebug`, `assembleDebugAndroidTest` e `lintDebug`: **BUILD SUCCESSFUL**.
- Lint final: **0 erros, 13 avisos**. Avisos de versão mais recente de dependências, textos em português sem recursos de tradução e construtor de view criada programaticamente. Chamadas antigas de insets/Voltar mantidas para compatibilidade com Android 8.
- APK de debug gerado e copiado para `dist/CamLite-Android-v1.0.apk`.
- `apksigner verify --verbose`: assinatura **v2 válida**, um assinante.
- `aapt dump badging`: pacote `com.camlite.android`, versão 1.0, Android mínimo API 26, alvo API 35, launcher presente.
- Tamanho: **5.065.049 bytes**, aproximadamente **4,83 MiB**. Não há biblioteca nativa embarcada dependente de uma ABI específica; os codecs vêm do Android.
- Revisão do código: limite de quatro cadastros; layouts 1/2/3/4; FIT; áudio desativado; TCP explícito; início em intervalos de um segundo; sessão, watchdog e backoff independentes; liberação em segundo plano; ordenação; credenciais criptografadas com Keystore; gravação atômica; fullscreen, brilho da janela, KEEP_SCREEN_ON e Home opcional.
- Regras de backup/transferência excluem dados privados. Nenhuma chave privada de assinatura está no projeto ou ZIP de fontes.

SHA-256 do APK:

```
9CD38117CFCFC2CEA1350C76B467BE9FE0E8AB8FCC5FB290728F63C755453B0C
```

## Não validado em execução

Não havia celular conectado nem câmera RTSP fornecida. Foi feita uma tentativa curta com emulador Android 11 x86_64. O driver de aceleração não está instalado; em modo de software, o emulador permaneceu offline e a tentativa foi encerrada. Não foram alterados drivers nem instaladas configurações de virtualização do Windows.

Portanto, **não se declara abertura do app, aparência final das telas, cadastro pela interface, persistência em dispositivo, reprodução, autenticação real, múltiplos decodificadores, watchdog, fullscreen, rotação, tela ligada, Home após reinício ou estabilidade 24/7 como testados em execução**. Esses recursos estão implementados e compilados; a validação final depende do aparelho real.

O teste curto `SmokeInstrumentation.kt` também foi compilado, mas **não executado**. Ele cobre URIs com caracteres especiais, endpoint duplicado, armazenamento real com Keystore, ausência de credenciais em texto puro, abertura da Activity, geometria/FIT dos mosaicos, KEEP_SCREEN_ON, endpoint offline, Voltar e reabertura no Monitor. Usa somente dados fictícios e loopback; não comprova reprodução RTSP de uma câmera, cadastro manual pela interface ou funcionamento prolongado.

Para executar esse teste somente em um **emulador descartável**:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.camlite.android.test/com.camlite.android.SmokeInstrumentation
```

O teste substitui as configurações do aplicativo por dados fictícios e limpa esses dados ao terminar. Não o execute em aparelho com câmeras cadastradas.

O roteiro curto de testes reais está no README. Não foram realizados testes de 24 horas, benchmarks ou ciclos repetitivos.
