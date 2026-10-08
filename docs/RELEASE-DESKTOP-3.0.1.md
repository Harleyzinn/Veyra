# Veyra Life Desktop 3.0.1

Correção da sincronização no Windows: a conta Google podia entrar normalmente, mas o envio ficava com “Acesso negado” e alterações pendentes. O Firebase de produção recusa o endpoint de abertura de transação usado na versão 3.0.0.

O desktop agora usa leitura autenticada e gravação atômica com precondição da versão do documento. Edições simultâneas continuam preservadas como conflitos; alterações offline permanecem na fila até confirmação do servidor. Não é necessário desativar regras, verificação de e-mail ou mudar o plano gratuito.

Instale esta versão por cima da atual e clique em **Sincronizar agora**. Mantenha os dados locais e os backups existentes. O atualizador do próprio Veyra também detecta esta versão.

Validação: reprodução da recusa no Firebase real e confirmação de login verificado, criação de perfil, envio de nota/dispositivo, atualização de revisão, conflito preservado, marcador e isolamento entre usuários com conta temporária de teste. Os registros de teste foram removidos; dados reais não foram alterados.
