# Validação da entrega 1.1 — 12/10/2026

## Resultado executado

- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: **BUILD SUCCESSFUL**.
- Cinco testes JVM passaram, sem falhas/erros, em aproximadamente 0,04 segundo. Usam o `RecoveryLoop` de produção com relógio e agendador simulados, sem conexões RTSP ou esperas reais.
- Cobertura desses testes: nenhuma tentativa sem rede; retorno de quatro sessões em 0/1/2/3 segundos; perda de rede cancela retry pendente; outra sessão em rede válida não é liberada/reiniciada; falhas duplicadas de player/watchdog coalescem em uma tentativa; backoff 1/2/5/10/30 segundos; recuperação estável restaura intervalo inicial; parar sessão impede conexões posteriores; limites dos dois perfis; retorno de uma LAN preferida antecipa somente a sessão com falha e eventos repetidos não adiam o retorno.
- Lint final: **0 erros, 17 avisos**, principalmente versões de dependências, textos sem recursos de tradução e avisos de acessibilidade de gesto em view. Nenhum erro de API mínima. Insets/Voltar e snapshot inicial de redes usam APIs antigas para compatibilidade com Android 8.
- `assembleDebugAndroidTest` compilou o teste Android de Keystore, URIs, Activity, layouts, ampliação/retorno com identidade das sessões e persistência do perfil. **Não executado em dispositivo**.
- APK final: `dist/CamLite-Android-v1.1.apk`, versão 1.1, versionCode 2, mínimo API 26, alvo API 35, **4.819.728 bytes** (aproximadamente 4,60 MiB).
- Assinatura verificada pelo `apksigner`. Certificado SHA-256 igual ao APK 1.0 desta entrega: `9e7287463942b2d3a9e836a6273811f8ba20dfe74ee35d9e86109385636d193d`. Instalação sobre a 1.0 é compatível com essa assinatura.
- Dependências de execução do APK continuam as mesmas três bibliotecas Media3 1.6.1. JUnit está somente no escopo de testes e não é embarcado no APK.
- Nenhuma chave privada incluída no projeto/arquivo de fontes. APK e fontes 1.0 preservados em `dist`.

SHA-256 do APK 1.1:

```
8250E4F7D68B1DBCE6A73CA6E2531AB63EDC874844D594DF16E2D28DC64F5AFF
```

## Revisão da implementação

- Um `NetworkCallback` por Activity visível, com remoção no segundo plano e proteção contra callbacks antigos. Apenas eventos oficiais, sem sondagem da internet nem polling de rede.
- Redes de uso geral com endereço/interface, sem exigir INTERNET/VALIDATED. Rotas e transporte ajudam a escolher a rede; a socket factory vincula a sessão RTSP e sua resolução de nomes à rede escolhida. A perda da rede só interrompe as sessões nela vinculadas. Mudanças de validação não recriam players.
- Rede nova estabiliza por 1,5 segundo. Cada célula reutiliza seu atraso de 0/1/2/3 segundos. Sem rede, a sessão fica Aguardando conexão e não agenda retries RTSP.
- Gesto Android de duplo toque chama ampliação de bounds/visibilidade no mosaico e desacopla superfícies ocultas. Mantém instâncias de player, ordem, layout, URL/substream e FIT. Nenhuma chamada de reconexão nesse caminho.
- Recuperação individual usa um único coordenador, cancela o job anterior, remove listener, limpa referências e libera o player afetado. Timestamps de entrega de frames permitem detectar ausência de frames sem tratar uma imagem estática como defeito. Verificação de frames é ignorada nas câmeras ocultas; ao exibir novamente há tolerância para retomar a superfície.
- Econômico/Normal alteram buffer e alvo de memória; não mudam FPS/resolução na câmera nem prometem reduzir decodificação por descarte de frames. Normal é o padrão, inclusive ao ler arquivo da 1.0.
- Limite de quatro cadastros/células, credenciais Keystore/AES-GCM, bloqueio de duplicatas, ordem, orientação, Monitor/fullscreen, brilho, tela ligada, início automático ao abrir e Home opcional preservados no código.

## Limites da evidência

Não há celular conectado (`adb devices` vazio) nem câmera RTSP acessível fornecida. O emulador não foi tentado novamente: na 1.0 permanecia offline sem driver de aceleração. Não houve alteração de drivers/sistema, teste prolongado, benchmark ou teste de 24 horas.

A simulação JVM comprova a política de agendamento/cancelamento e isolamento, **não** o comportamento de Wi-Fi do fabricante, transporte RTSP, decoder real ou recuperação de uma câmera ao vivo. O teste Android compilado não comprova telas ou gestos em execução.

Ficam para o celular: aparência/usabilidade das telas, dois toques reais e renderização de superfícies, aplicação/persistência dos perfis pelo cadastro/interface, autenticação/reprodução, Wi-Fi sem internet, desligamento do roteador, volta da rede, falha de decoder, rotação, fullscreen, brilho/tela ligada, Home após boot e estabilidade/consumo 24/7. Não se declara nenhum desses itens como testado em aparelho real.

## Roteiro rápido para o usuário

1. Instale a 1.1 por cima da 1.0 desta entrega, sem limpar dados. Confira que as câmeras/ordem/layout continuam salvos.
2. Abra Configurações → Desempenho, selecione Econômico e salve. Reabra e confira persistência. Depois teste Normal. Configure o substream no equipamento conforme orientação do README.
3. No mosaico, dê dois toques em uma câmera e novamente para voltar. Confira imagem completa, layout/ordem e ausência de reinicialização geral. Repita após rotação.
4. Desligue o Wi-Fi do celular ou o roteador. Sem outra rede utilizável, observe Aguardando conexão. Religue e confirme retorno automático e gradual. Teste também Wi-Fi sem internet.
5. Desconecte somente uma câmera ou use uma URL inválida em uma célula; verifique que as demais continuam. Restaure a câmera e confira recuperação com intervalos progressivos.
6. Confira toque longo/Voltar, tela ligada, brilho e retorno do segundo plano. Depois faça seu teste prolongado no aparelho, quando desejar.

Para executar o teste Android somente em **emulador descartável** (ele substitui configurações por dados fictícios):

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.camlite.android.test/com.camlite.android.SmokeInstrumentation
```

Fontes oficiais consultadas: [NetworkCallback](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback), [Network/SocketFactory](https://developer.android.com/reference/android/net/Network), [capacidades de rede](https://developer.android.com/reference/android/net/NetworkCapabilities), [cliente RTSP Media3 1.6.1](https://github.com/androidx/media/blob/1.6.1/libraries/exoplayer_rtsp/src/main/java/androidx/media3/exoplayer/rtsp/RtspClient.java).

