# Veyra Glass Panels

Disponíveis em **Configurações → Janelas** e no ícone da bandeja: Mini Dashboard, Focus Overlay, Quick Capture, Veyra Dock, finanças, tarefas, clima, calendário, hábitos, relógio, cronômetro e notas.

Arraste o cabeçalho vazio para mover. Botões, campos e menus continuam interativos. Nas bordas dos painéis redimensionáveis, arraste para ajustar o tamanho. Passe o mouse ou use o teclado para revelar os controles próprios: fixar acima das outras janelas, recolher, opções e fechar.

O menu **⋯**, também disponível pelo botão direito fora dos campos, permite escolher modo, opacidade, encaixe, efeitos, monitor e restaurar posição. Compacto reduz detalhes; micro está disponível em foco, finanças, clima, relógio e cronômetro. As preferências e a posição são salvas por painel e por workspace. Janelas fechadas individualmente não reabrem automaticamente; painéis abertos voltam após reiniciar o Veyra.

**Ctrl+Shift+Space** abre a captura rápida. Escolha tarefa, gasto, entrada, nota ou evento. ESC fecha; salvar fecha com animação discreta. Editores abertos pelos widgets usam a captura em uma janela separada.

**Ctrl+Shift+R** recupera os painéis: desativa click-through, traz para frente e coloca em uma tela disponível. Esse atalho pode ser alterado nas configurações. Click-through só pode ser ativado com um atalho de recuperação registrado e não está disponível na captura. A bandeja também permite recuperar, ocultar, mostrar e desativar click-through.

O Mini Dashboard e o Dock mostram os módulos escolhidos nas configurações do Mini Dashboard. A seleção é compartilhada entre os dois; tamanho, modo, fixação, monitor e opacidade são independentes. O Dock reúne vários resumos em uma única janela e reduz a quantidade de processos de renderização.

## Efeitos e compatibilidade

A janela é transparente de verdade, sem captura do wallpaper. No Windows 11 22H2 ou superior, a API nativa do Electron aplica Acrylic quando disponível. O contorno é recortado pela região da janela, para não desenhar um retângulo atrás dos cantos. Se o recorte ou o efeito não estiver disponível, o painel usa a alternativa translúcida, sem Acrylic. A API de recorte do Electron é experimental.

Windows 10 usa essa alternativa, sem bibliotecas nativas adicionais. **Reduzir efeitos visuais** desliga Acrylic e animações, reduz sombras e aumenta a cobertura do fundo. Preferências do sistema de contraste/transparência e movimento são respeitadas quando expostas pela plataforma. Não há integração com a camada do wallpaper por mecanismos privados do Windows.

Cada janela independente possui um processo de renderização do Electron. Em uma amostra com a janela principal e nove painéis, o conjunto usou aproximadamente **1,50 GiB de memória**, CPU total de **0,64%** e pico de **0%** nos contadores GPU disponíveis em repouso. É uma amostra curta, não uma garantia em outros computadores. O Dock é recomendado para muitos módulos; reduzir efeitos reduz composição, mas não elimina a memória de cada janela.

Veja [o que foi realmente testado](VALIDATION-DESKTOP-3.1.0.md). O instalador preserva a conta e a fila local: não apague o cache para atualizar.
