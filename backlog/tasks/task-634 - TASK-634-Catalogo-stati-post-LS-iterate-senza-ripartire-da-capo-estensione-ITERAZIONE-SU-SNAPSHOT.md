---
id: TASK-634
title: >-
  TASK-634 - Catalogo stati post-LS: iterate senza ripartire da capo (estensione
  ITERAZIONE SU SNAPSHOT)
status: Done
assignee: []
created_date: '2026-09-20 17:54'
updated_date: '2026-09-20 18:23'
labels:
  - claude-planned
  - m-15
  - infrastruttura-banco
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla domanda di maintainer (20/09 sera): "agiamo solo sulle ultime fasi della pipeline; serve davvero ricominciare da capo ogni volta? non possiamo tenerci un catalogo di soluzioni post-LS su cui iterare?" - ESTENSIONE della direttiva ITERAZIONE SU SNAPSHOT (6/09): da uno-snapshot-per-volta a un catalogo indicizzato. EVIDENZA DEL GIORNO (perche' ora): (1) banco 628: il ciclo plain non contiene la fase di consegna, ogni misura della terna ha dovuto ricostruire stati post-LS (parse 111MB a 12g per volta); (2) caso T13/h7swap: serviva uno stato room-faithful inesistente, il laptop ha dovuto costruire una fixture CH-only ad hoc; (3) diagnosi erosione 631: checkpoint riesumato per il replay. Un catalogo avrebbe reso istantaneo ciascuno.

DISEGNO: punti di cattura = fine B-TIMESLOT (post-LS, pre-RELEASE) e pre-delivery di ogni ciclo del wrapper, per tentativo; formato = export room-faithful + lessons.txt + statics necessarie (la pipe nuda senza stanze serve a poco: lezione 632b); metadati OBBLIGATORI per voce = commit del codice, HASH DEI CSV DI SEED (incarichi+calendari: la lezione H25 replay-artifact: export pre-cura contro quote post-cura produce famiglie fantasma), race/attempt, score e famiglie residue, room-faithful flag; POLITICA DI FRESCHEZZA = il replay rifiuta (o marca inaffidabile) una voce il cui hash seed diverge da quello corrente; RETAINING = gli N stati per famiglia di residuo (uno per razza di bacino: la lezione 628 e' che i bacini differiscono), non tutti; RINFRESCAMENTO = i cicli nuovi aggiungono voci, il catalogo non e' fisso (anti-overfit).

VALORE ATTESO: banchi istantanei sui stati di confine giusti, blind puzzle paralleli coi dati subito (regola near-miss di maintainer), basi pronte per le prove di valore (h7swap: la sua prova finale e' letteralmente una voce di catalogo con overcap quota-tight), A/B appaiati su razze diverse senza 17h di banco.

PRIMA ITERAZIONE (proposta): parte dal ciclo integrato-6 in volo: catturare i suoi tentativi come prime voci col formato metadati; costruire l'indice (file JSON nella dir catalogo) e la guardia hash-seed nel replay harness (DeliveryTrioBenchTest gia' legge export da path: gli manca il check di freschezza).
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
20/09 SERA, PRIMA ITERAZIONE COSTRUITA SU fedora-box (ordine diretto di maintainer 'fallo subito e su questa macchina', commit 9c15523d): strumento scripts/analyze/post_ls_catalog.py (add/status/list), indice in claudedocs/spalmone-2026-27/post-ls-catalog/index.json, README col protocollo e le tre regole. Payload puntati (path+sha256), non committati. DETTAGLIO DI DISEGNO COLTO IN CORSO: l'hash del seed default (working tree) mente per i run storici: m39 hashato col seed di oggi leggeva VALID; corretto con --seed-commit (hash calcolato dal commit del run). PROVA SUL POPOLAMENTO: 4 voci (m39-off/on room-faithful -11/-13, integrato-1/5 lessons-only -4/-1); i tre pre-cura-18/09 = STALE-SEED (b3e622cb), integrato-5 = VALID (007203dc): la guardia dimostra da sola la lezione H25. Due difetti colti alla rilettura prima di runnare (payloadSha256 non inizializzato; parse hard sul bracket sbagliato: medium invece di hard) + fix Pyright: la disciplina rileggi-il-diff ha funzionato. PROSSIMI PASSI: (1) le voci di integrato-6 (addendum mandato al laptop per la cattura per-tentativo con metadati); (2) la guardia Java nel replay harness (DeliveryTrioBenchTest: check seedHash prima del replay, la forma hard); (3) trasferire/puntare l'export room-faithful di integrato-5 (vive sul laptop) come voce di catalogo.

20/09 SERA, CODE-REVIEW HIGH (lanciata da maintainer, 9 finding) TUTTA CURATA in ce9a23da: F1 --export-inesistente ora ERR (era roomFaithful=false silenzioso, la trappola 632b); F4 status exit 1 se non-valid (gate scriptabile); F5 commit reali (m39=2f686d18, integrato-5=267f9aca, integrato-1=38669df3) al posto di date libere; F6 rel() con messaggio pulito fuori-repo; F7 contatori filtrati + ERR su label inesistente; F8 hash-domain ricorsivo+repo-relativo allineato nei due rami (voci ri-aggiunte); F2/F9 rimosse da settings.local.json l'allowlist Bash(git rm *) e la one-shot morta; F3 .gitignore ignora i payload 100MB+ di task604-results (la negazione TASK-401 li avrebbe spazzati nel results-pack = GH001). Verdetto del reviewer: meccanismo core sano, la guardia riproduce esattamente quanto dichiarato; F1/F3/F4 erano i pre-rquisiti per le voci di integrato-6: curati prima.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
