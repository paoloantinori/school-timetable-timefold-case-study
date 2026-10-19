# VERDICT 639 (validazione) + 640 (footprint), laptop 22/09 mattina

## 639: validazione lunga FALLITA a entrambi gli heap

- heap 3g: OOM (Exception in staged-solve-resume, poi vertx-checker). Dato 640.
- heap 8g: MT-LS stall ALLA STESSA FIRMA di pre-cura: "channel 0 did not accept the
  evaluation in 5000ms (full or diverged). Runners alive: true". Il banco ha abortito
  correttamente (abort-on-failure). La cura 416e8516 ha curato la BARRIER
  (deciderBarrierJoin attesa unica 60s) ma il punto di stallo osservato e' OFFERLOUD
  (SingleWriterOperationDispatcher.java:52-57, PUT_TIMEOUT_MS 5000): il canale del
  runner 0 non accetta l'offerta. La causa del canale-pieno/restio NON e' la barrier.
- Test rapido: ChannelDivergenceStallTest 3/3 verde (la unit della cura passa: la
  cura cura cio' che testa).

## 640: footprint

- 3g: OOM nel banco in-JVM su 2.6.0 (confermato quanto visto ieri a 2g/4g nel ciclo
  app; ora anche nel banco: 2.3.0 a 3g completava, 2.6.0 no).
- 8g: il ciclo gira oltre l'OOM ma muore per il MT-LS stall sopra.
- Sonda heap: jcmd non attacca su macOS (AttachOperationFailed): sample vuoti.
  Il dato footprint resta: 3g non basta su 2.6.0.
