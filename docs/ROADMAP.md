# Matriz da evolução Veyra Life

Estado de referência: Desktop 4.0.0 e Android 2.2.0. Os números abaixo correspondem ao pedido de evolução geral de 8 de outubro de 2026. “Entregue” descreve o comportamento real; limites de hardware e funcionalidades futuras são explícitos.

| Requisitos | Implementação / estado | Limites concretos |
| --- | --- | --- |
| 1–2, 65 | Auditoria P0–P5; caches e dados preservados; regras, Auth, IPC, arquivos e updater revisados | Login humano e aparelhos físicos dependem do ambiente; sem migração destrutiva |
| 3, 6 | Home acionável: dia/atrasos, saldo, agenda, hábitos, Inbox, contas; dicas pelo horário | Faturas ficam detalhadas no Finance Center; não reorganiza layout automaticamente |
| 4–5 | Galeria 17 widgets, seis categorias, preview contextual, arrastar/setas/largura/altura/remover/reset | Reordenação entre widgets, sem grade com posições livres sobrepostas |
| 7–9, 24, 33, 73 | +, Ctrl+N, interpretação local revisável, Ctrl+K, Spotlight configurável, comandos de tema/agenda/foco/backup | Português determinístico; não interpreta qualquer frase livre |
| 10 | SQL local parametrizado; type/date/tag/category/amount/favorite combináveis; pesquisa módulos/comandos | Sem embeddings ou busca semântica; busca SQL usa índice existente e filtro de conteúdo |
| 11 | Inbox universal; organização em tarefa/nota/gasto/evento/projeto preserva original e vínculo | Anexos permanecem no original até escolha de cópia local |
| 12–16 | Finance Center existente: contas/carteiras/transferências, cartões/faturas/parciais, parcelas, orçamentos, metas/insights | Cartão guarda só dados organizacionais; sem CVV/senha/número completo |
| 17–21 | Calendário financeiro, previsão real/esperada 7/30/90/180/365 dias, assinaturas e relatórios por período/categoria/conta/cartão | Cadastro explícito; sem conectar bancos ou prometer previsões certas; export CSV desktop |
| 22–23 | Tarefas, filtros, lista/Kanban/calendário, prioridades/tags/projetos/subtarefas/recorrência/dependências; workspace de projeto | Projeto reúne registros e descendentes; arquivo/link pelo cadastro compatível |
| 25–26 | Markdown/GFM, editor visual básico, checklist/tabelas/código, links/backlinks, imagens/anexos locais, tags/pastas/favoritos/fixação/history/autosave | Markdown principal; sem baixar imagens externas; rich text não é processador de texto completo |
| 27–28 | Calendário dia/semana/mês/agenda; Timeline diária; planner por horários e teclado/arraste; financeiro separado integrado | Sem calendário externo Google/Microsoft; hábitos por painel/dia e estatísticas |
| 29–30 | Pomodoro/pausas/personalizado/cronômetro, vínculo com tarefa/projeto, histórico e análises dia/semana/mês | Sessão livre limitada a 24 horas; sem sistema de cobrança |
| 31–32 | Glass comum, Mini/Dock/captura/foco/widgets; native frameless/cantos/blur condicional/pin/opacidade/click-through/compact/micro/posição; Dock esquerda/direita | Suporte físico Windows 10, múltiplos monitores/DPI/sleep precisa de equipamento; Dock conserva RAM frente a várias janelas |
| 34–37, 77 | Tray, atalhos rápidos, resumos opcionais em Glass, revisão e planejamento | Resumos com app aberto; não são alarmes garantidos se fechado |
| 38–40 | Builder QUANDO/SE/ENTÃO, alerta por gasto/categoria/valor/moeda, tarefa/alerta semanal, modelos de tarefa/nota/projeto | Ações limitadas e explícitas; sem código arbitrário/pagamentos; modelo de projeto cria tarefas, sem orçamento automático |
| 41–44 | Vínculos por IDs, contexto de projetos, favoritos Ctrl+P e itens recentes | Recentes de registros abertos; não registra cada relatório temporário |
| 45–47 | Undo de exclusão, lixeira lógica, recuperação, histórico/auditoria | Histórico mantém versões locais; sem undo genérico de todas as operações |
| 48–49 | Sync Center, fila/status/erro/última confirmação/dispositivos; offline/reconexão/conflitos | Dispositivos são presença; sem revogação remota administrativa; anexos locais |
| 50–51 | Automático/Qualidade/Economia; bateria reduz Acrylic; módulos lazy, cache de itens/finanças por versão/UID/parâmetros | Electron tem custo de RAM; não promete cold boot físico medido |
| 52–53 | Teclado/captura/comandos/favoritos, atalhos globais configuráveis, contextos/drag/hover/tooltips | Atalhos internos permanecem fixos; personalização global detecta conflitos |
| 54–56 | Perfil inicial de uso sem esconder funções, temas/cores/densidade/escala/animações/glass, sidebar por contexto com rolagem | Perfil só configura dashboard inicial; sidebar sem editor arbitrário de categorias |
| 57–62 | Veyra Design Language, hierarquia/layouts próprios, transições discretas, estados vazios, skeleton/cache, mensagens e rascunhos | Capturas reais verificadas; não equivale a auditoria completa de acessibilidade assistiva |
| 63–64 | Privacidade e backup existentes integrados aos comandos, permissões locais explícitas, import preview/confirm/criptografia opcional | JSON sem senha é legível; faça backup; sem cloud attachments/Blaze |
| 66 | Rascunhos criptografados, compare-and-save, recuperação como cópia, gravação atômica e .previous | 30 rascunhos/300 KB; últimos caracteres antes da gravação podem ser perdidos |
| 67–68 | Updater estável existente com Ed25519/SHA-256/host/versão/ação explícita | Beta adiado até pipeline próprio; sem certificado Authenticode comercial |
| 69–70 | Diagnóstico seguro com versão/OS/runtime/cache/banco/sync/requisições/processos; health de persistência e erro recuperável | PRAGMA quick_check só no diagnóstico solicitado; startup não prova acesso remoto antes do Auth/sync |
| 71–72 | Interfaces compartilhadas de módulos/widgets/comandos/busca/automação/notify/sync; arquitetura pronta para ampliar | Sem loader de plugins externos ou IA externa/telemetria |
| 74–76 | Central, read/dismiss/open, DND, silêncio durante foco, agrupamento/dedup, thresholds de orçamento | Avisos enquanto aberto; lembretes vencidos no mesmo dia podem aparecer após retomada |
| 78 | Navegação interna por IDs/ações enumeradas e protocolo local restrito | Não registra protocolo público para comandos arbitrários de outros aplicativos |
| 79–80 | CSV financeiro com preview/idempotência, backup JSON criptografável compatível/preview, export CSV filtrado/anexos locais | Desktop não importa OFX/OCR nem exporta PDF especializado; Android mantém ferramentas |
| 81–82, 90 | Unit, Rules, sessão/sync/conflito/offline, runtime e EXE real, Glass Win32, produção sintética, medições e updater público | Humano Google/mobile físico/Windows10/monitores físicos/sleep não declarados concluídos |
| 83–85 | Nenhuma dependência nova; editor visual com DOM seguro; código de notas antigo removido; docs atualizadas | Dependências de ferramentas podem emitir avisos de depreciação fora do app |
| 86–89 | Implementação em módulos integrados, testes/correções/medição/build/release; qualidade priorizada | A matriz explicita recursos condicionais e validação física futura |

Leia [guia da plataforma](PLATFORM-4.0.md), [auditoria](AUDIT-PLATFORM-4.0.md) e [validação](VALIDATION-DESKTOP-4.0.0.md). Ferramentas especializadas Android continuam no celular, sem reorganização do banco ou módulos Kotlin.
