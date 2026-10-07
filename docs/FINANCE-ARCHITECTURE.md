# Evolução financeira — auditoria e plano de migração

Auditoria realizada em 6 de outubro de 2026 antes de alterar o código. Stack: Android nativo, Kotlin 2.1.20, Jetpack Compose/Material 3, AGP 8.9.2, JDK 17, SDK 35, mínimo Android 8.0. Os módulos `core:model`, `core:data`, `core:designsystem`, `core:integration` e `feature:*` permanecem separados.

## Estado encontrado

Room 2.7.1, banco versão 2: `items`, `preferences` e `entries` legado. Os detalhes dos registros são um mapa JSON; IDs, anexos, lixeira e backups são reais. A migração 1→2 já preserva dados. Valores são centavos `Long`, com conversão `BigDecimal` exata. Transferências usam um registro único, sem lançamentos parciais. CSV/OFX, OCR local, exportação, tarefas, notas, clima e demais módulos serão preservados.

O financeiro atual usa um editor genérico; confunde fluxo do mês com saldo disponível, usa `planned`/`done` para estados, não calcula faturas por ciclo e só gera assinaturas mensais. A interface carrega o histórico inteiro, incluindo anexos. Não existem autenticação, UID, sincronização, revisão ou histórico de alteração. A exposição de dados no widget e nas notificações precisa depender de consentimento.

## Migração planejada

1. Preservar o banco local e todos os IDs. Migrar Room 2→3 de forma aditiva, com índice financeiro, revisões, auditoria e fila durável de sincronização. Manter testes da migração 1→2→3 e do backup anterior.
2. Interpretar valores e estados antigos sem apagar campos desconhecidos. Marcar compras antigas já debitadas para não alterar retroativamente saldos nem gerar outra cobrança. Manter IDs determinísticos de assinaturas e pagamentos antigos.
3. Separar motores JVM de cálculos, faturas, parcelas, recorrências, previsão e validação da UI. Recorrências são regras e exceções; ocorrências são calculadas apenas para o período solicitado. Não gerar histórico infinito.
4. Consultar janelas e páginas com índices, usar saldo acumulado anterior à janela e carregar anexos apenas quando necessários. Atualizar registro, índice, auditoria e outbox na mesma transação local.
5. Isolar fisicamente os bancos por UID. O banco convidado original continua separado. A importação para a conta exige escolha explícita, preserva IDs e só conclui após confirmação remota. Ao trocar conta, limpar o estado visível antes de abrir o novo banco.
6. Implementar Firebase Auth, Firestore, Storage e App Check com configuração real. Usar revisões, operações idempotentes e conflitos preservados; não sobrescrever uma alteração concorrente silenciosamente. Regras validam proprietário, estrutura, tipos e tamanho. Anexos ficam no Storage, não em documentos Firestore.
7. Refazer o financeiro com lançamento simples e detalhes expansíveis, mantendo os demais módulos. Testar precisão, migração, transferências, recorrências, faturas, persistência, isolamento e regras; compilar e executar lint.

O usuário informou que ainda não tem projeto Firebase. O código deve compilar e funcionar localmente sem credenciais inventadas. Login e sincronização em produção só poderão ser validados após configuração na conta do usuário; o guia de setup deve indicar exatamente essas etapas.
