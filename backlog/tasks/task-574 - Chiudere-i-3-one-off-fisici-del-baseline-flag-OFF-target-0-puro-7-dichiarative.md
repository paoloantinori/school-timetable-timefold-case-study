---
id: TASK-574
title: >-
  Chiudere i 3 one-off fisici del baseline flag-OFF: target [0 puro / -7
  dichiarative]
status: Done
assignee: []
created_date: '2026-08-30 12:50'
updated_date: '2026-08-30 13:19'
labels:
  - claude-planned
  - flag-off
  - hard-zero
milestone: m-13
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Decisione maintainer 30/08 (post n=3 e verifica golden-first): il regime quasi-ON e' RESPINTO (il costo dominante non e' pratica del golden) e le violazioni fisiche non sono accettabili. Il target diventa: ciclo flag-OFF deterministico che consegna [0 puro / ~-7 dichiarative], chiudendo i 3 one-off fisici del baseline v2.1 (identificati dal checkpoint export task564-results/gate-ciclo-v21b-28/export.json.gz):

1. H7 -2: overload 7h (cap 6). Candidati dal conteggio: T03 2026-11-04 (7h) e T26 2026-09-30 (7h); lo score ne conta UNO (verificare quale: esenzioni pinned o conteggio). Golden: NESSUN docente supera mai 6h (input corretto, non curabile nei fogli).
2. H29 -1: lezione senza stanza: T10, Lavorazioni meccaniche, G13, 2026-11-04 (stesso giorno dell'overload T03: probabile interazione). Diagnosi da fare: tag richiesto (meccanica), occupazione dei lab meccanica quel giorno, perche' RoomTailFinisher non l'ha chiusa.
3. H33 -2: 1 giorno sotto pavimento (candidati dal conteggio rapido: 7 giorni sotto il mio floor approssimato, lo score ne conta 1: usare DayFloorDeriver o il drill-down per identificare quello vero).

APPROCCIO OBBLIGATORIO (maintainer): checkpoint/fasi, NIENTE full-solve per indagare. Il checkpoint esiste gia' (export del baseline); i pass mirati si testano con la famiglia *CheckpointTest / replay.

Domanda aperta di ricerca: i 3 one-off sono chiusibili con pass deterministici mirati (stile finisher) o richiedono pressione di search (pesi/mosse)? Il basso numero (3 celle) e la natura one-off suggerisce pass mirati tipo RoomTailFinisher (per H29) e un pass overload-day (per H7).

Out-of-scope: il regime quasi (chiuso), il debito dichiarativo oltre -7 (e' la pratica golden).
<!-- SECTION:DESCRIPTION:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
DESIGN OneOffFinisher (30/08, da validare su banco PureOneOffCheckpointTest):

POSIZIONE: pass deterministico post-B-ROOM, DOPO RoomTailFinisher, pre-save (sibling della famiglia;RoomTail non puo' chiudere l'H29 da solo: muove stanze, non timeslot). Flag planner.construction.oneoff-finisher.enabled forma opt-out standard + banner. Oracle: ensureImproveOrNoop (HardLevels lessicografico). Dopo ogni mossa timeslot, la lezione riceve stanza al landing (check H2/H-M2): non ci sono fasi room successive.

FAMIGLIA A (H7 relief): per ogni (docente, data) con ore > effectiveMax: candidati = lezioni UNPINNED del docente quel giorno (tutte pinnate = skip + log), ordinate per movabilita' (bordo del blocco prima, non quota-bound). Target = (gruppo-giorno con deficit H32/H34 O headroom) x slot dove: docente libero, gruppo libero, slot in finestra calendario + in-sessione (riga quota), catena gate condivisa ExactFinisher (H1/H3/H14/H11/H7/H17/H31/H5/H28). Applica se il puro miglioria.

FAMIGLIA B (H29 relocation): per ogni lezione roomless a docente reale: se requiredRoomTags: cerca (data, slot) con stanza del tag LIBERA (R15 e' unica: muoversi nel tempo, come fa il golden) + docente/gruppo liberi + quota-compatibile (H23 per gruppi FCQuota) + H17/H31-safe; muovi e assegna stanza al landing. Senza tag: assegna stanza libera allo slot corrente (fallback, di solito rfa di RoomTail).

FAMIGLIA C (H33 floor fill): per ogni (gruppo, data) in-sessione con ore < floor: donatori = giorni same-group sopra floor/quota, mosse tail-first (stile QFR v2); se le tail sono bloccate da pin: estrazione mid-blocco + slide (macchinario EDF). Riempi al FLOOR (non alla quota: disturbo minimo). Guardie: H31 (prima ora donatore mai), H17 entrambi i lati, H5 docente, H1/H3.

TARGET VERIFICABILI sul baseline (il banco RED li pinna): A chiude T03 04/11 e T26 30/09 (H7 2->0), B chiude T10 meccanica G13 04/11 h12 (H29 1->0), C chiude G14 30/04 1h->3h (H33 -2->0). Il dichiarativo resta -7 (accettabile: oltre-golden = 6 vuoti coda-d'anno + 1 cella G13 06/11; G14 fuori scope mappe ma H33 coperta da C). Esito atteso banco: pure 0 / dichiarative -7.

TDD: (1) banco RED gia' scritto (PureOneOffCheckpointTest, esecuzione pendente finestra JVM); (2) micro-unit per le guardie di mossa (fixture sintetiche stile RepairGuardRevertTest); (3) ciclo completo di conferma sul laptop solo dopo banco verde.

INVENTARIO FINALE baseline (fonte: export + mappe + score): puro -5 = H7 T03 04/11 (7h, tri-gruppo), H7 T26 30/09 (7h, 4 gruppi), H29 T10 meccanica G13 04/11 h12, H33 G14 30/04 deficit 2. Dichiarativo -7 = H34 x6 (G06 09-10/06, G09 03/06, G10 09/06, G11 04/06, G12 14/05: tutti oltre-golden, coda d'anno) + H32 x1 (G13 06/11 4h vs golden 5).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Identita' completate 30/08 pomeriggio (tutte da checkpoint export, zero JVM):

1. H7a: T03 2026-11-04 7h (cap 6): giornata tri-gruppo (G03 termoidraulica h8-9, G08 Taglio+Sicurezza h10/12, G13 Tecnologia h11, G10 Sicurezza h14:30-15:30). Golden: mai >6h (verificato su tutto l'anno).
2. H7b: T26 2026-09-30 7h: maratona 4 gruppi (Servizio stilistico G10 h8-9 + G12 h11 + G06 h14:30-15:30, Ra lab aperto G02 h12-13). Il docente del triangolo G06.
3. H29: T10, Lavorazioni meccaniche, G13, 2026-11-04 h12. R15 e' l'UNICA stanza meccanica (ROOM_TAG_MAP) e a h12 tiene il lab quota-bound G04/T15. Golden: ZERO collisioni meccanica in anno (separa sempre). NB stesso giorno di H7a: il 04/11 e' un giorno caldo.
4. H33: G14 2027-04-30: 1h collocata (Servizio stilistico, unpinned, R03) vs floor 3. Golden quel giorno: 3h (Competenze Digitali x2 + Orientamento). Gli altri candidati sotto-floor sono fuori scope H33 (giorni senza quota aula+lab; le ore slippate li' sono pinnate, esenti H26).

H7 conteggio: penalta' PER ORA in eccesso (ofHard(1) x excess): il -2 = due match da 1 (T03 + T26), non uno da 2. H33 pure per-ora: -2 = deficit 2 del giorno G14.

DISSEGNO proposto (da validare su replay): pass deterministico post-B-ROOM, tre famiglie di mosse: overload-relief (sposta l'ora in eccesso del docente), roomless-relocation (slot dove la stanza taggata e' libera), floor-fill (porta il gruppo al pavimento). Oracle improve-or-noop standard. Banco: SnapshotFixture.loadFromExport sul baseline export + le 4 celle come target.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
