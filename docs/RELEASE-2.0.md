# Veyra Life 2.0.0 — central financeira

A antiga planilha passa a ser uma central com lançamento rápido, detalhes avançados e cálculos separados da interface. Tarefas, notas, clima, scanner, desenhos, jogos, ferramentas e os demais módulos do Veyra continuam disponíveis.

## Registrar e acompanhar

- Entrada e gasto com valor, descrição, categoria e data; detalhes expansíveis de conta, cartão, forma de pagamento, moeda, status, vencimento, competência, horário, tags, pessoa, centro de custo, notas, comprovante e lembrete.
- Saldo realizado de contas e carteiras separado de entradas previstas, vencimentos pendentes e projeção. Transferências alteram contas sem aumentar receitas ou despesas.
- Busca indexada, autocomplete do histórico, categorias próprias, regras por descrição e modelos de lançamento rápido. Filtros por período, tipo, status, categoria, conta, cartão, valor, recorrência e parcelamento.
- Painel, fluxo futuro com datas selecionáveis, comparação mensal, categorias, calendário com faturas e recorrências, CSV de 15 colunas e PDF. Exportações de movimentações respeitam os filtros.

## Planejar

- Recorrências diárias, semanais, quinzenais, mensais, bimestrais, trimestrais, semestrais, anuais e personalizadas; início/fim, quantidade, dias de semana, dia do mês e último dia de segunda a sexta.
- Editar/excluir uma ocorrência, futuras ou toda a série; pausar/retomar sem duplicar pagamentos ou gerar retroativos durante a pausa. Pagamentos já realizados são preservados nas alterações da série.
- Cartões com fechamento e vencimento, limite usado/disponível, compras por fatura e pagamento pela conta. Parcelamento distribui os centavos exatamente e permite antecipar vencimentos.
- Orçamentos e faixas de alerta, metas, estimativa de conclusão, reserva de emergência baseada nos gastos registrados, assinaturas, dívidas com quitação efetiva e patrimônio com fotografias e fechamento mensal.
- Alertas de vencimentos, orçamento, limite, atraso, saldo negativo e gastos atípicos; insights e indicador financeiro com fatores visíveis. São cálculos descritivos dos registros, sem aconselhamento profissional.

## Integridade e proteção

Migração Room 1→2→3 aditiva: mantém IDs, dados antigos e módulos, adicionando índice financeiro, resumos sem binários, busca FTS, auditoria, revisões, conflitos e fila de sincronização. A conversão financeira preserva campos antigos e marca compras antigas já debitadas para evitar outra cobrança. Dinheiro usa `Long` em unidades mínimas e `BigDecimal` para conversão exata.

Lixeira, desfazer e restauração de parcelas/quitações mantêm as contrapartidas. O banco convidado original fica separado dos bancos por UID. Trocar conta limpa o estado visível; lembretes, backups, widgets e ações ficam vinculados à conta. Anexos são carregados sob demanda.

Modo para ocultar valores, autenticação do Android no financeiro, widget financeiro opcional e notificações privadas com consentimento, limite diário e controle de repetição. PIN/biometria bloqueiam a interface; o banco local não recebeu criptografia adicional.

## Firebase preparado

`core:cloud` implementa Auth por email/senha e Google, perfil, verificação/recuperação, reautenticação para alterações sensíveis, Firestore, anexos privados em Storage e App Check. Sincronização usa operações idempotentes, revisões e conflitos preservados para resolução explícita, além de importação do banco convidado mediante escolha.

As regras validam UID, verificação de email, estrutura, dinheiro, referências de contas/cartões, versões e arquivos. Índices e testes de Auth/Firestore/Storage estão no repositório. **O usuário ainda não criou o projeto Firebase: não houve deploy nem validação da nuvem em produção.** O APK desta release funciona localmente; a ativação exige configurar o projeto e gerar outro build conforme `docs/FIREBASE-SETUP.md`.

Sem conexão bancária, câmbio, cotação ao vivo ou feriados municipais automáticos. Fotografias patrimoniais usam os valores registrados; alterações posteriores de ativos não reconstroem valores históricos ausentes. Projeções informam a janela de recorrências usada. As demais limitações e instruções estão em `docs/FINANCE-GUIDE.md`.

O APK mantém a chave de assinatura original e o atualizador via release GitHub, com validação de assinatura/hash e confirmação do instalador Android. Isso não garante classificação do Play Protect nem elimina a verificação de aplicativos ainda desconhecidos. Veja [Play Protect](PLAY-PROTECT.md).

A evidência de testes, lint, migrações e build fica em `docs/VALIDATION-2.0.md`.
