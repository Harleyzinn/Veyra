# Veyra Life — plataforma desktop 4.0

O celular mantém o Android 2.2.0 para captura e consulta. O desktop 4.0 amplia planejamento, organização e análise, usando o mesmo UID, Firebase, envelope e registros existentes. Não há migração destrutiva nem alteração do plano gratuito. Novos recursos específicos do PC não significam uma nova interface Android.

## Uso diário

A Visão geral destaca tarefas de hoje/atrasadas, hábitos disponíveis, saldo realizado, próxima agenda e contas previstas. As sugestões variam com o horário; não reorganizam o layout. **Personalizar** abre a galeria de 17 widgets em seis categorias. É possível adicionar, remover, arrastar, usar setas, escolher largura, redimensionar a altura e restaurar o padrão. Widgets com altura limitada têm rolagem para preservar o conteúdo.

O **+** e **Ctrl+N** abrem a captura universal. `Gastei 42,35 no almoço`, `Academia amanhã 18h` e `Internet 120 todo dia 10` propõem registros editáveis antes de salvar. A interpretação é determinística, em português; não executa pagamentos e não envia texto a serviços de IA. **Guardar na Inbox** adia a escolha do tipo. Organizar uma captura preserva o original e vincula o novo registro.

**Ctrl+K** pesquisa ou executa comandos. `> dark`, `amanhã`, `gasto 50 mercado`, `backup`, `diagnóstico` e `iniciar foco` têm ações locais. Filtros combinam `type:expense date:2026-10 category:"Alimentação" amount:>50 tag:viagem`. **Ctrl+P** começa pelos favoritos. O Spotlight global usa **Ctrl+Alt+Space**, configurável em Configurações; a captura global continua **Ctrl+Shift+Space**. Ctrl+Shift+T/G/N cria tarefa/gasto/nota na janela ativa.

Projetos reúnem descendentes de tarefas, subtarefas, notas, eventos, metas e foco, com progresso e prazo. **Planejar meu dia** permite arrastar ou selecionar uma tarefa e horário; reagenda o mesmo registro. Dependências impedem concluir tarefas antes dos requisitos e rejeitam ciclos. Foco registra tempo por tarefa/projeto, com análises de hoje, semana e mês. Revisão apresenta pendências e próximo planejamento sem pontuação de cobrança.

## Finanças e conhecimento

Contas, transferências, cartões, fechamento, faturas/pagamentos parciais, parcelas, recorrências/exceções/pausas, orçamentos, metas, reserva, simulador, dívidas e projeções continuam no motor existente. Cálculos usam unidades mínimas exatas por moeda. Transferências internas não são despesa; pagar fatura não duplica a compra. A previsão é separada do saldo realizado.

Novas abas: **Calendário financeiro**, **Assinaturas**, **Relatórios**. Calendário exibe compromissos de caixa e faturas restantes; compras do cartão pertencem à fatura. Assinaturas são cadastradas explicitamente e têm custo previsto de 12 meses e equivalente mensal. Relatórios filtram período, moeda, categoria, conta e cartão. Exportação CSV contém apenas colunas selecionadas pelo relatório, sem notas/anexos, e neutraliza células com fórmulas. Importação CSV exige type/date/title/amount/currency, até 2 mil linhas/10 MB, mostra prévia e evita repetir o mesmo arquivo. Referências desconhecidas ou ambíguas são recusadas. OFX/OCR/PDF especializados continuam no Android.

Notas oferecem Markdown, visualização GFM, editor visual básico, checklist/tabelas/código, pastas, tags, vínculos, fixação, favoritos, imagens e anexos locais. `[[Título]]` conecta notas; títulos duplicados exigem `[[ID]]`. Backlinks mostram referências recebidas. O modo Markdown é o formato principal; edição visual tem ferramentas de destaque, itálico, título, citação e código, sem a abrangência de um processador de texto. Imagens externas não são baixadas. Prévia local aceita somente PNG/JPEG/GIF/WebP identificados pelos bytes; SVG fica como arquivo exportável.

Autosave agrupa alterações após 650 ms de pausa e guarda rascunhos locais durante a escrita. A verificação da versão base evita sobrescrever outra janela/dispositivo. Em falha, compare o histórico ou recupere **como cópia** em Sync Center/Diagnostics. Os últimos 30 rascunhos têm limite individual de 300 KB. Uma queda nos primeiros milissegundos anteriores à gravação ainda pode perder os últimos caracteres. Anexos binários existentes sobrevivem a saves parciais e alterações remotas.

## Rotinas opcionais e desktop

Automações usam **QUANDO/SE/ENTÃO**: gasto novo com categoria/valor mínimo/mesma moeda → alerta; dia da semana → alerta ou tarefa. Precisam ser habilitadas explicitamente no PC e executam com o app aberto. IDs e marcadores evitam duplicação. Não executam código, pagamentos, mensagens externas ou cadeias arbitrárias. Modelos criam tarefas/notas ou projetos com tarefas; não criam orçamento automaticamente.

Central de notificações permite leitura, dispensa e abertura de registros. Lembretes são agrupados e deduplicados; avisos de orçamento em 80/100/120% vão para a mesma central. **Não Perturbe** e silêncio durante foco afetam os avisos do Windows. Resumos opcionais: início com Windows, dia após 21h e domingo após 18h, uma vez por data. Não são alarmes do sistema quando o Veyra está fechado.

Todos os overlays usam o sistema Glass existente: Mini Dashboard, Dock, Quick Capture/Spotlight, foco, finanças, tarefas, calendário, clima, hábitos e resumo. Dock pode ser posicionado à esquerda/direita. Transparência real, Acrylic compatível, cantos, fixação, opacidade, modos compactos, recuperação de click-through, posição e tamanho persistem. Economia reduz efeitos/animações; Automático reduz Acrylic na bateria. O Dock agrupa vários módulos em um renderizador, reduzindo o custo frente a muitas janelas separadas.

Sync Center mostra conexão, última confirmação, dispositivos, fila, conflitos, rascunhos e diagnóstico. O diagnóstico copiado exclui UID, e-mail, tokens, títulos, conteúdo e caminhos. Os contadores Firestore são requisições desta execução, não documentos faturados. Backups mantêm prévia, restauração explícita, compatibilidade Android e criptografia opcional com senha. Lixeira, desfazer de exclusão e histórico oferecem recuperação. Permissões locais não são copiadas de backups.

## Arquitetura e extensão

`shared/commands.ts` define navegação/captura; `shared/platform.ts`, parser, busca, vínculos, dependências, resumo, widgets e planos de automação; `shared/settings.ts`, configurações validadas; `shared/reporting.ts`, consultas e CSV. `electron` valida capacidades, UID/origem e gravação. O renderer apresenta módulos carregados sob demanda. Extensões futuras devem passar por essas interfaces e autorização do processo principal; nenhum carregador de plugins externos foi adicionado.

Cache de itens usa versão invalidada pela gravação. Snapshot financeiro depende também de UID, período, horizonte, dia e moeda. Metadados de painéis não invalidam registros. Login mantém navegador externo/nonce e Auth Firebase; sync mantém leitura e commit CAS com Rules e precondições. O perfil é preparado por sessão; lastSync persistente é limitado a cinco minutos, enquanto a interface mostra a confirmação atual. O marcador remoto continua sendo verificado. Nenhum secret ou credencial administrativa entra no instalador.

Arquivos arrastados exigem concessão de uso único vinculada à janela/UID e confirmação; uma IPC não pode escolher arbitrariamente qualquer caminho. Protocolos `veyra://app` servem recursos locais fixos; navegação de registros é interna por ID/ações enumeradas. Não há protocolo público que execute comandos recebidos de outro aplicativo.

## Limites de distribuição e validação

Canal Stable permanece padrão. Beta, plugins executáveis, IA externa e banco automático não foram adicionados sem infraestrutura apropriada. O atualizador existente verifica Ed25519, SHA-256, host fixo e versão superior; instalar depende da ação do usuário. A assinatura do manifesto não substitui certificado Authenticode nem garante reputação SmartScreen/Play Protect.

Veja [auditoria](AUDIT-PLATFORM-4.0.md), [matriz de requisitos](ROADMAP.md), [validação e medições](VALIDATION-DESKTOP-4.0.0.md), [arquitetura](DESKTOP-ARCHITECTURE.md), [Firebase](FIREBASE-SETUP.md) e [Glass](DESKTOP-GLASS.md). Login humano Google, aparelho Android físico, Windows 10, monitores físicos adicionais e suspensão real ainda exigem validação nesses ambientes. Esses limites não são apresentados como testes concluídos.

O [refinamento 4.1](AUDIT-REFINEMENT-4.1.md) acrescenta fluxo de caixa por data de pagamento, agenda com faturas/saldo restante, preservação financeira no CSV e comparação/recuperação de conflitos no editor de notas. [Validação atual](VALIDATION-DESKTOP-4.1.0.md).
