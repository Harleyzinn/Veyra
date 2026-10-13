# Fases 0 e 1 — validação executada

12/10/2026, com operações em UTC após meia-noite de 13/10. Nenhum teste Android,
desktop ou Firebase foi reexecutado: seus fontes/configurações não foram alterados.

- **11 testes de unidade aprovados**, executados com unittest/Python 3.12.14.
- Escrita/leitura real, atualização por hash e proteção de texto humano em saída gerada.
- Persistência entre instâncias, fontes e estados mantidos; expiração e limite de busca.
- FTS5 literal, filtros, idempotência de eventos/indexação, IDs duplicados e notas inválidas.
- Caminhos traversal/absolutos/dispositivos Windows e credenciais reconhecidas bloqueados.
- Conteúdo com instruções externas permaneceu dado; nenhum comando foi executado.
- Backup restaurado, SQLite íntegro, busca no banco restaurado e reconstrução de índice
  a partir de Markdown numa base nova. Arquivos alterados/manifestos inseguros rejeitados.
- Baseline de arquivos gerados aceita somente hashes exatos de saída distribuída; edições
  não correspondentes exigem revisão humana e não são sobrescritas.
- Timestamps com offsets são normalizados para UTC no índice e a selagem protege saídas modificadas.
- Auditoria real guarda operações/durações/referências, sem corpos de notas nas entradas.

Verificação end to end do **Vault real** via `tools/omega/verify_foundation.py`:

- 27 notas válidas antes do handoff, nenhum erro de indexação.
- Novo processo recuperou quatro resultados para financeiro com referências.
- SESSION_BOOTSTRAP STANDARD gerado com limite de caracteres e fronteira de confiança.
- Backup `20261013T003350-0a58def8`, SHA-256
  `69358f786569edf3323f0a31a63f132951fcccdd9cb89b0dcb0fc3c2dd204eff`.
- Restauração em destino novo `.omega/verified-restore-20261013T003350-0a58def8`:
  **38 arquivos com hashes conferidos**, SQLite integrity_check ok.
- Nova indexação sem alteração produziu zero mudanças. Handoff persistido no Vault.

O primeiro método de restauração por rename de diretório falhou com acesso negado no
workspace Windows/OneDrive; a implementação passou a copiar para destino novo, verificar
hashes e marcar conclusão. Os testes e a restauração completa foram repetidos após a
correção e passaram. Os primeiros testes no TEMP restrito do ambiente não tinham permissão;
foram executados em diretórios temporários próprios dentro do workspace.

As notas ADR e de lições foram adicionadas após esse snapshot; o dashboard apresenta a
contagem atual. Relatório bruto operacional: `.omega/validation.json` (ignorado pelo Git).

Conferência final: **30 notas válidas, zero erros**. Backup completo
`20261013T004143-6da15fd7`, SHA-256
`5c2d7fe4f3a1de61e52cd43a42910629e4c87e256897cf32e56ade6e59b6f803`,
restaurado em `.omega/final-restore-20261013T004143-6da15fd7` com **41 arquivos
conferidos e banco íntegro**. O backup reindexa e compara os hashes do Vault com o
snapshot antes de declarar sucesso. Saídas geradas foram seladas para inicialização
segura em cópias novas.

## Limites explícitos

Obsidian 1.12.7 instalado; propriedades/Bases configurados no novo Vault. Cadastro/abertura
pela interface Obsidian não foi automatizado nem visualmente certificado. Nenhum Vault
pessoal existente foi modificado. Busca semântica, consolidação automática, detecção de
contradições semânticas, GitHub incremental, ponte ChatGPT/MCP e execução de agentes
aguardam próximas fases. Aprovação para publicação/ações externas sensíveis continua humana.

Apenas presença de fontes não verifica sua verdade. Limites são de caracteres, não tokens
exatos. Atualização por hash é otimista; não coordena editores externos simultâneos. Backups
não são criptografados; padrões de segredos não substituem revisão. A CLI local usa a conta
Windows, não autenticação de API. Nenhum dado foi enviado ao Firebase ou a um serviço de IA.
