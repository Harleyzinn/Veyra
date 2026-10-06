# Veyra Life 1.1.0

Expansão do projeto Android criado a partir da base `veyra-android-main.zip`.

## Novidades

- Central de rotina com progresso diário, atividades atrasadas e atalhos.
- Checklists separados por lista, filtros de pendentes e concluídos.
- Cardápio por dia, navegação semanal e envio dos ingredientes às compras sem repetir o mesmo envio.
- Rotina da casa, responsáveis e cômodos; cuidados dos pets.
- Medicamentos com dose informada pelo usuário, horário, estoque e lembrete; consultas e exercícios.
- Registros de humor, energia e medidas corporais.
- Metas financeiras com valor reservado, restante e barra de progresso.
- Dívidas a pagar/a receber com valor quitado e restante.
- Contas a pagar: pagamento registra uma despesa vinculada, com operação atômica e proteção contra duplicação.
- Revisão de serviços pouco usados e estimativa mensal de economia.
- Contagem regressiva, lista de desejos, presentes, cursos com progresso e candidaturas de emprego.
- Busca, favoritos, lixeira, vínculos e backup reutilizados em todos os registros.
- Build de APK identifica automaticamente a versão e mantém a mesma chave local da versão 1.0.

## Uso

Abra **Mais → Abrir central de rotina**. Os novos módulos também aparecem na busca de módulos. Perfis filtram a lista de módulos; a central permanece acessível para todos os perfis.

Uma conta paga produz uma despesa realizada no dia do pagamento. Para corrigir um pagamento, edite ou exclua a despesa correspondente na planilha. Evite lançar novamente a mesma conta como despesa manual. Dívidas e metas são controles separados e não movimentam saldo automaticamente. Refeições possuem registros independentes por data; os ingredientes enviados ficam no módulo Lista de compras.

Os módulos de bem-estar armazenam informações pessoais. Não sugerem doses ou tratamentos. Lembretes usam WorkManager e podem sofrer atrasos do sistema. Não há sincronização bancária, integração com empregadores ou estoque automático de medicamentos.

## Compilação e distribuição

Android 8+, pacote `app.veyra.life`, versão 1.1.0 (110). O APK local usa a mesma assinatura da 1.0 e pode ser instalado como atualização. Chaves, senhas, SDK, dependências locais e artefatos de compilação ficam fora do Git.

O workflow GitHub gera um APK debug nos artefatos da execução. O APK release assinado com a chave original é gerado localmente por `scripts/build-apk.ps1`.
