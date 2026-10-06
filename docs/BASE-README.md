# Veyra Android

**Sua vida, em um só lugar.** Central pessoal Android nativa, modular e offline-first.

## Versão 0.2.0 — expansão funcional

O projeto agora reúne módulos persistentes e integrações Android reais. O [roadmap](docs/ROADMAP.md) distingue recursos entregues, parciais e pendentes; o [escopo original](docs/REQUISITOS-ORIGINAIS.md) permanece preservado.

### O que funciona

- Home personalizável, onboarding, cinco abas, busca global, favoritos, tags, vínculos entre registros, lixeira e desfazer. Temas sistema/claro/escuro/AMOLED/dinâmico e perfis com organização dos módulos.
- Tarefas com prioridade, status, recorrência e lembrete; projetos, metas, planejamento, calendário mensal/semanal e Kanban.
- Finanças com receitas/despesas, contas, transferências, cartões e limites, parcelas, orçamentos, assinaturas, investimentos manuais, categorias e gráficos. Valores calculados em centavos; lançamentos previstos separados dos realizados.
- Importação CSV/OFX com prévia, confirmação e IDs estáveis para impedir duplicatas. OCR local de imagens de recibos com revisão antes de salvar uma despesa. Exportação CSV/PDF.
- Notas com salvamento automático e visualização Markdown básica, diário com humor/gratidão, links, anexos e pastas.
- Hábitos com check-in/streak, água, sono, treino e registros manuais de saúde.
- Disciplinas, provas, notas/média ponderada, frequência, flashcards com intervalo de revisão e histórico de foco. Pomodoro com duração, ciclos, pausas, pausa/retomada, vínculo e serviço em segundo plano.
- Viagens com orçamento e registros relacionados, reservas, mala, contatos, aniversários, livros, filmes e jogos. Casa com compras e total, despensa, receitas que geram compras, patrimônio, garantias e documentos. Veículos com abastecimento, manutenção e estimativa de consumo.
- Clima real por várias cidades configuráveis, previsão de sete dias, cache e data da consulta. A consulta online é opcional e usa Open-Meteo; o app não solicita GPS.
- Calculadora científica, juros compostos, conversores, cronômetro com voltas, datas, ferramentas de texto, geração/leitura de QR por imagem e gerador local de senhas.
- Widget Android de tarefas, atalhos do launcher, Quick Settings Tile, compartilhamento de texto/imagem, anexos pelo seletor Android e notificações com concluir/adiar.
- Regras locais para pendências, orçamento, validade e manutenção com inbox e deduplicação; templates; assistente local de consultas e voz pelo provedor Android. IA online opcional com endpoint/modelo/chave de sessão e confirmação antes de enviar a pergunta.
- PIN/biometria, backups JSON com anexos, backup manual opcional protegido por senha AES-GCM e restauração validada em transação. Backup automático local opcional, em JSON legível, no espaço privado do app.

Os cálculos, cadastros e fluxos acima funcionam localmente. Isso ainda não significa todo o escopo original: faturas por ciclo e pagamento, conciliação bancária, simulados, scanner multipágina, LLM embarcado, sync em nuvem e criptografia do banco estão pendentes. As telas usam um editor compartilhado de campos e a UI ainda está concentrada em `app`; a modularização visual continuará evoluindo.

## Compilar e instalar

JDK 17, Android SDK 35 e Build Tools 35. Android mínimo 8.0/API 26.

```sh
./gradlew :core:model:test :feature:finance:test :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No Windows use `gradlew.bat`. Configure o SDK em `local.properties` (ignorado pelo Git) ou `ANDROID_HOME`. Gradle 8.11.1 possui SHA-256 fixado. Os primeiros builds baixam dependências; uso local não exige conexão.

Com um emulador conectado:

```sh
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

No GitHub: **Actions → Android APK → artifacts → veyra-debug-apk**. O APK debug permite instalar para desenvolvimento. Uma distribuição de produção requer assinatura própria, revisão das regras de publicação e testes em aparelhos físicos.

## Privacidade e dados

Sem login obrigatório, anúncios ou analytics. Room mantém dados no espaço privado do app, sem criptografia adicional do banco. PIN/biometria bloqueiam a interface; não equivalem a criptografia em repouso. Auto Backup do Android está desativado.

Backups JSON e CSV/PDF são legíveis. No backup manual, informar uma senha de pelo menos oito caracteres protege o arquivo com AES-GCM e PBKDF2. Guarde a senha: não há recuperação. Anexos selecionados são copiados para o banco local e incluídos no backup, com limite de 10 MB por arquivo. Importação mescla por ID; não apaga registros ausentes no arquivo.

Clima envia o nome da cidade e suas coordenadas ao Open-Meteo. IA online envia somente a pergunta confirmada ao endpoint HTTPS configurado, sem inclusão automática dos registros. Chave de IA fica na memória da sessão. Voz usa o aplicativo reconhecedor do Android e pode depender de internet. OCR de texto é local, com modelo incluído no APK.

## Estrutura

| Área | Responsabilidade |
| --- | --- |
| `app` | UI Compose, coordenação e integrações Android |
| `core:model` | Tipos, validações, dinheiro, recorrência e ferramentas |
| `core:data` | Room, migração 1→2, importação/backup e criptografia de backup |
| `core:designsystem` | Temas e cores |
| `core:integration` | Contratos de integrações |
| `feature:*` | Catálogos de domínio, importadores e provedor de clima |

Veja [arquitetura](docs/ARCHITECTURE.md), [design](docs/DESIGN.md) e [validação](docs/VALIDATION.md).
