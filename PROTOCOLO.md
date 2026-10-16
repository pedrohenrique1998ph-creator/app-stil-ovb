# Protocolo com o gateway ESP32 V3.0.2

Bluetooth Classic SPP: UUID `00001101-0000-1000-8000-00805F9B34FB`, dispositivo `STILO_OVERBOOST`.

O Android **envia comandos ASCII, uma linha por comando**, terminados por `\n`: `HELLO`, `STATUS`, `CAN LIST`, `DEC LIST`, `DISCOVER START SPEED`, `DISCOVER MARK`, `DISCOVER STOP`, `DEC SET ...`, `ROLE SPEED n`, `DEC SAVE`, `VSS ON/OFF`. O ESP32 responde com JSON por linha.

Leituras:

- `type=status` + `tach.valid` e `tach.rpm`: rotação, apenas tacômetro da Octtane se instrumentado no ESP32.
- `status.ac.permit`: permissivo A/C; `status.sensor_ok`: integridade sensor. Não indicam necessariamente que o compressor esteja ligado.
- `status.can.ok`, `.ids`: estado da CAN; não são velocidade.
- `type=can_list` + `frames`: snapshots de IDs e bytes; não é um streaming garantido de *todos* os frames.
- `type=decoders` + `items`: decodificadores runtime salvos pelo ESP32. Apenas sinal nomeado speed/velocidade/vss e `valid=true` alimenta o velocímetro.
- `type=discovery` + `candidates`: sugestões de ID/posição para verificação, **não confirmações**.

Por padrão, o app rejeita comandos `TX MASTER`, `TX ARM`, `TX ONCE`, `TXT ENABLE`, `AC ENABLE` e demais ações não previstas, mesmo que o firmware tenha suporte a elas. Essas operações exigem testes de bancada e revisão explícita da interface.
