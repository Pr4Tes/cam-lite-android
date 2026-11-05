# Cam Lite Android 1.5.0

- Som de uma câmera por vez: escolha Som das câmeras na tela inicial ou Som de uma câmera no menu do Monitor (toque longo após desbloquear os toques). Escolha Sem som para desligar. Ajuste o volume de mídia com os botões do aparelho.
- A seleção é salva junto às configurações criptografadas, por identificador da câmera. Instalações anteriores iniciam sem som. Apenas câmeras do layout atual podem ser ouvidas.
- Foco de áudio Android: chamadas/outros aplicativos e desconexão de fone silenciam a reprodução sem pausar os vídeos. Para reativar, selecione a câmera novamente. Áudio não continua com o app em segundo plano.
- Faixa ausente/incompatível e erros de decoder de áudio são tratados sem deixar o vídeo preso em repetidas falhas de som. Ative áudio AAC no stream RTSP escolhido da câmera; AC3 depende do aparelho. Microfone, gravação e conversa de retorno não foram adicionados. G.711/G.726 não são garantidos por este backend.
- Buffer Normal: 300–800 ms; Econômico: 300–600 ms. Início: 100 ms; retomada: 250 ms. Mantidos RTSP TCP, FIT e recuperação individual.
- Recuperação de fila local acima de 4 s, persistente por 15 s após estabilização. Limitada a uma tentativa a cada 2 minutos por câmera. Não mede atraso entre captura e tela.
- Sincronizar imagens agora no menu do Monitor reabre as câmeras gradualmente. A ação interrompe a imagem por alguns instantes.
- Ajuda mostra a versão instalada corretamente. Mantida a verificação de atualizações sem cache da 1.4.2.

Instale por cima da versão atual, sem desinstalar nem limpar dados. Mesmo pacote e certificado originais. Buffers menores podem causar mais pausas em Wi-Fi instável.

Validação: 30 testes JVM aprovados, zero falhas/erros/ignorados, incluindo exclusividade do áudio, persistência/migração, recuperação/cooldown do backlog e descoberta real no GitHub. Testes de política não substituem reprodução Android real. Sem tablet/câmera conectado: áudio efetivo, codecs da câmera, foco real, rede e redução física de latência ainda dependem de conferência no aparelho. Não se promete ausência total de atraso ou estabilidade 24/7.
