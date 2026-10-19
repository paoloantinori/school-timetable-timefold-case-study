#!/bin/bash
set -euo pipefail

# Analysis-discipline reminder (maintainer, 8/09: "troppi falsi entusiasmi"): nine
# false findings in two days, ALL born in ad-hoc derived artifacts (python
# heredocs / greps over seed CSVs, dumps, checkpoints) and all in the
# discovery direction. This hook fires at the exact moment of risk - a Bash
# command that runs python over the project's data artifacts - and injects the
# four-point reconciliation discipline. A reminder, never a block.

input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // empty')

if echo "$command" | grep -qE 'python3?' && echo "$command" | grep -qE 'calendario_|lessons\.|lessons\.txt|checkpoint|POST_B_ROOM|meta\.tsv|tsmap|export\.json|analysis\.json|incarichi'; then
  cat <<'EOF'
{
  "reminder": "ANALYSIS-DISCIPLINE (lessons.md famiglia 3: DIECI falsi ritrovamenti in tre giorni, tutti in questa forma di comando): (1) RICONCILIA i totali con una sorgente indipendente PRIMA di riportare (placed totali=12510, hard: analysis.json, pratica: il golden) e fallisci rumorosamente se non tornano - gli zeri silenziosi sono il sintomo ricorrente; (2) chiediti se lo strumento che leggi e' CAMPIONE o CENSIMENTO (precedenti: cap del tracer, grep troncato); (3) per affermazioni sulla pratica della scuola: cosa fa il GOLDEN prima di dirle; (4) le scoperte non ancora riconciliate si etichettano TENTATIVE nel report, mai come fatti; (5) quando carichi una mappa di supporto estratta altrove (meta/tsv/tsmap/quote) o filtri il SUO output: verifica la CODIFICA dei campi e il match del filtro su 3 righe di campIONE PRIMA di usarli - un filtro che non matcha mai non fallisce, filtra in silenzio dal lato sbagliato (falsa scoperta #10: pseudo 't'/'f' vs 'true'/'false' = una notte di cure su un fenomeno inesistente; e il grep '^[A-Za-z]' che scartava tutte le righe 'G04')."
}
EOF
  exit 0
fi

exit 0
