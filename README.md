# Rodagem Leitor

App Android que acompanha o Rodagem (PWA). Lê as ofertas de corrida dos apps de motorista pela API de Acessibilidade.
Regra fixa: só lê e mostra. Nunca toca em aceitar ou recusar.

## Versão atual (diagnóstico + leitura de corridas)
Registra os textos das telas de oferta, para mapear onde ficam valor, km e tempo em cada app.
Os apps lidos são escolhidos na tela, com toggle. Vêm ligados por padrão: Uber Driver, 99, inDrive, Taxsee Driver e EasyMob (motorista).

Das telas registradas, o extrator monta as corridas e acompanha cada uma até o fim
(oferta → aceita → no local → em corrida → concluída ou cancelada). Elas aparecem no cartão
"6. Corridas lidas" da tela do app. O botão "Reler registro" refaz a lista a partir das telas guardadas.

### O que o extrator já entende
- **Maxim (Taxsee Driver)**: oferta "Pedido atribuído!", pedido aberto da lista ("Solicitar"), corrida em andamento ("A caminho"),
  cartões de pedido do mapa e das listas, e o histórico "Meus pedidos" (concluído / cancelado).
  Lê preço (`tv_price`), pagamento, origem e destino, km e minutos até o passageiro, km da rota, categoria e o nome do cliente no comentário.
  O R$ do título ("Centro (R$ 16,88)") é o saldo do motorista e não é usado como preço.
- **Easy (EasyMob Motorista)**: oferta (valor que o motorista ganha, embarque, destino com km e minutos, número da OS),
  "Vá até o passageiro", "Aguarde o passageiro", "Vá até o destino", resumo de cobrança e o aviso "Você ganhou".
- **Uber e 99**: ainda não. Os registros enviados até agora não têm telas de oferta deles.

## Semáforo
Quando aparece uma oferta (por enquanto da Maxim e da Easy), o leitor põe por cima da tela um cartão
**verde (aceitar)**, **amarelo (avaliar)** ou **vermelho (recusar)**, com o valor, R$/km, R$/hora,
o lucro depois do custo do carro, os km e minutos (até o passageiro + corrida) e o motivo da cor.
O cartão não recebe toque (o toque passa direto para o app de corrida) e some quando a oferta sai da tela.
Usa a janela do próprio serviço de Acessibilidade, então não pede permissão nova.

Na tela do app, cartão "5. Semáforo" → "Ajustar faixas e custo do carro":
- **Faixas**: R$/km, R$/hora e nota do passageiro (vermelho abaixo de / verde a partir de; o meio é amarelo).
- **Limites** (0 = desligado): valor mínimo, lucro mínimo em R$ e em %, distância e tempo até o passageiro,
  tamanho máximo da corrida, velocidade média para estimar o tempo quando o app só mostra os km.
- **Comissão do app**: % que o app ainda desconta do valor mostrado (por app).
- **Custo do carro**: combustível, consumo, km por mês, parcela, seguro, IPVA, manutenção, desvalorização
  e outros viram um custo por km, usado no lucro.
- **Testar cartão**: mostra três ofertas de exemplo (boa, no meio e ruim) com os ajustes atuais.

A cor final é a pior entre todas as regras. Oferta sem destino (a Maxim às vezes mostra "0 km") fica amarela,
com os números "até" (contando só o caminho até o passageiro); se nem assim passa do vermelho, fica vermelha.
A lista "6. Corridas lidas" mostra também o que o semáforo diria de cada corrida.

## Como o APK é gerado
A cada envio para a branch `main`, o GitHub Actions compila o APK e deixa para baixar na aba **Actions**
(execução "Gerar APK", em Artifacts: `rodagem-leitor-apk`).
Requer o segredo `LEITOR_KEYSTORE` (Settings > Secrets and variables > Actions), com a chave de assinatura em base64.
Sempre a mesma chave: é o que permite instalar versões novas por cima da antiga.

## Arquivos
- `app/src/main/java/app/rodagem/leitor/LeitorService.kt`: serviço de Acessibilidade (leitura)
- `app/src/main/java/app/rodagem/leitor/MainActivity.kt`: tela do app
- `app/src/main/java/app/rodagem/leitor/Apps.kt`: lista de apps instalados e quais estão ligados
- `app/src/main/java/app/rodagem/leitor/Registro.kt`: registro de diagnóstico no aparelho
- `app/src/main/java/app/rodagem/leitor/Tela.kt`: lê o texto de cada tela e monta a árvore de elementos
- `app/src/main/java/app/rodagem/leitor/ExtratorCorrida.kt`: regras de cada app para tirar a corrida da tela
- `app/src/main/java/app/rodagem/leitor/Corrida.kt`: dados da corrida e os status
- `app/src/main/java/app/rodagem/leitor/BancoCorridas.kt`: corridas guardadas no aparelho (SQLite)
- `app/src/main/java/app/rodagem/leitor/Avaliador.kt`: cálculo do semáforo (faixas, limites, custo por km)
- `app/src/main/java/app/rodagem/leitor/Semaforo.kt`: o cartão por cima da oferta
- `app/src/main/java/app/rodagem/leitor/Ajustes.kt`: onde os ajustes ficam guardados
- `app/src/main/java/app/rodagem/leitor/AjustesActivity.kt`: tela de ajustes do semáforo
- `app/src/main/res/xml/leitor_config.xml`: configuração da Acessibilidade
- `.github/workflows/gerar-apk.yml`: compilação automática
