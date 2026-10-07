# Veyra Life

Sua vida, em um só lugar. App Android nativo em Kotlin/Jetpack Compose, criado a partir do Veyra fornecido pelo usuário.

## APK e instalação

Baixe pela [release oficial](https://github.com/Harleyzinn/Veyra/releases/latest). O APK release assinado local fica em `dist/Veyra-2.0.0.apk`. Requer Android 8.0 ou superior e usa o pacote `app.veyra.life`. A assinatura original foi preservada para permitir atualizar versões anteriores sem reinstalar. Faça backup antes de atualizar. Veja [como instalar e usar](docs/INSTALAR.md).

## Novidades da versão 2.0

Central financeira com captura rápida e modo avançado, dinheiro em unidades mínimas exatas, saldo realizado separado da previsão, recorrências com exceções e escopos de edição, faturas por fechamento, parcelas, transferências, contas a pagar/receber, orçamentos, metas, reserva, quitação de dívidas, patrimônio, relatórios CSV/PDF, gráficos interativos, histórico e lixeira. Os demais módulos permanecem disponíveis.

Novo módulo `core:cloud` com Firebase Auth, login Google, Firestore, Storage privado, App Check, sincronização com revisões, fila offline e conflitos preservados. O app funciona localmente sem configuração Firebase. Para ativar a nuvem, crie seu projeto e siga o [setup exato](docs/FIREBASE-SETUP.md). Não há projeto de produção ou credenciais incluídos no código/APK desta entrega. Leia os [detalhes da versão](docs/RELEASE-2.0.md), [guia financeiro](docs/FINANCE-GUIDE.md) e [validação](docs/VALIDATION-2.0.md).

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

Os registros começam vazios no APK. Exemplos dos testes e capturas não entram na instalação. Não há conexão bancária, câmbio automático ou cotação de investimentos; valores são informados pelo usuário. Firebase requer configuração do proprietário e novo build. As limitações de autenticação, App Check e distribuição estão no guia de setup.

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
