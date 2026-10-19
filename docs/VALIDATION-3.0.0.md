# Validação Veyra Finanças 3.0.0 — 19/10/2026

## Evidências executadas

- TypeScript: `node apps/desktop/node_modules/typescript/bin/tsc -p apps/finance/tsconfig.json`, sem erros.
- JavaScript financeiro: 36 testes aprovados, zero falhas: 22 da nova experiência e 14 de regressão do motor desktop reaproveitado. Evidência local: `.tools/finance30-js-tests.log`. Cobrem centavos, transferências, confirmação idempotente, parcelas, pagamento de fatura, moeda, saldo seguro, feriados/sábado, datas curtas/bissextas, séries, pausa, CSV, backup, histórico e contraste dos 24 temas.
- Núcleo Android: 158 testes registrados sem falhas nos relatórios de `core:model`, `feature:finance` e `core:cloud`. O Gradle reutilizou resultados não afetados e reexecutou os afetados pela classificação financeira nova.
- APK e lint: `:app:assembleRelease :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` concluídos. Lint: zero erros e 12 avisos, incluindo alvo SDK e textos nativos fixos. Empacotamento final repetido após atualizar os assets. Logs: `.tools/finance30-delivery-build.log` e `.tools/finance30-package.log`.
- Emulador isolado `Veyra_QA`, API 35: seis testes aprovados em 81,741 s. Três do portal (ler/gravar Room, revisão obsoleta, recriação, classificação privada de salário e exclusão da preferência nova da fila incompatível) e três de regressão (fatura parcial, bloqueio sem credencial, contexto de tarefas/notas/financeiro e recriação da atividade). Os testes de fluxos anteriores agora abrem explicitamente a experiência anterior. Evidência: `.tools/finance30-native-ui.log`.
- Edge/Chromium e WebKit: 16 verificações de fluxo em cada motor, zero erros JavaScript. Cadastros, filtros, salário por dia útil, compra em três parcelas com soma exata, modos, persistência após recarregar, duas abas com revisão conflitante, prévia/deduplicação de backup, download real, 24 temas, quatro gráficos, Ctrl+K, 390/820/1440 px e reabertura com rede realmente desativada. Evidências: `.tools/finance30-qa/ui-results.json` e `.tools/finance30-webkit/ui-results.json`.
- APK de produção instalado por atualização no emulador, sem desinstalar nem limpar o banco. Pacote `app.veyra.life`, versão 3.0.0 (300). Hierarquia real confirmou “Você pode gastar”; sem erro AndroidRuntime observado. As capturas do emulador ficaram uniformemente pretas inclusive no launcher Android; a hierarquia de acessibilidade confirmou o conteúdo, mas a validação de pixels nativos ficou inconclusiva neste ambiente. As prévias web foram capturadas e verificadas nos dois motores.

## Integridade

APK: `dist/Veyra-3.0.0.apk`.

SHA-256: `4e19c8d894dfff38ce53114deef61c81e2567d32b9cdaa1accf15678bbf7c523`.

Assinatura original, esquemas v2/v3 verificados pelo apksigner. O processo não expõe a senha e não altera a chave. Não foi testada instalação num aparelho físico pessoal.

## Capturas

As capturas usam dados sintéticos de teste, identificados por QA. Não são registros do usuário nem dados iniciais do produto.

- `docs/screenshots/finance-3.0-desktop.png`
- `docs/screenshots/finance-3.0-mobile.png`

## Limites reais

WebKit no Windows não substitui teste num iPhone físico. Não há IPA/App Store, integração bancária nem sincronização Firebase da PWA. Preferências novas são locais por usuário e entram no backup; sincronização delas aguarda adaptação específica do contrato do servidor. Regras e autenticação Firebase remotas não foram modificadas nem revalidadas numa conta real nesta sessão.

Feriados móveis/locais são cadastrados manualmente; alertas e recebimentos automáticos novos rodam ao abrir o app. CSV importa receitas/despesas, enquanto JSON preserva vínculos completos e transferências. Testes de seletor de arquivo e biometria em cada fabricante/dispositivo permanecem pendentes. Recursos anteriores continuam no acesso legado.

Publicação e URL de produção devem ser confirmadas no registro de publicação antes de declarar implantação bem-sucedida.
