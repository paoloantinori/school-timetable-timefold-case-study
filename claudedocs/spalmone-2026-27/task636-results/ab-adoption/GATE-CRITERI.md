# GATE PRE-REGISTRATO (Fedora, criteri del gate 20/09) + piano di misura

Criteri MECCANICI (numeri dai raw, non dal pane):
1. hard di consegna del braccio ON mai peggiore dell'OFF
2. ricorrenza unpinned >= 68 in ENTRAMBI i bracci
3. ore di Servizio tecnico e stilistico su G06 a giugno: ON <= OFF
4. conteggio celle H34 sul consegnato (decomposition, scope corretto)
5. log braccio ON: "Cadence-pace tail (TASK-636): armed=true" con paceBoostApplications > 0

Piano: due profili FRESCHI consecutivi (task636-off, task636-on), java -jar diretto
(PLANNER_JAVA_OPTS), heap 2g, caffeinate, capture ON, capture-run.sh in
task636-results/ab-adoption/{off,on}/, banner-check prima di fidarsi della misura.
Lettura visiva dei confronti HTML (regola consumo-dataviz) nel verdetto.
