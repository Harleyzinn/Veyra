# Validação Desktop 4.0.0

Windows 11 build 26200, x64, Electron 44.7.0. Testes de execução usam perfis temporários separados; nenhum teste apaga ou sobrescreve o perfil pessoal. Instalador: `apps/desktop/release/VeyraLife-Setup-4.0.0.exe`. Binário validado: `release/win-unpacked/Veyra Life.exe`, gerado pelo mesmo build NSIS.

## Resultado

- Typecheck e lint passaram. 51 testes unitários/protocolo passaram: dinheiro exato, previsão/faturas/parcelas/recorrências, backups compatíveis, anexos, cache criptografado, recuperação offline, isolamento de UID, conflitos/outbox, parser/filtros, vínculos/backlinks, dependências/ciclos e cadeia de 5 mil tarefas, configurações, automações e updater.
- 30 testes de Security Rules passaram no emulador. Integração real Auth/Firestore validou duas sessões/dispositivos, revisão, conflito, retomada offline e isolamento. Teste de sessão no Electron validou login, sync, logout, troca de conta e rejeição de editor antigo. Recusas PERMISSION_DENIED dos testes negativos são esperadas. As ferramentas Firebase/JDK emitiram avisos de depreciação; não foram erros do Veyra.
- A interface em execução confirmou financeiro (saldo exato), Markdown autosave, Kanban, captura, mini janela e persistência após reiniciar. A suíte nova foi executada também no binário empacotado: galeria/reset/largura, captura revisada com centavos exatos, comando de tema, filtros, Inbox sem perda do original, projeto contextual, planner, backlink, concorrência de notas com rascunho e recuperação como cópia, modelos, CSV prévia/idempotência/export filtrado, editor visual com negrito persistido, novas abas financeiras e telas/Brief sem erro JavaScript.
- A concessão de arquivo recusou um caminho fornecido diretamente pela IPC. Prévia raster local funcionou; SVG não foi renderizado. Diagnóstico não retornou conteúdo/tokens/caminhos dos dados sintéticos. O renderer não tem `require`, Node ou acesso de rede.
- Glass no EXE: leitura Win32 confirmou janela frameless, canto recortado e transparência; DWM informou Acrylic (`3`). Pin, opacidade, click-through/recovery, resize, micro/compact, foco pausa/retomada, quick save/close, widgets, claro/escuro e preferências após reinício passaram. Geometria multi-monitor e DPI 100/125/150/200% foram cobertos por casos simulados, sem afirmar ensaio físico.
- Firebase de produção: conta temporária verificada confirmou perfil, dispositivo/nota, commit CAS, revisão/conflito, marcador e rejeição de UID alheio. O EXE 4.0.0 confirmou login Firebase, envio de nota, leitura remota e zero pendências. A conta e os documentos sintéticos foram removidos. A fila pessoal do usuário não foi usada como fixture e não está declarada resolvida por esse teste.
- Google: servidor de retorno real em localhost testado com origem, content-type, nonce incorreto e caminho arbitrário recusados; retorno correto aceito. O token desse teste é sintético e não comprova login humano na conta Google. O login de produção acima usa Auth de e-mail da conta temporária.

## Medições

1.500 registros sintéticos no executável: 1.000 notas e 500 despesas. Mesmo Windows e harness; 12 consultas por amostra. Não é cold boot físico: o teste não limpa cache de disco do Windows. A primeira amostra mede abertura com perfil isolado; a segunda, reinício com os dados locais existentes. Resultados não são SLA nem benchmark estatístico.

| Métrica | Base 3.1.0 | 4.0.0 |
| --- | ---: | ---: |
| Abertura com perfil isolado | 1.449 ms | 509–529 ms |
| Snapshot, mediana | 17,7 ms | 10,3–10,5 ms |
| Busca existente, mediana | 6,5 ms | 7,0–7,1 ms |
| Memória total dos processos | 514,8 MiB | 524,5–525,9 MiB |
| CPU na amostra de repouso | 0% | 0% |
| Reinício com cache de 1.500 registros | Não medido | 586 ms |
| Abertura de Dock + Mini + Foco | Não medido | 347 ms no total |
| Memória total com esses três painéis | Não medido | 1.035,4 MiB |

A busca existente não ficou mais rápida; o ganho medido foi no snapshot e na abertura. O custo de RAM permanece significativo e cresce com janelas independentes. Na amostra logo após abrir três painéis, CPU total ficou em cerca de 3,8%; o processo GPU usou aproximadamente 141,3 MiB e 1,27% de CPU. Isso mede o processo GPU, **não** utilização da placa gráfica. A amostra durante consultas ficou próxima de 6,03% de CPU; a janela temporal do contador Electron inclui atividade recente, sem precisão de profiler contínuo.

Diagnostics instrumenta requisições Firebase: totais, leitura, escrita e falhas HTTP, por execução. Não converte requisições em documentos faturados e não oferece estimativa de cobrança. A preparação de perfil deixa de ocorrer em cada ciclo; o marcador continua sendo consultado a cada 30 s e a persistência lastSync é limitada a cinco minutos. Dados não modificados reutilizam cache por versão/UID/período; notas agruparão saves e rascunhos. Nenhuma dependência foi adicionada.

## Evidência reproduzível

Em `apps/desktop`: `npm run typecheck`, `npm run lint`, `npm test`, `npm run test:ui`, `npm run test:platform`, `npm run test:packaged`, `npm run test:glass`, `npm run test:performance`. Para testar o binário nos harnesses, use `VEYRA_PLATFORM_PACKAGED=1` e `VEYRA_PANEL_PACKAGED=1`. Na raiz, `./scripts/test-firebase-security.ps1 -Desktop -JavaHome <JDK21-ou-superior>`. Rede local, inicialização de aplicativos e leitura Win32 podem exigir permissões do ambiente de execução. Dados/perfis/resultados `.qa` são ignorados pelo Git.

Captura de referência sintética: `screenshots/desktop-platform-home.png`. A documentação cobre também [limites funcionais](PLATFORM-4.0.md) e [todos os grupos do pedido](ROADMAP.md).

## Pendências de ambiente e distribuição

Login humano Google, confirmação física PC ↔ celular, Play Integrity/Play Protect, Windows 10, monitores físicos adicionais/remoção e suspensão real não foram certificados. O contrato Android e banco permanecem inalterados; dois clientes e Rules não substituem aparelho físico. Performance pode variar com máquina, disco, antivírus, GPU e volume de dados. Anexos continuam locais. Canal beta, plugins externos, IA externa, integração bancária e certificado Authenticode comercial não fazem parte desta release.

Atualizador público: manifesto e instalador da release precisam ser baixados e verificados após a publicação; a confirmação é registrada abaixo quando concluída. A assinatura Ed25519 não implica aprovação SmartScreen/Play Protect. O APK atual permanece 2.2.0 e a release desktop usa `make_latest=false` para preservar seu canal.
