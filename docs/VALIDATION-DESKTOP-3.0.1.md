# Validação Desktop 3.0.1

Em 7 de outubro de 2026, o Firebase real reproduziu `PERMISSION_DENIED` em `BeginTransaction` com usuário verificado; leitura individual, query e batchGet foram aceitas. As regras publicadas correspondem ao repositório, perfil real ativo/verificado e nenhum enforcement App Check foi alterado.

A correção remove BeginTransaction/rollback e usa batchGet + commit atômico com precondição exists/updateTime. Concorrência relê a versão vencedora e preserva a local como conflito. O marcador é confirmado no mesmo commit.

- 33 testes desktop passaram, incluindo corrida entre criações e mensagem de erro.
- 30 testes das regras Firebase passaram.
- Integração com emulador: dois dispositivos, conflito, fila offline/reabertura, isolamento UID.
- Sessões UI: login, cloud sync, logout, troca de conta, rejeição de editor obsoleto.
- Firebase real: login verificado, perfil, nota/dispositivo, revisão 2, conflito, marcador, isolamento UID.
- Executável Windows empacotado 3.0.1: perfil de teste isolado, login verificado, gravação confirmada no Firebase real e zero operações pendentes.
- Conta e documentos descartáveis foram removidos; os registros reais do usuário foram preservados.

O teste real usou e-mail/senha de conta sintética verificada para exercer o mesmo token Firebase e caminho de sincronização. Não afirma que a fila pessoal do usuário já foi enviada; isso ocorre após atualizar e sincronizar a instalação existente.
