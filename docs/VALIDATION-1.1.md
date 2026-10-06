# Validação do Veyra Life 1.1

Verificado em 6 de outubro de 2026 com JDK 17, SDK 35 e emulador isolado Android 15/API 35.

- 18 testes unitários aprovados: 14 no modelo e 4 em finanças.
- 5 testes de persistência aprovados: banco, migração e backups.
- 8 testes de interface aprovados: os 5 fluxos anteriores e 3 novos fluxos (pagamento de conta, ingredientes sem duplicação, metas e contagem regressiva).
- Lint: nenhum erro, 17 avisos e 1 informação. Build release otimizado aprovado.
- APK alinhado para páginas de 16 KB e assinatura v2/v3 verificada.
- Certificado SHA-256 igual ao da versão 1.0. Instalação da 1.1 por cima da 1.0 aprovada; perfil e nome persistiram na atualização. Abertura da Home e presença do novo atalho Rotina confirmadas.
- Pacote `app.veyra.life`; versão 1.1.0/110; mínimo Android 8/API 26. Tamanho: 45.398.251 bytes.
- SHA-256 do APK: `ef60fae7491bc94821a04ed3a92085c47a9bcf75629ffd6506ba5b6995b75850`.
- Captura real da tela de metas em `screenshots/metas-1.1.png`. Dados de teste não fazem parte do APK.

Os 31 testes não representam cobertura completa de todos os módulos. Não foi testado em celular físico. Doses, medidas, finanças e progresso são informados pelo usuário; não há integração bancária ou aconselhamento de saúde. Demais limites da base e da primeira versão estão em `VALIDATION.md` e `RELEASE-1.0.md`.
