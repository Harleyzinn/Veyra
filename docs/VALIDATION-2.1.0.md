# Validação — Veyra 2.1.0

Verificação realizada em 7/10/2026. Pacote `app.veyra.life`, versionCode 210. A evolução financeira não altera o esquema do banco nem a configuração do Firebase.

## Cálculos e regras

138 testes unitários passaram: 106 financeiros, 23 de modelo e 9 de nuvem. Incluem pagamentos parciais de R$ 33,33 em uma fatura de R$ 100,01, restante de R$ 66,68, débito único da conta e quitação posterior. O simulador cobre reserva mínima, centavos, entrada futura que não pode financiar gastos anteriores, cenários negativos, valores inválidos e imutabilidade da projeção original. A conferência cobre normalização de descrições, separação por conta/moeda, recorrências e registros inativos.

## Telas e persistência

8 testes Android passaram no emulador dedicado Veyra_QA/API 35, com dados sintéticos preservados e restaurados ao terminar. Verificam edição do saldo inicial, registro exato de despesa, pagamento parcial persistido e saldo remanescente, simulador sem escrita de lançamentos, ocultação dos valores, sugestões de duplicidade sem exclusão, proteção de favoritos financeiros, bloqueio sem credencial do aparelho e painel vazio sem dados de exemplo.

Capturas das telas foram inspecionadas: [pagamento parcial](screenshots/finance-partial-2.1.png), [resultado do simulador](screenshots/finance-scenario-2.1.png) e [conferência](screenshots/finance-review-2.1.png). As capturas são de dados sintéticos.

Lint debug terminou com zero erros e 23 avisos. O build de release, R8 e lint vital concluíram com sucesso. A assinatura v2/v3 foi validada e o certificado SHA-256 confere com a chave original. O APK final foi instalado por cima da versão existente no emulador, sem desinstalar; abriu normalmente e o processo permaneceu ativo.

SHA-256 do APK: `39116cf3df89bbdf542d66cbe8a183621a3de84d1d073a35781ad8db85fe3ca9`.

## Limites

A margem diária é uma projeção baseada no caixa e compromissos cadastrados, com saldo ao fim de cada dia; dados ausentes alteram os resultados. O simulador não realiza operações bancárias. Duplicidades são sugestões para revisão manual. O histórico exibido depende da janela de dados carregada, informada na tela.

Não foram repetidos nesta mudança os testes administrativos do Firebase descritos na [validação 2.0.1](VALIDATION-2.0.1.md). Login Google, sincronização em dois celulares e Play Integrity continuam dependendo de teste em aparelho real com Google Play Services. Anexos permanecem locais no plano gratuito.
