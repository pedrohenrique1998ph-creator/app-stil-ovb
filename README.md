# STILO OVERBOOST • SLIM V0.4.1

Projeto Android para **Samsung Galaxy A22**. Interface offline em HTML/CSS/JS dentro de um `WebView` Android nativo, com ponte Bluetooth Classic SPP escrita em Kotlin.

## Funcionalidades incluídas

- **Tacômetro corrigido**: arco e escala sincronizados de **0 a 7.000 RPM**, sem sobreposição e atualização sem reiniciar a página. Fonte do firmware: `status.tach.rpm` **somente se** `status.tach.valid == true`.
- **Bluetooth Classic SPP**: seleção de dispositivo já pareado, permissão Android 12+, leitura de mensagens JSON terminadas em `\n`; envio de comandos texto suportados pela V3.0.2.
- **CAN Explorer**: leitura de `CAN LIST`, identificação preliminar, `DISCOVER START SPEED`, `DISCOVER MARK`, `DISCOVER STOP`, sugestões de candidatos e importação de logs.
- **Velocidade**: leitura apenas de decodificador existente, válido e com nome `speed`, `velocidade` ou `vss` informado por `DEC LIST`. Sem decodificador, exibe indisponível.
- **Mapeamentos**: confirmação manual de ID/byte/endian/escala; botão **Aplicar** para configurar a velocidade usando `DEC SET`, `ROLE SPEED`, `DEC SAVE`. A validação da origem deve ser feita em ensaios com referência independente. A interface não pode garantir que o ID candidato esteja correto.
- **VSS**: comandos `VSS ON/OFF` incluídos com confirmação antes de habilitar. No gateway V3.0.2, a saída prevista é GPIO32. Pneu `195/55 R15` e 40 pulsos/volta estão fixados no firmware e não são alterados pelo APK.
- **A/C**: visualização do permissivo e do estado do sensor. **Não confunde permissivo com energização real do compressor.** O comando remoto A/C não é oferecido porque `AC ENABLE` na V3.0.2 refere-se à habilitação do circuito, não a um pedido de ar.
- **Configurações**: temas, ajuda `ⓘ`, simulação opcional, exportação JSON com o seletor de arquivos Android.
- **CAN TX**: somente editor e validação de frames na interface. **Não há envio CAN do APK nesta versão**. Os comandos TX/TXT do firmware ficam bloqueados na ponte Android até testes instrumentados em rede isolada. Nunca ligar a transmissão de teste na CAN original do veículo.

## Compilação automática pelo GitHub

1. Extraia este ZIP no **PC**.
2. Entre no repositório [Stilo-app](https://github.com/pedrohenrique1998ph-creator/Stilo-app).
3. Use **Add file → Upload files**. Selecione **o conteúdo** extraído, incluindo `app` e os arquivos `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` e `README.md`. Para o repositório existente, não precisa reenviar `.github` se o workflow anterior já estiver presente.
4. Confirme o upload em `main` (se perguntar, aceite substituir arquivos existentes).
5. Aba **Actions → Compilar APK Android**. Aguarde status verde.
6. Na execução bem-sucedida, em **Artifacts**, baixe `StiloOverboost-Android-debug`, extraia, e instale `app-debug.apk` no A22.

Este ZIP contém **código-fonte**, não um APK binário já compilado. A criação do APK ocorre no GitHub Actions. Não foi executada compilação Gradle nesta sessão; teste o resultado no aparelho.

## Conectar ao ESP32

- Pareie primeiro `STILO_OVERBOOST` pelas configurações Bluetooth do Android.
- Abra o app, toque **Conectar Bluetooth** e selecione o dispositivo.
- A conexão requisita `HELLO`, `STATUS`, `CAN LIST`, `DEC LIST`; o firmware envia `status` automaticamente a cada segundo enquanto conectado.
- Sem o sinal elétrico do tacômetro na entrada prevista do ESP32, a rotação permanece indisponível, mesmo com Bluetooth conectado.
- Sem CAN física ligada ao transceiver e um decodificador validado, velocidade e os IDs podem continuar vazios.

## Limitações de segurança

- O app **não altera o firmware V3.0.2** nem o usa como biblioteca. Conversa com a versão já gravada por protocolo de texto.
- No hardware do veículo, mantenha CAN em LISTEN-ONLY ao descobrir os IDs e não autorize envio TX sem isolamento físico da bancada.
- Antes de conectar VSS à Octtane, valide o nível, polaridade, forma de onda e compatibilidade elétrica do optoacoplador com osciloscópio.
- APK debug serve para testes internos; para distribuição, é necessário build release, assinatura e validação de segurança.

## Estrutura

```
.github/workflows/android-apk.yml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/assets/index.html
app/src/main/java/br/com/stilooverboost/app/MainActivity.kt
build.gradle.kts
settings.gradle.kts
gradle.properties
README.md
```


## Ícone do aplicativo

Projeto atualizado com o ícone STL / OVB em estilo racing.


## V0.4.2 — Monitor e pressão A/C

- Pressão em bar diretamente do campo `pressure` do status JSON V3.0.2 (sem converter ADC pelo Android). Em caso de `sensor_ok=false` ou `pressure=null`, aparece `—`.
- Aviso: GPIO34 desconectado pode flutuar e produzir leitura aparentemente válida. Não usar o indicador para liberar fisicamente o compressor; corrigir hardware/firmware antes de instalar.
- Em **Ajustes > Monitor RX/TX**, veja todas as mensagens Bluetooth de entrada e os comandos efetivamente escritos no socket (inclusive inicialização).
- Exportar `stilo-overboost-monitor-rx-tx.json`: até 1200 linhas cronológicas do protocolo Bluetooth; histórico é só da sessão e é mantido após desconectar.
- Em **CAN > Exportar CAN RX**, salve as respostas CAN LIST recebidas como snapshots. São snapshots do gateway, **não** captura integral dos frames CAN do veículo.
- **Exportar mapa CAN** continua salvando apenas sinais confirmados.
- Nenhuma mudança no firmware ESP32. Nenhuma permissão para enviar CAN TX ou `AC ENABLE`.
