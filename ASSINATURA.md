# Assinatura permanente e atualização

- Application ID: `com.camlite.android`.
- versionCode: 1.0 = 1, 1.1 = 2, 1.2 = 3, 1.3 = 4, 1.4.0 = 5. Futuras entregas devem incrementar esse valor.
- Certificado SHA-256 esperado: `9e7287463942b2d3a9e836a6273811f8ba20dfe74ee35d9e86109385636d193d`.
- A chave original foi preservada em armazenamento privado fora do workspace. É uma cópia da mesma chave utilizada na 1.0/1.1, não uma chave nova. O keystore original do perfil Android também permanece intacto.
- Arquivo externo padrão de propriedades: `%USERPROFILE%/.codex/private/cam-lite/signing.properties`. Pode ser informado outro caminho por `-PcamLiteSigningProperties=C:/caminho/externo/signing.properties`.

O arquivo externo contém as propriedades `storeFile`, `keyAlias`, `storePassword` e `keyPassword`, preenchidas com os dados da chave original. Não há valores de senha nem chaves privadas no projeto/ZIP. Não adicione esse arquivo ao repositório. Faça backup privado do keystore original e dessas propriedades; a perda da chave impede produzir novas atualizações compatíveis.

`verifyPermanentSigning` valida a presença do arquivo e o certificado antes de `preBuild`. Chave ausente ou certificado diferente interrompe a build. Não existe fallback que gere outra chave. A build 1.4.0 é release, sem flag debuggable, e conserva o certificado histórico de debug original; uma migração para certificado diferente não atualizará diretamente instalações existentes.

No celular, instale o APK novo por cima do anterior, sem desinstalar ou limpar dados. A atualização mantém dados privados e a chave Android Keystore do app. Não faça downgrade. Os identificadores `cameras.enc` e `cam-lite-v1` devem permanecer nas próximas versões; o esquema de dados é versionado separadamente dentro do conteúdo criptografado.

Se a instalação anterior tiver outro certificado, o Android recusará a atualização. Não contorne essa proteção nem desinstale antes de considerar os dados: o Keystore local normalmente é removido junto com o app. O projeto não oferece exportação de senhas em texto puro; sem a cooperação do aplicativo antigo e sua chave local, não há migração automática segura dessas credenciais.

Referência: [assinatura e atualizações no Android](https://developer.android.com/studio/publish/app-signing).


## Assinatura dos metadados

A publicação usa RSA-3072/SHA-256 separada da assinatura Android. Somente a chave pública de metadados é distribuída em app/src/main/res/raw/update_public_key.pem. A chave privada de metadados permanece fora do projeto e dos pacotes. Preserve backups privados de ambas as chaves: uma não substitui a outra. Consulte PUBLICACAO.md e scripts/sign_update.ps1.
