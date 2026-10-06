# Veyra Life 1.1

## Instalar no celular

1. Envie `Veyra-1.1.0.apk` ao celular pelo cabo, Drive ou outro meio que você já use.
2. Abra o arquivo no Android e autorize a instalação de apps pela origem que você usou, quando o sistema solicitar.
3. Toque em **Instalar**, abra **Veyra** e escolha seu nome, perfil e tema.

Requer Android 8.0 ou superior. O pacote é `app.veyra.life`: esta versão pode coexistir com o Veyra original, cujo pacote é `app.veyra.android`. Não é necessário root, login ou assinatura de serviço. O APK é uma versão release assinada localmente. Não foi publicado na Play Store.

## Começar a usar

- **Hoje:** tarefas do dia, fluxo financeiro, clima, hábitos, água e acesso ao foco. O botão **+** abre o cadastro; a lupa faz a busca global.
- **Tarefas:** use o atalho da Home para criar rapidamente, filtrar por prazo/prioridade e concluir. O cadastro completo permite recorrência, lembrete, tags e vínculos.
- **Agenda:** calendário mensal, dia, semana e Kanban. Em Kanban, use Começar e Concluir para mudar de etapa.
- **Finanças:** cadastre contas e saldo inicial; depois receitas, despesas, orçamentos e cartões. Valores **previstos** entram na projeção e só entram nos totais realizados quando marcados como realizados. A planilha permite busca, filtros, ordenação, navegação horizontal e exportação CSV das linhas selecionadas. Despesas podem ser convertidas em parcelas no detalhe do registro.
- **Notas:** crie notas, pastas, tags, favoritos, links e diário. O editor salva notas automaticamente quando o título está preenchido; Salvar conclui a edição. A prévia aceita títulos e negrito Markdown básico.
- **Clima:** em Previsão real, ative a consulta online, busque uma cidade e toque no resultado. Você pode cadastrar várias cidades e alternar entre elas. Meu clima permite escolher temperatura e condição manualmente; é uma personalização, não uma previsão real.
- **Mais:** hábitos, estudos, metas, viagens, listas de compras, casa, veículo, Pomodoro, assistente e ferramentas. A estrela fixa módulos como favoritos.
- **Configurações:** temas, perfil, cartões da Home, PIN/biometria, notificações, importar extratos e exportar/restaurar backup.

## Dados e cuidados práticos

Os dados ficam no aparelho, sem sincronização bancária ou em nuvem. Exporte um backup JSON para guardar seus registros antes de desinstalar o app ou trocar de celular. A senha opcional do backup protege esse arquivo com AES-GCM; o banco local não possui criptografia adicional. O PIN bloqueia o acesso à interface.

A exportação da tela Finanças é um relatório com 11 colunas (inclui transferências, contas, status, tags e notas), destinado a planilhas. Não substitui o backup JSON. A exportação/importação de extratos nas Configurações usa `date,title,amount,category`, datas `AAAA-MM-DD`, valor negativo para despesa. Há também importação OFX com prévia e exportação PDF.

O painel de cartões mostra compras por mês, limite, fechamento e vencimento informados. Não calcula ciclos completos de fatura, pagamento ou conciliação. Investimentos são registros manuais. Não há conexão com bancos. Clima online depende da internet e do Open-Meteo; o cache mostra a data da última consulta. O modo manual funciona offline. Notificações e reconhecimento de voz dependem das permissões e dos serviços do Android.

Os exemplos das capturas de tela são dados do emulador de teste. O APK começa com seus registros vazios.

Na versão 1.1, abra **Mais → Abrir central de rotina** para os novos módulos. Instale por cima da 1.0 para manter seus registros; faça um backup antes da atualização.