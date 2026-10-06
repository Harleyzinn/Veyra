# Veyra e Google Play Protect

O usuário relatou somente o aviso de app não verificado; não relatou uma classificação como prejudicial.

Um APK assinado pode continuar desconhecido pelo Google. A assinatura permite identificar o autor das atualizações e impedir a troca de certificado; não equivale a uma aprovação do Play Protect. A decisão e a análise de segurança pertencem ao Google.

## Para o aviso de app não verificado

1. Use o APK da [release oficial](https://github.com/Harleyzinn/Veyra/releases/latest).
2. Mantenha o Play Protect habilitado.
3. Se o sistema oferecer “Verificar app” ou enviar para análise, use essa opção e aguarde o resultado.
4. Se surgir uma classificação como prejudicial ou um bloqueio, registre o texto exato do aviso. O motivo precisa ser investigado antes de recorrer ao Google.

O app não modifica o Play Protect, não fornece meios de contornar a análise e não instala pacotes silenciosamente. O atualizador aceita apenas APKs do mesmo pacote e certificado, com versão superior e SHA-256 correspondente à release.

## O que é feito no projeto

- Chave original de assinatura mantida fora do GitHub.
- Pacote `app.veyra.life` preservado entre atualizações.
- Provider de atualização privado e restrito à pasta de cache de APKs.
- Conexões HTTP bloqueadas; downloads de atualizações restritos a HTTPS e aos hosts oficiais usados pelo GitHub.
- Sem permissões de acessibilidade, leitura de SMS, administrador do dispositivo, contatos, localização, câmera ou microfone.
- Imagens escolhidas pelo seletor do Android; não é solicitado acesso geral aos arquivos do celular.
- Sem publicidade ou analytics. A atualização consulta o GitHub e baixa o APK; configurações permitem desativar isso.

## Fontes oficiais

- [Orientação do Google para avisos do Play Protect](https://developers.google.com/android/play-protect/warning-dev-guidance).
- [Como o Play Protect verifica aplicativos](https://support.google.com/googleplay/answer/2812853).
- [Verificação de desenvolvedor Android](https://developer.android.com/developer-verification): a verificação da identidade/registro do desenvolvedor é um processo separado, realizado na conta do responsável.

Uma futura distribuição pela Play Store exige preparar uma variante compatível com as políticas da loja, com atualizações gerenciadas pelo Google Play e sem o instalador direto de APKs desta distribuição via GitHub.
