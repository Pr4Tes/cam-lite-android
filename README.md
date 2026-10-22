# Cam Lite Android 1.4.0

Monitor Android nativo e local de até quatro câmeras IP RTSP. Kotlin, views Android e Media3 1.6.1, sem conta, nuvem, anúncios ou gravação. Android 8.0 (API 26) ou superior. O mesmo APK usa os decodificadores do sistema em ARM32 e ARM64; compatibilidade de vídeo depende do aparelho.

## Instalação

1. Copie `dist/1.4.0/CamLite-Android-v1.4.0.apk` para o celular.
2. Abra o arquivo pelo gerenciador de arquivos e permita instalar aplicativos dessa origem, quando solicitado pelo Android.
3. Instale e abra **Cam Lite Android**. É um APK release para validação manual, com o certificado permanente original.
4. Celular e câmeras devem ter acesso entre si pela rede local. Não é necessária permissão de câmera do celular, root ou acesso à nuvem.

Instale a 1.4.0 sobre a 1.0/1.1/1.2/1.3 desta entrega, sem desinstalar ou limpar dados. O pacote e o certificado são os mesmos. Câmeras, senhas, ordem e preferências são migradas automaticamente; brilho anterior continua manual e o perfil de desempenho existente permanece. Se o Android informar assinatura diferente, preserve a instalação atual e não remova seus dados.

## Novidades da 1.4.0

Configurações → Atualizações permite consultar versões, notas, progresso e resultados. Verificação automática e notificações começam ativadas, com tentativa no máximo a cada 24 horas enquanto o app está ativo. As escolhas ficam salvas; não há instalação automática. A consulta HTTPS pública não transmite dados de câmeras. No Monitor, avisos aguardam a saída e consultas não recriam players.

Metadados têm assinatura RSA incorporada à confiança do app; downloads verificam tamanho, SHA-256, identidade e certificado Android original. Instalação usa confirmação oficial Android após ação explícita. A fonte preparada é um repositório público separado, ainda pendente de disponibilidade/publicação aprovada. Sem publicação, a consulta pode falhar sem afetar o uso local.

Consulte [ATUALIZACOES.md](ATUALIZACOES.md), [INSTALACAO-E-RECUPERACAO.md](INSTALACAO-E-RECUPERACAO.md), [PUBLICACAO.md](PUBLICACAO.md), [ASSINATURA.md](ASSINATURA.md) e [VALIDACAO.md](VALIDACAO.md). A build e 22 testes de lógica passaram; instalação, desbloqueio, vídeos e preservação de dados em Android real serão testados manualmente pelo usuário. Não foram executados testes prolongados.

## Histórico: novidades da versão 1.3 — Positivo Q8 Lite T810

O Monitor agora solicita **horizontal** na entrada e na abertura direta. As configurações de orientação anteriores permanecem armazenadas e continuam aplicadas fora do Monitor. O sistema/fabricante pode ignorar pedidos de orientação em janelas múltiplas ou modos especiais; o layout usa sempre as dimensões efetivas da janela.

Fullscreen usa WindowInsets a partir da API 30, com barras temporárias sobre o conteúdo, e flags oficiais de layout estável/immersive sticky nas versões anteriores. Não há rodapé, nomes ou indicadores permanentes; o desbloqueio continua como sobreposição temporária. Apenas recortes físicos da tela reservam área segura, quando presentes. Gestos de navegação Android continuam disponíveis.

Quatro câmeras usam sempre uma grade **2 × 2**, sem espaçamento: em uma janela útil de **1280 × 800**, cada célula mede **640 × 400**. FIT preserva a imagem completa. Para 2/3 câmeras, o cálculo considera as proporções reais recebidas pelo player e compara distribuições simples, favorecendo primeiro a maior imagem mínima e depois a maior área total. Isso evita aumentar muito uma câmera à custa de outras minúsculas. A terceira câmera pode usar a linha inteira, em vez de uma célula estreita central. A distribuição pode mudar quando a proporção real do stream for conhecida; a ordem das câmeras permanece.

O cálculo fica em cache e só é refeito quando dimensões/proporções mudam. Não há cálculo de layout a cada frame. O ajuste de brilho continua independente de orientação/layout/players, e alternar mosaico/ampliação mantém as sessões. Atribuições redundantes de superfície foram eliminadas. A URL/MediaItem são reutilizados durante reconexões da mesma sessão.

No perfil **Econômico**, o alvo de buffer codificado caiu de 4 para **2 MiB por player**, mantendo 1–2 segundos. Normal permanece em 8 MiB/1–3 segundos. Esses alvos não são limites totais de RAM e não garantem redução medida de consumo no tablet: decoder/superfícies também usam memória. Nada muda automaticamente na câmera.

Se houver erro de decoder, somente a célula afetada libera seus recursos, mantém sua posição e mostra orientação de usar substream H.264 ou reduzir o layout. A primeira tentativa aguarda pelo menos 5 segundos; erros de decoder consecutivos aguardam 30 segundos. As demais câmeras continuam. Após reprodução estável, o comportamento normal de recuperação volta. Não há garantia de quatro decodificadores simultâneos.

### Configuração recomendada para o Q8 Lite T810

1. Quando o tablet chegar, confira **Configurações do Android → Sobre o tablet** e anote versão do Android/Go e modelo exato. Não foi possível verificar o aparelho neste ambiente.
2. Instale a v1.3 por cima da versão anterior, sem limpar dados. Para uso dedicado, ative Iniciar diretamente no Modo Monitor e Manter tela ligada. A opção Home permanece opcional, sujeita ao Android/fabricante.
3. Selecione **Econômico**. Configure **na câmera** substream H.264 de 640 × 360 ou 640 × 480, 8–10 FPS, conforme as opções reais do equipamento. Áudio já fica desativado e o transporte é TCP.
4. Comece com **layout 1** e uma câmera. Aumente para 2, 3 e 4 apenas conforme o desempenho/decodificadores observados. Em caso de erro, reduza o layout e mantenha os substreams leves.
5. Use brilho programado conforme desejado (sugestão existente: 07:00/40%, 19:00/10%) e mantenha o bloqueio de toques. Deslize a barra para acessar duplo toque/menu.
6. Confira timestamps/bordas inteiros, tela horizontal, barras transitórias, recuperação de uma câmera e retomada de Wi-Fi. Testes reais de desempenho/estabilidade 24/7 ficam para você quando o tablet chegar.

A ficha técnica oficial da **família T810** confirma T310, tela de 8 polegadas e 1280 × 800, com opções de memória/armazenamento e referência a Android 11. Ela não comprova a versão Android/Go da unidade Q8 Lite comprada nem a quantidade de decodificadores. O código não fixa resolução, RAM ou versão do aparelho. [Ficha técnica Positivo T810](https://www.positivoempresas.com.br/wp-content/uploads/2022/04/POSITIVO_T810_Configuracoes_especificacoes.pdf)

## Recursos preservados da versão 1.2

### Atualização preservando dados

A identidade permanece `com.camlite.android`, versionCode 5. O arquivo `cameras.enc`, o envelope AES-GCM e o alias Android Keystore `cam-lite-v1` são os mesmos. Configurações sem versão das entregas 1.0/1.1 são tratadas como esquema 1 e migradas para esquema 2, com validação. Câmeras/credenciais/URLs/ordem e preferências anteriores são mantidas. Novas opções recebem padrões; brilho programado começa desativado para respeitar o brilho anterior.

Antes de substituir o arquivo, o app grava um candidato criptografado, relê/descriptografa e valida seu conteúdo. Conserva a versão anterior em `cameras.enc.previous`, também criptografada, e usa gravação atômica com sincronização. Falhas de leitura/migração impedem sobrescrita por configuração vazia. Os arquivos auxiliares ficam somente nos dados privados do app, sem exportação de senhas.

Compatibilidade de assinatura foi verificada para os APKs entregues 1.0/1.1/1.2/1.3. Uma versão assinada com outra chave não pode atualizar a instalação existente. Sem acesso autorizado aos dados e à chave local do app antigo, não há migração automática segura das credenciais; mantenha a instalação e obtenha um APK com a assinatura original. Não há opção de exportar senhas em texto puro.

### Tela → Brilho

Em **Configurações**, role até **Tela → Brilho**. Escolha **Brilho manual** (incluindo Usar brilho do Android) ou **Brilho programado**. Configure horários HH:mm diferentes e percentuais entre 1 e 100. Sugestões padrão: dia às **07:00**, **40%**; noite às **19:00**, **10%**. Salve e entre no Monitor.

A faixa diurna vai do horário de dia até o horário de noite; a outra faixa usa brilho noturno. Horários invertidos também funcionam, atravessando meia-noite. Usa o relógio/fuso do celular; mudanças de hora/fuso e retorno ao primeiro plano recalculam o brilho. Uma única tarefa aguarda a próxima mudança, sem polling contínuo nem serviço. Transições de horário de verão também são consideradas.

Somente `screenBrightness` da janela é alterado. O brilho global do Android permanece intacto; ao sair do Monitor/segundo plano, a janela volta ao brilho normal. Alterações programadas não reiniciam players nem saem do fullscreen. O fabricante pode aplicar limites mínimos/máximos, economia de energia ou ajustes próprios; porcentagem não garante luminância idêntica em todos os celulares.

### Tela → Bloqueio de toques

**Ativar bloqueio de toques no Modo Monitor** vem ligado. Ao entrar/retornar ao Monitor, uma camada transparente bloqueia toques comuns, duplo toque e menus, mantendo câmera, rede, watchdog, brilho e tela ligada.

Toque na tela bloqueada para revelar, na parte inferior, **Deslize para desbloquear**. Arraste o controle **>>** desde a esquerda até o final direito e solte. Um toque simples, gesto parcial ou cancelado não desbloqueia. A barra desaparece após cinco segundos sem interação; não redimensiona o mosaico. Nenhuma animação pesada é usada.

Depois de desbloquear, duplo toque e toque longo voltam a funcionar. No menu do toque longo, selecione **Bloquear toques** para bloquear novamente. **Bloquear automaticamente após inatividade** vem ligado, com **30 segundos**; configure 5–3600 segundos ou desative para manter controles disponíveis durante ajustes. O tempo é renovado por interações, e o menu aberto suspende o bloqueio automático.

Voltar, Home e os controles do Android continuam disponíveis. Voltar sai do Monitor mesmo bloqueado. O bloqueio não se aplica a cadastro/configurações. Com leitor de tela, a camada oferece uma ação explícita **Desbloquear toques (acessibilidade)** por ação de pressionar e segurar; o toque comum continua apenas revelando a barra. Para desativar completamente, saia do Monitor e altere a opção em Configurações.

## Recursos mantidos da versão 1.1

- Recuperação de Wi-Fi/rede local por `ConnectivityManager.NetworkCallback`, aceitando redes sem internet. Ao perder a rede da câmera, o app cancela as tentativas pendentes, libera o player afetado e mostra **Aguardando conexão**. Uma rede nova aguarda cerca de 1,5 segundo para estabilizar; as câmeras afetadas retomam em intervalos de um segundo, na ordem original.
- Cada conexão RTSP é vinculada à rede escolhida. Redes com rota mais específica para o IP da câmera têm prioridade; depois vêm VPN, Ethernet e Wi-Fi, antes de celular. A câmera já funcionando em outra conexão válida é preservada. Mudanças de validação de internet não reiniciam o mosaico. Rede disponível não garante que a câmera ou sua rota estejam acessíveis: nesse caso vale o backoff individual.
- Dois toques em uma câmera ampliam a célula; dois toques novamente restauram o mesmo mosaico e ordem. FIT permanece ativo. Não há troca para stream principal. As outras sessões/player continuam existentes; suas superfícies de exibição são desacopladas enquanto ocultas, evitando composição desnecessária. Isso não promete eliminar decodificação ou tráfego das câmeras ocultas.
- O mesmo watchdog agora também detecta ausência prolongada de entrega de frames durante reprodução visível. Examina timestamps de entrega, não os pixels: uma cena estática com frames regulares não aciona recuperação. Falhas continuam isoladas por câmera, com cancelamento de tentativas antigas, remoção de listeners e backoff limitado a 30 segundos por tentativa.

## Desempenho: Normal e Econômico

Abra **Configurações → Desempenho**, selecione o perfil e toque em **Salvar**. A escolha é criptografada com as demais preferências e aplicada na próxima entrada no Monitor/prévia. Configurações da 1.0 usam Normal automaticamente.

| Perfil | Buffer de vídeo | Alvo de memória para buffer codificado por player | Stream recomendado na câmera |
| --- | --- | --- | --- |
| Normal (padrão) | 1–3 segundos | 8 MiB | Substream H.264, 12–15 FPS |
| Econômico | 1–2 segundos | 2 MiB | Substream H.264 de resolução menor, 8–10 FPS |

Esses valores são limites/alvos de buffer, não limite total de RAM do player. Decoder, superfícies e rede também consomem memória. Os dois perfis mantêm áudio desligado, preferência por hardware, FIT e a mesma recuperação individual. O app não altera automaticamente FPS/resolução internos da câmera, não descarta frames para fingir menor decodificação e respeita a URL/stream cadastrado. Configure um substream realmente mais leve no equipamento para reduzir trabalho de decodificação. Nenhuma função de proteção térmica foi adicionada.

Teste rápido da 1.1 no celular: selecione os dois perfis e reabra para conferir persistência; amplie/retorne por duplo toque; desligue o Wi-Fi e observe **Aguardando conexão**, depois ligue-o e confira retomada gradual. Desconecte apenas uma câmera para verificar que as demais continuam. Use também uma rede Wi-Fi sem internet. Os testes de estabilidade/consumo 24/7 ficam para o aparelho real. Veja `CHANGELOG.md` e `VALIDACAO.md`.

## Cadastro e teste

Toque em **Adicionar câmera**. Informe nome, IP/hostname, porta (554), usuário, senha e canal (1). O stream secundário vem ativado. O caminho padrão é:

```
rtsp://IP:554/cam/realmonitor?channel=1&subtype=1
```

Esse caminho é comum em Intelbras/Dahua, mas depende do modelo. Em **Avançado**, use uma URL RTSP personalizada para outros equipamentos. Usuário e senha informados nos campos substituem as credenciais da URL; deixe o usuário vazio para utilizar as credenciais que já estão na URL. Digite senha normalmente nos campos: os caracteres especiais são codificados pelo app. Se inserir credenciais diretamente na URL avançada, faça a codificação percentual (`@` como `%40`, por exemplo). O parser de autenticação do Media3 não suporta dois-pontos no nome de usuário; a senha aceita dois-pontos e outros caracteres especiais.

Use **Testar câmera** para visualizar o vídeo antes de salvar. O teste é uma prévia local, não altera configurações da câmera. **Salvar** persiste a configuração; **Editar**, **Remover**, **↑** e **↓** gerenciam a lista. Mesmo IP/porta/canal não pode ser cadastrado duas vezes silenciosamente. URLs avançadas sem parâmetro `channel` usam o canal informado para detectar duplicatas.

## Monitor e layouts

Em **Configurações**, selecione 1, 2, 3 ou 4 câmeras. O layout usa as primeiras câmeras na ordem cadastrada. Posições sem câmera ficam pretas. O Monitor pede orientação horizontal. Duas câmeras usam colunas ajustáveis conforme as proporções; três escolhem uma distribuição que amplia as imagens completas sem deixar uma delas desnecessariamente pequena (para streams 16:9, duas acima e uma centralizada na linha inteira abaixo). Quatro sempre usam 2 × 2 sem separações. A opção de orientação anterior permanece salva e vale para as telas administrativas. Cada vídeo usa FIT, sem corte ou distorção, com barras pretas quando necessário.

Toque em **Entrar no Modo Monitor**. O fullscreen oculta controles e barras do sistema, conforme permitido pelo Android. Desbloqueie a camada de toques por deslizamento antes de usar o duplo toque para ampliar/retornar. Mantenha um toque pressionado por cerca de um segundo em uma célula para abrir o menu. O botão/gesto Voltar sai do Monitor; o menu também permite fechar o app. Barras podem reaparecer temporariamente com gestos do Android.

**Manter tela ligada** vem ativado e usa `FLAG_KEEP_SCREEN_ON` enquanto o Monitor está visível. O brilho configurado afeta somente a janela do Monitor; **Usar brilho do Android** restaura o brilho normal. **Iniciar diretamente no Modo Monitor** aplica isso na próxima abertura, sem interação manual. Ao ir para segundo plano ou fechar, os players são liberados; no retorno são iniciados novamente, em intervalos de um segundo.

## Inicialização após reinício

Para um celular dedicado, ative **Disponibilizar como Home/Launcher**, salve e abra a escolha de app de início do Android. Selecione **Cam Lite Monitor** como Home padrão. O app entra no Monitor quando aberto como Home. Para desfazer, selecione o launcher original nas configurações do Android antes de desativar a opção Home.

Não há tentativa de iniciar uma Activity por um receiver de boot em segundo plano. Versões recentes do Android restringem esse comportamento. O caminho oficial implementado é a seleção de Home padrão, sem root, permissões especiais ou kiosk corporativo. Pode ser necessário desbloquear o aparelho após o boot; fabricante, política do dispositivo e configuração de Home podem impedir abertura automática. Não há garantia universal. A opção **Iniciar diretamente no Modo Monitor** funciona quando o app for aberto, mesmo sem ser Home.

## Reprodução e recuperação

- RTSP/RTP sobre TCP, autenticação Basic/Digest pelo Media3, H.264 com SPS/PPS no SDP.
- Áudio desativado, buffers conforme perfil, preferência pelos codecs acelerados por hardware do sistema.
- Inicialização escalonada em 0/1/2/3 segundos; cada posição tem seu próprio player.
- Erro do player, fim inesperado, buffering prolongado e ausência de entrega de frames por 25 segundos durante reprodução visível acionam reconexão somente da célula afetada.
- Intervalos de reconexão: 1, 2, 5, 10 e 30 segundos; voltam ao início após reprodução estável por 30 segundos.
- Timeout de transporte de 12 segundos e watchdog de buffering de 25 segundos. Nenhuma comparação de pixels: uma cena estática não é tratada como falha.
- Falhas de codec exibem orientação para usar H.264 e menos câmeras, preservando as outras células.

Não é possível detectar todo defeito de firmware que continue enviando RTP válido, mas repita conteúdo antigo. Também não há garantia de quatro decodificadores simultâneos em todos os celulares. Para aparelhos antigos, configure manualmente substreams H.264 em aproximadamente 640 × 360 ou 640 × 480, 10–15 FPS; o app não modifica a câmera.

## Credenciais e funcionamento prolongado

Configuração inteira protegida com AES-GCM e chave não exportável no Android Keystore, arquivo escrito atomicamente e backup Android desativado. Não há senhas em arquivos de preferências ou no código. Logs da biblioteca Media3 estão desativados para evitar URLs com credenciais em mensagens de erro. Janelas com credenciais e vídeo usam `FLAG_SECURE` (o Android pode bloquear capturas/gravação da tela). A proteção é de armazenamento local: RTSP comum não criptografa o tráfego da rede; use uma rede confiável.

Se a leitura/migração falhar, o app preserva os arquivos e impede sobrescrita silenciosa. Não desinstale nem limpe os dados. Verifique a versão instalada e reabra. Perder a chave do Android Keystore torna as credenciais existentes irrecuperáveis; uma cópia do arquivo criptografado isoladamente não substitui essa chave.

Para uso prolongado, diminua o brilho, mantenha ventilação e configure manualmente o limite de carga de 80% ou equivalente, se disponível. O app não controla carregamento. Tela ligada não elimina riscos de aquecimento ou desgaste da tela. Avalie a temperatura e estabilidade no seu celular.

## Compilar

Requisitos: JDK 17, Android SDK com plataforma 35 e build tools 35.0.0. Gradle Wrapper 8.11.1, Android Gradle Plugin 8.9.2 e Kotlin 2.1.20. Configure `ANDROID_HOME` ou `sdk.dir` em um `local.properties` local.

```powershell
.\gradlew.bat testDebugUnitTest assembleRelease lintRelease
```

Saída: `app/build/outputs/apk/release/app-release.apk`. A build exige a chave original e propriedades de assinatura externas, descritas em `ASSINATURA.md`. O certificado é verificado antes da compilação: chave ausente/diferente interrompe a build. Não é gerada uma nova chave automaticamente. Nenhuma chave privada ou senha acompanha o código. Este APK mantém a assinatura de debug original das primeiras entregas; mudar para outra assinatura impediria atualização direta dessas instalações.

## Testes no celular

1. Abra, cadastre uma câmera e use a prévia. Verifique autenticação e imagem H.264 completa, incluindo timestamp/bordas.
2. Feche e reabra; confirme persistência. Cadastre até quatro câmeras e tente uma duplicata.
3. Teste os quatro layouts, orientação e ordem. Confirme áudio ausente, barras pretas e zero corte.
4. Entre no Monitor, toque para revelar a barra e deslize completamente para desbloquear. Verifique toque longo/Voltar, brilho e que a tela não apaga por inatividade.
5. Observe a entrada progressiva dos vídeos. Desconecte uma câmera: as demais devem continuar; reconecte-a e observe a recuperação.
6. Vá ao segundo plano e retorne; confirme retomada. Ative início no Monitor e reabra.
7. Opcionalmente selecione o app como Home e reinicie; confira comportamento após desbloqueio. Restaure o launcher original ao terminar.
8. Depois das verificações rápidas, faça seu teste 24/7 avaliando temperatura, estabilidade e reconexões no aparelho real.

Consulte `VALIDACAO.md` para separar o que foi verificado neste ambiente do que depende de celular/câmeras reais.

Referências oficiais: [RTSP e autenticação do Media3](https://developer.android.com/media/media3/exoplayer/rtsp), [restrições de abertura em segundo plano](https://developer.android.com/guide/components/activities/secure-bal), [tela ligada](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on).



