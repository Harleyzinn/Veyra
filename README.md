# Veyra Finanças

Seu dinheiro, com clareza. Receitas, gastos, contas, cartões, planejamento e economia em uma experiência para Android e web instalável no iPhone.

- [Abrir o web-app](https://harleyzinn.github.io/Veyra/)
- [Baixar o APK Android](https://github.com/Harleyzinn/Veyra/releases/tag/v3.0.0)
- [Funcionalidades, arquitetura, instalação e limitações](docs/FINANCE-3.0.md)
- [Validação da versão 3.0](docs/VALIDATION-3.0.0.md)

## No celular e no computador

Android 8+: APK com pacote `app.veyra.life` e a chave original, para atualizar sem reinstalar. Faça backup antes da atualização. A nova experiência usa o banco existente. Configurações → Abrir experiência anterior e conta mantém login, sincronização, tarefas, notas e os módulos anteriores.

No iPhone, abra o web-app no Safari e use Compartilhar → Adicionar à Tela de Início. No computador e Android, use o navegador ou instale a PWA quando o navegador oferecer essa opção. A PWA funciona offline após o primeiro carregamento; seus dados ficam no navegador, com backup JSON e CSV. Esta entrega não inclui aplicativo iOS nativo nem sincronização Firebase no web-app.

## O que há de novo

Início financeiro, indicador explicável “Você pode gastar”, modos simples/completo, receitas por dia útil com calendário configurável, confirmações sem duplicar saldo, transferências, faturas, parcelas, metas, orçamentos, relatórios acessíveis e 24 temas completos com prévia e favoritos. Importações validam dados e mostram prévia antes de gravar.

Feriados estaduais, municipais e móveis são manuais. Registro automático de receitas e novos alertas são executados ao abrir o aplicativo. Preferências novas ficam salvas no usuário local e no backup; as regras de segurança Firebase não foram alteradas. Nenhuma conexão bancária é necessária.

## Desenvolvimento

Node 24; JDK 17 e Android SDK 35 para APK. Dependências fixadas no projeto existente:

```powershell
npm ci --prefix apps/desktop
node apps/finance/build.mjs
node apps/desktop/node_modules/typescript/bin/tsc -p apps/finance/tsconfig.json
node apps/desktop/node_modules/tsx/dist/cli.mjs --test apps/finance/tests/domain.test.ts apps/desktop/tests/finance.test.ts
./scripts/build-apk.ps1
```

O build web gera os assets Android. GitHub Actions compila ambos; `finance-web.yml` publica apenas o shell da PWA no Pages. Banco, dados do usuário, senhas, configuração privada Firebase e assinaturas não são versionados.

## Continuidade e documentação anterior

O [Omega Brain](docs/omega/README.md) mantém documentação operacional local, SQLite/FTS5 e handoffs com proveniência. Busca semântica e agentes autônomos não estão implementados.

O aplicativo Windows 4.2 e os módulos anteriores permanecem no repositório. [Histórico 2.x e instruções anteriores](docs/README-2.x-HISTORY.md) · [Windows](docs/DESKTOP.md) · [Firebase](docs/FIREBASE-SETUP.md) · [Play Protect](docs/PLAY-PROTECT.md) · [Arquitetura financeira anterior](docs/FINANCE-ARCHITECTURE.md).

Fonte Manrope embarcada, licença [SIL OFL](docs/Manrope-OFL.txt). Exemplos aparecem apenas nos testes e capturas de validação; a instalação nova começa sem transações fictícias.
