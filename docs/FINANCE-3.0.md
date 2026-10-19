# Veyra Finanças 3.0

## Produto e plataformas

Experiência centrada em dinheiro, compartilhada entre o APK Android e uma PWA responsiva. O iPhone usa o web-app pelo Safari e pela opção Compartilhar → Adicionar à Tela de Início. Não há binário iOS nativo nem publicação na App Store nesta entrega.

O Android mantém `app.veyra.life`, banco Room, assinatura, autenticação, fila de sincronização, bloqueio financeiro e módulos anteriores. A entrada principal abre o novo financeiro. Em Configurações → Abrir experiência anterior e conta continuam disponíveis login, integrações e módulos legados. Compartilhamento, atalhos e entradas por notificação continuam usando os fluxos nativos existentes. O retorno da experiência anterior recarrega preferências antes de reabrir o financeiro.

## Navegação

| Área | Recursos |
|---|---|
| Início | Saldo confirmado, próxima renda, contas próximas, gasto mensal, economia, meta e cálculo explicável do valor seguro |
| Lançamentos | Receitas, gastos, transferências, edição, confirmação, lixeira, busca, filtros e CSV |
| Contas e calendário | Lista, calendário mensal, atrasos, receitas previstas, parcelas, faturas, prazos e aportes de metas |
| Cartões | Limite, fechamento, vencimento, compras, parcelas com centavos exatos, faturas e antecipação |
| Contas e carteiras | Saldo inicial, tipo, cor, ícone e inclusão no disponível |
| Planejamento | Metas, valores reservados/planejados, orçamentos e reserva de emergência por categorias |
| Relatórios | Categorias, períodos, contas, comparação mensal, saldo, compromissos, metas e tabelas acessíveis |
| Receitas e recorrências | Dia fixo, dia útil escolhido, semanal, duas semanas, duas datas e avulsa; contas fixas |
| Assinaturas | Próxima cobrança via agenda, custo equivalente mensal/anual e pausa preservando histórico |
| Aparência | 24 temas completos, prévia, favoritos, claro/escuro por sistema, texto, fonte, densidade e movimento |
| Configurações | Simples/completo, calendário, margem, economia, backup/importação, categorias e recuperação |

Celular: Início, Lançamentos, Contas, Cartões e Mais. Tablet: sidebar compacta. Computador: sidebar e Ctrl/Cmd+K. Ambos os modos usam os mesmos registros; simples reduz controles dos formulários e ações imediatas.

## Regras financeiras

- Todos os valores monetários são inteiros em centavos. A apresentação usa BRL/pt-BR; datas são civis e o dia atual é local.
- Receitas previstas não aumentam o saldo. Confirmações usam IDs estáveis e não geram outro recebimento/pagamento ao repetir a ação.
- Recebimento pode ter valor real diferente do previsto. Registro automático de salário é **opcional e executado ao abrir o app**, nas datas já vencidas; não é um serviço permanente em segundo plano.
- Sábados e feriados nacionais fixos são configuráveis. Feriados móveis, estaduais, municipais e datas de convenção devem ser cadastrados manualmente. Estado/cidade são referências informadas, não uma integração automática de feriados. O cálculo mostra as regras utilizadas.
- Alterações de séries distinguem uma ocorrência de esta e as próximas, mantendo registros confirmados. Pausas mantêm os intervalos históricos sem regenerar cobranças durante a pausa.
- Transferências movimentam contas sem aumentar receita/despesa. Compras no cartão entram nas análises; pagamentos de fatura reduzem caixa e não entram novamente como gasto.
- “Você pode gastar” parte do dinheiro confirmado em contas selecionadas. Desconta pendências, faturas, reservas, aportes planejados e margem. Receitas futuras só entram na projeção opcional. Aportes e reservas não movimentam dinheiro automaticamente. Uma reserva ligada a conta excluída não é deduzida duas vezes.
- Reserva de emergência usa as despesas registradas **no mês selecionado**, com categorias e meses de cobertura explícitos. Não estima uma média inexistente.
- Histórico de reservas das metas começa com as alterações registradas na versão 3.0. Não inventa movimentações anteriores.
- Alertas são apresentados no painel ao abrir o app. Não foram implementadas novas notificações push/em segundo plano para a PWA. Encerrar acompanhamento de assinatura não cancela o serviço contratado.

## Persistência e compatibilidade

`apps/finance/domain.ts` reaproveita `apps/desktop/shared/model.ts` e `finance.ts`, acrescentando calendário, salário, cálculo disponível, importação e histórico. `salary_schedule` possui IDs de ocorrência estáveis e classificação financeira também nos controles de privacidade legados.

PWA: IndexedDB, transação atômica e comparação de revisão para impedir sobrescrita entre abas. Não exige conta nem conexão bancária. Não há sincronização Firebase ou login no web-app nesta versão; backup estruturado é o meio de transferência entre dispositivos. O armazenamento depende do navegador; limpar dados do site remove a cópia local.

Android: `FinancePortal.kt` carrega somente assets locais em uma origem HTTPS fixa. O bridge bloqueia navegação/conteúdo remoto e passa por `FinanceAccessGate`. `financePortalCall` valida revisão, registros, referências e moeda. `WorkspaceStore.saveFinancePortal` grava registros e preferências numa transação Room, preservando auditoria e fila existentes. A interface não recebe tokens.

As preferências novas ficam no banco **do usuário ativo** e no backup Android. `financePortal` não entra em `SYNC_PREFERENCES`: as regras atuais do servidor não autorizam essa chave, e enviá-la quebraria a sincronização existente. Não foram alteradas regras remotas, App Check nem permissões Firebase. Sincronização real de uma conta e estas novas preferências entre dispositivos ainda precisam de validação/integração específica.

Registros antigos não são apagados. Módulos não financeiros permanecem na experiência anterior e no backup. A nova apresentação prioriza reais; outras moedas continuam preservadas e devem ser geridas na experiência anterior. Não há conversão silenciosa de moedas.

## Backup e importação

JSON preserva registros, IDs, campos e preferências. A importação mostra quantidade e prévia, ignora IDs existentes e não sobrescreve registros locais. Preferências de um backup só são recuperadas automaticamente num espaço vazio. Arquivos inválidos são recusados antes da escrita.

CSV usa UTF-8, ponto e vírgula e cabeçalho `Data;Tipo;Descrição;Categoria;Valor (R$);Situação`. Importação CSV é para receitas e despesas; transferências precisam do JSON, que contém referências a ambas as contas. Valores e datas são validados. Conteúdo idêntico é tratado conservadoramente como duplicata. Arquivos CSV não guardam todos os vínculos de cartão/recorrência; use JSON para uma restauração completa. A exportação neutraliza células que poderiam virar fórmulas em uma planilha.

Backups contêm dados privados sem criptografia. Não devem ir ao repositório público.

## Desenvolvimento no Windows

Dependências: Node 24, pacotes fixados em `apps/desktop/package-lock.json`; Android SDK 35/JDK 17 para APK. React, TypeScript, esbuild e Lucide são reaproveitados do projeto; nenhuma biblioteca de valores monetários, banco ou calendário externa foi adicionada.

```powershell
npm ci --prefix apps/desktop
node apps/finance/build.mjs
node apps/desktop/node_modules/typescript/bin/tsc -p apps/finance/tsconfig.json
node apps/desktop/node_modules/tsx/dist/cli.mjs --test apps/finance/tests/domain.test.ts apps/desktop/tests/finance.test.ts
node apps/finance/tests/ui.mjs
# Opcional: instalar WebKit de teste, depois repetir a suíte:
node apps/desktop/node_modules/playwright/cli.js install webkit
$env:VEYRA_BROWSER='webkit'
node apps/finance/tests/ui.mjs
./scripts/build-apk.ps1
```

O build gera `apps/finance/out` para hospedagem e `app/src/main/assets/finance` para o Android. Ambos são derivados, ignorados pelo Git. O workflow Android gera os assets antes de compilar. O workflow `finance-web.yml` valida tipos e domínio e publica apenas `apps/finance/out` no Pages, sem banco, credenciais ou dados financeiros.

O Service Worker guarda o shell e fontes locais com cache versionado pelo conteúdo. Uma atualização aguarda o encerramento das páginas antigas antes de substituir a versão em uso. Instalação física no iPhone/App Store e comportamento dos seletores de arquivo em cada fabricante permanecem verificações em dispositivos reais.
