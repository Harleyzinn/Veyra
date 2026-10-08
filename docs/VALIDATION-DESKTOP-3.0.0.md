# Validação — Desktop 3.0.0 e Android 2.2.0

Executada no Windows 11 x64, em 7 de outubro de 2026. Dados de interface e contas dos emuladores são sintéticos e não acompanham as instalações.

| Verificação | Resultado |
| --- | --- |
| Typecheck e lint desktop | Aprovados |
| Testes unitários desktop | 30 aprovados, zero falhas |
| Regras Firebase/Auth/Storage | 30 testes aprovados no projeto local `demo-veyra` |
| Protocolo desktop contra Firestore real emulado | Dois clientes, ACK/revisões, edições concorrentes, escolha explícita, queda de rede, fila reaberta e UID cruzado aprovados |
| Interface desktop | Dashboard, saldo com conta inicial, edição Markdown/autosave, Kanban, conclusão, mini janela, captura rápida e reabertura offline aprovados |
| Sessões na interface | Login por e-mail, envio cloud, logout, troca de conta, retorno ao cache original e rejeição de editor com UID antigo aprovados |
| Aplicativo empacotado | Abertura, versão 3.0.0, configuração Firebase embarcada e ausência de Node no renderer aprovados |
| Instalador Windows | Instalação em diretório de teste, abertura do programa instalado e desinstalação aprovadas; cache preservado |
| Android | 138 testes unitários de modelo/finanças/cloud, lint e build release aprovados |
| Assinatura APK | v2/v3 válidas; mesma chave original da versão anterior |

Os testes financeiros incluem centavos e moedas com precisões diferentes, datas impossíveis, mês com dia 31, caixa futuro/pendente, compras de cartão antigas, transferências, fechamento, pagamentos parciais, parcelas com resto, pausas e exceções de recorrência, projeção sem cobrança duplicada e reserva diária do simulador. Os testes de persistência incluem arquivo cifrado, recuperação, isolamento físico, operação antiga reconhecida após edição nova, conflitos, anexos locais, importação sem substituição por padrão e preservação de permissões locais.

Foi encontrado um bloqueio temporário de arquivo durante o teste em uma pasta OneDrive. A escrita atômica agora tenta novamente somente erros de arquivo ocupado/acesso temporário, por tempo limitado, e mantém a versão anterior diante de falha. A correção foi validada com o teste específico e com a sincronização posterior.

O Firebase de produção recebeu apenas a autorização de `localhost` para o login Google do desktop; as regras existentes, a ausência de cobrança e a política de anexos locais foram preservadas. Nenhum dado real foi apagado ou importado nos testes.

Limites da validação: Windows 10 não foi executado em uma máquina separada; Google OAuth no navegador do titular e Play Integrity em um celular físico precisam de validação manual. O executável não possui certificado comercial Authenticode. Os testes com clientes emulados não equivalem a um teste com o celular físico conectado. O APK contém o listener de marcador com proteção de UID e foi compilado/validado, mas o agendamento Android em segundo plano continua dependente do sistema.

As capturas [central desktop](screenshots/desktop-central-3.0.png) e [financeiro desktop](screenshots/desktop-finance-3.0.png) mostram a interface real com dados sintéticos.
