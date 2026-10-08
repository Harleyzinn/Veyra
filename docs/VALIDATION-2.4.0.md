# Validação Android 2.4.0 — 8 de outubro de 2026

Pacote `app.veyra.life`, versão `2.4.0`, código `240`. Mesma assinatura original do Veyra; Android 8.0 ou superior, alvo Android 15.

## Cálculos e compilação

115 testes financeiros aprovados, incluindo quatro cenários novos: conservação dos totais em todos os agrupamentos, semanas na fronteira do mês e períodos zerados; data de pagamento e fatura sem duplicação; agrupamento da cauda sem perder centavos e contas de nomes iguais separadas; previsão excluída e período inválido rejeitado. Os 25 testes de modelo e 9 de nuvem da versão anterior continuam aplicáveis, sem mudanças nesses módulos.

Debug e release compilados, R8 concluído, assinatura verificada. Lint: zero erros, 23 avisos existentes e uma informação. Nenhuma dependência externa de gráficos adicionada.

## Interface e integridade

Verificação no emulador dedicado Veyra_QA com dados sintéticos no espaço visitante. Os testes preservam/restauram registros e preferências. Os seis formatos foram revisados visualmente: barras, linhas, área, rosca, pizza e tabela. Rosca/pizza mostraram R$ 300,01, divididos entre R$ 200,00 e R$ 100,01; a tabela confirmou entradas de R$ 50,00 e resultado negativo de R$ 250,01.

Dois fluxos de interface aprovados: o relatório por pagamento/CSV/PDF e a troca dos seis gráficos com agrupamento mensal, resultado negativo e privacidade. O teste dos gráficos foi reexecutado após ajustar a leitura do espaçamento monetário localizado, sem alterar o código do app. APK release instalado sobre a versão existente no emulador, versão 240/2.4.0 confirmada e nenhum erro fatal no log de inicialização.

[Linhas](screenshots/chart-linhas-2.4.png) · [Área](screenshots/chart-area-2.4.png) · [Barras](screenshots/chart-barras-2.4.png) · [Rosca](screenshots/chart-rosca-2.4.png) · [Pizza](screenshots/chart-pizza-2.4.png) · [Tabela](screenshots/chart-tabela-2.4.png) · [Resultado negativo](screenshots/chart-result-2.4.png).

SHA-256 do APK: `8eb0c594d72109dfde61c7315156dacc37eb1c074e581da2b26e1f693794b7c6`.

Certificado SHA-256: `8595f5acf3676e236dd32198a08e71bf5d04d875697d395f5bccd6b7a4498f21`.

## Limites

Revisão em emulador, sem certificação Play Protect/Play Integrity ou teste em aparelho físico. Firebase e protocolo de sincronização não foram alterados; segue gratuito, com anexos locais. Exportação de imagem do gráfico não foi adicionada: CSV/PDF continuam exportando os lançamentos do relatório. As opções novas ficam no Android, em Gráficos da seleção; os gráficos antigos do resumo e do desktop permanecem disponíveis.
## Publicação pública

[Release v2.4.0](https://github.com/Harleyzinn/Veyra/releases/tag/v2.4.0) publicada do commit `4bc5203`. A API pública confirmou esta versão como mais recente, estável e sem rascunho. O APK foi baixado sem credenciais; hash igual ao arquivo local e ao digest do GitHub, com certificado original verificado. O atualizador usa essa mesma fonte; a instalação exige a confirmação normal do Android.