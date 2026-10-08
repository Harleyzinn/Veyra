# Veyra Life

Sua vida, em um só lugar. Android nativo em Kotlin/Jetpack Compose e aplicativo Windows integrado ao mesmo Firebase e à mesma conta Google.

## Windows + celular

Baixe o [instalador Veyra Life Desktop 4.1.0](https://github.com/Harleyzinn/Veyra/releases/tag/desktop-v4.1.0) e o [APK Android 2.2.0](https://github.com/Harleyzinn/Veyra/releases/tag/v2.2.0). Central personalizável, financeiro completo, tarefas/Kanban, notas Markdown, calendário, hábitos, foco, captura global, bandeja e widgets. Cache desktop criptografado e fila offline com conflitos preservados. [Instalação, uso e desenvolvimento](docs/DESKTOP.md) · [Arquitetura desktop](docs/DESKTOP-ARCHITECTURE.md) · [Glass Panels](docs/DESKTOP-GLASS.md) · [Validação 4.1.0](docs/VALIDATION-DESKTOP-4.1.0.md).

O Android 2.2 recebe mudanças remotas automaticamente enquanto o app está ativo e registra presença dos dispositivos. Nenhum módulo ou banco Android foi reorganizado. O instalador Windows não tem certificado Authenticode comercial; o atualizador usa assinatura Ed25519 e SHA-256.

## Evolução desktop 4.0

Home acionável, galeria de widgets, captura local revisável, Inbox, Spotlight, projetos integrados, planejador, backlinks/editor visual de notas, rascunhos protegidos, calendário financeiro, assinaturas/relatórios/CSV, automações, modelos e diagnóstico seguro. [Guia e limites](docs/PLATFORM-4.0.md) · [Matriz dos requisitos](docs/ROADMAP.md). O Android permanece 2.2.0; os dois compartilham os mesmos dados Firebase.

## APK e instalação

Baixe pela [release oficial](https://github.com/Harleyzinn/Veyra/releases/latest). O APK release assinado local fica em `dist/Veyra-2.2.0.apk`. Requer Android 8.0 ou superior e usa o pacote `app.veyra.life`. A assinatura original foi preservada para permitir atualizar versões anteriores sem reinstalar. Faça backup antes de atualizar. Veja [como instalar e usar](docs/INSTALAR.md).

## Novidades da versão 2.1

Pagamento parcial de faturas com data e histórico, simulador de caixa com reserva mínima e margem diária adicional, e conferência de possíveis duplicidades e registros sem categoria. Cálculos exatos, privacidade e dados existentes preservados. Veja [detalhes](docs/RELEASE-2.1.0.md) e [guia financeiro](docs/FINANCE-GUIDE.md).

## Novidades da versão 2.0

Central financeira com captura rápida e modo avançado, dinheiro em unidades mínimas exatas, saldo realizado separado da previsão, recorrências com exceções e escopos de edição, faturas por fechamento, parcelas, transferências, contas a pagar/receber, orçamentos, metas, reserva, quitação de dívidas, patrimônio, relatórios CSV/PDF, gráficos interativos, histórico e lixeira. Os demais módulos permanecem disponíveis.

Na versão 2.0.1, Firebase Auth por e-mail e Google e Firestore estão conectados ao projeto real do Veyra. Plano gratuito sem cobrança, regras privadas implantadas e App Check registrado. Registros sincronizam com fila offline e conflitos; anexos permanecem no aparelho original. A configuração administrativa e os testes no Firebase real foram concluídos; Google Login e Play Integrity ainda precisam de validação no celular. Leia o [setup](docs/FIREBASE-SETUP.md), [detalhes da atualização](docs/RELEASE-2.0.1.md), [guia financeiro](docs/FINANCE-GUIDE.md) e [validação](docs/VALIDATION-2.0.1.md).

## Novidades da versão 1.2

Atualizador via GitHub com download automático em rede sem cobrança por uso, validação de assinatura/hash e confirmação pelo Android. Desenho e galeria PNG, leitor de códigos em imagens, sorteios/equipes/dados, três jogos offline e relógio mundial. Veja [detalhes](docs/RELEASE-1.2.md) e [Play Protect](docs/PLAY-PROTECT.md).

## Novidades da versão 1.1

18 novos módulos de registros e uma central de rotina: checklists, cardápio, casa, pets, medicamentos, consultas, humor, medidas, exercícios, metas financeiras, dívidas, contas a pagar, desejos, presentes, cursos, vagas, contagem regressiva e revisão de serviços. Veja [detalhes e limites](docs/RELEASE-1.1.md).

## O que está no app

- Dashboard com tarefas interativas, finanças, clima, hábitos, água e foco.
- Tarefas com prioridade, etapas, prazo, recorrência, lembrete e vínculos; calendário e Kanban.
- Finanças com captura simples e avançada, saldo atual e previsto, contas e carteiras, transferências, faturas e parcelas, regras recorrentes, orçamentos, metas, dívidas, patrimônio, fechamento mensal, alertas, gráficos, filtros e CSV de 15 colunas. Importação CSV/OFX, OCR local, comprovantes e PDF.
- Notas com pastas, tags, arquivo, favoritos, salvamento automático e prévia Markdown; diário e links.
- Clima por várias cidades, previsão horária/7 dias e cache. Modo manual de temperatura/condição para personalização.
- Hábitos, água, sono, saúde, treinos, Pomodoro, estudos e flashcards.
- Viagens, reservas, mala, compras, despensa, receitas, patrimônio, documentos, contatos, aniversários e veículo.
- Calculadora, conversores, juros compostos, cronômetro, datas, texto, senhas e QR.
- Busca global, favoritos, lixeira, desfazer, widget, atalhos, notificações, backup JSON e bloqueio PIN/biometria.
- Temas claro/escuro/AMOLED/sistema/dinâmico, fonte Manrope embarcada e identidade violeta/grafite.

Os registros começam vazios no APK. Exemplos dos testes e capturas não entram na instalação. Não há conexão bancária, câmbio automático ou cotação de investimentos; valores são informados pelo usuário. O APK 2.0.1 já inclui a configuração Firebase real; builds do código-fonte precisam do arquivo local ignorado pelo Git. As limitações de autenticação, App Check e distribuição estão no guia de setup.

## Compilar

JDK 17, SDK 35 e Build Tools 35.0.0. Configure `JAVA_HOME` e `ANDROID_HOME`, ou `local.properties` para o SDK.

```powershell
./gradlew.bat :core:model:test :feature:finance:test :app:lintDebug :app:assembleDebug
./gradlew.bat :core:cloud:testDebugUnitTest
./gradlew.bat :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
./scripts/test-firebase-security.ps1
./scripts/build-apk.ps1
# Depois de commit e push, publicar o APK com a mesma chave:
./scripts/publish-release.ps1
```

O script gera a chave local na pasta privada e ignorada `.signing`, alinha o APK para páginas de 16 KB, assina e verifica o resultado. Preserve a chave e a senha para futuras atualizações. O projeto não inclui credenciais ou chaves no Git/ZIP.

## Base e arquitetura

`app` contém Compose e integrações Android. `core:model` preserva os modelos; `core:data` gerencia Room, índice financeiro, busca FTS, auditoria, revisões, outbox e isolamento físico por UID. `feature:finance` contém os motores JVM de dinheiro, recorrências, cartões, previsões e indicadores. `core:cloud` contém autenticação, sincronização e anexos Firebase; `core:designsystem`, o tema. A [auditoria e migração](docs/FINANCE-ARCHITECTURE.md) explica as mudanças. A documentação anterior foi preservada em [BASE-README.md](docs/BASE-README.md).

Clima: [Open-Meteo](https://open-meteo.com/), geocodificação GeoNames. Manrope: licença SIL Open Font License em [Manrope-OFL.txt](docs/Manrope-OFL.txt). Sem anúncios, analytics ou conta obrigatória. Dados locais e backups continuam sujeitos às condições descritas no guia.
