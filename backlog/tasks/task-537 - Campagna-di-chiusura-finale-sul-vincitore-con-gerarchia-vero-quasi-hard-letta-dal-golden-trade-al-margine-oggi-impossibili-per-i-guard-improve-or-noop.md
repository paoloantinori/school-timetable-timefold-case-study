---
id: TASK-537
title: >-
  Campagna di chiusura finale sul vincitore con gerarchia vero/quasi-hard letta
  dal golden (trade al margine oggi impossibili per i guard improve-or-noop)
status: Done
assignee: []
created_date: '2026-08-20 11:38'
updated_date: '2026-08-20 11:55'
labels:
  - claude-planned
  - orchestration
  - quasi-hard
  - golden-first
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalle riflessioni con maintainer 20/08 pomeriggio (crawl-back, soluzione quasi-perfetta come checkpoint, hard sacrificabili in extremis letti dal golden): campagna di chiusura finale SUL VINCITORE del multi-start, deterministica, replay su checkpoint. I repair pass gia' fanno chiusure mirate ma i loro guard improve-or-noop trattano TUTTI gli hard come uguali: la mossa che chiudeva 5 celle H32 pagando 1 ora H17 veniva REVERTITA (evidenza 19/08 notte). Nessuna famiglia era sacrificabile = nessun trade al margine possibile. La gerarchia vero/quasi-hard si LEGGE dal golden (audit Fedora 20/08): VERI (0 violazioni golden): H1/H2/H3 conflitti, H5, H6, H17 (1/1900), H28 (definizione reale), H30, H25, H26. QUASI col budget che il golden pratica: H11 (43 giorni sopra 6h, 0 sopra 8: il cap praticato e' 8), H31 (~13-17 giorni, quasi tutti il primo giorno di scuola), H-COPRESENCE (44 celle), H32 (1h/anno G09), H33 sotto-4h (4 giorni: G09 x2, G08 x2; G14 15 fuori perimetro). ZERO domande referente per la parte meccanica: al massima conferma di cortesia sul cap-8 di H11. Questa campagna valida anche la SEMANTICA del 4o livello (TASK BendableScore): se i trade funzionano e i budget bastano, il refactor ha la spec collaudata.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Pass deterministico post-pick-best sul checkpoint del vincitore (replay, secondi per iterazione)
- [ ] #2 Gerarchia letta dal golden (spec tabella: H11 cap praticato 8 / 43 giorni-oltre-6; H31 ~1 giorno/gruppo; co-presenza 44; H32 1h; H33 4 giorni G09/G08 + G14)
- [ ] #3 Regola di trade: i veri-hard (H1/H2/H3/H5/H6/H17/H28/H30/H25/H26) non si pagano MAI con altri veri; i quasi sono la valuta per chiudere i veri; mai pagare H17 con un quasi se il vero si puo' chiudere altrimenti... NO: regola corretta: chiudi i veri-hard autorizzato a spendere quasi entro budget golden; mai aumentare un vero
- [ ] #4 Verdetto dichiarato nella forma '0 veri-hard, -N quasi entro budget' con tabella per famiglia
- [ ] #5 DoD standard
- [ ] #6 Validato sui checkpoint di qfr3 e del run combinato: (a) i trade VERO-chiuso-pagando-QUASI entro budget devono riuscire (oggi impossibili: guard uniforme); (b) i trade QUASI-chiuso-pagando-VERO (i revert di QFR del 19/08) devono essere RIFIUTATI esplicitamente dalla gerarchia, non solo annullati dal guard: la direzione del trade e' la semantica, non il segno del delta
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
