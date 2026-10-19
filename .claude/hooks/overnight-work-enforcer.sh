#!/bin/bash
set -euo pipefail

# Overnight work enforcer (maintainer, 9/09: "serve un hook per evitare questo di
# nuovo"): quando il mandato 'andate avanti fino a domattina, non fermarsi mai'
# e' attivo (marker file), BLOCCA la fermata del turno e ricorda di continuare.
# Il marker si crea quando maintainer da' il mandato notturno e si rimuove quando
# maintainer dice di fermarsi (o all'ora concordata).

MARKER="/tmp/planner-overnight-active"

if [ -f "$MARKER" ]; then
  # Read the deadline from the marker (if present, else assume 08:00)
  DEADLINE=$(cat "$MARKER" 2>/dev/null | grep -oE '[0-9]{2}:[0-9]{2}' || echo "08:00")
  NOW=$(date +%H:%M)

  # If past the deadline, remove the marker and allow the stop
  if [ "$NOW" \> "$DEADLINE" ]; then
    rm -f "$MARKER"
    echo "Overnight marker expired (deadline $DEADLINE, now $NOW): stop allowed." >&2
    exit 0
  fi

  # Block the stop: remind to continue
  echo "MANDATO NOTTURNO ATTIVO (scadenza $DEADLINE, ora $NOW)." >&2
  echo "La direttiva e' 'non fermarsi mai fino a domattina'." >&2
  echo "Se hai finito un'attivita', passa alla successiva dal piano." >&2
  echo "Se non hai piu' attivita', guarda: checkpoint in memoria, backlog, journal differito," >&2
  echo "verifiche dal golden, pulizia, documentazione. MAI fermarsi al bilancio." >&2
  echo "Per terminare: rm $MARKER (o aspetta la scadenza)." >&2
  exit 2
fi

exit 0
