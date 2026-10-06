# Validação do Veyra Life 1.0

Verificação realizada em 6 de outubro de 2026, com JDK 17, SDK 35, Gradle 8.11.1 e AVD isolado Veyra_QA/API 35 (Android 15).

## Resultados confirmados

- **13 testes unitários aprovados:** dinheiro, parcelas, transferências, previstos/realizados, recorrência, hábitos, ferramentas, CSV/OFX, projeção financeira, evolução diária e relatório completo com proteção contra fórmulas.
- **5 testes de persistência aprovados:** armazenamento/reabertura, migração do banco, backup e validação/transação, incluindo backup protegido por senha.
- **5 testes de interface aprovados:** captura/salvamento/busca global; entrada rápida e conclusão de tarefa; calendário/Kanban; planilha e orçamentos; notas e clima manual com persistência após reabrir.
- **Lint debug:** zero erros; 17 avisos da base e dependências (incluem atualizações disponíveis e textos do widget). O relatório completo fica em `app/build/reports/lint-results-debug.html`.
- **Verificação visual:** Home, agenda, notas, finanças, planilha e clima no emulador, com renderização habilitada. Capturas reais em `docs/screenshots`, usando registros exclusivos do teste.
- **Serviço de clima:** consulta HTTP ao endpoint usado pelo app retornou dados atuais, 168 pontos horários e sete dias para São Paulo. Esse teste externo não substitui testes sob indisponibilidade da rede.
- **APK release:** build otimizado aprovado, assinatura v2/v3 verificada, instalação e abertura no emulador aprovadas. Pacote `app.veyra.life`, versão 1.0.0/100, mínimo API 26; inclui ARM/ARM64/x86/x86_64. Tamanho: 45.365.483 bytes. SHA-256: `a4c268587092690cc042feb16e75202c477050953609c35d443957c3f7f80f6d`.

O script `scripts/build-apk.ps1` executa os testes unitários, lint e build release, alinha o arquivo e verifica a assinatura do APK. O hash SHA-256 da distribuição fica em `dist/SHA256SUMS.txt`.

## Limites da verificação

Não foi testado em um celular físico. Biometria, widgets em launchers diferentes, reconhecimento de voz, OCR em muitos tipos de recibos, todas as permissões negadas, ciclos longos de foco/processo encerrado e grandes históricos ainda exigem testes nos aparelhos de uso. Os testes não representam cobertura completa de todos os módulos herdados. A documentação de validação anterior está preservada em `BASE-VALIDATION.md`.
