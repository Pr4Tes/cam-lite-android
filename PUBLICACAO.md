# Publicação preparada — aprovação pendente

Versão anterior 1.3, versionCode 4. Candidata 1.4.0, versionCode 5. Nada nesta entrega publica automaticamente uma release estável. O manifesto contém canal stable porque é o artefato preparado para a publicação futura; estar no Drive não torna o canal público operacional.

## Destino

Repositório público separado: `Pr4Tes/cam-lite-android-releases`. Fontes permanecem privadas em `Pr4Tes/cam-lite-android`. Na conferência desta preparação, o destino público retornou não encontrado; é preciso criá-lo vazio e público ou ajustar conscientemente a fonte incorporada, antes de publicar. O conector disponível não cria repositórios. Não tornar público o repositório de fontes.

Após os testes manuais e a aprovação explícita do usuário, criar release de produção `v1.4.0` no repositório de distribuição com estes três assets, preservando seus bytes:

- CamLite-Android-v1.4.0.apk
- version.json
- version.json.sig

Publicar como release estável, não prerelease, e definir como latest. Não usar builds experimentais como latest. O APK usa o certificado permanente original, embora o nome histórico do certificado seja Android Debug; o artefato atual foi compilado como release, sem flag debuggable.

## Conferência anterior à publicação

Verifique pacote `com.camlite.android`, código 5, Android mínimo 26, certificado conforme ASSINATURA.md e SHA-256 conforme SHA256SUMS.txt. Execute `scripts/verify_update.ps1` com -Directory apontando para a pasta dos três arquivos. O verificador autentica metadados antes do JSON, testa adulteração e compara tamanho/hash do APK.

Se a publicação atrasar, revise as datas: o manifesto expira 90 dias após ser assinado. Gere nova dupla com PowerShell 7, usando `scripts/sign_update.ps1 -Package <APK> -Output <pasta> -NotesFile <notas>`. A chave privada fica fora do projeto, no armazenamento privado de atualizações. O script exige correspondência com a chave pública incorporada e não publica arquivos. Nunca coloque chave privada, keystore, propriedades, tokens ou dados de câmeras nos assets.

Depois da aprovação/publicação, confira por HTTPS público, sem autenticação: o endpoint latest de version.json, sua assinatura e o APK indicado. Verifique novamente RSA, bytes, tamanho e hash, além do fluxo em um Android de teste. Nenhuma disponibilidade pública ou instalação Android foi comprovada nesta preparação.

## Próximas versões e recuperação

Incremente versionCode, preserve assinatura/pacote/armazenamento e revise compatibilidade. Compile, valide, gere metadados e peça a aprovação final sobre artefatos concretos. Para falha do servidor, restaure os assets corretos; não desative a validação criptográfica. Publicação antiga/expirada ou downgrade é recusado. Uma correção deve usar código maior. Não existe rollback automático de APK nem exportação da chave Android Keystore.
