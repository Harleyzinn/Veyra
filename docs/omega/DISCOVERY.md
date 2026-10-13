# Discovery — 12/10/2026

Inspeção real, sem modificar os aplicativos Android/Windows, Firebase ou outros Vaults.

| Fonte | Evidência | Conclusão |
|---|---|---|
| Git local | HEAD `19164ee30a05ae3be96d2c24f07736d44aa844b5`, árvore inicialmente limpa | Android 2.4 publicado no histórico |
| GitHub | `git ls-remote origin refs/heads/main` retornou o mesmo SHA | Acesso de leitura e main conferidos; não é sync incremental |
| Android | app/build.gradle.kts e settings.gradle.kts | Kotlin/Compose, Android 8+, versão 2.4.0, módulos core/feature |
| Windows | apps/desktop/package.json | 4.1.0, Electron/React/TypeScript, sql.js |
| Firebase | firebase/package.json e docs/FIREBASE-SETUP.md | Contratos/testes de regras presentes; configuração remota não revalidada |
| iOS | busca de Swift/Xcode no código não encontrou implementação | Planejado, sem projeto iOS identificado neste repositório |
| Obsidian | C:/Program Files/Obsidian/Obsidian.exe e configuração local de Vaults | Aplicativo instalado; outros Vaults existentes preservados |
| Python | Runtime local Codex: Python 3.12.14, SQLite 3.53.1, CREATE FTS5 real | Fundação executável sem instalar dependências pip |
| Integrações IA | Acesso Codex ao workspace | Leitura local disponível; ponte ChatGPT/MCP dedicada não configurada |

Nenhum conteúdo dos Vaults pessoais existentes foi importado. Nenhuma credencial foi lida para esta descoberta. Caminhos protegidos de assinatura, cliente Firebase, caches e bancos pessoais ficam fora da indexação. O índice lê somente o novo Vault.

Arquitetura: Vault como fonte humana; SQLite/FTS5 derivados; CLI Python independente do Veyra e sem servidor. Dados e backups em `.omega` ignorada pelo Git. SQLite vetorial local será avaliado com corpus/benchmarks antes de acrescentar dependências. Chroma/Qdrant não justificam serviço extra na fundação.

Windows/OneDrive: banco SQLite local deve ter um escritor por computador; não operar o mesmo banco em máquinas simultâneas. O backup usa a API SQLite, não copia um banco aberto de forma cega. Não existe armazenamento de segredo ou criptografia automática nesta fase. O acesso local segue permissões da conta Windows.
