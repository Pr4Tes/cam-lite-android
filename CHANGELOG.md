# Changelog

## 1.3 — 12/10/2026

- Monitor pede horizontal na entrada/abertura direta; preferência de orientação anterior preservada nas telas administrativas.
- WindowInsets com layout que não reserva barras transitórias na API 30+; modo estável/imersivo mantido nas versões antigas, com área segura para recortes físicos. Sem margens/rodapés de UI no Monitor.
- Grade de quatro fixa em 2 × 2 sem espaçamento (640 × 400 em janela 1280 × 800). Geometria responsiva de 2/3 câmeras usa proporções reais, preserva FIT e amplia a terceira linha quando útil, sem alterar players. Cálculo em cache, fora do processamento de frames.
- Econômico: alvo de buffer codificado 2 MiB por player, mantendo 1–2 segundos. Normal disponível e preferência existente preservada.
- Erros de decoder isolados com liberação e tentativas mais espaçadas (5 s inicialmente, 30 s em falhas consecutivas), sem interromper outras câmeras. Sem promessa de quatro decodificadores no tablet.
- URL/MediaItem reutilizados em reconexões; evita reassociar a mesma superfície desnecessariamente. Sem novas dependências, serviços ou threads.
- Pacote/certificado/esquema de configuração preservados; versionCode 4. Guia para Q8 Lite T810, sem assumir Android/Go exato da unidade.
- Compilação, assinatura, lint e 15 testes de lógica passaram. Tablet/câmeras/upgrade real não executados; a validação real fica com o usuário, conforme combinado.

## 1.2 — 12/10/2026

- Mesmo pacote/assinatura e versionCode 3. Chave original preservada fora do projeto; build verifica o certificado e falha se a assinatura estiver ausente/diferente, sem gerar substituta.
- Esquema 2 de configuração; leitura das configurações 1.0/1.1, validação e migração preservando credenciais, câmeras/ordem e preferências. Mesmo alias Keystore e envelope criptografado. Candidato durável verificado antes da troca e cópia anterior criptografada para recuperação de falhas.
- Brilho manual ou programado por dia/noite, horários e percentuais configuráveis, agendamento de próxima transição, ajuste após mudanças de relógio/fuso e retorno ao primeiro plano. Só altera brilho da janela, sem recriar players.
- Bloqueio de toques por camada do Monitor, barra temporária inferior e deslizamento completo para desbloquear. Menu permite bloquear novamente; opção de bloqueio automático por inatividade (30 s padrão), desativável. Navegação Android e ação alternativa de acessibilidade preservadas.
- Nenhuma biblioteca nova de execução, serviço permanente ou thread para essas funções. JSON adicionado somente aos testes JVM. Todos os recursos da 1.1 preservados.
- Compilação, lint, assinatura e testes curtos de lógica executados. Instalação sobre versão anterior e gestos reais em dispositivo não executados: sem celular/emulador conectado.

## 1.1 — 12/10/2026

- Monitoramento oficial de redes sem polling, sem exigir internet validada e sem novos serviços.
- Sessões RTSP vinculadas à rede selecionada; perda de rede cancela retries e exibe Aguardando conexão. Redes recuperadas estabilizam por 1,5 segundo e retomam as câmeras gradualmente.
- Conexões ainda válidas preservadas em alterações de conectividade.
- Duplo toque para ampliar/retornar, reutilizando players e mantendo layout, ordem e FIT. Superfícies ocultas desacopladas; sem mudança automática de stream.
- Recuperação individual unificada: tokens invalidam trabalhos antigos, listeners são removidos e só o player afetado é liberado/recriado. Watchdog inclui ausência de entrega de frames, sem comparar imagens estáticas.
- Perfis Normal (padrão) e Econômico persistidos no armazenamento criptografado, com diferentes limites de buffer/memória e orientações para configurar substream/FPS na câmera.
- Configurações da 1.0 compatíveis. Mesma identificação de pacote e assinatura de debug desta máquina; versionCode 2.
- Mantidas funções da 1.0 e limite de quatro câmeras. Sem proteção térmica, telemetria ou dependências novas no APK. JUnit adicionado somente aos testes JVM.

## 1.0 — 11/10/2026

- Primeira entrega Kotlin/Media3: até quatro câmeras RTSP TCP, layouts 1/2/3/4, FIT, cadastro/ordenação, Keystore, Monitor imersivo, tela ligada/brilho, início gradual, watchdog individual e Home opcional.
- APK de debug compilado e assinatura verificada; execução em aparelho/câmeras reais ficou para o usuário.
