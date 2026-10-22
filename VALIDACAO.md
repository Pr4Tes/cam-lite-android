# Validação 1.4.0 — 22/10/2026

Anterior: 1.3/code4. Nova: 1.4.0/code5. Pacote com.camlite.android, mínimo API26, alvo/compilação API35. Media3 1.6.1 mantido. Não há bibliotecas nativas no APK; utiliza os decodificadores do Android e aceita as ABIs declaradas no manifesto, sujeito à capacidade real do aparelho.

## Executado

- Gradle testDebugUnitTest, assembleRelease e lintRelease: BUILD SUCCESSFUL. 22 testes JVM, zero falhas/erros: geometria 4, recuperação 6, tela/migração 5, atualizações 7. Sem testes prolongados.
- Atualizações: versão nova/igual/downgrade, app/plataforma/Android/ABI, assinatura antes de JSON malformado, expiração, intervalo de 24h/relógio/reinício, notificação negada/Monitor/adiamento, origens HTTPS, hash/tamanho, cancelamento/interrupção e limpeza, identidade/certificado. São testes de lógica e streams locais, não instalação ou tráfego Android real.
- Lint: zero erros, 52 avisos, incluindo recursos de texto, padrões de views e verificações conservadoras. A compilação não constitui prova de comportamento físico.
- apksigner verificou o APK com o mesmo certificado SHA-256 permanente: 9e7287463942b2d3a9e836a6273811f8ba20dfe74ee35d9e86109385636d193d. A proteção verifyPermanentSigning foi mantida.
- aapt confirmou versão/code/pacote/API e ausência de application-debuggable. Release com redução de código; nenhuma chave nova de APK gerada.
- Script local verificou RSA de metadados, rejeição de adulteração, tamanho e hash do APK.

APK: CamLite-Android-v1.4.0.apk
Tamanho: 985426 bytes
SHA-256: 8574fa6a9c4fadc84f79c09d1f118b58788d43ac8cba7c9b08503486b466a9c5

## Revisão do código

O armazenamento de câmeras não foi alterado: cameras.enc, envelope AES-GCM, esquema 2, alias cam-lite-v1 e cópia anterior criptografada preservados. Câmeras, credenciais, ordem, layouts, brilho, bloqueio, perfil e preferências continuam no armazenamento existente. Novos dados de atualização usam área separada.

O listener de atualização apenas atualiza sua própria tela. Não reconstrói mosaico, players ou brilho. Monitor impede diálogos/notificações de atualização até a saída. A tarefa usa executor com uma thread que termina ociosa, sem serviço/wakelock. Tentativa persistida antes da rede inclui falhas; falha de persistência suspende tentativa automática até recuperação manual.

A publicação em cache é reautenticada. O download exige consulta nova, limite de bytes, SHA-256, compatibilidade e certificado instalado; instalação revalida o arquivo. Instalação oficial exige ação/confirmacão do usuário. Resultado depende do callback Android e da versão observada; não se afirma conclusão apenas por submeter a sessão.

## Pendente no celular

O usuário escolheu testar manualmente. Sem dispositivo Android disponível, não foram executados: abertura/telas, upgrade real/Keystore, autorização de notificações/origem externa, PackageInstaller/cancelamento, desbloqueio/toques, fullscreen/brilho com vídeos, streams RTSP, recuperação de Wi-Fi/câmera, consumo ou quatro decodificadores simultâneos. Runner de upgrade anterior permanece preparado; não foi executado nem deve ser usado no aparelho pessoal com dados importantes.

Use INSTALACAO-E-RECUPERACAO.md para o roteiro curto. Não desinstale nem limpe dados. Testes 24/7 ficam com o usuário.

## Distribuição

Fontes privadas e artefatos foram preparados para entrega. Endpoint incorporado: https://github.com/Pr4Tes/cam-lite-android-releases/releases/latest/download/version.json . Destino público ainda depende de disponibilidade e publicação aprovada. Nenhuma release estável foi publicada nesta etapa. PUBLICACAO.md descreve assets, conferência e aprovação final. O fluxo completo por HTTPS e em Android depende dessa publicação e de aparelho de teste.
