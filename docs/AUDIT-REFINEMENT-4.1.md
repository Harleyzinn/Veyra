# Auditoria e refinamento Desktop 4.1

A revisão priorizou integridade das notas e clareza financeira. Os problemas abaixo foram identificados no código e reproduzidos com registros sintéticos no aplicativo.

| Problema | Mudança | Verificação |
| --- | --- | --- |
| Entrar/sair da edição visual serializava a nota sem uma edição e podia perder linguagem de código e marcadores | Serialização somente em alterações, preservando o texto original de blocos intactos | Comparação exata da nota ao alternar; negrito em um título mantém código TypeScript, lista e tabela |
| Controles do editor podiam encolher e sobrepor-se em notas longas | Controles mantêm altura e conteúdo tem rolagem | Cliques reais nos modos e formatação, sem clique forçado |
| Conflito de edição mostrava apenas um erro | Comparação lado a lado e salvamento da edição como nova nota | Original remoto preservado, cópia única, rascunho antigo removido após sucesso |
| Uma nota limpa não acompanhava mudanças de outra janela | Atualização automática quando não há edição local pendente | Atualização remota aparece no editor; edição concorrente continua protegida |
| Busca local limitava resultados silenciosamente a 100 | Consulta memoizada com todos os resultados e exibição incremental | 150 correspondências: total correto, primeiras 100, expansão para 150 |
| Rascunhos disparavam gravações frequentes durante digitação | Intervalo de rascunho passa de 250 ms para 1 s; autosave após 650 ms de pausa continua | Inspeção dos temporizadores e regressão de autosave/recuperação |
| Erro podia afirmar rascunho salvo sem confirmar a gravação | Mensagem considera o resultado da gravação | Caminho de erro distingue rascunho gravado de falha de armazenamento |
| Home/widget não incluíam fatura aberta | Agenda compartilhada para contas e faturas, com saldo restante e atraso | Fatura de R$ 300 com R$ 100 pagos aparece como R$ 200; moeda e cartão removido são respeitados |
| Relatórios não explicitavam data da compra versus pagamento | Seletor de gastos registrados ou fluxo de caixa | Compra em mês diferente do pagamento, cartão, transferências, futuros e pendentes |
| CSV perdia metadados de pagamento e semântica de compras de cartão | Exporta/importa data do pagamento, vencimento da fatura e versão financeira | Arquivo exportado e reimportado pelo diálogo real; compra não entra novamente no caixa, quitação entra uma vez; metadados inválidos recusados |

Não há migração destrutiva, mudança do envelope Android ou nova dependência. Firebase continua gratuito e anexos locais.

Próximas melhorias justificadas: medir e reduzir o consumo das janelas independentes; aumentar a cobertura de edição visual avançada; testar login Google humano, sincronização no Android físico, monitores físicos e suspensão. A memória continua sendo uma limitação; este refinamento não declara resolvê-la. CSV é uma importação de lançamentos com prévia e não substitui backup completo: exportar um conjunto e importá-lo no mesmo espaço pode adicionar registros equivalentes aos originais, pois a deduplicação cobre a repetição do mesmo arquivo importado.

Veja [validação desta versão](VALIDATION-DESKTOP-4.1.0.md).
