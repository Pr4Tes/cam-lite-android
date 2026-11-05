# Cam Lite Android 1.5.1 — compatibilidade do atualizador

Corrige datas dos metadados públicos: UTC com Z e segundos inteiros, compatível com Instant.parse do Android antigo usado pelas versões já instaladas. Manifestos anteriores usavam offset +00:00, aceito no Java17 dos testes mas rejeitado pelo InstantPrinterParser antigo que exige Z. Essa diferença produz o erro Campos dos metadados inválidos da foto enviada pelo usuário.

O app passa a ler datas usando ISO_OFFSET_DATE_TIME/OffsetDateTime e informa erro específico para data malformada. Mantidas assinatura obrigatória, validade, hash, proteção contra downgrade, áudio de uma câmera e ajustes de atraso da 1.5.0. A nova publicação pode ser detectada pela 1.4.1 sem instalar antes esta correção, pois o ajuste também está no manifesto do servidor.

31 testes JVM passaram. O formato legado foi conferido no código oficial do Android8 InstantPrinterParser (appendLiteral Z). Emulador local não concluiu inicialização: não houve validação nativa nem confirmação no tablet. Teste de instrumentação de compatibilidade foi adicionado e compilado; sua execução permanece pendente.

Instale por cima, sem desinstalar nem limpar dados. Para som, escolha uma câmera e ative AAC no stream.
