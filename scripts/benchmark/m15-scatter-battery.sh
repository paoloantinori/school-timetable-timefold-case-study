#!/usr/bin/env bash
#
# m15-scatter-battery.sh - batteria A/B a due worktree su snapshot (TASK-419 watch-item,
# 2026-07-26 notte; irrobustita dopo il code-review del 2026-07-29).
#
# Domanda originale: la curva M20 progressiva (marginale 1000->4500 sui giorni profondi)
# spinge la LS di fase 2 a SPEZZARE i same-day same-subject (M15 ~1200/split) pur di
# riempire i giorni profondi? E' il timore storico del task-349. Esito: REFUTATO su
# entrambi i bracci (campaign-status-2026-07-26-gate419.md, sezione "Batteria M15-scatter").
#
# Protocollo: 3 run x 2 bracci di Phase2SmokeSnapshotTest (600s, NON_REPRODUCIBLE), stessa
# partenza = dump di FINE COSTRUZIONE (la costruzione e' identica nei due bracci):
#   P = tree principale (HEAD)
#   F = worktree ${FLAT} (la revisione di confronto)
# Bracci ALTERNATI (P1,F1,P2,F2,P3,F3) per equita' di carico nel tempo. Ogni run salva il
# grid finale in ${OUT}/{P,F}N.txt e il log in ${OUT}/{P,F}N.log; ${OUT}/progress.txt porta
# esito, orario e commit di ciascun run. L'analisi (M15 non-lab, giorni profondi,
# ricorrenza) e' dell'orchestratore, a valle.
#
# TREND ONLY - uno snapshot fissa UNA traiettoria; mai adozione da questa batteria.
#
# Perche' i controlli qui sotto: un braccio che non produce il dump, o una `cd` fallita che
# fa girare due volte lo stesso albero, genera artefatti dall'aria credibile e conclusioni
# sbagliate su un A/B costato un'ora. Meglio abortire rumorosamente.
set -uo pipefail
MAIN=${MAIN:-/home/user/data/repo/personal/planner}
FLAT=${FLAT:-/home/user/data/tmp/planner-flat}
OUT=${OUT:-/home/user/data/tmp/battery}
SNAP=${SNAP:-$OUT/constr}
BUDGET=${BUDGET:-600}
GRID=target/ph2smoke-lessons.txt

fatal() { echo "FATAL: $*" | tee -a "$OUT/progress.txt" >&2; exit 1; }

mkdir -p "$OUT" || { echo "FATAL: non posso creare $OUT" >&2; exit 1; }
[ -d "$SNAP" ] || fatal "snapshot di partenza assente: $SNAP"
for tree in "$MAIN" "$FLAT"; do
  [ -d "$tree" ] || fatal "albero assente: $tree (worktree potato? clone fresco?)"
done

run_arm() { # $1=tree $2=label
  local tree=$1 label=$2
  cd "$tree" || fatal "$label: cd fallita su $tree"
  # Il dump precedente va rimosso PRIMA: test.sh non fa `clean`, quindi un run che muore
  # prima di scriverlo lascerebbe copiare il grid del run precedente come campione nuovo.
  rm -f "$GRID"
  PLANNER_TEST_PH2SMOKE="$SNAP" PLANNER_TEST_PH2SMOKE_BUDGET=$BUDGET \
    PLANNER_TEST_ENV_MODE=NON_REPRODUCIBLE \
    ./scripts/core/test.sh -c Phase2SmokeSnapshotTest > "$OUT/$label.log" 2>&1
  local e=$?
  local head_sha dirty
  head_sha=$(git -C "$tree" rev-parse --short HEAD 2>/dev/null || echo '?')
  dirty=$(git -C "$tree" status --porcelain 2>/dev/null | head -1)
  echo "$label EXIT=$e $(date +%H:%M:%S) tree=$tree commit=$head_sha${dirty:+ DIRTY}" >> "$OUT/progress.txt"
  # Il codice di uscita del test non e' il criterio: il criterio e' il campione. Un braccio
  # senza grid non ha prodotto nulla di analizzabile e la batteria non deve proseguire
  # bruciando altri run su una configurazione gia' nota come sterile.
  [ -f "$GRID" ] || fatal "$label: nessun grid prodotto (vedi $OUT/$label.log)"
  cp "$GRID" "$OUT/$label.txt" || fatal "$label: copia del grid fallita"
}

echo "battery start $(date +%H:%M:%S) budget=${BUDGET}s" > "$OUT/progress.txt"
for i in 1 2 3; do
  run_arm "$MAIN" "P$i"
  run_arm "$FLAT" "F$i"
done
echo "battery done $(date +%H:%M:%S)" >> "$OUT/progress.txt"
