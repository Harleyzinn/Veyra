# Validação do Veyra Life 1.2

Verificado em 6 de outubro de 2026 com JDK 17, SDK 35 e o AVD isolado Veyra_QA, Android 15/API 35.

- 27 testes unitários aprovados: 23 no modelo e 4 em finanças. Incluem versões numéricas, endereços de atualização, SHA-256, rejeição de hosts falsos/HTTP, sorteios sem duplicação, equipes equilibradas, limites de dados e regras do jogo da velha.
- 5 testes de persistência aprovados: banco, migração e backups.
- 15 testes instrumentados do app aprovados: os 8 fluxos anteriores; desenho salvo/reaberto, sorteio guardado como nota, resposta do adversário, fusos salvos, leitura de QR de imagem e rejeição de APK inválido ou não mais recente.
- Total: 47 testes aprovados. A rodada completa terminou sem falhas.
- Lint: nenhum erro, 20 avisos e 1 informação. Os novos avisos são sugestões de KTX e simplificação da verificação da API mínima.
- APK release otimizado, alinhado para páginas de 16 KB e assinatura v2/v3 verificada. Pacote `app.veyra.life`; versão 1.2.0/120; mínimo API 26.
- Tamanho: 45.431.075 bytes. SHA-256: `e8efc2226c7fe34b6b0a49a5806e9fd9c3fbba898a15e92141336d70e3ce3b14`.
- A publicação também confere o certificado original, o pacote/versão e o SHA-256 informado pelo GitHub após cada upload.
- Teste adicional com uma instalação QA 1.1.9 assinada pela chave original: o app encontrou a release pública 1.2.0, baixou o APK real e liberou o botão de instalação após conferir hash, pacote, versão e certificado. Veja [a tela capturada](screenshots/atualizacao-1.2.png).

O teste completo de instalação pelo atualizador não foi concluído: a imagem Android 15 ATD não contém a tela de autorização de fontes de instalação. Uma tentativa em outro AVD, com Android 17/API 37 em prévia, apresentou ANR de foco na abertura da tela; essa prévia não foi validada. Não há confirmação de instalação pelo atualizador nem de preservação dos dados por esse fluxo em um aparelho físico.

O Play Protect não foi executado neste emulador e não há uma aprovação de segurança do Google. O usuário relatou apenas um aviso de app não verificado. A assinatura/hash validam autenticidade e integridade; não substituem a análise do Google. Veja `PLAY-PROTECT.md`.

Não foi testado em celular físico. Os testes não cobrem todos os módulos, condições de rede, marcas de aparelho ou versões do Android. O download automático depende do agendador e de uma rede sem cobrança por uso; a instalação depende da confirmação do sistema.
