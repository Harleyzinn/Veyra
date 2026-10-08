# Auditoria da evolução da plataforma Veyra

Base: Desktop 3.1.0 (`d786482`), Android 2.2.0. Pedido de 8 de outubro de 2026. A inspeção antecedeu alterações de produto. Este documento acompanha achados, implementação e limites, sem declarar como validado aquilo que depende de hardware ou login humano.

## Base revisada

Arquitetura Kotlin/Room e motores financeiros, protocolo de envelope Android/desktop, regras Firestore, armazenamento sql.js/AES-GCM/DPAPI, autenticação Google no navegador com nonce e retorno local, fila/revisões/conflitos, backups compatíveis, preload/IPC/origem/UID, anexos locais, atualizador Ed25519, janelas Glass, tray/atalhos, UI de finanças/produtividade/configurações e testes existentes. A fonte de verdade é o código; o ROADMAP 0.2 está desatualizado e será substituído por uma matriz atual.

Base automática: typecheck e lint sem erros; 37 testes unitários passaram. `npm audit --omit=dev`: nenhuma vulnerabilidade reportada nas dependências de produção. Isso não equivale a uma garantia de segurança. A suíte de regras e os fluxos de produção foram validados na entrega anterior e serão reexecutados quando pertinentes. Não há analytics; nenhuma IA externa é necessária para comandos locais.

## Prioridades

| Nível | Achado concreto | Tratamento |
| --- | --- | --- |
| P0 | Dois editores locais podem substituir conteúdo sem verificar se outra janela já editou o registro | Precondição local no editor e autosave; preservar rascunho em falhas |
| P0 | Gravação de registro vindo de snapshot sem binário pode perder anexo local | Preservar binário existente no caminho de gravação parcial; testar |
| P0 | Campos/tipos de metadados desktop aceitos sem validação estrutural; backup importa aparência não validada | Normalização explícita, limites, validação no importador e IPC |
| P1 | Captura universal abre diretamente tarefa, sem interpretar intenção nem oferecer Inbox | Captura revisável local, oito tipos, Inbox com organização explícita |
| P1 | Projetos são cadastros; não há workspace que reúna tarefas, notas, foco e prazos | Visão contextual e vínculos baseados nos IDs existentes |
| P1 | Busca não interpreta filtros e comandos naturais; Home não prioriza pendências de vários módulos | Consulta parametrizada e centro diário com ações reais |
| P1 | Dashboard tem reordenação e tamanhos, mas sem galeria/preview/reset | Galeria categorizada e controles acessíveis |
| P2 | Snapshot recarrega todos os itens e recalcula finanças para cada janela e mudança de estado | Cache invalidado por versão dos dados e parâmetros, sem usar cache entre UIDs |
| P2 | Autosave de notas exporta e criptografa o banco inteiro a cada tecla; histórico cresce por caractere | Agrupar a edição e conservar rascunho antes da confirmação |
| P2 | Foco principal mantém intervalo de um segundo mesmo parado | Atualização somente durante sessão ativa visível |
| P2 | Sincronização relê perfil e grava lastSync sem mudança em cada ciclo | Limitar leituras/gravações redundantes sem ocultar erro ou dispensar Rules |
| P2 | Lembretes percorrem todos os tipos e podem notificar registros sem intenção de lembrete | Selecionar tipos/horário válido, deduplicar e agrupar; Não Perturbe |
| P3 | Automações/templates existentes não possuem execução desktop integrada e revisão clara | Motor local limitado, sem comandos arbitrários ou alterações financeiras automáticas |
| P3 | Finanças já têm contas, cartões, faturas, parcelas, orçamentos, recorrências, previsão e CSV | Preservar motor e acrescentar calendário, horizontes e relatórios úteis |
| P3 | Notas têm Markdown, histórico e anexos; faltam backlinks e ferramentas de formatação | Ligações por título/ID e edição estruturada sem carregar imagens remotas |
| P4 | Navegação, erros, estados vazios, onboarding, favoritos/recentes e diagnósticos dispersos | Componentes compartilhados e centros dedicados |
| P5 | Plugins, IA externa, wallpaper hacks e canais beta sem pipeline | Interfaces internas; não executar código de terceiros, não enviar dados privados |

## Preservação e limites

Não alterar o envelope sincronizado, coleções, assinatura dos aplicativos, chave do atualizador nem plano Spark. Novos dados de domínio usam `Item.fields`/tipos permitidos no workspace; preferências exclusivas do PC permanecem locais. Qualquer adição de tabela local exige migração transacional e teste de abertura do schema anterior. Backups continuam com preview e escolha explícita para substituições.

O teste 3.1 mediu aproximadamente 1,50 GiB com dez janelas; a arquitetura Electron usa um renderizador por janela. Dock é a alternativa de agrupamento, não uma promessa de custo zero. Windows 10, monitores físicos adicionais e atestação Play Integrity continuam dependentes de equipamento real. Login Google não será simulado nem receberá credenciais humanas por chat. A validação separa teste de protocolo, executável, emulador e equipamento físico.

## Execução

Corrigir P0/P2 → fundação de comandos/busca/integrações → Home/captura/workspaces → finanças/notas/planejamento → automações/notificações/diagnóstico → regressão, medição e executável. A matriz de requisitos será atualizada com recursos existentes, novos e limites concretos após validação.

## Correções e evidência final

Saves parciais preservam anexos; compare-and-save e rascunhos recusam sobrescrita concorrente. Configuração desktop/backup tem allowlist/validação estrutural. Acesso a caminhos de arquivo arrastado exige concessão de uso único por janela e UID; chamadas arbitrárias são recusadas. Prévia de imagem identifica bytes raster, sem SVG executável. O dashboard respeita o conteúdo/rolagem ao redimensionar. O build lazy foi corrigido para gerar app.js/app.css efetivamente consumidos pelo HTML, evitando arquivo antigo residual.

Validada a concorrência em dois editores reais, recuperação como cópia, CSV prévia/deduplicação/export filtrado, cálculos/recorrências/dependências, isolamento e Rules. Não foi adicionada dependência. [Resultado dos testes/medições](VALIDATION-DESKTOP-4.0.0.md) e [matriz atualizada](ROADMAP.md).
