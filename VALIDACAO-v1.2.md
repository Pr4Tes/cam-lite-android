# Validação da entrega 1.2 — 12/10/2026

## Verificado neste ambiente

- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: **BUILD SUCCESSFUL**.
- Dez testes JVM passaram, sem falhas/erros: cinco do coordenador de recuperação 1.1 e cinco de migração/tela 1.2. Executados em menos de um segundo, com horários/agendamento simulados e dados fictícios.
- Testes novos: migração de configuração sem esquema preserva câmeras, IDs, ordem, IP/hostname, porta, canal, usuários, senhas fictícias, URL personalizada, stream e todas as preferências antigas; campos novos fazem round-trip; esquema futuro/inválido e mais de quatro câmeras são rejeitados; limites exatos de 07:00/19:00, meia-noite e faixas invertidas; próximo horário/fuso/horário de verão; toque simples, parcial, invertido/cancelado não desbloqueia, enquanto arrasto completo desbloqueia e o estado pode ser bloqueado novamente.
- Lint: **0 erros, 22 avisos**. Avisos de versões de dependências, textos em português, acessibilidade de views/gestos e atributo de Voltar utilizado somente a partir da API 33. Nenhum erro de compatibilidade mínima.
- APK final `dist/CamLite-Android-v1.2.apk`: pacote `com.camlite.android`, versionCode **3**, versão **1.2**, mínimo API **26**, alvo API **35**, **4.782.298 bytes** (~4,56 MiB).
- `apksigner verify`: assinatura **v2 válida**. Certificado SHA-256 **igual** aos APKs 1.0/1.1: `9e7287463942b2d3a9e836a6273811f8ba20dfe74ee35d9e86109385636d193d`.
- Chave original preservada fora do projeto e configuração de assinatura externa. `verifyPermanentSigning` executado antes da build. Teste negativo com caminho de propriedades ausente falhou corretamente nessa tarefa em menos de um segundo; não houve geração de chave nova. Não foi usado outro certificado para assinar.
- O teste Android de upgrade e o teste Smoke foram **compilados**, sem execução em dispositivo. O teste de upgrade é dividido em gravação de uma configuração fictícia criptografada na 1.1 e verificação após instalação da 1.2, usando o Keystore real do emulador.
- Dependências de execução continuam as mesmas três bibliotecas Media3. JUnit/JSON adicionais são somente para testes. Sem serviços ou threads novos para brilho/bloqueio.

SHA-256 do APK 1.2:

```
53A2F4B84E67E547244B2B839FC0C4CE6A31FAF66E9C3D07518BAE7B719931F4
```

## Evidência da implementação

- Arquivo `cameras.enc`, alias `cam-lite-v1`, AES-GCM e envelope de criptografia byte 1 permanecem. O esquema do JSON passa de 1 (implícito nas versões antigas) para 2. Leitura de versões futuras/desconhecidas falha sem substituição por configuração vazia.
- Migração/gravação valida configuração, grava candidato criptografado com fsync, relê/descriptografa/confere, preserva e confere cópia anterior criptografada, depois grava atomicamente e verifica o destino. Em falha após a troca, tenta restaurar o original; a cópia anterior permanece privada. A chave ausente em leitura não é silenciosamente regenerada.
- Brilho antigo permanece manual; agendamento começa desativado. Novas sugestões: 07:00/40% e 19:00/10%. Só `WindowManager.LayoutParams.screenBrightness` é alterado. O método de mudança de brilho não chama criação/reconexão de players nem altera o fullscreen.
- Há uma tarefa para a próxima transição, recalculada após eventos de relógio/fuso e retorno ao primeiro plano; removida no segundo plano. Não há relógio consultado continuamente nem serviço de brilho.
- Camada transparente do Monitor intercepta toques. Deslizamento deve começar no controle esquerdo e atingir o final; barra sobreposta não altera dimensões do mosaico. Toque simples revela a barra, sem desbloqueio. Menu pode bloquear novamente; inatividade usa uma tarefa única renovável e é suspensa com menu aberto. Fora do Monitor a camada é removida.
- Voltar/Home e controles do Android permanecem disponíveis. A ação de acessibilidade oferece alternativa explícita de desbloqueio. Câmeras, rede, watchdog e brilho continuam independentes da camada.
- Limite de quatro câmeras, RTSP TCP/FIT, credenciais, layout/ordem, orientação, perfis, tela ligada, fullscreen, rede e recuperação individual permanecem no código existente.

## O que não foi executado

Os APKs anteriores e a chave estão disponíveis, mas **não há celular/emulador conectado** (`adb devices` vazio). O emulador da primeira entrega não inicializava sem driver de aceleração; não foi tentado novamente nem houve alteração de drivers/sistema.

Portanto, **não foi realizada a instalação 1.1 → 1.2 em Android** e não se declara upgrade real como testado. Certificado/pacote/versionCode e migração de JSON foram verificados; isso não substitui validar o ciclo de instalação, os arquivos privados e o Keystore no aparelho.

Também ficam não validados em execução: aparência da barra, deslizamento físico/retorno dos controles e bloqueio automático, leitor de tela, aplicação de luminância pelo fabricante, mudança de brilho com vídeo ao vivo, rede/câmeras, decoder, fullscreen, tela ligada, rotação e estabilidade 24/7. O teste de gesto JVM valida a lógica do arrasto, não a UI Android. A gravação atômica/rollback com Keystore real foi implementada e compilada, mas não sofreu injeção de falha em dispositivo.

Não houve teste prolongado, benchmark, centenas de gestos/instalações ou repetição de builds sem mudança/checagem correspondente.

## Teste de upgrade preparado — não executado

Em **emulador descartável**, compile `assembleDebugAndroidTest`, tenha os APKs 1.1/1.2 em `dist` e execute:

```powershell
.\scripts\test-upgrade-emulator.ps1 -Serial emulator-5554
```

O script recusa dispositivo físico, instala a 1.1, grava dados fictícios criptografados, instala a 1.2 com `-r` e verifica esquema, todas as câmeras/credenciais/preferências e cópia anterior criptografada. Ele substitui configurações; não use em instalação com dados que deseja manter. Um único ciclo é suficiente. O script confirma o resultado da instrumentação, além do status do adb.

## Roteiro curto no celular do usuário

1. Instale `CamLite-Android-v1.2.apk` **por cima**, sem desinstalar/limpar dados. Confira câmeras, senhas, ordem/layout, orientação, início no Monitor, brilho anterior e perfil.
2. Em Configurações → Tela → Brilho, selecione Brilho programado e configure dia/noite. Para uma checagem curta, use horários próximos ao relógio do celular; não precisa esperar até 19:00. Confira que só o brilho muda e os vídeos continuam.
3. Entre no Monitor bloqueado: um toque/duplo toque não deve ampliar ou abrir menu. Toque para revelar a barra e arraste desde a esquerda até o final direito. Confira que duplo toque/toque longo voltam a funcionar.
4. No menu, use Bloquear toques. Desbloqueie e aguarde o tempo de inatividade escolhido (30 s padrão). Desative bloqueio automático para manter ajustes liberados.
5. Use Voltar mesmo bloqueado e confira que as telas de cadastro/configuração permanecem livres. Verifique brilho do Android após sair do app. Faça os testes prolongados somente quando desejar no aparelho real.

Referências: [assinatura Android](https://developer.android.com/studio/publish/app-signing), [brilho por janela](https://developer.android.com/reference/android/view/WindowManager.LayoutParams).
