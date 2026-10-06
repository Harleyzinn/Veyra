# Roadmap do super app

A versão 0.2 amplia a fundação para uso real. Um cadastro funcional não conclui todos os recursos avançados de um domínio. Esta tabela registra esse limite explicitamente.

| Área | Entregue na 0.2 | Ainda pendente |
| --- | --- | --- |
| Fundação e produto | Room com migração, onboarding, busca global, temas, perfis, home reordenável, tags, favoritos, lixeira/undo | FTS/paginação, DataStore, reorganização por arrastar, extração da UI para features |
| Produtividade | Tarefas/prioridade/status/recorrência/lembrete, projetos e vínculos, calendário mês/semana, Kanban, metas e planos | Subtarefas com dependências, planner anual, alarmes exatos opcionais, recorrência avançada |
| Finanças | Contas/transferências, cartões/limites, parcelas sem duplicar compra, recorrentes, orçamentos, gráficos, categorias, investimentos manuais, CSV/OFX com prévia/dedup, OCR revisado | Ciclo de fatura e pagamento, conciliação, integração bancária, múltiplas moedas, rentabilidade e comparação de preços |
| Meu Dia | Hábitos/check-ins/streak, metas, água, resumos, favoritos e clima configurável com cache | Metas de hábito por quantidade/frequência, widgets personalizáveis por módulo |
| Estudos e foco | Disciplinas/provas/notas ponderadas/frequência, anexos, flashcards/revisão, Pomodoro com ciclos/pausas/histórico/vínculo e serviço foreground | Simulados, cronograma automático, sons e modo foco dedicado |
| Vida pessoal | Diário/humor/gratidão/anexos, saúde manual, sono/treino, contatos/aniversários, contador de datas | Editor rico, bloqueio individual de nota/documento, sensores/Health Connect |
| Viagens e lazer | Viagem/reserva/mala/documentos, orçamento/contagem regressiva/itens vinculados, livros/filmes/jogos | Roteiro visual com mapas, séries/episódios, integrações externas |
| Casa e veículo | Compras/total, despensa/validade, receita→compras, patrimônio/garantia, abastecimento/manutenção/estimativa de consumo | Compra→despensa e despesa com confirmação, manutenção automática por quilometragem, relatório de custo total |
| Ferramentas | Calculadora científica/juros, conversores, QR por imagem, texto, senhas, cronômetro/voltas, datas | Timer avulso, mais grandezas, painel dispositivo/bateria, câmera QR contínua |
| Android | Widget de tarefas, shortcuts, Tile, share texto/imagem, voz, anexos SAF, notificações concluir/adiar | Mais widgets, scanner PDF multipágina, deep links públicos, testes completos de acessibilidade |
| Automações e IA | Regras locais de quatro condições/inbox/dedup, templates, assistente local, IA online opcional com confirmação | Editor livre SE/ENTÃO, mais ações, LLM offline embarcado, busca semântica |
| Segurança e release | PIN/biometria, backup manual AES-GCM, anexos, JSON/CSV/PDF, backup automático privado em JSON | Backup automático criptografado com Keystore, criptografia do banco, sync opcional, assinatura release, matriz de aparelhos |

## Integrações entregues

- Projeto/viagem/disciplina/veículo/patrimônio podem ser contextos de tarefas, notas, eventos, gastos, documentos e outros registros.
- Viagem calcula gastos vinculados e mostra orçamento e contagem regressiva.
- Disciplina agrega notas ponderadas, frequência e sessões de foco.
- Receita gera itens de compra; compras somam quantidade × preço com arredondamento monetário.
- OCR cria rascunho revisável, extrato importa registros confirmados, parcelas preservam o total em centavos.
- Regras criam avisos deduplicados a partir de dados reais; notificações e widget consultam o banco local.

## Próxima prioridade

1. Completar ciclo de faturas e conciliação com testes monetários e importação idempotente.
2. Automatizar compra→despensa/despesa e manutenção por quilometragem.
3. Criptografar backup automático e melhorar armazenamento de anexos fora da linha do banco.
4. Extrair telas/casos de uso para features, introduzir FTS e testar grandes históricos.
5. Executar matriz de aparelhos, acessibilidade, notificações negadas, modo avião e release assinado.
