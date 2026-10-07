# Veyra 2.0.1 — Firebase conectado

Esta atualização conecta o aplicativo ao projeto Firebase real do Veyra. Login por e-mail/senha e Google habilitados, configuração Android oficial incorporada, certificados originais registrados e regras e índices privados do Firestore publicados.

O plano escolhido é gratuito, sem cobrança vinculada. Tarefas, notas, finanças e demais registros podem ser sincronizados após autenticação e verificação do e-mail. Fotos, PDFs e outros anexos permanecem no aparelho original; não são enviados ao Firestore nem ao Storage. Edições vindas de outro aparelho preservam os arquivos locais. A limpeza de cache é bloqueada quando apagaria anexos sem cópia na nuvem; use backup completo para trocar de celular.

App Check registrado com Play Integrity para distribuição do APK fora da Play Store. A obrigatoriedade da proteção depende da validação em dispositivo real; autenticação e regras por proprietário continuam protegendo os dados. Google Login e atestado de integridade ainda exigem validação no aparelho com Google Play Services. Isso não garante aprovação do Play Protect.

Mantém a assinatura original e a atualização pelo GitHub. Instale por cima da versão anterior, sem desinstalar. O espaço local é preservado; depois de entrar, a tela Conta oferece importar os dados locais mediante escolha.

Configuração detalhada em `docs/FIREBASE-SETUP.md`; validação desta atualização em `docs/VALIDATION-2.0.1.md`.
