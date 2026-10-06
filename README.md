# Rodagem Leitor

App Android que acompanha o Rodagem (PWA). Lê as ofertas de corrida do Uber, da 99 e do inDrive pela API de Acessibilidade.
Regra fixa: só lê e mostra. Nunca toca em aceitar ou recusar.

## Versão atual: 0.1.2 (diagnóstico)
Registra os textos das telas de oferta, para mapear onde ficam valor, km e tempo em cada app.
Os apps lidos são escolhidos na tela, com toggle. Vêm ligados por padrão: Uber Driver, 99, inDrive, Taxsee Driver e EasyMob (motorista).

## Como o APK é gerado
A cada envio para a branch `main`, o GitHub Actions compila e publica o APK em **Releases**.
Requer o segredo `LEITOR_KEYSTORE` (Settings > Secrets and variables > Actions), com a chave de assinatura em base64.
Sempre a mesma chave: é o que permite instalar versões novas por cima da antiga.

## Arquivos
- `app/src/main/java/app/rodagem/leitor/LeitorService.kt`: serviço de Acessibilidade (leitura)
- `app/src/main/java/app/rodagem/leitor/MainActivity.kt`: tela do app
- `app/src/main/java/app/rodagem/leitor/Apps.kt`: lista de apps instalados e quais estão ligados
- `app/src/main/java/app/rodagem/leitor/Registro.kt`: registro de diagnóstico no aparelho
- `app/src/main/res/xml/leitor_config.xml`: configuração da Acessibilidade
- `.github/workflows/gerar-apk.yml`: compilação automática
