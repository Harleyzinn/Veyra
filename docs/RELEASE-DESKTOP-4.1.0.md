# Veyra Life Desktop 4.1.0

Refinamento de finanças e notas para evitar informações ambíguas e proteger melhor suas edições.

- Relatórios com dois modos: gastos pela data da compra e fluxo de caixa pela data do pagamento. A quitação da fatura entra no caixa sem repetir as compras do cartão.
- Home e widgets agora mostram contas e faturas, valor restante após pagamentos parciais e atraso.
- CSV preserva data do pagamento, vínculo com a fatura e comportamento financeiro do cartão ao reimportar. Mantém prévia e deduplicação de arquivos repetidos.
- Notas preservam Markdown ao alternar modos e os blocos intactos ao formatar outro trecho. Controles do editor permanecem acessíveis.
- Conflitos de notas ganham comparação lado a lado e recuperação como cópia. Atualizações de outra janela aparecem quando a nota não tem edição pendente.
- Busca de notas mostra o total completo, com carregamento incremental. Rascunhos fazem menos gravações durante digitação contínua e mensagens de erro informam a situação real.

Instale sobre a versão anterior. Exporte um backup em Configurações → Dados antes de atualizar. O Android continua 2.2.0; esta release é para Windows. Firebase permanece no plano gratuito e anexos continuam locais. O instalador não tem certificado Authenticode comercial; o atualizador verifica manifesto Ed25519 e SHA-256.

[Testes e limites](VALIDATION-DESKTOP-4.1.0.md) · [Problemas encontrados e correções](AUDIT-REFINEMENT-4.1.md).

Validação: 53 testes de lógica/protocolo, 30 de regras Firebase, integração de sincronização/troca de conta e testes de interface no executável final, incluindo reimportação financeira e janelas Glass. A amostra com 1.500 registros usou 560,7 MiB; com Dock, Mini e Foco, 1.068,0 MiB. O consumo de memória continua uma limitação conhecida.
