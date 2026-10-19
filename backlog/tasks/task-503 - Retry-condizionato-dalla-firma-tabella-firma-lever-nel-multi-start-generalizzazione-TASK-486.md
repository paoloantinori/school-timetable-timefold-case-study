---
id: TASK-503
title: >-
  Retry condizionato dalla firma (tabella firma->lever nel multi-start,
  generalizzazione TASK-486)
status: To Do
assignee: []
created_date: '2026-08-16 16:00'
updated_date: '2026-08-16 20:28'
labels:
  - claude-planned
  - solver
  - multistart
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Generalizzazione di TASK-486 (M-TC per-retry, oggi hardwired per una sola firma). Oggi i tentativi 2+ del multi-start sono GRASP con seed=numero tentativo, ciechi rispetto al fallimento precedente (verificato in WeeklyTemplateBuilder/ScheduleService: l'unica memoria e' pick-best + stop-al-primo-0-hard, piu' M-TC per-retry incondizionato). Poiche' i fallimenti sono strutturali (census bad-seed n=19: 5 firme distinte, quasi deterministiche per seed), un loop diagnosi->risposta ha segnale reale. Forma: a fine tentativo classificare il residuo hard per famiglia (l'infrastruttura esiste: drill-down analysis, split timeslot-tier/room-tier gia' usato dal gate B-TIMESLOT) e consultare una tabella di regole scritte a mano. Con max 4 punti di feedback per run l'inferenza deve essere regole, non statistica. LIMITI dichiarati (caso live 364+467 round 2): quando la causa e' la configurazione (i pin), nessun reseed cura; il retry smart serve solo i fallimenti da sfortuna di seed.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Classificazione fine-tentativo implementata come funzione pura unit-testabile (input: score/constraint breakdown dell'atttempt, output: firma)
- [ ] #2 Tabella firma->lever con i tre casi censiti (tag->M-TC, H17-composizione->CHC/Kempe, boxed->noop) + estensibilita' documentata
- [ ] #3 Ogni lever attivato dalla tabella resta improve-or-leave: il tentativo con lever non puo' finire PEGGIO del pick-best gia' in mano (il revert e' nel contratto)
- [ ] #4 Flag forma standard opt-out, DEFAULT OFF fino a gate A/B su bad-seed noti (grasp-seed-1 H17, grasp-seed-6 tag)
- [ ] #5 Nessuna modifica al modello/constraint set esistente (direttiva modello-vs-ricerca: solo ricerca)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Validazione OFFLINE della classificatrice (16/08 notte, coda del mandato)

Applicata agli analysis.json del census bad-seed (task452-results/badseed-frequency-input-2026-08-14, n=21 run). Tabella di contegno:

| run | firma attesa (census) | firma classificata |

|---|---|---|

| grasp-seed-1 | H17 strutturale G11 | H17_COMPOSITION (H17 -9 + H29 -1) OK |

| grasp-seed-9 | H17 G11 | H17_COMPOSITION (H17 -9) OK |

| grasp-seed-17 | H17 G13 mercoledi | H17_COMPOSITION (H17 -7) OK |

| grasp-seed-12 | H0+H17 mixed | BOXED_H0 (H0 -32 + H17 -6: H0 all'84%) OK, coerente col caso censito |

| grasp-seed-6 | H29 room-null mono-tag | TAG_SATURATION (H29 -1) OK |

| grasp-seed-18 | H28 -32 | UNKNOWN: CORRETTO, la firma H28 non e' nella tabella (nessun lever censito per H28) e il fallback e' il reseed di oggi |

| tutti gli altri 15 | 0-hard (good) | CLEAN (nessuna chiamata: il wrapper classifica solo i falliti) OK |

5/5 firme censite classificate correttamente + il caso non-censito (H28) cade onestamente in UNKNOWN. NOTA emersa: grasp-seed-12 conferma che la soglia 50% mette BOXED_H0 sopra H17 quando H0 domina numericamente (-32 vs -6): coerente col census che lo descriveva H0-dominante.

Nessun codice nuovo (specchio python della regola Java per l'offline), contegno registrato qui.
<!-- SECTION:NOTES:END -->

NOTTE 03/10 (laptop, lane): A/B su cicli freschi completato ma VERDETTO INCONCLUNTE per difetto di disegno. Braccio A (regola 486): [0/0]hard, 63min. Braccio B (signature table): [-2/0]hard, 68min; primo run di B morto su FLAKE INFRA (PSQLException connection closed a meta ciclo, container Up: pool/proxy lane, NON il flag), rerun su DB vergine completato. La leva del tentativo 2 in B e stata M-TC (stesso banner della regola 486): su questo bacino la tabella coincide con la regola. DIFETTI DI APPAIAMENTO (da curare prima di un nuovo gate): (a) graspSeedCounter e JVM-static: i due bracci hanno reseedato con serie DIVERSE (A da #26, B da #1); (b) il tentativo 1 stesso diverge fra JVM (variabilita intrinseca MT-LS dichiarata). Con n=1/braccio e bacini non appaiati, -2 vs 0 non e attribuibile al flag. Il flag resta DEFAULT OFF. Via piu pulita per il prossimo gate: paired-seed via operatore HONOR (stesso base seed nei due bracci) o banco a livello di tabella.
