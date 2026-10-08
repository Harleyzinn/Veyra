# Validação Android 2.3.0 — 8 de outubro de 2026

Pacote `app.veyra.life`, versão `2.3.0`, código `230`. Android 8.0 ou superior; alvo Android 15. A assinatura permanece a original do Veyra.

## Verificações locais

- 145 testes unitários aprovados: 25 de modelo/organização, 111 financeiros e 9 de nuvem.
- Compilação debug e release, otimização R8 e lint concluídos. Lint: zero erros e 23 avisos existentes.
- Relatórios distinguem a data da compra da data do pagamento, excluem transferências e evitam duplicar compras atuais no cartão com pagamento da fatura.
- Cenários cobrem moeda, período inválido, totais da seleção e vencimentos com fatura parcialmente paga.
- 11 fluxos de interface aprovados: a rodada geral aprovou nove; os dois afetados pela automação de clique/rolagem passaram na reexecução direcionada após corrigir a espera e os limites visíveis.
- Interface revisada no emulador dedicado Veyra_QA, Android 15, com dados sintéticos no espaço visitante. Os testes preservam e restauram os registros e preferências desse espaço.
- O fluxo novo de relatório confirmou um gasto de R$ 42,00 comprado no mês anterior e pago no atual. CSV contém a data de pagamento; o teste gerou um PDF real e verificou sua assinatura de arquivo.
- O resumo confirmou fatura de R$ 300,00 com R$ 100,00 já pagos, exibindo R$ 200,00 restantes e excluindo a pendência em USD.
- Notas foram ordenadas por título mantendo a busca aplicada.
- APK release instalado com substituição no emulador, sem desinstalação; versão 230/2.3.0 confirmada, preferências anteriores presentes, processo aberto e nenhum erro fatal no log de inicialização.

[Relatório no emulador](screenshots/finance-report-2.3.png) · [Vencimentos no emulador](screenshots/finance-agenda-2.3.png).

## Integridade

SHA-256 do APK: `49cd800a3a84551d614280e4a0ba18b61d2dc6b39152349302aabde82d867082`.

Certificado SHA-256: `8595f5acf3676e236dd32198a08e71bf5d04d875697d395f5bccd6b7a4498f21`.

## Limites

Esta rodada não certifica Play Protect ou Play Integrity, nem substitui teste em aparelho físico. Não houve alteração de regras Firebase, esquema de sincronização ou plano: segue Spark, com anexos locais. Login Google e integração com serviços externos não foram revalidados nesta versão. CSV/PDF são relatórios, não backups completos. Exporte um backup no app antes de instalar a atualização.
## Publicação verificada

Release [v2.3.0](https://github.com/Harleyzinn/Veyra/releases/tag/v2.3.0) publicada a partir do commit `89d35e6`. Consulta pública sem credenciais confirmou `v2.3.0` como release mais recente, estável e sem rascunho. O APK foi baixado pelo link público; seu SHA-256 corresponde ao arquivo local e ao digest do GitHub. A assinatura do APK baixado também foi verificada com o certificado original. Isso confirma o arquivo oferecido pela fonte usada pelo atualizador; a instalação continua sujeita à confirmação do Android.