# Publicação Veyra Finanças 3.0.0 — 19/10/2026

Código funcional enviado a `Harleyzinn/Veyra`, branch `main`, commit `6dcf406ff78d6c7717e99dd97063542dffea8065`. Os commits locais anteriores foram enviados juntos, preservando o histórico e sem force push.

- Web-app público: https://harleyzinn.github.io/Veyra/
- Workflow Pages aprovado: https://github.com/Harleyzinn/Veyra/actions/runs/39335318391
- Release pública: https://github.com/Harleyzinn/Veyra/releases/tag/v3.0.0
- APK: https://github.com/Harleyzinn/Veyra/releases/download/v3.0.0/Veyra-3.0.0.apk
- Código-fonte: https://github.com/Harleyzinn/Veyra/releases/download/v3.0.0/Veyra-Android-3.0.0.zip
- Hashes: https://github.com/Harleyzinn/Veyra/releases/download/v3.0.0/SHA256SUMS.txt

O deploy Pages terminou com estado `completed / success`. O endereço público respondeu HTTP 200 e foi aberto em contextos novos de Edge e WebKit: dashboard real carregado, manifesto correto, zero erros JavaScript e reabertura offline confirmada nos dois motores. Foram utilizados espaços locais vazios, sem dados financeiros pessoais. Evidência local: `.tools/finance30-public-smoke.json`.

O APK público foi baixado novamente e comparado com o APK local testado. Ambos têm SHA-256 `4e19c8d894dfff38ce53114deef61c81e2567d32b9cdaa1accf15678bbf7c523`. Assets enviados foram conferidos por tamanho e digest; assinatura original preservada.

Backup Omega `20261019T223010-f858cf5a` verificado e restaurado num destino isolado novo: 49 arquivos, integridade SQLite/manifesto `ok`. Esse backup é operacional/local e não contém os dados financeiros do aplicativo.

Limites funcionais e de plataforma permanecem em [FINANCE-3.0.md](FINANCE-3.0.md) e [VALIDATION-3.0.0.md](VALIDATION-3.0.0.md): iPhone pela PWA, sem IPA/App Store; PWA sem Firebase; feriados locais/móveis manuais; alertas/recebimentos automáticos ao abrir; validação em aparelhos físicos pendente.
