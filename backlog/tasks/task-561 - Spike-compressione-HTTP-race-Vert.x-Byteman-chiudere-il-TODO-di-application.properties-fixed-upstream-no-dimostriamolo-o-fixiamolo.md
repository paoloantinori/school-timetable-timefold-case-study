---
id: TASK-561
title: >-
  Spike compressione HTTP + race Vert.x Byteman: chiudere il TODO di
  application.properties (fixed upstream? no: dimostriamolo o fixiamolo)
status: Done
assignee: []
created_date: '2026-08-26 12:23'
updated_date: '2026-08-26 12:23'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Il TODO aperto in application.properties:52 (compressione HTTP disabilitata per il race Vert.x "Response head already sent", marzo 2026, commit 601aa94d + 337cc4c0) va chiuso con una risposta definitiva. VERIFICATO 26/08 con gh: le tre issue referenziate (#13797 chiusa 2020, #26464 chiusa 2022, #49875 chiusa 2025-09) sono tutte ANTERIORI al nostro hit, che avvenne su Quarkus 3.31 (upgrade 5/3/2026, bug 9/3/2026): il nostro interleaving esatto non risulta fissato upstream. L'unica risposta decisiva e' empirica, e il nostro harness Byteman (TASK-543/544: profilo -Pbyteman, race-force.btm, pattern freeze+spin, verifica javap) permette la prova deterministica.

FASE 1 (decisiva, ore): profilo probe con quarkus.http.enable-compression=true + hammering concorrente del path statico (RootPathHandler / e /assets/*, bundle ~527KB), evoluzione della metodologia 50/50 curl: N client paralleli (es. 50x loop), assertion header-first (wget -S / curl --http1.1, parse ordine), zero "HTTP/0.9 when not allowed", zero "Response head already sent" nei log, su almeno 2-3k richieste. PREDIZIONE PRIMA dell'esito. Se pulito a carico alto: il race e' morto sullo stack attuale (Quarkus 3.31.4 + Vert.x corrente del dependency tree, da verificare con dependency:tree e dichiarare): riabilitare la compressione in application.properties, chiudere il TODO, dichiarare chiuso anche il task. Nessun upstream.

FASE 2 (solo se riproduce): interleaving deterministico con Byteman sulle classi REALI del path (individuare con javap il punto dove Content-Encoding viene calcolato rispetto alla prima write; regola AFTER WRITE / freeze+spin come TASK-544): banco che mostra il body-prima-degli-header solo con interleaving forzato e mai serializzato. Questo dimostra il meccanismo.

FASE 3 (solo se fase 2 riesce): reproducer minimale (progetto Quarkus/Vert.x nudo, senza il nostro dominio) + issue upstream (quarkusio/quarkus o eclipse-vertx/vert.x, decidere dove in base al punto esatto del fault) con il test deterministico allegato. Bozza in claudedocs, invio a maintainer (LUI spedisce: regola invii esterni). Precedente: draft upstream Timefold.

REGOLE: RootPathHandler resta comunque (cura indipendente del 500 di produzione). Nessuna modifica al path di serving per il solo gusto di testare: la fase 1 usa un profilo probe, il default production resta compressione-off finche' la fase 1 non decide. Priorita' BASSA rispetto al fronte H34/TASK-560: item serale/di coda. Nessun em-dash.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Fase 1 eseguita: profilo probe con compressione ON, hammering concorrente (>=2-3k richieste, N parallelo), predizione PRIMA, esito documentato con numeri (conteggio malformazioni, ordine header verificato)
- [x] #2 Stack dichiarato: versione Vert.x effettiva dal dependency tree riportata nel task/registro
- [x] #3 Se fase 1 pulita: compressione riabilitata in application.properties con nota di chiusura TODO + task chiuso
- [ ] #4 Se riproduce: fase 2 (banco Byteman deterministico sul path reale, javap-verificato) + fase 3 (reproducer minimale + bozza issue upstream in claudedocs; invio solo a maintainer) [NON NECESSARIA: nessuna riproduzione]
- [x] #5 RootPathHandler invariato; default production resta compression-off finche' la fase 1 non decide [deciso: ON]
<!-- AC:END -->

## ESITO 26/08 14:35 (fase 1 PULITA)
Predizione scritta PRIMA (registro): 0 malformazioni attese. MISURA: la property e'
BUILD-TIME (il -D runtime viene rifiutato col warning "build time fixed": primo giro
banco nullo, dichiarato e rifatto col flag nel build). Hammering: 3000 static
(RootPathHandler / + bundle 308KB) + 3600 JSON compressi (status/phase-progression/
config-slots), 50-60 thread, connessione nuova per richiesta, header-first assertito
al byte su ogni risposta: 0 malformazioni su 6600, 0 "Response head already sent",
0 "HTTP/0.9", 0 eccezioni Vert.x. Compressione attiva sul JSON REST (gzip 3600/3600);
il path statico Quinoa su 3.31.4 serve non compresso comunque (l'arena di marzo e'
immune per costruzione). Stack: Quarkus 3.31.4 + Vert.x 4.5.25 (jar runtime).
DECISIONE: compressione riabilitata in application.properties con nota di chiusura;
suite 1182/0 EXIT 0 col flag ON; fasi 2-3 non necessarie; RootPathHandler intatto.
