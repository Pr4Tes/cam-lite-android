# Atualizações — Cam Lite Android 1.4.0

Em Configurações → Atualizações, consulte versão instalada/disponível, última tentativa, resultado, notas, progresso e situação observável da instalação. Verificação automática e notificações começam ativadas, canal estável. As escolhas ficam salvas. Verificar agora é manual; Mais tarde evita repetir o aviso daquela versão.

O aplicativo ativo tenta consultar no máximo uma vez a cada 24 horas, contando falhas. Usa horário monotônico na mesma inicialização e horário persistido entre reinicializações. Não há serviço permanente, wakelock ou consulta frequente. Uma consulta não recria players nem modifica layout, brilho ou bloqueio. No Monitor, diálogos e avisos de atualização aguardam sua saída. Sem rede, a versão atual continua utilizável.

O monitoramento e as credenciais continuam locais. Apenas a consulta de publicações e o download solicitado usam HTTPS público, sem credenciais de câmeras ou token privado. Notificações usam a permissão do Android quando aplicável; a tela interna funciona mesmo com notificações negadas.

## Instalação solicitada pelo usuário

Atualizar agora consulta novamente a publicação, confere compatibilidade/espaço e baixa um candidato temporário com progresso. Valida manifesto, tamanho, SHA-256, pacote, versão, Android, arquitetura e certificado. Arquivos incompletos são apagados. Nenhum APK é instalado só por uma consulta ou notificação.

Fora do Monitor, o usuário confirma que a instalação poderá encerrar o aplicativo e interromper os vídeos. Se necessário, permite a origem externa na configuração oficial e retorna à tela para continuar: o arquivo é revalidado. A ação Continuar instalação Android abre a confirmação oficial do sistema. Não há instalação silenciosa, root ou acessibilidade.

Cancelamento/falha mantém os dados. O resultado é mostrado quando o Android o fornece; sucesso só é reconhecido após observar a versão instalada. O sistema pode encerrar o processo durante a atualização.

## Protocolo e confiança

Adaptação Android do protocolo schema 1 compartilhado em `C:\Codex projetos\yt-dlp\UPDATE_SYSTEM_SPEC.md`, consultado sem alteração. `version.json.sig` contém Base64 de RSA PKCS#1 v1.5/SHA-256 dos bytes UTF-8 exatos de `version.json`. Chave RSA de 3072 bits; chave pública incorporada em `app/src/main/res/raw/update_public_key.pem`, identificador `prates-updates-2026`. A assinatura é verificada antes de interpretar JSON. Alterar espaços ou finais de linha invalida a assinatura.

Campos: schema, app_id, version, version_code, channel, os, arch, architectures, package_type, min_os_build, min_sdk, url, size, sha256, published_at, expires_at, notes, mandatory e key_id. Aceita somente este app, Android, stable, APK, versão compatível e não inferior à instalada/maior publicação já observada; validade máxima de 90 dias. APK máximo de 64 MiB. Relógio incorreto pode impedir validação; corrija data/hora do aparelho.

HTTPS limita redirecionamentos aos hosts oficiais GitHub e de seus assets; URL inicial do APK pertence ao repositório de distribuição e a uma release. Sem comandos/scripts remotos. A chave de metadados autentica a publicação; o certificado permanente Android autentica o APK. São verificações distintas. Não existe rotação de confiança por chave recebida do servidor.

## Fonte preparada

Metadados: https://github.com/Pr4Tes/cam-lite-android-releases/releases/latest/download/version.json

Fontes privadas: https://github.com/Pr4Tes/cam-lite-android

O repositório público separado ainda precisa estar disponível e receber a publicação aprovada. Até isso acontecer, consultas podem falhar sem afetar as câmeras. Consulte PUBLICACAO.md. O APK é fornecido para teste manual; o fluxo Android real não foi executado neste ambiente.
