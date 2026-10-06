# Arquitetura 0.2

Android nativo em Kotlin/Compose, Room como fonte local de verdade, corrotinas no IO e operações coordenadas por Mutex no ViewModel. Não há servidor obrigatório ou dados demonstrativos inseridos.

`app → feature:* / core:designsystem / core:data → core:model`. As features definem catálogos tipados com campos e validações; finanças contém importador CSV/OFX e clima é uma biblioteca Android com provedor HTTP. A UI e a orquestração continuam em app. Esta é uma fronteira de evolução, não modularização completa das telas.

## Dados e relacionamentos

Room schema 2 adiciona ItemRecord e PreferenceRecord. A migração 1→2 preserva IDs, datas e valores do Entry legado, sem fallback destrutivo. Um Item contém tipo de domínio, campos, contexto parentId, datas, favoritos, tags e tombstone de lixeira. Os catálogos evitam formulários livres; Workspace valida tipos, números, datas e valores antes de salvar. Contextos ligam tarefas/notas/gastos a projetos, viagens, disciplinas, veículos e patrimônio.

Campos monetários persistem decimal canônico; cálculos usam centavos Long e BigDecimal, incluindo distribuição de parcelas. Transferências alteram contas sem virar receita/despesa. Previstos não contam no realizado antes de conclusão. Importação usa IDs determinísticos para evitar duplicatas e confirmação por prévia. Ainda não há conciliação bancária ou ciclo completo de faturas.

Backup schema 2 valida integralmente antes da transação de merge por ID, aceita schema 1, inclui anexos base64 e exclui PIN/chave online. Limites: arquivo 30 MB, 30 mil registros e anexo selecionado 10 MB. Banco e backup automático local ainda são legíveis no sandbox; backup manual opcional usa AES-GCM-256, salt aleatório e PBKDF2-SHA256/210 mil iterações.

## Android e rede

WorkManager executa lembretes e regras periódicas sem garantia de horário exato. Foreground service de foco mantém deadline/estado, pausa e ciclos; sessões concluídas têm IDs estáveis. Widget usa RemoteViews e atualização após operações. Notificações oferecem concluir/adiar; Tile e shortcuts abrem captura.

Permissões: INTERNET, POST_NOTIFICATIONS, FOREGROUND_SERVICE/specialUse e USE_BIOMETRIC. SAF seleciona somente o arquivo autorizado. Não são solicitados GPS, armazenamento amplo, câmera ou microfone: imagens chegam pelo seletor/share e voz delega ao reconhecedor Android.

Clima é consulta opcional Open-Meteo por cidades manuais com cache timestamp. OCR ML Kit possui modelo local incluído. Assistente local é baseado em regras; IA online usa endpoint HTTPS configurável e envia a pergunta somente após confirmação, com chave mantida em memória. Nenhum LLM está embarcado.

## Evolução técnica

Extrair UI/casos de uso de app para features, repositórios por domínio e injeção de dependências; FTS/paginação; anexos em arquivos privados em vez de base64 no banco; backup automático com Keystore; precisão de recorrências e cartões; testes extensivos de acessibilidade e histórico grande. Sync, outbox e resolução de conflitos continuam pendentes.
