# Mudanças na versão 1.0

Projeto derivado do ZIP `veyra-android-main.zip` fornecido pelo usuário. A documentação original foi preservada em `BASE-README.md`. A base Kotlin, Compose, Room, WorkManager e seus módulos continuam sendo usados.

## Implementado nesta versão

- Identidade violeta/grafite, Manrope embarcada, temas claro/escuro/AMOLED, cartões, ícones, menu e boas-vindas redesenhados.
- Home com atalhos, tarefas interativas, finanças, clima, hábitos, registro rápido de água e foco.
- Busca global recolhível, botão de captura, navegação Voltar e estado das abas preservado em recriação.
- Tarefas com entrada rápida, busca e filtros; atualização consistente de conclusão e etapa.
- Agenda mensal, semanal, diária e Kanban com mudança de etapa.
- Finanças com fluxo realizado, projeção, taxa de economia, categorias, gráfico acumulado diário, planilha horizontal filtrável/ordenável e CSV completo com proteção contra fórmulas em campos de texto.
- Cadastros existentes de contas, transferências, orçamentos, cartões, assinaturas, parcelas e investimentos expostos na nova área financeira.
- Notas, diário e links em nova apresentação com pasta, favoritos, busca e arquivo.
- Clima com cidade principal, várias cidades, previsão diária/horária e modo manual para temperatura/condição. Preferências do clima incluídas no backup.
- Catálogo em cartões com favoritos e perfis, mantendo os módulos funcionais da base.
- Novo pacote Android para coexistir com a versão original; APK release reduzido e assinado localmente.

## Limites conhecidos

Faturas completas por ciclo, pagamentos de cartão e conciliação bancária não foram implementados. Sincronização em nuvem, banco criptografado e LLM embarcado continuam fora desta versão. A IA online é opcional e requer configuração própria. Vários módulos secundários usam o editor compartilhado da base.

## Reproduzir

JDK 17, SDK/API 35, Build Tools 35.0.0. No Windows:

```powershell
./gradlew.bat :core:model:test :feature:finance:test :app:lintDebug :app:assembleDebug
./gradlew.bat :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
./scripts/build-apk.ps1
```

O último comando cria/verifica o APK release assinado em `dist`. A pasta privada `.signing` contém a chave e sua senha; mantenha uma cópia privada para assinar futuras atualizações compatíveis. Ela não é incluída no ZIP do código nem no Git. Sem essa chave, uma nova instalação com outra assinatura exigiria desinstalar a anterior.
