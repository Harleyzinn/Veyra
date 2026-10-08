# Veyra Life Desktop 4.0.0

Uma evolução integrada para capturar, planejar, organizar e analisar no computador, preservando os dados, o Android 2.2.0 e o Firebase gratuito.

- Home com próximo passo e sugestões discretas; galeria de 17 widgets, tamanhos/altura, reordenação e restauração do layout.
- Captura universal com interpretação local em português e revisão; Inbox, Spotlight global, comandos e busca com filtros.
- Projetos com tarefas/notas/metas/foco, planejador por horário, dependências, revisão diária/semanal e análises de foco.
- Notas com editor visual básico/Markdown, backlinks, imagens locais, autosave agrupado e rascunhos recuperáveis. Edições concorrentes não sobrescrevem silenciosamente.
- Calendário financeiro, assinaturas, relatórios filtrados e importação CSV com prévia/deduplicação. Cálculos exatos e separação de transferências/faturas preservados.
- Automações locais limitadas, modelos, favoritos/recentes, central de notificações, Não Perturbe, resumos opcionais, Dock lateral e modos de desempenho.
- Diagnóstico sem dados privados, caches por versão e módulos carregados sob demanda. Proteção adicional de acesso a anexos e preservação de arquivos locais em edições parciais.

Instale sobre a versão anterior e conserve seus arquivos de dados. É recomendável exportar um backup em Configurações → Dados antes de atualizar. Anexos continuam no aparelho original; não exigem Blaze. O instalador Windows não tem certificado comercial Authenticode. A atualização possui manifesto Ed25519 e hash SHA-256 verificáveis.

Validação: 51 testes de lógica/protocolo, 30 testes de regras Firebase, integração de sessão/sync/offline/conflitos e testes do executável final, incluindo Glass e os novos fluxos. Uma conta temporária confirmou login Firebase, gravação em produção e fila vazia; seus registros foram removidos. Esse teste não confirma a fila pessoal do usuário nem substitui login humano Google ou um aparelho Android físico.

Medição de execução isolada com 1.500 registros: abertura aproximada de 0,51–0,53 s; reinício com cache de 0,59 s; consultas de snapshot em 10–11 ms e busca em 7 ms; cerca de 524–526 MiB. Dock, Mini Dashboard e Foco juntos levaram aproximadamente 1,01 GiB no total, incluindo a janela principal. São amostras deste Windows 11, sem garantia para outras máquinas.

[Guia dos recursos e limites](PLATFORM-4.0.md) · [Medições e testes](VALIDATION-DESKTOP-4.0.0.md) · [Matriz da evolução](ROADMAP.md).
