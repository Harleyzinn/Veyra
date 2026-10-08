# Validação Desktop 4.1.0

Executado em Windows 11 build 26200, x64, Electron 44.7.0, com perfis temporários e registros sintéticos separados dos dados pessoais. O instalador `apps/desktop/release/VeyraLife-Setup-4.1.0.exe` e o executável empacotado são do mesmo build.

## Verificações concluídas

- Typecheck e lint passaram; 53 testes de lógica/protocolo passaram. Os novos casos cobrem saldo restante de fatura, atraso, moeda/cartão removido, relatórios por data real de pagamento, compras de cartão, transferências e valores pendentes/futuros.
- 30 testes de regras Firebase passaram no emulador. A integração de duas sessões cobriu revisão, conflitos, retomada offline e isolamento de UID. Login, sincronização, logout, troca de conta e rejeição de editor antigo passaram no Electron. Recusas de acesso são esperadas nos casos negativos. Os avisos de depreciação do JDK e ferramentas Firebase não impediram os testes.
- Regressão de interface: financeiro exato, Markdown autosave, Kanban, mini janela e recuperação offline passaram.
- No executável final: versão 4.1.0, configuração Firebase embarcada, isolamento do renderer, captura revisada, galeria, comandos/busca, Inbox, projetos, planner, backlinks, conflitos/rascunhos, modelos, CSV e diagnóstico passaram.
- A nova suíte também passou no executável final: lembrete de fatura com R$ 200 restantes; alternar edição visual/Markdown preserva a nota byte a byte; formatar o título mantém código TypeScript, marcador de lista e tabela intactos; controles podem ser clicados; atualização de nota limpa por outra janela; conflito comparado e recuperado como cópia sem substituir o original; 150 resultados de busca acessíveis; seletor de fluxo de caixa.
- Exportação/reimportação por arquivo e diálogo real preservou versão financeira, data do pagamento e vínculo da fatura. R$ 42 de compra e R$ 42 de quitação resultaram em R$ 42 de saída de caixa. Importar o mesmo arquivo novamente adicionou zero registros. Tipo de pagamento desconhecido foi rejeitado. CSV continua sendo importação de lançamentos, não restauração de backup.
- Glass no executável: Win32 confirmou janela sem moldura, canto nativo e transparência; DWM informou Acrylic (`3`). Foco/pausa, modo micro, pin, opacidade, recuperação de click-through, captura, widgets, claro/escuro e restauração após reinício passaram.

## Medição de referência

Uma amostra com 1.500 registros (1.000 notas/500 despesas), 12 consultas, no executável final. Não limpa cache do Windows e não é benchmark estatístico.

| Métrica | Amostra 4.1.0 |
| --- | ---: |
| Abertura com perfil separado | 562 ms |
| Reinício com cache de 1.500 registros | 694 ms |
| Snapshot, mediana | 10,8 ms |
| Busca existente, mediana | 7,2 ms |
| Memória total dos processos | 560,7 MiB |
| CPU em repouso | 0% na amostra |
| Abrir Dock, Mini e Foco | 515 ms |
| Memória total com esses painéis | 1.068,0 MiB |

A [medição 4.0](VALIDATION-DESKTOP-4.0.0.md) ficou em aproximadamente 525 MiB e 1.035 MiB com três painéis. Esta amostra não demonstra ganho de velocidade ou redução de RAM; o consumo das janelas independentes continua uma prioridade. Não foi feita atribuição de memória por mudança com profiler. A redução da frequência de gravação dos rascunhos é uma alteração de frequência de gravação verificada no código; não foi convertida em promessa de consumo ou autonomia.

## Reprodução e limites

Em `apps/desktop`: `npm run typecheck`, `npm run lint`, `npm test`, `npm run test:ui`, `npm run test:platform`, `npm run test:refinement`, `npm run test:packaged`, `npm run test:glass`, `npm run test:performance`. Para o executável final: `VEYRA_PLATFORM_PACKAGED=1` e `VEYRA_PANEL_PACKAGED=1`. Na raiz: `./scripts/test-firebase-security.ps1 -Desktop -JavaHome <JDK21-ou-superior>`.

Captura sintética: `screenshots/desktop-refinement-reports-4.1.png`. [Auditoria das mudanças](AUDIT-REFINEMENT-4.1.md).

O Firebase de produção não foi novamente modificado/testado nesta versão; os resultados de produção 4.0 não são apresentados como novos ensaios. Login Google humano, celular Android físico, Windows 10, monitores físicos adicionais, suspensão real e aprovação Play Protect permanecem fora da validação. Android continua 2.2.0, sem alteração do contrato ou banco; Firebase Spark e anexos locais permanecem. A assinatura Ed25519 do atualizador não equivale a certificado Authenticode ou aprovação SmartScreen/Play Protect.
