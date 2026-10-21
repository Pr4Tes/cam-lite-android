# Validação da entrega 1.3 — 12/10/2026

## Verificado neste ambiente

- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: **BUILD SUCCESSFUL**. Após adaptar o teste Android de upgrade à 1.3, `assembleDebugAndroidTest` também passou; não foi necessária nova compilação do APK principal.
- **15 testes JVM passaram**, sem erros/falhas: 4 de geometria, 6 de recuperação individual e 5 de migração/brilho/toques. Execução em menos de um segundo, sem vídeo real, benchmarks ou esperas prolongadas.
- Geometria de produção em **1280 × 800**: quatro células exatamente **640 × 400**, cobrindo a janela inteira sem espaçamento/sobreposição. A proporção dos streams não muda a grade obrigatória 2 × 2.
- Outras dimensões verificadas: 1920 × 1080, 801 × 1281 e 641 × 401, para 1/2/3/4 células. Todas permanecem dentro da janela, cobrem sua área sem sobreposição e preservam quantidade/ordem dos slots.
- Para três streams 16:9, a terceira célula usa a linha inteira, com maior área de imagem FIT total e sem diminuir a menor imagem em relação à antiga célula central estreita. Streams retrato e ultralargos escolhem outras distribuições úteis. Duas câmeras retrato/paisagem ajustam as larguras de suas colunas.
- Testes de recuperação preservam: ausência de tentativas sem rede, retorno escalonado 0/1/2/3 s, cancelamento de operações antigas, backoff e isolamento de uma conexão saudável. Novo teste confirma cooldown de decoder de 30 s somente na sessão afetada e retorno ao intervalo normal após estabilidade.
- Migração/serialização, brilho programado e lógica de arrasto mantêm os testes anteriores passando. Configurações 1.2 usam o mesmo esquema 2; não é necessária migração adicional nesta versão.
- Lint do APK: **0 erros, 22 avisos**. APIs de recorte de tela são protegidas por checagem de versão; suporte mínimo permanece Android 8/API 26.
- APK: `dist/CamLite-Android-v1.3.apk`, **4.859.896 bytes** (~4,63 MiB), pacote **com.camlite.android**, versão **1.3**, versionCode **4**, API mínima **26**, alvo **35**.
- `apksigner`: assinatura **v2 válida**, certificado SHA-256 igual às entregas anteriores: `9e7287463942b2d3a9e836a6273811f8ba20dfe74ee35d9e86109385636d193d`.
- Verificação permanente de assinatura executada antes da build. Nenhuma chave nova gerada; arquivos privados de assinatura não são incluídos nas fontes.
- Nenhuma dependência nova de execução/teste nesta atualização. Mantidos serviços/threads existentes, sem adicionar serviço de monitor, brilho ou desempenho.

SHA-256 do APK:

```
4A5EA2093AE5DF502D4D226EDA414A07F724C5AF63E3010013C569D5372A3362
```

## Revisão dos caminhos Android

- Entrada/abertura direta no Monitor pede SENSOR_LANDSCAPE. Configurações administrativas mantêm a preferência de orientação previamente salva. O pedido só é atribuído quando muda; brilho não chama esse método.
- API 30+: conteúdo fora do ajuste automático das barras (`setDecorFitsSystemWindows(false)`), WindowInsets para esconder barras e comportamento transitório por swipe. No Monitor, barras transitórias não acrescentam padding; somente recortes físicos reservam área segura. Nas APIs anteriores, flags LAYOUT_STABLE/LAYOUT_FULLSCREEN/LAYOUT_HIDE_NAVIGATION/IMMERSIVE_STICKY mantêm o layout imersivo. Android/fabricante pode impor restrições em janelas especiais.
- PlayerView mantém **RESIZE_MODE_FIT**, centralizando a imagem inteira e permitindo barras pretas. Nenhum caminho de Crop/Cover foi introduzido. A geometria modifica bounds, não o conteúdo do stream.
- Dimensões são as medidas reais da view. 1280 × 800 está nos testes/documentação, sem fixar a resolução ou a RAM do dispositivo no código.
- Proporção recebida de VideoSize atualiza o cálculo de 2/3 câmeras só quando muda. Quatro câmeras não recalculam a grade por mudança de proporção. Rotação/alteração de janela reutiliza as mesmas células/players; geometria fica em cache.
- Econômico mantém 1–2 s e reduz alvo de buffer codificado de 4 para **2 MiB** por player. Normal mantém 1–3 s/8 MiB. Não são limites totais de RAM nem medição de consumo no T810. Áudio continua desativado e seleção de codec prioriza hardware.
- Falha de decoder libera somente seu player, preserva posição/ordem e informa uso de H.264/substream ou layout menor. Retentativa inicial de decoder: pelo menos 5 s; consecutivas: 30 s. Rede/decoder continuam no mesmo coordenador de recuperação, sem watchdog duplicado.
- Superfícies não são reassociadas quando a visibilidade já é a mesma; URI/MediaItem são reutilizados em retries. Ampliação continua com o mesmo player, sem mudar automaticamente o stream.
- `updateBrightness` altera apenas brilho da janela e seu agendamento. Nenhuma chamada a recriar mosaic/player, orientação ou fullscreen. Toque bloqueado continua como camada transparente sobreposta, sem reservar rodapé.
- applicationId, assinatura, esquema, `cameras.enc`, Keystore `cam-lite-v1`, credenciais, nomes/IPs/ordem/layout, preferências e Home/início no Monitor permanecem. As versões anteriores seguem em `dist`.

## O que depende do tablet

O tablet ainda não está disponível; `adb devices` continua sem dispositivo conectado. Conforme escolha anterior do usuário, os testes reais serão feitos manualmente no aparelho. Não foi executado outro emulador nem alterado driver/sistema.

A ficha oficial da família T810 confirma referência de tela/T310 e variantes, mas **não comprova o Android/Go exato da unidade Q8 Lite comprada**. Confira Configurações → Sobre o tablet quando chegar. O código escolhe APIs pela versão real do Android em execução.

**Não se declara quatro câmeras H.264 simultâneas como validadas.** Quantidade de decodificadores, compatibilidade/falhas de codec, consumo de RAM/CPU, Wi-Fi e estabilidade 24/7 ainda precisam do aparelho e câmeras reais. Os testes JVM comprovam geometria e política de recuperação, não desempenho ou reprodução Android.

Fullscreen horizontal, barras transitórias, ausência de crop em vídeo ao vivo, brilho com vídeos, toque/duplo toque, rotação e instalação sobre versão anterior foram implementados/compilados/revisados, mas **não executados em Android neste ambiente**. Mesma assinatura/pacote/versionCode e testes de configuração sustentam compatibilidade de atualização; não equivalem a um teste real de instalação/Keystore no tablet.

## Teste de upgrade preparado, não executado

O runner Android foi compilado e o script teve sua sintaxe validada. Em emulador descartável:

```powershell
.\scripts\test-upgrade-emulator.ps1 -Serial emulator-5554
```

Agora usa 1.2 → 1.3 por padrão, com parâmetros PreviousVersion/NextVersion. Instala uma versão anterior, grava configuração fictícia criptografada de esquema 2 (incluindo tela/brilho/bloqueio), instala a nova por cima e verifica câmeras, credenciais, preferências, bytes do arquivo criptografado e pedido de orientação horizontal. Recusa dispositivo físico porque substitui dados por exemplos. Não foi executado aqui e não deve ser usado na instalação pessoal com dados importantes.

## Roteiro curto para o Q8 Lite T810

1. Confira modelo e versão do Android/Go. Instale a 1.3 por cima da anterior, sem desinstalar/limpar dados; confira credenciais, ordem/layout e preferências.
2. Selecione Econômico e comece com layout 1. Configure o substream **na câmera**: H.264, 640 × 360 ou 640 × 480, 8–10 FPS quando disponível.
3. Entre no Monitor: confira horizontal, fullscreen, imagem inteira/timestamps e desbloqueio por deslizamento. Se a janela útil for 1280 × 800, quatro células devem ocupar cerca de 640 × 400 cada.
4. Aumente gradualmente para 2, 3 e 4 somente conforme o aparelho permitir. Se houver limitação, reduza o layout; as câmeras funcionando não devem reiniciar quando apenas uma falhar.
5. Teste duplo toque, barras temporárias, perda/retorno de Wi-Fi e desconexão de uma câmera. Observe início/retorno gradual.
6. Confira que brilho programado altera apenas a luminosidade; mantenha bloqueio de toques e tela ligada conforme suas preferências. Testes prolongados e avaliação de consumo ficam para você no tablet.

Fontes oficiais: [Positivo T810](https://www.positivoempresas.com.br/wp-content/uploads/2022/04/POSITIVO_T810_Configuracoes_especificacoes.pdf), [modo imersivo Android](https://developer.android.com/develop/ui/views/layout/immersive), [insets e área da janela](https://developer.android.com/develop/ui/views/layout/insets).
