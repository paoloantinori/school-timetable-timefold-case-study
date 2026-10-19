# Audit contaminazione JAR-worktree: classificazione completa (TASK-605, 12/09)

Meccanismo: la copia di `prod-start-and-wait.sh` nel worktree day-compact-542 risolve
PLANNER_ROOT al worktree e serve il suo JAR (build 29/08 08:15, privo di: SurplusHarvest
1/09, OneOffFinisher 30/08, RA-cure 11/09, pool-cap-fill 9/09, M38 2/09, e tutto cio'
che e' entrato dopo). Il JAR contiene invece: pre-booking v2.1 (28/08 18:14), EDF
(27/08), H33 (20/08), RoomTail (25/08), QFR (19/08). Finestra: 29/08 08:15 a oggi.
La contaminazione e' INTERMITTENTE (il cwd scivola solo a volte): ogni ciclo va
classificato da solo. Metodi: marker genetico nei dati consegnati (dati/banner di
meccanismi post-29/08 negli artifact propri), discriminante RA (pool G08 626/667)
per i cicli post-11/09, principio di severita' (la misura e' void solo dove il
meccanismo/quadro attivo differisce fra 29/08 e l'HEAD dichiarato).

## VERDETTO: 11 pulite, 2 void, 4 valide-comunque (meccanismi pre-29/08), 1 ignota a basso impatto

| ciclo | classe | evidenza |
|---|---|---|
| v11-n2 (4/09) | PULITA | dati SurplusHarvest nel proprio artifact (codice 1/09 assente dal JAR-29/08) |
| notte-4sep | PULITA | dati SurplusHarvest (2 file) |
| v12-5sep | PULITA | dati SurplusHarvest |
| v123-6sep | PULITA | dati SurplusHarvest |
| v131-7sep | PULITA | dati SurplusHarvest |
| v132-7sep | PULITA | dati SurplusHarvest (la fixture del residual ledger resta una consegna pulita) |
| slotretry-9sep | PULITA | banner surplus-harvest=true verificato nel VERDICT + PREFLIGHT-OK classes; la bocciatura slot-retry veniva comunque dal BANCO FEDORA (H34 22->68) |
| boxdef-8sep | PULITA | "banner BOXED + holds (108 osservato al via)": il banner del meccanismo misurato (8/09) osservato con valore |
| ciclo-sh-on | PULITA | pass girato in-ciclo 3x con contatori ("moves 14-16, cells 9-13, reverted ~2000"): il pass esiste solo nel codice 1/09+ |
| ciclo-sh-fix2 | PULITA | banner surplus-harvest=true verificato nel log (citato nel registro TASK-577) |
| pin-7sep | PULITA | "GUARDIA al banner: SUPERATA... N=9628": banner del pin-fix (7/09) osservato con valori live; verdetto comunque controverificato da Fedora |
| control-11sep | **VOID** | pool G08 626 + zero Recuperi (discriminante RA, provata) |
| spread-11sep | **VOID** | pool G08 626 + zero Recuperi (discriminante RA, provata) |
| edfh33-7sep | VALIDA-COMUNQUE | misurava EDF (27/08, nel JAR-29/08) x H33 (20/08): meccanismi attivi identici nei due JAR, flag armati tutti pre-29/08 |
| edfh33-8sep | VALIDA-COMUNQUE | idem |
| twin-tail-a1 | VALIDA-COMUNQUE | pre-booking v2.1 (28/08, NEL JAR-29/08) x GRASP (antico): quadro effettivo identico |
| ab-grasp-s25-29 | VALIDA-COMUNQUE | idem twin-tail |
| control-10sep | IGNOTA-BASSO-IMPATTO | checkpoint sovrascritti, nessun banner nel REPORT, distribuzione G04 inconclusiva (il repair livella). Severita': le affermazioni portanti del [-17] (celle H34 in gruppi non-FC, H0 G14, H7) sono INDIFFERENTI a pool-cap-fill (che tocca solo i target di costruzione 1/G08, esclusi da H34); cadono comunque l'etichetta "quadro ratificato completo" e l'uscita G08-dai-rossi (gia' ritrattata: pool 626) |

task576 e1/e1bis: banchi live-state in-test (Fedora), non cicli prod laptop: immuni per costruzione.

## Cosa cade con i due void (e solo con loro)

1. "Prima misura end-to-end della cura RA" (control-11sep): MAI AVVENUTA. La prima
   misura vera sara' il braccio A del banco M39 in volo (verificato pulito).
2. "Gate TASK-599 sul quadro attuale" (spread-11sep): il delta [-26]->[-11] resta
   leggibile come evidenza di meccanismo (il lever era nel build 29/08), NON del
   quadro attuale. Le altre gambe (banco locale Fedora, iter-11, regola del
   pianificatore umano) non toccate.
3. L'attribuzione "G08 uscito dai rossi grazie al RA" in control-11sep: falsa
   (pool 626 nel run: uscita da altro).
4. La serie [-13/-17/-26] come "quadro evolvendo": da rileggere (control-10sep e
   control-11sep possono essere stessa-JAR; la differenza resta bacino/flag).

## Verdetti storicamente dipendenti: tutti salvi

- slot-retry BOCIATO: banco Fedora (immune), ciclo comunque pulito.
- SurplusHarvest (adozione): ciclo-sh-on/fix2 puliti (pass girato con contatori).
- pin-full-cells BOCIATO: pin-7sep pulito (banner) + controverifica Fedora.
- boxdef REJECT: boxdef-8sep pulito (banner osservato).
- EDF, pre-booking v2.1, twin-tail: meccanismi nel JAR-29/08, misure valide su
  entrambi i JAR.
- Residual ledger (TASK-539.01): le fixture derivano da v132 (PULITA): provenance
  corretta, nessuna correzione necessaria.

## Lezioni di processo (già meccanizzate)

- Guard anti-worktree nei launcher (c6ae19e4), PRIMA di ogni side effect.
- Banner-check a PRE-solve nel mandato + memoria launch-guard.
- NUOVO (questo audit): la discriminante RA (pool G08 = 667 e righe Recuperi > 0
  nel lessons.txt) va meccanizzata nel capture-doctor come DOCTOR-WARN JAR-STALE:
  verifica genetica automatica di ogni consegna futura.
