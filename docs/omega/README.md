# Veyra Omega Brain — fundação local

Fases 0 e 1: código real para notas, metadados SQLite/FTS5, contexto textual limitado,
dashboard, eventos idempotentes, auditoria, backup e restauração isolada.
Não requer o app Veyra em execução, Firebase ou assinatura de IA.

## Instalação no Windows

Use Python **3.12 ou superior**, com SQLite/FTS5. O script procura primeiro
`OMEGA_PYTHON`, depois o runtime Python do Codex, depois Python real no PATH.
O alias da Windows Store não é usado. Para outro Python:

```powershell
$env:OMEGA_PYTHON='C:/caminho/Python312/python.exe'
./scripts/omega.ps1 init
./scripts/omega.ps1 index
./scripts/omega.ps1 search 'financeiro gráficos'
./scripts/omega.ps1 context 'alterar financeiro' --level STANDARD
./scripts/omega.ps1 dashboard
./scripts/omega.ps1 backup
./scripts/omega.ps1 health
```

Se a política da sessão bloquear scripts, execute o script local revisado com
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/omega.ps1 health`.
Isso não muda permanentemente a política do Windows. Não exige pip.

No Obsidian, escolha **Abrir pasta como Vault** e selecione a pasta
`Veyra-Omega-Brain` deste repositório. Abra `00-COMMAND-CENTER/OMEGA-DASHBOARD.md`.
Bases é um recurso complementar; o dashboard Markdown funciona sem ele.
Não é necessário plugin comunitário nem alterar seus Vaults pessoais.

Antes de versionar alterações em arquivos gerados, execute `./scripts/omega.ps1 seal`.
Isso registra os hashes exatos das saídas geradas atuais para que um clone novo
possa reconhecê-las sem confiar apenas em uma propriedade da nota. Edições humanas
que não correspondem ao registro local são recusadas. Um clone com baseline
desatualizado preserva os arquivos e exige revisão, em vez de sobrescrever texto.

## Notas e segurança

Campos obrigatórios: UUID, título, classe de memória, estado de verificação,
timestamps com fuso e fontes. Frontmatter usa um subconjunto YAML estrito:
valores JSON por linha. Strings precisam de aspas, listas são inline. Isso evita
um parser YAML extra, tags executáveis e coerções ambíguas. YAML arbitrário/multilinha
é rejeitado com erro de indexação e a fonte é preservada para revisão.

`store caminho.md --input arquivo.md` cria uma nota validada. Para editar,
passe `--expected-hash SHA256_DO_ARQUIVO_ATUAL`. A atualização é otimista, com
duas verificações; não substitui um sistema de locks compartilhado com Obsidian.
Evite editar a mesma nota enquanto executa uma atualização CLI. Arquivos gerados
editados manualmente são protegidos; restaure a versão gerada/revise o conflito,
nunca remova o texto humano automaticamente.

Busca textual tem limites, filtros de classe, preferência por estados verificáveis,
referências e exclusão de notas expiradas. Estado `verified` é uma afirmação
registrada, não uma certificação automática de verdade. Recuperação não executa
texto das notas. Contexto MINIMAL/STANDARD/DEEP usa limites de caracteres;
tokenização exata e avaliação de suficiência são próximas fases.

Bootstrap, dashboard e saúde gerados são saídas derivadas e ficam fora da busca
de conhecimento, evitando que um resumo se torne evidência de si mesmo. Use health
para métricas operacionais. A matriz histórica do produto não substitui os guias
e o estado atual do código.

Segredos e dados pessoais não entram no Vault. A proteção por padrões de credenciais
é limitada; não substitui revisão. Banco, auditoria e backups ficam em `.omega`
e não são publicados pelo Git. Backups não são criptografados. Não compartilhar
o banco via OneDrive entre escritores simultâneos.

## Backup, reconstrução e restauração

`backup` cria ZIP versionado com notas/configurações e snapshot consistente do banco;
reindexa antes, confere notas contra os hashes do snapshot e valida integridade SQLite.
Evite edição concorrente durante a operação; mudanças detectadas fazem o backup falhar
em vez de declarar uma cópia coerente. `restore ZIP DESTINO` só aceita um destino NOVO
dentro de `.omega` e nunca substitui o Vault em uso:

```powershell
./scripts/omega.ps1 restore '.omega/backups/ID.zip' '.omega/recovery-review'
```

A cópia concluída contém `vault/`, `metadata.sqlite3` e `RESTORE-VERIFIED.json`.
Uma cópia sem essa marca não deve ser tratada como recuperação concluída. Revise os arquivos antes de qualquer
restauração manual ao Vault ativo. Para reconstruir apenas índices, guarde o banco
antigo e reindexe numa cópia isolada do repositório. Auditoria histórica não é
reconstruível do Markdown; ela vem do backup SQLite. Manifestos verificam corrupção,
não autenticam backups desconhecidos. Exclusão/retention automática não implementada.

## Testar

```powershell
& $env:OMEGA_PYTHON -m unittest discover -s tools/omega/tests -v
```

Os testes usam diretórios temporários, escrita/leitura real, FTS5 real e ZIP/SQLite
restaurados. Consulte [VALIDATION.md](VALIDATION.md) para resultados executados.

## Fases seguintes

Fase 2: consolidação, resumos, proveniência granular, qualidade e memória temporal.
Fase 3: commits/issues/PRs/releases do GitHub, incremental e idempotente.
Fase 4: ponte autenticada compatível com ferramentas disponíveis, sessões/handoffs.
Fase 5: papéis executados por etapas autorizadas, sem rede de processos inúteis.
Fase 6: command center com monitoramento real. Fase 7: embeddings locais, grafo
tipado/impacto/contradições. Fase 8: hardening e desempenho.

Não existem embeddings, dispatcher autônomo, servidor HTTP, ChatGPT conectado,
sync automático GitHub nem execução de agentes nesta entrega. A integração Codex
local usa AGENTS.md e a CLI; uma nova sessão pode pesquisar o SQLite persistido.

Referências técnicas: [Vault local no Obsidian](https://obsidian.md/help/vault),
[Bases](https://obsidian.md/help/bases/syntax), [SQLite FTS5](https://www.sqlite.org/fts5.html),
[SQLite Backup API](https://www.sqlite.org/backup.html). Biblioteca padrão mantém
baixo custo operacional; FastAPI/Pydantic/embeddings só entram quando a fase exigir.
