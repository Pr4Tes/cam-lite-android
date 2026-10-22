# Instalação e teste manual — 1.4.0

1. Baixe CamLite-Android-v1.4.0.apk. Confira o SHA-256 em SHA256SUMS.txt se desejar verificar a transferência.
2. Abra pelo gerenciador de arquivos do celular, autorize essa origem somente se o Android solicitar e confirme a atualização. Android mínimo: 8.0.
3. Instale por cima da versão anterior. **Não desinstale nem limpe os dados.** O pacote e o certificado permanente foram preservados. Se houver mensagem de assinatura incompatível, cancele e mantenha a instalação atual.
4. Abra o app e confira câmeras, credenciais, ordem, layout, brilho, perfil de desempenho e preferências. O armazenamento criptografado e o alias Keystore anteriores foram mantidos; instalação real ainda precisa ser validada no aparelho.

## Roteiro curto no celular

- Comece com uma câmera, substream H.264 e RTSP/TCP. No cadastro, informe IP/host, porta 554, usuário/senha, canal e stream secundário; teste e salve. Intelbras/Dahua têm modelo próprio; URL personalizada fica na área avançada. Não compartilhe senhas ou URLs privadas ao relatar problemas.
- Entre no Monitor, confira imagem inteira, horizontal, fullscreen e tela ligada. Aumente para 2/3/4 conforme os decodificadores permitirem. No Q8 Lite T810 de 2 GB, comece no perfil Econômico, 640×360/480 e 8–10 FPS configurados na câmera.
- Confira bloqueio de toques e desbloqueio pela barra deslizante; depois, duplo toque para ampliar/voltar e gesto/menu de saída. Verifique brilho manual/programado sem reinício dos vídeos.
- Desconecte uma câmera e confira recuperação apenas dela; teste retorno do Wi-Fi e início gradual. Essas observações precisam de câmeras reais.
- Em Configurações → Atualizações, confira versão 1.4.0, preferências e Verificar agora. Enquanto a fonte pública não estiver publicada, uma falha de consulta é esperada. Negar notificações deve manter a tela interna disponível. Consultas não devem abrir diálogos sobre o Monitor nem reiniciar streams.
- Após existir uma versão posterior aprovada, teste download, cancelamento, permissão de origem externa e confirmação Android. A própria 1.4.0 não deve oferecer reinstalação como se fosse uma versão nova.

## Uso dedicado e recuperação

Ative Manter tela ligada e Iniciar diretamente no Modo Monitor. Home/Launcher é opcional, depende do Android/fabricante e não garante abertura automática em todos os aparelhos. Para uso contínuo, configure manualmente limite de carga de 80% se disponível, ventilação e brilho moderado; o aplicativo não controla carregamento.

Falha de rede/download: continue usando a versão instalada e tente manualmente depois. Arquivo inválido: descarte o download e obtenha novamente da fonte oficial, sem contornar validações. Falha/cancelamento do instalador: volte ao app; não limpe dados. Sem espaço: libere arquivos alheios ao app. Em problema com câmeras após upgrade, preserve os dados e relate versão, Android e mensagem sem credenciais.

O arquivo anterior de configuração é mantido criptografado internamente; não é um backup portátil, não inclui exportação de Keystore e não deve ser manipulado manualmente. Desinstalar pode tornar credenciais irrecuperáveis. Não há downgrade/rollback automático.
