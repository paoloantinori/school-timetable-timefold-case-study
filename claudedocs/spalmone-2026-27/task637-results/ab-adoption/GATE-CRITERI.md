# GATE PRE-REGISTRATO TASK-637 theory-spread (Fedora, criteri 21/09)

1. hard consegna ON mai peggiore di OFF
2. ricorrenza unpinned >= 68 entrambi
3. celle teoriche 2+ same-day (M-TR) nel consegnato: ON < OFF (python su lessons.txt,
   set esenzioni M-TR)
4. full-day in banda (>= 93)
5. banner ON: "Theory-spread tail (TASK-637): armed=true theoryHoldFills>0"

Piano: due profili freschi consecutivi (task637-off/on), heap 2g, caffeinate, capture
ON, checkpoints copiati in ab-adoption/{off,on}/checkpoints/ PRIMA del braccio
successivo, banner-check prima di fidarsi.
