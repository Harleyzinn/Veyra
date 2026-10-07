# Validação — Veyra 2.0.1

Configuração realizada em 7/10/2026 no projeto `veyra-life-harleyzinn`, pacote `app.veyra.life`. Firebase Android app registrado, SHA-1/SHA-256 originais cadastrados, Auth por e-mail/senha e Google confirmados via API administrativa e configuração Android oficial baixada. O arquivo real é ignorado pelo Git. A API de cobrança confirmou `billingEnabled=false`.

Firestore `(default)` Standard/Native em `nam5`, cota gratuita confirmada via CLI. O primeiro deploy habilitou a API e criou o banco automaticamente; ele não está em São Paulo. Regras compiladas, índices implantados e regras publicadas com sucesso.

App Check e Play Integrity APIs habilitadas. Configuração Play Integrity registrada e consultada novamente: TTL 3600s, versão fora da Play Store permitida, licença da loja não exigida e `MEETS_DEVICE_INTEGRITY`. A obrigatoriedade não foi habilitada; depende de métricas do APK em dispositivo real. Nenhum token debug foi registrado.

## Verificação nesta versão

- Build release, R8 e lint concluídos; zero erros de lint, 23 avisos e uma informação. Assinatura v2/v3 validada com a chave original. APK final instalado por cima da versão anterior no AVD, abertura concluída e processo permaneceu ativo.
- Suítes financeiras/modelo do script de build preservadas; 9 testes unitários de nuvem passaram (6 de dinheiro exato e 3 da política de anexos locais).
- 4 testes Android de persistência passaram no AVD próprio Veyra_QA/API 35: migração, backup protegido, importação atômica e edição remota preservando arquivo local. O teste novo também confirma que limpar cache não pode apagar um anexo sem cópia na nuvem.
- Teste no Firebase real: cadastro e login com e-mail de domínio reservado, rejeição de leitura anônima e de registros por usuário não verificado, leitura autorizada do caminho próprio e rejeição de outro UID. A verificação do e-mail da conta sintética foi definida administrativamente somente para testar regras, sem enviar mensagens. A conta temporária foi excluída após o teste; nenhuma conta real foi alterada.

Uma primeira execução do teste real consultou o perfil (permitido ao próprio usuário mesmo sem e-mail verificado) em vez da coleção privada. O teste foi corrigido para `users/{uid}/notes/{documentId}` e passou. O teste usa leitura de documento inexistente (404 autorizado versus 403 negado), sem alegar CRUD ou sincronização completa em dois dispositivos.

## Limites da validação

Google Credential Manager, envio/recebimento real de verificação e recuperação por e-mail, sincronização em dois celulares e atestado Play Integrity ainda precisam ser verificados num aparelho com Google Play Services. A ativação administrativa dos provedores não equivale a esses testes. Storage não foi provisionado nem implantado nesta versão; arquivos são locais. A aprovação do Play Protect continua a cargo do Google.

APK publicado como atualização 2.0.1/versionCode 201, sem substituir a release 2.0.0. O mecanismo de atualização e a assinatura original foram preservados.
