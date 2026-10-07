# Validação Veyra 2.0.0

Execuções em 7 de outubro de 2026, Windows, JDK 17, SDK/Build Tools 35 e AVD isolado `Veyra_QA` com Android 15/API 35. Os dados de teste e logs locais ficam em `.tools`, ignorada pelo Git, e não entram no APK.

## Testes automatizados

| Área | Testes aprovados | Cobertura principal |
|---|---:|---|
| `core:model` | 23 | Compatibilidade dos módulos e cálculos existentes |
| `feature:finance` | 96 | Centavos, moedas, transferências, recorrências, pausas, escopos, faturas, parcelas, previsões, orçamentos, metas, dívidas e patrimônio |
| `core:cloud` JVM | 6 | Conversão estrita de valores legados, JPY/KWD/BRL/USD, precisão, limites e rejeição de valores inválidos |
| `core:data` Android | 22 | Migração Room, persistência, índices, busca, auditoria, fila, conflitos, isolamento por UID, anexos e exclusões |
| `app` Android | 27 | Fluxos existentes e financeiros, integração de notificações/widget, favoritos protegidos e proteção de acesso |
| Firebase emuladores | 30 | Auth real local, propriedade, revisões, estrutura, dinheiro, datas, referências, preferências, Storage privado e exclusão |
| **Total** | **204** | Sem falhas ou testes ignorados nas execuções finais aprovadas |

Os testes de regressão finais incluem mudança do dia da recorrência sem duplicar pendentes, preservação do ciclo pago, pagamento futuro antecipado, consulta exata da série fora da janela da tela e permanência da supressão após excluir definitivamente uma ocorrência com data alterada.

Excluir/restaurar uma regra inteira também foi testado: somente seus pendentes acompanham a regra; pagos e ocorrências que já estavam excluídas permanecem preservados. Regras excluídas definitivamente não são expandidas nem quebram o painel após a remoção dos detalhes. O teste real do scanner recebeu espera pelo seletor e nova leitura do elemento quando o Android muda sua árvore durante a animação. Duas rodadas intermediárias detectaram falhas dessa navegação; após a correção, a rodada completa de 27 testes passou.

Comandos reproduzíveis:

```powershell
./gradlew.bat :core:model:test :feature:finance:test :core:cloud:testDebugUnitTest
./gradlew.bat :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
./gradlew.bat :app:lintDebug :app:assembleRelease
npm ci --prefix firebase
./scripts/test-firebase-security.ps1
./scripts/build-apk.ps1
```

Os emuladores Firebase usam exclusivamente `demo-veyra`, sem deploy em produção. O script exige JDK 21 para esse runtime, separado do JDK 17 do Android, e isola a configuração da CLI.

## Lint e limites da cobertura

Lint debug: **0 erros e 23 avisos**. Os avisos dizem respeito a versões de dependências/target mais recentes, sugestões de extensões Kotlin e layout/texto do widget. O target e a stack foram mantidos na configuração compilada e testada; não houve atualização indiscriminada de dependências. Lint vital release também faz parte do build.

Os testes de autenticação Android cobrem a recusa de acesso sem credencial e o vínculo com a conta; não certificam reconhecimento biométrico em fabricantes reais. Google OAuth, Play Integrity/App Check e sincronização em produção dependem da configuração do projeto Firebase e de validação em aparelho real. O Play Protect não foi declarado aprovado. Fontes muito grandes, TalkBack completo e múltiplos fabricantes ainda precisam de QA específico.

## Migração e configuração

Room usa migrações aditivas 1→2→3, preservando registros, preferências e IDs. A migração financeira mantém campos legados e identifica compras antigas já debitadas. SQL transporta saldos anteriores à janela; valores novos usam inteiros em unidades mínimas. Bancos por UID são separados do banco convidado original.

O APK funciona localmente. Não há projeto Firebase de produção criado ou implantado. Para ativar a nuvem, siga [FIREBASE-SETUP.md](FIREBASE-SETUP.md) e gere uma nova versão com a mesma chave. O guia contém os caminhos, certificados, autenticação, regras, índices, App Check e verificações que dependem do proprietário.

## APK e atualização instalada

Build release final: `BUILD SUCCESSFUL in 1m 49s`; 375 tarefas. APK alinhado para páginas de 16 KB e assinado com a chave original, assinaturas v2 e v3 verificadas. Pacote `app.veyra.life`, `versionCode=200`, versão `2.0.0`, mínimo API 26.

- Arquivo: `Veyra-2.0.0.apk`, 46.613.585 bytes.
- SHA-256 do APK: `d46ee123296d1966cbd50113edb7fa47a235998134bcfb086f0a3f1764e08419`.
- SHA-256 do certificado: `8595f5acf3676e236dd32198a08e71bf5d04d875697d395f5bccd6b7a4498f21`.

Atualização instalada por cima do APK original 1.2.0, **sem desinstalar**: uma nota, uma conta com R$ 250,00 e um gasto realizado de R$ 37,25 foram criados pela interface antiga. A interface 2.0 mostrou saldo de **R$ 212,75**. Backups exportados pelas duas interfaces confirmaram os três IDs, títulos/conteúdo, vínculo de conta e conversão exata para `openingMinor=25000` e `amountMinor=3725`; o backup novo usa schema 3. Os dados antigos não foram substituídos por exemplos.

O APK release também exibiu a nota antiga, o formulário rápido, a movimentação paga e um erro explícito para filtro monetário inválido, bloqueando exportação CSV/PDF nesse caso. Capturas reais: [após atualização](screenshots/upgrade-2.0.png), [movimentações](screenshots/movimentacoes-2.0.png), [gasto rápido](screenshots/quick-expense-2.0.png). Todos os valores nessas capturas são dados de QA.

O AVD ATD não suporta configuração de bloqueio de tela. O teste da Activity prepara `financeLock` no banco local de teste para verificar que uma nota favorita permanece visível, o título de uma conta favorita não é exposto e o acesso financeiro exige autenticação. Isso não substitui validar biometria/PIN em aparelho real.
