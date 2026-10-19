---
id: TASK-672
title: >-
  Re-pin PureOneOffCheckpointTest: pin -12 stantio (replay -151 identico
  pre/post TASK-652, discriminante laptop 27/09)
status: Done
assignee: []
created_date: '2026-09-27 20:22'
updated_date: '2026-09-28 10:33'
labels: []
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
CORREZIONE 28/09 mattina (supersede "pin stantio"): il rosso del 27/09 era INPUT SBAGLIATO del banco: h32b-run1 invece del checkpoint TASK-574 (task564-results/gate-ciclo-v21b-28/export.json.gz, MAI COMMITTATO; recuperato su disco laptop come artefatto STRIPPATO non caricabile dal fixture odierno: solo lessons+quotas, datetime nel campo date). Le quattro identita' pinnate non esistono in h32b-run1 (G14 30-04 = 3 lezioni) e le one-off fisiche non esistono piu' in NESSUNA consegna moderna. SOLUZIONE: RE-FREEZE su integrato-7 (consegna di frontiera 23/09): RED = -1 (sola cella H17 G04 2026-11-20 gap Scienze/Lingua -> Orientamento/Informatica; H7/H29/H33 a zero, verificati con ri-derivazione indipendente dal raw: 12551 lezioni), APPLY = no-op safety (movesApplied 0, score invariato). Arma curativa = TASK-673. CHECKPOINT COMMITTATO: forma VRP-cache-stripped (availableTimeslotsFresh svuotato, 111MB -> 500KB, la cache e' ricostruita dal fixture al load; il push del 111MB era stato RIFIUTATO dal limite 100MB di GitHub). PROVENANCE: originale pieno sha256 4edf359556bc0dacecf38a86617b3a82e84e92d2b7f5b46c88a82650290f526e (copia untracked su Fedora e laptop come export-full-untracked.json.gz), stripped committato sha256 0d37fc83682abb4cd23b7eb7b52a4187d26a80878c3f014330e373eff548dc96. BLOAT DEL WRITER: scoperto che ogni export dal 23/09 serializza la cache VRP per-lezione (~100KB/lezione, 500KB -> 111MB): TASK-674. Gates: /simplify 4-angle (10 fix applicati) + code-review high 5/5 CONFIRMED + suite 1398/0/104 EXIT 0 + (pendente) verifica armata RED+APPLY su laptop.
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Chiuso 28/09 ore 12:28. Consegna: PureOneOffCheckpointTest re-freezato sul checkpoint di frontiera integrato-7, COMMITTATO in forma VRP-cache-stripped (500KB, sha256 0d37fc83...; l'originale pieno 111MB resta come export-full-untracked.json.gz su Fedora e laptop, sha256 4edf3595..., dopo che il push del pieno era rifiutato dal limite 100MB GitHub). RED pin: -1 = sola cella H17 (G04 2026-11-20, gap Scienze/Lingua -> Orientamento/Informatica), famiglie one-off fisiche H7/H29/H33 a zero (verificate con ri-derivazione indipendente dal raw, 12551 lezioni). APPLY pin: no-op safety (Result[0,0,0,0], score invariato). VERIFICA ARMATA SU laptop (HEAD 837488b1): RED_EXIT=0 e APPLY_EXIT=0 con breakdown identico ai pin. La causa del rosso del 27/09 era l'input sbagliato (h32b-run1 al posto del checkpoint 574 perduto, mai committato e recuperato solo come artefatto stripped non caricabile); il banco ora nomina e committa il proprio input (difetto di design chiuso). Gates: /simplify 4-angle (10 fix), code-review high 5/5 CONFIRMED + 2 finding curati (javadoc .gz, export nello stesso commit), suite 1398/0/104 EXIT 0. Derivati: TASK-673 (arma curativa con one-off iniettate), TASK-674 (bloat export 200x: la cache VRP per-lezione serializzata dal writer dal 23/09).
<!-- SECTION:FINAL_SUMMARY:END -->
