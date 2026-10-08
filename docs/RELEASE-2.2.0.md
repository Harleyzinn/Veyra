# Veyra Life Android 2.2.0

Integração com o novo Veyra Life Desktop. O Android observa o marcador privado de mudanças da conta e busca automaticamente as edições recebidas do PC enquanto o controlador do app está ativo. Mantém fila offline, debounce, paginação, conflitos e regras existentes.

Dispositivos autenticados passam a registrar presença privada diária, visível no desktop. Desabilitar sincronização remove o listener remoto. Nenhum módulo, banco, anexo ou registro anterior é apagado; o APK usa o mesmo pacote e a assinatura original.

O novo instalador Windows está na [release desktop](https://github.com/Harleyzinn/Veyra/releases/tag/desktop-v3.0.0). Use a mesma conta Google nos dois aparelhos. O plano Firebase continua gratuito, com anexos somente locais.

Validação: testes unitários de modelo, finanças e cloud, lint Android e build release assinado. Os testes locais das regras Firebase e do protocolo desktop também cobrem isolamento por UID, revisões, conflitos e retomada offline. Login Google e Play Integrity em um celular físico ainda dependem de validação pelo titular da conta. Leia o [guia desktop](DESKTOP.md) e as [informações sobre Play Protect](PLAY-PROTECT.md).
