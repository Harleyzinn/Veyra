# Veyra Life 2.0

## Instalar no celular

1. Faça um backup nas Configurações do Veyra, caso já use uma versão anterior.
2. Baixe `Veyra-2.0.0.apk` da release oficial e envie ao celular pelo cabo ou outro meio que você já use.
3. Abra o arquivo no Android e autorize a instalação pela origem escolhida quando o sistema solicitar.
4. Toque em **Instalar**. A chave original permite atualizar por cima das versões anteriores de `app.veyra.life`. Na primeira instalação, escolha nome, perfil e tema.

Requer Android 8.0 ou superior. O pacote é `app.veyra.life`: esta versão pode coexistir com o Veyra original, cujo pacote é `app.veyra.android`. Não é necessário root, login ou assinatura de serviço. O APK é uma versão release assinada localmente. Não foi publicado na Play Store.

## Começar a usar

- **Hoje:** tarefas do dia, fluxo financeiro, clima, hábitos, água e acesso ao foco. O botão **+** abre o cadastro; a lupa faz a busca global.
- **Tarefas:** use o atalho da Home para criar rapidamente, filtrar por prazo/prioridade e concluir. O cadastro completo permite recorrência, lembrete, tags e vínculos.
- **Agenda:** calendário mensal, dia, semana e Kanban. Em Kanban, use Começar e Concluir para mudar de etapa.
- **Finanças:** use **+ Entrada** e **− Gasto**; o modo avançado contém contas, cartões, datas, parcelas, recorrência e comprovantes. **Explorar** abre movimentações, a pagar/receber, fluxo futuro, calendário, contas, cartões, orçamentos, metas, assinaturas, dívidas, patrimônio, relatórios, alertas, regras, configurações e lixeira. Cadastre uma conta com saldo inicial antes de registrar os lançamentos vinculados. Uma compra de cartão é um gasto; o pagamento da fatura debita a conta sem somar outro gasto. Veja [o guia completo](FINANCE-GUIDE.md).
- **Notas:** crie notas, pastas, tags, favoritos, links e diário. O editor salva notas automaticamente quando o título está preenchido; Salvar conclui a edição. A prévia aceita títulos e negrito Markdown básico.
- **Clima:** em Previsão real, ative a consulta online, busque uma cidade e toque no resultado. Você pode cadastrar várias cidades e alternar entre elas. Meu clima permite escolher temperatura e condição manualmente; é uma personalização, não uma previsão real.
- **Mais:** hábitos, estudos, metas, viagens, listas de compras, casa, veículo, Pomodoro, assistente e ferramentas. A estrela fixa módulos como favoritos.
- **Configurações:** temas, perfil, cartões da Home, PIN/biometria, notificações, importar extratos e exportar/restaurar backup.

## Dados e cuidados práticos

Os dados ficam no aparelho. Firebase está implementado e requer [configuração do projeto e novo build](FIREBASE-SETUP.md); este APK não tem um projeto Firebase configurado. Exporte um backup JSON antes de desinstalar ou trocar de celular. A senha opcional protege o arquivo com AES-GCM; o banco local não possui criptografia adicional. PIN e autenticação do Android protegem a interface. O banco convidado original é preservado e separado dos bancos por conta.

A exportação da tela Finanças é um relatório com 15 colunas, incluindo transferências, contas, cartões, status, vencimento, competência, tags e notas. Respeita os filtros de movimentações e não substitui o backup JSON. A importação de extratos nas Configurações aceita CSV do relatório e `date,title,amount,category`, datas `AAAA-MM-DD`, valor negativo para despesa. Há importação OFX com prévia e exportação PDF.

Cartões calculam ciclos de fechamento/vencimento, faturas abertas e limite disponível. Investimentos e valores de ativos são informados manualmente; não há conexão bancária ou câmbio. Clima online depende da internet e do Open-Meteo; o cache mostra a última consulta. O modo manual funciona offline. Notificações e reconhecimento de voz dependem das permissões e serviços do Android. Alertas financeiros e widget financeiro são opcionais; valores de notificações e widget ficam privados por padrão.

Os exemplos das capturas de tela são dados do emulador de teste. O APK começa com seus registros vazios.

Na versão 1.1, abra **Mais → Abrir central de rotina** para os novos módulos. Instale por cima da 1.0 para manter seus registros; faça um backup antes da atualização.
Na versão 1.2, abra Configurações → Atualizações. As próximas versões serão consultadas automaticamente; o Android pede confirmação para instalar. Os novos módulos estão em Mais. Veja RELEASE-1.2.md e PLAY-PROTECT.md.

Na versão 2.0, o financeiro recebe a nova central e migração aditiva do banco. Veja [mudanças e limites](RELEASE-2.0.md). A assinatura não garante aprovação do Play Protect; a análise e os avisos do Google dependem do próprio serviço.
