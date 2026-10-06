# Validação 0.2.0

Validação local em 5 de outubro de 2026 (America/Sao_Paulo), JDK 17 e SDK 35.

## Resultados confirmados

- `:core:model:test`: 7 testes aprovados. Dinheiro, parcelas exatas em centavos, transferências, previstos, recorrência com IDs estáveis, streak e ferramentas matemáticas.
- `:feature:finance:test`: 3 testes aprovados. CSV com aspas, OFX com valores assinados, IDs de deduplicação e rejeição de arquivo inválido.
- `:core:data:connectedDebugAndroidTest`: 5 testes aprovados no AVD isolado VeyraStable/API 35. CRUD/reabertura, backup, migração SQLite 1→Room 2 preservando IDs/centavos, AES-GCM com senha incorreta e importação inválida sem alteração parcial.
- `:app:assembleDebug`: aprovado; APK 0.2.0 gerado, assinatura debug verificada por apksigner e instalação aprovada no emulador.
- `:app:lintDebug`: zero erros e 18 avisos. Incluem SDK alvo conservador, atualizações de dependências, texto do widget e recomendações de APIs/ciclo de vida. O relatório completo é gerado em `app/build/reports/lint-results-debug.html`.
- Tela inicial/onboarding inspecionados visualmente no emulador API 35 com desenho habilitado. Nenhum dado demonstrativo é incluído na distribuição.

SHA-256 do APK entregue: `a810d4d105702df8d9ef645a5a2404af9f492516d60c8b97358c66eeff20adc9`.

## Interface e limites

O smoke test de captura → salvar → pesquisa global passou com UI Automator no AVD isolado VeyraTest/API 37 (Android 17). A verificação revelou e corrigiu a ausência de rótulo acessível do botão Capturar. São 16 testes aprovados no conjunto: 10 unitários, 5 instrumentados de dados e 1 fluxo de interface. Os testes de dados rodaram no API 35; o fluxo de interface no API 37, com 4 GB de RAM no emulador.

ATDs removem componentes e desativam renderização por padrão; consulte [documentação Android](https://developer.android.com/studio/test/managed-devices). Esses emuladores não substituem aparelhos físicos.

Ainda não validados integralmente: OCR em uma coleção real de recibos, consultas de clima sob falha de rede, endpoints externos de IA, PIN/biometria em aparelho físico, widgets em diferentes launchers, notificações com permissão negada, todas as variantes de SAF e ciclos longos de foco/process death. Recursos dessas áreas foram implementados, mas exigem a matriz abaixo antes de release.

## Matriz para release

1. CRUD e vínculos de cada domínio, modo avião, reabertura e grandes históricos.
2. Backup/anexos/senha inválida/limites/importação e atualização preservando dados 0.1.
3. Fluxo de cartões/parcelas, importação repetida e extratos de bancos diferentes.
4. Foco com tela apagada, pausa/retomada, encerramento e perda do processo.
5. Widget, Tile, shortcuts, compartilhamento, notificações e permissões negadas.
6. TalkBack, fontes grandes, temas, teclado, telas pequenas e Android 8–17.
7. Assinatura própria de release, revisão de regras de publicação e performance.

O roadmap documenta os recursos avançados pendentes; a aprovação desses testes não significa implementação integral do escopo original.
