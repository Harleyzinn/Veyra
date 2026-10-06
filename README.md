# Veyra Life

Sua vida, em um só lugar. App Android nativo em Kotlin/Jetpack Compose, criado a partir do Veyra fornecido pelo usuário.

## APK e instalação

O APK release assinado fica em `dist/Veyra-1.1.0.apk`. Requer Android 8.0 ou superior e usa o pacote `app.veyra.life`, independente do original. Veja [como instalar e usar](docs/INSTALAR.md).

## Novidades da versão 1.1

18 novos módulos de registros e uma central de rotina: checklists, cardápio, casa, pets, medicamentos, consultas, humor, medidas, exercícios, metas financeiras, dívidas, contas a pagar, desejos, presentes, cursos, vagas, contagem regressiva e revisão de serviços. Veja [detalhes e limites](docs/RELEASE-1.1.md).

## O que está no app

- Dashboard com tarefas interativas, finanças, clima, hábitos, água e foco.
- Tarefas com prioridade, etapas, prazo, recorrência, lembrete e vínculos; calendário e Kanban.
- Finanças com receitas/despesas realizadas e previstas, contas, transferências, orçamento por categoria, cartões, assinaturas, parcelas, investimentos manuais e gráficos. Planilha com filtros, ordenação e exportação CSV de 11 colunas. Importação CSV/OFX, OCR local de recibos e PDF herdados da base.
- Notas com pastas, tags, arquivo, favoritos, salvamento automático e prévia Markdown; diário e links.
- Clima por várias cidades, previsão horária/7 dias e cache. Modo manual de temperatura/condição para personalização.
- Hábitos, água, sono, saúde, treinos, Pomodoro, estudos e flashcards.
- Viagens, reservas, mala, compras, despensa, receitas, patrimônio, documentos, contatos, aniversários e veículo.
- Calculadora, conversores, juros compostos, cronômetro, datas, texto, senhas e QR.
- Busca global, favoritos, lixeira, desfazer, widget, atalhos, notificações, backup JSON e bloqueio PIN/biometria.
- Temas claro/escuro/AMOLED/sistema/dinâmico, fonte Manrope embarcada e identidade violeta/grafite.

Os registros começam vazios no APK. Exemplos usados nos testes e nas capturas de tela não são incluídos na instalação. Não há sincronização bancária ou em nuvem, nem faturamento completo de cartão por ciclo. Veja os [limites e mudanças](docs/RELEASE-1.0.md).

## Compilar

JDK 17, SDK 35 e Build Tools 35.0.0. Configure `JAVA_HOME` e `ANDROID_HOME`, ou `local.properties` para o SDK.

```powershell
./gradlew.bat :core:model:test :feature:finance:test :app:lintDebug :app:assembleDebug
./gradlew.bat :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
./scripts/build-apk.ps1
```

O script gera a chave local na pasta privada e ignorada `.signing`, alinha o APK para páginas de 16 KB, assina e verifica o resultado. Preserve a chave e a senha para futuras atualizações. O projeto não inclui credenciais ou chaves no Git/ZIP.

## Base e arquitetura

`app` contém a interface Compose e integrações Android. `core:model` contém os modelos e cálculos; `core:data`, Room/backup; `core:designsystem`, o tema. `feature:*` mantém os domínios e integrações da base. A documentação anterior foi preservada em [BASE-README.md](docs/BASE-README.md).

Clima: [Open-Meteo](https://open-meteo.com/), geocodificação GeoNames. Manrope: licença SIL Open Font License em [Manrope-OFL.txt](docs/Manrope-OFL.txt). Sem anúncios, analytics ou conta obrigatória. Dados locais e backups continuam sujeitos às condições descritas no guia.
