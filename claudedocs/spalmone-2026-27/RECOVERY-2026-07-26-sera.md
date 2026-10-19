# CHECKPOINT DI RIPRESA, 2026-07-26 ~19:00 (shell Bash morta ambiente-wide)

SINTOMO: ogni comando Bash (anche `true`, anche sandbox disabilitata) ritorna exit 1
senza output, sia nella sessione principale sia nelle shell fresche dei subagent.
Probabile: da riavviare Claude Code (nuova shell) o guardare la macchina (disco? profilo
shell?). NIENTE del lavoro e' perso: e' tutto su file, non committato.

## Stato del working tree (NON committato, HEAD = 7d19ce5f)

Treno locale COMPLETO NEL CODICE ma NON VERIFICATO (la shell e' morta prima):
1. M20 progressivo (weigher triangolare STEP=500) - VERIFICATO prima del guasto
   (suite 784 EXIT=0 dall'agente M20).
2. M20.2 coerenza (stessa curva; guard repin -8000 -> -14000/2 motivato) - compilata
   nell'ultimo `-s` buono, test aggiornati NON eseguiti.
3. Gate stage C allineato a BoxRepair (primitives estratte, non duplicate) +
   HoleRepair post-smarcatura pre-B-ROOM (livelli: coda libera -> min-slide ->
   ex-promosse; min-slide su smoke2 e' dead-weight dichiarato) - QUATTRO FILE MAI
   COMPILATI: HoleRepair.java (ultime 3 edit), HoleRepairTest.java, i 2 casi nuovi
   in StagedPinPolicyTest, Task419HoleRepairCheckpointTest.java.
4. Banner UI staged (typecheck/build 0, verificati prima del guasto).
5. In volo al momento del guasto: agente hole-topology-kpi (script Python + marker
   viz + censimento) - controllare il suo esito/file.

## COMANDI DI RIPRESA (in ordine, appena la shell vive)

1. `./scripts/core/test.sh > /tmp/train3.log 2>&1; echo $?`  -> DEVE essere 0; se
   compile error, sono nei 4 file sopra (fix piccoli attesi).
2. `PLANNER_TEST_HOLE_REPAIR=/tmp/val419-staged-smoke2 ./scripts/core/test.sh -c Task419HoleRepairCheckpointTest`
   -> LA domanda aperta: il pass chiude i 14 slot di buco (H17 -14 -> 0)? L'assert
   giusto e' reduction >= closed (una partenza puo' chiudere anche il buco d'origine).
3. `./scripts/ui/ui-typecheck.sh; ./scripts/ui/ui-build.sh` (banner UI).
4. Verifica esito agente hole-topology (script + golden-hole-topology.json + marker
   nella viz: golden DEVE dare 1 buco interno, smoke2 9/14, val408 1/1;
   bordo golden: 131 fine-anticipata / 2 inizio-tardivo).
5. Review di treno leggera sul diff accumulato, poi commit per blocchi (M20prog+M20.2;
   stageC+HoleRepair; UI; KPI topologia) e push.
6. laptop: controllare l'A/B staged (era in corso run1/run2 post-fix H16b; VERDICT atteso
   in serata in task419-results/) - il cron di monitoraggio muore col riavvio: ricrearlo
   o controllare a mano. Poi il GATE FINALE: staged completo (con HoleRepair+M20prog)
   x2 vs val408, attese: hard 0, H16b 0, H17 0, buchi interni 0, ricorrenza ~74%,
   burst ~42, giorni profondi non-caveat ~golden, fine-anticipata come unica forma di
   ora mancante.

## Regole topologiche verificate oggi (per la scorecard)

Golden: buchi interni 1/anno (eccezione pinned H17 documentata), inizio-tardivo 2,
fine-anticipata 131 = unica forma normale. Modello gia' compatibile (H17 hard / M18 /
M20); strumenti resi consapevoli dal nuovo hole-topology-kpi (in volo).
