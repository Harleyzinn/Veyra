# Veyra 2.1.0 — planejar e conferir

O financeiro agora permite registrar pagamentos parciais de faturas, escolher a data real do pagamento, conferir o restante em aberto e consultar pagamentos carregados. Faturas em aberto recebem prioridade, e listas maiores podem ser expandidas. O pagamento continua debitando apenas a conta escolhida, sem repetir o gasto da compra.

Novo **Simulador** para testar uma entrada ou gasto extra em uma data, com reserva mínima, comparação do saldo final, menor saldo diário, gráfico e margem diária adicional. O cálculo verifica os dias anteriores aos depósitos futuros e preserva centavos. A simulação não cria lançamentos nem altera dados reais.

Nova **Conferência** de possíveis duplicidades e registros sem categoria no período financeiro. Agrupa sugestões por descrição, data, valor e conta/cartão, sem excluir ou mesclar registros. Parcelas, recorrências e pagamentos de fatura são tratados separadamente. Abra cada registro para revisar.

Mantém os dados e módulos existentes, o Firebase conectado no plano gratuito, anexos locais, a assinatura original e o atualizador GitHub. Nenhuma mudança de esquema do banco foi necessária. Faça backup e instale por cima da versão anterior, sem desinstalar.

Os resultados do simulador dependem dos dados cadastrados; o app não consulta bancos nem realiza pagamentos. Google Login e Play Integrity continuam dependendo da validação em um aparelho com Google Play Services. A configuração Firebase não garante aprovação do Play Protect.
