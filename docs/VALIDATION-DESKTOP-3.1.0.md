# Validação Desktop 3.1.0

Executável final: `apps/desktop/release/win-unpacked/Veyra Life.exe`, versão 3.1.0, Windows 11 build 26200. Os testes usam perfil temporário separado por `--user-data-dir`, sem limpar o perfil pessoal.

## Janela nativa e interface

- Leitura Win32 confirmou ausência de caption e thick frame, região arredondada com cantos externos excluídos e fundo HTML transparente.
- DWM confirmou Acrylic (`DWMWA_SYSTEMBACKDROP_TYPE=3`); redução de efeitos confirmou desativação (`1`).
- Hit-test do cabeçalho retornou `HTCAPTION`: região de arraste nativa. Redimensionamento personalizado alterou os limites nativos com cursor simulado e preservou o recorte.
- Always-on-top, opacidade 80%, click-through nativo e recuperação foram confirmados. A captura recusou click-through.
- Foco: pausa/retomada, modo micro, expansão temporária do menu e restauração do tamanho.
- Captura: nota salva e janela fechada. Finanças, tarefas, clima, calendário, hábitos, cronômetro e Dock abriram sem moldura.
- Temas claro/escuro/sistema, restauração de preferências após reiniciar e recuperação de janela fora da área visível passaram. Capturas estão em `screenshots/desktop-glass-*.png`.

## Dados e sincronização

37 testes unitários, verificação de tipos e lint passaram. 30 testes de regras Firebase passaram; integração de sincronização e sessão validou login, envio, saída, troca de conta, conflitos, retomada offline e rejeição de editor de outro UID.

Firebase de produção: a recusa de BeginTransaction com token de usuário foi reproduzida antes da correção 3.0.1. O protocolo CAS confirmou envio de nota/dispositivo, revisão, marcador, isolamento e conflito. Uma corrida de criação com dois clientes preservou uma versão como conflito. O executável final confirmou login, gravação remota e zero operações pendentes na conta temporária. Registros temporários foram removidos; dados pessoais não foram modificados pelo teste. A fila pessoal do usuário depende de atualizar e sincronizar no próprio perfil.

## Posicionamento e limitações

Testes simulados cobriram escalas 100/125/150/200%, monitores com coordenadas negativas, remoção de monitor, resolução menor, encaixe e limites visíveis. O evento de retorno de suspensão foi emitido no processo para verificar o tratamento; não houve suspensão física do Windows. Não foram testados fisicamente Windows 10, dois monitores, todas as escalas, nem maximização de programas externos. Esses cenários continuam pendentes de validação em equipamento correspondente.

## Desempenho

Amostra curta em repouso, janela principal e nove painéis (dez renderizadores), foco pausado: soma da CPU dos processos aproximadamente 0,64%; soma dos working sets aproximadamente 1,50 GiB; dez contadores de engines GPU disponíveis com máximo de 0%. Parte dos painéis estava com efeitos reduzidos. Esses números não representam desempenho sob carga nem outras GPUs. O Electron mantém isolamento por janela; Dock agrupa módulos em um renderizador. Relógio atualiza a cada 30 segundos, foco somente enquanto ativo/visível, e gestos de resize limitam chamadas em andamento.

## Reproduzir

Na pasta `apps/desktop`, execute `npm run typecheck`, `npm run lint`, `npm test` e `npm run dist`. Depois, em PowerShell, `$env:VEYRA_PANEL_PACKAGED='1'; npx tsx tests/glass-ui.ts`. O teste lê somente os handles do próprio aplicativo. Os testes Firebase exigem os emuladores conforme os scripts existentes; as verificações de produção foram administrativas e temporárias, não são parte da execução comum do aplicativo.
