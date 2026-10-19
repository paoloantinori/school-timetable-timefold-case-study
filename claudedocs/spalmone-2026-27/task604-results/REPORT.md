# REPORT: banco M39 (TASK-604, 12/09) - VERDETTO DI MORTE

## ESITO (i criteri di morte pre-registrati SCATTANO)

**Braccio B (M39 ON) -13hard: PEGGIORE del braccio A (controllo) -11hard
di 2, con H34 INVARIATA (3=3).** Il criterio "se il tier hard non migliora
o una famiglia regredisce oltre il guadagno H34, M39 muore da questa
forma" e' soddisfatto nella direzione negativa: hard regredisce, H34 non
guadagna nulla. **M39 muore da questa forma.** Il flag resta nel codice
opt-in DEFAULT OFF; la decisione finale e' di Fedora/maintainer su questo REPORT.

## Vettori hard a confronto (analysis.json)

| famiglia | A (m39-off) | B (m39-on) | delta |
|----------|-------------|------------|-------|
| H17 Vertically compact | -3 (3) | -4 (4) | peggiora |
| H32 Quota fill | -3 (3) | -3 (3) | invariata |
| H34 Empty declared day | **-3 (3)** | **-3 (3)** | **INVARIATA** |
| H0 Unassigned | -2 (1 lez.) | -2 (1 lez.) | invariata |
| H7 Teacher daily | 0 | **-1** | nuova |
| TOTALE hard | **-11** | **-13** | **-2** |
| M39 medium (la tassa) | 0 | **-100 (1 match)** | |

## PREDIZIONE: CONFUTATA (con una premessa da dichiarare)

La predizione (H34 10-><=4, ~23 ore migrate, medium ~-2300) era calibrata
sulla consegna spread-11sep (H34=10). MA questo braccio A (stessi flag,
profilo fresco, MT-LS diversa) ha consegnato H34=3: la composizione del
bacino e' differente (l'H34 del -11 di stamattina era 10; qui e' 3, con
H17/H32 a 3). Su QUESTA coppia A/B (l'unica internamente valida: tutto
identico tranne M39): (1) H34 non si muove; (2) la tassa M39 e' -100 =
1 sola lezione matching (non 23: o la composizione di questo bacino non
ha le ordinarie-in-stage, o le 22 altre sono gia' altrove); (3) hard
peggiora di 2 (H17 +1, H7 +1). Nessun componente della predizione si
avvera su questo run.

## Banner-verification del braccio B: SUPERATA

`M39 stage-day preference (TASK-604, prototype): enabled=true, weight=100`
nel log alle 15:33:04 + cwd guard `/Users/user/planner` (lsof). Il
banco e' VALIDO: il flag e' arrivato al codice.

## INCIDENTE registrato (primo lancio braccio A)

Il primo lancio del braccio A (11:27) ha servito il JAR DEL 29/08 del
worktree day-compact-542 (cwd del processo nel worktree, `-jar target/...`
relativo): misurava il quadro di tre settimane fa. Fermato alle 11:44,
profilo m39-off eliminato, RILANCIATO con `cd /Users/user/planner`
esplicito nella stessa riga + doppia guard (lsof cwd + banner
`Teacher-weekday-spread (TASK-599, RATIFIED 12/09): enabled=true` nel
log, presente solo nel jar di oggi). Il dato del primo lancio e' stato
scartato (cattura mai eseguita). La guard e' ora parte del protocollo
di lancio.

## Tentativi, durate, vincitori

- Braccio A: lancio valido 11:48, consegna 15:09 (3h21m), multi-start,
  vincitore attempt-1 ("restoring best attempt 1 (last run was #2)"),
  [-11/0]hard/[-1188844/1420]soft (score.txt; analysis medium -654806,
  divergenza TASK-509).
- Braccio B: lancio 15:33, consegna 18:45 (3h12m), multi-start,
  vincitore attempt-1, [-13/0]hard/[-1136506/967]soft (analysis medium
  -606374).
- Sanity: A 4 rossi, B 5 rossi (G07 x2, G08, G09, G13).
  Full-day: B 93,2%.

## Dottore (verbatims)

- m39-off: `DOCTOR-WARN sanity-reds=4 hard=[-11/0]` + WARN export 106MiB
- m39-on: `DOCTOR-WARN sanity-reds=5 hard=[-13/0]` + WARN export 106MiB

## Classificazione H34 residue (braccio B)

H34=3 matches: identificazione per gruppo/data nel dump lessons.txt
(elenco nel confronto HTML; le G13 04-26/05-24 attese non verificate
esplicitamente qui: da leggere dal confronto).

## Export

Entrambi gli export.json.gz (106 MiB) restano SOLO sul laptop nelle rispettive
dir, non committati (cap GitHub 100MB).
