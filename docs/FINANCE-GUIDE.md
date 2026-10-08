# Guia da central financeira do Veyra

Este guia descreve o comportamento implementado na versão 2.0. O Veyra acompanha o que você registra. Os valores das telas não vêm de um banco, de uma corretora ou de uma cotação automática.

## Começar

1. Em **Configurações**, escolha a moeda padrão e o primeiro dia do seu mês financeiro.
2. Em **Contas**, cadastre bancos, carteiras ou dinheiro e informe o saldo inicial. Esse saldo é a base dos cálculos; não representa uma receita do mês.
3. Em **Cartões**, informe limite, fechamento, vencimento e, se desejar, a conta usada para pagar faturas.
4. Use **Entrada** e **Gasto** para registrar as movimentações. Preencha valor, descrição, categoria e data. Os detalhes adicionais ficam em seções expansíveis.

O aplicativo funciona com dados locais sem criar uma conta na nuvem. A troca de conta usa espaços de dados separados; a importação dos dados de convidado para uma conta exige uma escolha explícita.

## Lançamento rápido e detalhes

O editor permite registrar conta, cartão, forma de pagamento, moeda, status, vencimento, data de recebimento/pagamento, competência, horário, tags, pessoa, centro de custo, notas, comprovante e lembrete. Recorrência e parcelamento ficam na mesma área de detalhes.

As sugestões usam lançamentos reais do histórico. Categorias recentes e conta usada anteriormente ajudam a preencher novos registros. Uma sugestão não comprova que uma despesa foi paga.

Comprovantes podem ser selecionados no aparelho, com limite de 10 MB por anexo. A visualização carrega o arquivo quando necessário. O envio à nuvem depende de uma configuração Firebase real e de uma conta autenticada.

### Estados

| Estado | Efeito |
| --- | --- |
| Recebido / Pago | Altera o caixa na data de liquidação; compras novas no cartão seguem o ciclo da fatura. |
| Pendente / Previsto | Entra na previsão e nos vencimentos; não aumenta nem reduz o saldo realizado. |
| Atrasado | Indicação automática para pendência cujo vencimento já passou. |
| Cancelado | Não entra em saldos, previsão ou gastos reconhecidos. |
| Na lixeira | Fica fora dos cálculos até ser restaurado. |

Para confirmar um recebimento ou pagamento, abra o lançamento e registre a liquidação. Marcar uma previsão como paga altera o saldo; use isso apenas quando o dinheiro tiver efetivamente movimentado.

## Entender os números

- **Saldo atual**: saldos iniciais das contas mais entradas e saídas liquidadas até hoje. O saldo anterior ao período consultado é preservado pelo banco local.
- **Entradas do período**: receitas recebidas, organizadas pela data do lançamento, dentro do mês financeiro selecionado. A data de liquidação controla quando a receita entra no caixa.
- **Gastos do período**: despesas pagas e compras de cartão já registradas até a data de referência. O pagamento da fatura não repete esses gastos.
- **Economia**: entradas recebidas menos gastos reconhecidos do período. Pode ser negativa.
- **Saldo previsto**: saldo atual mais entradas esperadas, menos saídas esperadas e faturas abertas até a data escolhida.
- **Patrimônio líquido**: caixa mais ativos informados e valores a receber de dívidas cadastradas, menos saldo devedor e faturas em aberto.

Uma transferência muda os saldos de duas contas e mantém o saldo total. Ela não vira receita ou gasto. As duas contas precisam usar a mesma moeda.

Data do lançamento, vencimento e liquidação têm funções diferentes. Por exemplo: um gasto registrado em outubro e pago em setembro aparece no período de outubro, mas sua saída de caixa pertence a setembro. O saldo acumulado evita contar essa saída duas vezes.

### Moedas e precisão

A interface oferece BRL, USD, EUR e GBP. Totais e patrimônio são calculados separadamente por moeda. Não existe câmbio automático nem transferência entre moedas diferentes.

O dinheiro é armazenado em unidades mínimas inteiras. Valores com casas decimais incompatíveis com a moeda são rejeitados em vez de serem arredondados silenciosamente. Em reais, `33,33` representa exatamente R$ 33,33. Percentuais e gráficos são apresentações desses valores; não são a base monetária do saldo.

### Mês financeiro

O primeiro dia pode ser de 1 a 31. Se for 5, o período de outubro vai de 5 de outubro a 4 de novembro. Em meses curtos, dias inexistentes são ajustados para o último dia daquele mês, sem criar lacunas entre períodos.

O calendário continua mostrando os dias do mês civil. Orçamentos, resumo, comparação e fechamento usam o período financeiro escolhido.

## Recorrências

Use recorrências para salário, aluguel, internet, serviços e outras entradas ou saídas repetidas. Há frequências diária, semanal, quinzenal, mensal, bimestral, trimestral, semestral, anual e personalizada. A personalizada permite intervalos em dias, semanas, meses ou anos.

Você pode definir início, fim, quantidade, dias da semana, dia do mês e último dia de segunda a sexta. Uma recorrência mensal no dia 31 usa o último dia dos meses que não têm 31 e volta ao dia 31 nos seguintes. Fevereiro e anos bissextos são tratados pelo calendário.

**Último dia útil** significa o último dia de segunda a sexta. Feriados nacionais, estaduais, municipais e bancários não são consultados. Se o pagamento muda por um feriado, ajuste a ocorrência.

As ocorrências futuras são calculadas para o intervalo solicitado. Criar uma regra não grava milhares de lançamentos antecipados. Quando você edita ou liquida uma ocorrência, ela passa a ter um registro próprio que substitui a previsão correspondente.

### Editar, excluir e pausar

| Escolha | Comportamento |
| --- | --- |
| Somente esta | Altera ou exclui a ocorrência selecionada. A regra continua. |
| Esta e próximas | Divide ou encerra a série a partir da ocorrência selecionada. |
| Toda a série | Altera a regra ou encerra a série; pagamentos já registrados permanecem. |

As alterações e exclusões da série preservam lançamentos liquidados. Para corrigir um pagamento já registrado, abra aquele lançamento. Isso evita apagar silenciosamente dinheiro que já entrou ou saiu.

Se uma edição muda a data ou frequência e já há pagamentos, o calendário anterior é preservado até o último ciclo pago; o novo calendário começa no ciclo seguinte. Um pagamento antecipado continua quitado e não é cobrado outra vez pela regra sucessora. Alterar só o valor mantém as datas. As ações consultam as ocorrências gravadas em todo o histórico, incluindo as que estão fora do período aberto na tela. Excluir definitivamente uma ocorrência remove seus detalhes, mantendo apenas os vínculos mínimos que impedem sua recriação pela previsão.

Pausar mantém o histórico anterior e interrompe as ocorrências no intervalo da pausa. Retomar não cria cobranças retroativas desse intervalo. Se a regra tem uma quantidade definida, os intervalos pausados não consomem essa quantidade. Registros antigos que já estavam pausados sem uma data de pausa mantêm seus lançamentos existentes, mas não geram um histórico de pausa que nunca foi registrado.

## Fluxo futuro, atrasos e calendário

O fluxo parte do saldo realizado de hoje. Inclui pendências persistidas, recorrências previstas, movimentos liquidados com data futura e faturas em aberto. Vencimentos anteriores ainda pendentes entram no primeiro ponto da projeção; isso permite enxergar a pressão dos atrasos sobre o caixa.

As telas **A pagar** e **A receber** informam a data inicial usada para as ocorrências virtuais: “Inclui vencimentos anteriores desde …”. Essa janela é importante: uma regra antiga não prova que todas as suas cobranças passadas continuam em aberto. Pendências já gravadas continuam sendo consideradas mesmo quando são anteriores à janela.

O banco consulta um período limitado e carrega o saldo anterior separadamente. Consultas de recorrência aceitam até dez anos por intervalo; um intervalo inválido gera uma mensagem para reduzir a consulta. Não existe expansão infinita de uma regra.

O gráfico permite selecionar pontos e consultar a data, o saldo e os registros que o compõem. Um aviso de saldo negativo indica a primeira data negativa nos registros projetados, mesmo se uma entrada posterior recuperar o saldo. A previsão não movimenta dinheiro nem confirma que um depósito ocorrerá.

O calendário agrupa vencimentos, recorrências e faturas. Abra um registro para ver ou corrigir sua origem.

## Cartões, faturas e parcelas

Compras novas de cartão reconhecem o gasto e comprometem o limite. Elas não debitam a conta bancária imediatamente. O caixa sai quando você registra o pagamento da fatura.

Uma compra no dia do fechamento entra no ciclo seguinte. Se o dia de vencimento é igual ou anterior ao fechamento, o vencimento fica no mês seguinte. Dias inexistentes são ajustados ao fim do mês. Estes são os critérios do Veyra; confira o extrato do emissor se o seu cartão usa outro critério e ajuste o vencimento registrado.

O limite usado inclui parcelas futuras em aberto. A fatura reúne as compras do seu ciclo e desconta os pagamentos registrados. **Registrar pagamento da fatura** abre um formulário de valor e data: você pode pagar uma parte ou quitar o restante. A data precisa representar um pagamento já realizado. O app cria uma saída na conta escolhida e reduz a obrigação; não efetua uma transferência no banco. O valor remanescente continua no fluxo previsto e no limite usado; os gastos das compras não são contados novamente.

A tela mostra total, valor pago, saldo aberto e pagamentos carregados na janela atual. Faturas em aberto aparecem antes das quitadas; use **Mais faturas** e **Mais pagamentos** para expandir. Totais incluem os resumos históricos, mas a lista detalhada não afirma conter registros que estão fora da janela carregada. Abrir um pagamento permite conferir ou corrigir seu registro. Se outro lançamento mudou a fatura enquanto o formulário estava aberto, a gravação é recusada para você rever o valor atualizado.

Parcelar divide o total em até 120 partes sem perder centavos. Em R$ 10,00 divididos em três vezes, as partes somam exatamente R$ 10,00. A primeira parcela determina o mês de início; a data original da compra é mantida. **Antecipar vencimentos** move parcelas futuras para a data da antecipação. Não calcula desconto, juros do emissor ou uma negociação bancária.

Compras antigas que a versão anterior já descontava imediatamente preservam esse comportamento na migração. Elas não recebem uma nova cobrança de fatura. Essa marca mantém os saldos históricos compatíveis com os seus dados anteriores.

## Simulador de caixa

Em **Finanças → Explorar → Simulador**, escolha o horizonte e informe uma entrada extra, um gasto extra, a data e uma reserva mínima. Os ajustes são únicos: não representam mensalidades repetidas. Só afetam o dia escolhido e os posteriores. O simulador mostra saldo final com e sem cenário, menor saldo de fim de dia, primeira data abaixo da reserva e gráfico.

A **margem diária adicional** divide uma despesa extra igualmente entre hoje e o fim do horizonte, arredondando para baixo em centavos. Ela respeita a reserva em todas as datas previstas, incluindo os dias anteriores a um depósito futuro. Se o fluxo já está abaixo da reserva, a margem é zero. O saldo agregado não garante dinheiro disponível em uma conta específica.

A reserva é um piso para comparação e não é descontada como despesa. O cenário não grava movimentos, altera saldos ou faz transações bancárias. Os resultados dependem de entradas e obrigações cadastradas; gastos não registrados não aparecem na projeção. Esta é uma ferramenta descritiva dos seus registros. O modo de ocultar valores esconde o simulador.

## Conferência

Em **Finanças → Explorar → Conferência**, o app lista possíveis duplicidades no mês financeiro escolhido e lançamentos sem categoria. As sugestões combinam descrição (ignorando diferenças de acentos, espaços e maiúsculas), data, valor exato, tipo, conta/cartão e forma de pagamento. Moedas, registros apagados e cancelados são separados ou excluídos. Parcelas, recorrências automáticas, previsões e pagamentos de fatura não entram na comparação.

Nenhum registro é excluído ou mesclado automaticamente: duas compras iguais podem ser legítimas. Abra cada item para conferir e corrigir usando os detalhes e a lixeira existentes. Os valores respeitam o modo privacidade. Grupos e listas longas são expandidos sob demanda.

## Orçamentos, metas e reserva

Orçamentos acompanham gastos reconhecidos por categoria e, quando cadastrada, subcategoria. As faixas padrão são 50%, 75%, 90% e 100%. Você pode definir outras faixas no cadastro, usando percentuais inteiros de 1 a 100 separados por vírgula, como `25,60,85,100`. A lista é ordenada e percentuais repetidos são removidos. O alerta usa a última faixa atingida, com controle de repetição por orçamento e período. O cálculo da faixa usa valores exatos, mesmo quando a porcentagem mostrada na tela é arredondada.

Metas têm valor objetivo, valor já reservado, conta opcional, data alvo e aporte mensal planejado. A conclusão estimada usa o saldo que falta dividido pelo aporte, arredondando para o próximo mês inteiro. A data alvo é uma referência cadastrada; não provoca um aporte automático. Sem aporte definido, não há uma data de conclusão inventada.

Atualizar “já reservado” não debita uma conta e não cria uma transferência. Uma meta representa a destinação de dinheiro; esse valor não é somado outra vez ao patrimônio. Registre a transferência real separadamente se mover dinheiro entre contas.

A reserva de emergência usa os três meses civis completos anteriores. A média considera os meses desses três que têm gastos registrados; um mês sem registros não é tratado como comprovação de gasto zero. Escolha 3, 6, 12 ou 24 meses na calculadora. Sem histórico suficiente, ela mostra zero como referência calculável, sem inventar despesas.

## Assinaturas e dívidas

Assinaturas usam regras de recorrência. O total dos próximos 12 meses soma as ocorrências previstas nesse intervalo; o equivalente mensal segue a frequência cadastrada. Pausar interrompe as próximas cobranças calculadas, mas não cancela o serviço no fornecedor.

Dívidas registram credor ou pessoa, direção, valor original, valor quitado, juros informados, quantidade e valor de parcelas e próximo vencimento. **Registrar quitação** cria uma despesa, ou uma receita em dívidas a receber, e atualiza o valor quitado no mesmo salvamento. A conta precisa usar a mesma moeda.

Juros e parcelas cadastrados são informações manuais. A tela não cobra juros automaticamente, não reajusta o saldo devedor a cada dia, não cria cobranças bancárias e não aplica o cálculo de amortização ao histórico sem lançamentos. O motor tem um cálculo de amortização testado, mas essa simulação não está exposta como uma ferramenta de empréstimo na interface atual. Ao registrar juros efetivamente cobrados, confira e atualize a dívida e os lançamentos correspondentes.

## Patrimônio, fotografias e fechamento

Ativos e investimentos usam o valor atual informado por você. Não há preço de mercado automático. Se um investimento já está no saldo de uma conta, evite cadastrar o mesmo valor outra vez como ativo; o motor também aceita a indicação de ativo incluído em conta para impedir essa duplicidade.

**Guardar fotografia do patrimônio** salva o valor calculado naquela data. **Guardar fechamento** salva receitas, gastos, economia, maior categoria e patrimônio do período. A identidade do registro usa moeda e data, ou moeda e período: guardar novamente a mesma fotografia ou o mesmo fechamento atualiza aquele registro, com auditoria da alteração, em vez de duplicar a mesma data.

**Limite histórico importante:** o saldo de caixa pode ser reconstruído pelas datas dos lançamentos. Valores atuais de ativos, saldo inicial de contas, montantes de metas e saldos de dívidas são cadastros editáveis. Alterá-los hoje não registra automaticamente quanto valiam em cada dia do passado. Um fechamento antigo calculado hoje usa os valores cadastrados disponíveis hoje para esses componentes; não equivale a uma cotação ou avaliação histórica comprovada. As fotografias e fechamentos já guardados são a referência explícita para acompanhar valores registrados ao longo do tempo.

A comparação de entradas e gastos mostra o mês selecionado e os três anteriores. As listas mostram os últimos fechamentos e fotografias guardados. Não há gráfico anual completo de patrimônio, histórico automático de cotações ou projeção de valorização de investimentos nesta versão. Para analisar intervalos maiores, selecione as datas e exporte os registros.

## Busca, categorias, regras e modelos

A busca considera descrição, notas, tags, valores e campos financeiros, incluindo nomes de conta e cartão. Ela ignora diferenças de acentos e maiúsculas. O conteúdo binário dos comprovantes não faz parte dessa busca.

Filtros permitem período, tipo, status, categoria, conta, cartão, faixa de valor, recorrência e parcelamento. As exportações da lista usam os registros filtrados.

Categorias e subcategorias personalizadas ficam em **Modelos e regras**. Regras reconhecem um trecho da descrição e preenchem categoria ou subcategoria nos novos lançamentos. Uma escolha explícita no editor é preservada. Desativar a regra impede sua aplicação; ela não recategoriza automaticamente todos os registros antigos. As sugestões de histórico também podem ser usadas antes de confirmar o lançamento.

Modelos guardam o preenchimento de um lançamento. Ao usar um modelo, o editor cria um novo registro com nova identidade e a data atual para você revisar. Um modelo não executa pagamentos.

## Alertas e indicador financeiro

Os alertas usam os dados registrados: atrasos, vencimentos próximos, faturas, faixas de orçamento, metas concluídas e previsão de saldo negativo. Insights comparam categorias e assinaturas e identificam gastos acima do padrão recente. Um gasto atípico exige pelo menos cinco registros comparáveis da mesma categoria e moeda; ele não é uma detecção de fraude bancária.

O indicador de 0 a 100 mostra os fatores e os pontos que explicam o resultado: relação entre receitas e gastos, orçamento, dívidas, reserva e vencimentos. Sem receitas registradas no período, o aplicativo pede dados antes de calcular. Ausência de orçamento ou de histórico de reserva usa pontuação neutra explicitada. Esse indicador descreve os registros disponíveis; não é análise de crédito nem aconselhamento financeiro profissional.

Notificações financeiras e exposição de valores dependem das preferências e da permissão do Android. Há limite de três alertas por dia e controle de repetição. A economia de bateria do aparelho pode atrasar a entrega; um lembrete não comprova que a conta foi paga.

## Lixeira, histórico, exportação e proteção

Excluir move o registro para a lixeira e retira seu efeito dos cálculos. Excluir um plano de parcelas move junto as parcelas ativas daquele plano. Restaurar o plano recupera somente as parcelas excluídas com ele; parcelas que já estavam excluídas antes não reaparecem. Registros excluídos definitivamente não podem ser restaurados pela lixeira.

Excluir uma quitação de dívida reverte o valor quitado. Restaurar refaz essa contrapartida, desde que ela continue compatível com as quitações posteriores. Para restaurar um lançamento vinculado, a conta e o cartão precisam estar ativos e usar a mesma moeda. Consulte o histórico de alterações se um valor parecer divergente.

CSV e PDF são relatórios para análise e compartilhamento. Não substituem um backup completo: regras, exceções, vínculos e anexos exigem o backup do aplicativo. A importação de extrato CSV/OFX usa uma prévia e identidades estáveis para evitar repetir o mesmo arquivo. OFX identifica registros pelo identificador fornecido no extrato; fontes sem uma identidade bancária confiável exigem revisão da prévia.

O modo de ocultar valores reduz a exposição na interface. A proteção do financeiro usa a autenticação do aparelho; configure o bloqueio de tela do Android antes de ativá-la. O widget financeiro é opcional. Bloquear ou ocultar a interface não adiciona criptografia ao banco local. Um arquivo exportado contém os valores reais; revise o destino antes de compartilhá-lo.

Login e sincronização exigem o projeto Firebase configurado pelo proprietário do aplicativo. O código suporta autenticação e sincronização reais, mas um APK sem essa configuração funciona localmente e informa a indisponibilidade. Não há credenciais de demonstração nem uma conta fictícia conectada. Consulte o guia de configuração Firebase do repositório antes de ativar a nuvem.

## Referências de implementação

Os cálculos ficam no módulo [feature/finance](../feature/finance/src/main/kotlin/app/veyra/feature/finance). [FinancialDomain](../feature/finance/src/main/kotlin/app/veyra/feature/finance/FinancialDomain.kt) centraliza moeda, estados e validação; [FinanceEngine](../feature/finance/src/main/kotlin/app/veyra/feature/finance/FinanceEngine.kt), [RecurrenceEngine](../feature/finance/src/main/kotlin/app/veyra/feature/finance/RecurrenceEngine.kt), [CardEngine](../feature/finance/src/main/kotlin/app/veyra/feature/finance/CardEngine.kt) e [FinancialAnalytics](../feature/finance/src/main/kotlin/app/veyra/feature/finance/FinancialAnalytics.kt) calculam os resultados sem depender das telas. [FinanceActions](../feature/finance/src/main/kotlin/app/veyra/feature/finance/FinanceActions.kt) prepara os conjuntos de mudanças que o banco salva de forma atômica.

O contexto da migração está em [FINANCE-ARCHITECTURE.md](FINANCE-ARCHITECTURE.md). A evidência da versão publicada deve ser conferida no documento de validação correspondente à release.
## Relatórios no Android 2.3

Em Finanças → Explorar → Relatórios, escolha **Gastos registrados** ou **Fluxo de caixa**. Gastos registrados seguem a data da compra; fluxo de caixa considera pagamentos e recebimentos confirmados pela data do pagamento. Compras atuais no cartão entram no primeiro modo, enquanto a quitação da fatura entra no segundo. Registros antigos que já afetavam o caixa continuam respeitando esse histórico.

Abra **Período e filtros do relatório** para definir De/Até, busca, conta, cartão e categoria. Os totais e arquivos correspondem a essa seleção. A prévia mostra até cinco registros; PDF e CSV incluem todos os selecionados. Previsões podem aparecer em gastos registrados, mas não entram nos totais realizados. Transferências internas não são receita nem despesa nesses relatórios. Use **Limpar filtros do relatório** para voltar ao período financeiro escolhido no topo.

O gráfico mensal e o fechamento do mês continuam separados do relatório filtrado. Exportações nas listas de movimentações e pendências também usam totais da seleção, sem incluir saldo de contas ou patrimônio como se fossem resultados do período. CSV ganha as datas e referências de pagamento; exportar CSV não é restaurar um backup nem importar automaticamente no desktop. Use o backup completo para recuperação dos dados.

No Resumo, **Vencimentos em foco** mostra contas e faturas vencidas ou com prazo nos próximos sete dias. As faturas exibem o saldo restante; compras do cartão não são duplicadas como contas avulsas. A moeda escolhida é respeitada. Os valores seguem o botão de privacidade do financeiro.

## Visualizações no Android 2.4

Abaixo do relatório filtrado, **Gráficos da seleção** oferece **Barras**, **Linhas**, **Área**, **Rosca**, **Pizza** e **Tabela**. Use **Visualização** para trocar o formato. Em séries e tabela, **Agrupar por** escolhe dia, semana ou mês. Semanas começam na segunda-feira; o primeiro rótulo pode ser anterior ao início do filtro, mas os registros continuam limitados à seleção. Meses seguem o calendário; um período financeiro personalizado pode abranger dois meses.

Em barras, linhas e área, **Comparar** alterna entre entradas/gastos e o resultado (entradas menos gastos). O resultado pode ser negativo e não inclui saldo inicial de contas. Toque no desenho ou use **Anterior/Próximo** para ler os valores do período selecionado. A tabela tem páginas de doze períodos, com entradas, gastos e resultado.

Rosca e pizza mostram apenas gastos realizados. **Distribuir gastos por** escolhe categoria, conta ou cartão. Toque na legenda para selecionar um grupo. Até sete grupos principais aparecem individualmente; quando há mais de oito, o restante é somado no oitavo. Contas/cartões sem vínculo ficam identificados, e nomes repetidos recebem um identificador curto. Os percentuais são arredondados para uma casa decimal e podem não somar exatamente 100% na tela.

Os dados seguem os filtros, moeda e modo Gastos registrados/Fluxo de caixa do relatório. As visualizações incluem somente os valores que entram nos totais realizados; previsões ficam fora. O modo de privacidade oculta todos os gráficos e valores, inclusive tabela e legendas. Trocar a visualização não altera os registros nem o formato das exportações CSV/PDF; essas exportações continuam sendo relatórios de lançamentos.