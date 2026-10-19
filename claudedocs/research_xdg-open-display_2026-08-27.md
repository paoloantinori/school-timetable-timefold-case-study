# Research Report: perche' xdg-open ha smesso di funzionare nei terminali herdr

**Date**: 2026-08-27
**Depth**: standard
**Confidence**: HIGH (tutti i passaggi riprodotti o ispezionati sul vivo: processi, ambienti, .desktop)

## Executive Summary

xdg-open non si e' rotto: e' sempre stato a meta' strada. La catena: il server herdr (PID 1742, nato il 26/08 alle 11:47) e' stato avviato **senza DISPLAY** nell'ambiente, quindi ogni pane che genera nasce senza le variabili grafiche. Quando il file da aprire ha come handler un'app **gia' in esecuzione** (firefox ieri per il playground, VS Code quando e' gia' aperto), il processo lanciato delega all'istanza viva ed esce senza mai toccare X: sembra funzionare. Quando l'app deve **creare** la sua prima finestra (VS Code chiuso, come stamattina), Electron inizializza X11, non trova DISPLAY/XAUTHORITY e muore con l'errore visto. La config nativa che rende xo superfluo esiste: esportare l'ambiente grafico risolto dinamicamente nell'init della shell.

## Findings

1. **Il server herdr e' il vettore** (HIGH): `/proc/1742/environ` non contiene DISPLAY ne' XAUTHORITY, mentre un altro contesto herdr (PID 17074) li ha: i pane ereditano a sorte di nascita. La sessione grafica e' Wayland (mutter, Xwayland su :0 con cookie `/run/user/1000/.mutter-Xwaylandauth.*` che cambia suffisso a ogni sessione).
2. **L'handler del .md e' VS Code lanciato direttamente** (HIGH): `xdg-mime query default text/markdown` = `code.desktop`, `Exec=/usr/share/code/code %F`, **nessuna chiave DBusActivatable** neanche in firefox.desktop: xdg-open/gio fanno spawn diretto che eredita l'ambiente del chiamante. Nessun portale interviene.
3. **L'asimmetria funziona/non-funziona e' la delegazione** (HIGH sul meccanismo): firefox gira da ieri (1 processo master), VS Code ha 12 processi vivi ADESSO ma stamattina all'errore presumibilmente no: quando l'istanza esiste, `code file`/`firefox url` delegano via IPC ed escono 0 senza X; quando non esiste, serve la finestra e serve X. Questo spiega il "da un momento all'altro": e' il ciclo di vita dell'app, non xdg-open.
4. **Verifica che l'env esplicito risolva**: `/usr/share/code/code --version` con `DISPLAY=:0 XAUTHORITY=<mutter>` gira (exit 0; i warning DBus riguardano il bus, non il display). Lo script xo incapsula esattamente questo.

## La config nativa (rendere xo superfluo)

Aggiungere a `~/.bashrc` (e l'equivalente zsh se serve) la risoluzione dinamica, cosi' ogni pane eredita un ambiente grafico valido:

```bash
# sessione grafica Wayland per le shell nate senza (herdr panes)
if [ -z "$DISPLAY" ] && [ -e /run/user/$(id -u) ]; then
  _xa=$(ls /run/user/$(id -u)/.mutter-Xwaylandauth.* 2>/dev/null | head -1)
  if [ -n "$_xa" ]; then
    export DISPLAY=:0 XAUTHORITY="$_xa" WAYLAND_DISPLAY=wayland-0
  fi
  unset _xa
fi
```

**Caveat dichiarato**: nei pane longevi i valori diventano stantii dopo un riavvio della sessione grafica (il suffisso del cookie cambia); xo si auto-risolve a ogni invocazione e sopravvive anche a quello. Quindi: bashrc = correzione nativa, xo = cintura auto-guarante. Si possono tenere entrambi (xo usa XO_DISPLAY/XAUTHORITY solo se assenti) o tenere solo il bashrc e accettare il raro caso stale.

Alternativa scartata: `systemd-run --user --desktop` (systemd 259 su fc44) non espone il flag --desktop in help: non percorribile qui.

## Confidence Assessment

HIGH su tutto cio' che e' ispezionato (env dei processi, .desktop, handler, delegazione). L'unico elemento ricostruito non direttamente osservato: che VS Code fosse chiuso al momento dell'errore di stamattina (coerente con meccanismo e sintomo, non screenshot-provato).

## Sources

- Ispezione diretta: /proc/1742/environ, /proc/17074/environ; /usr/share/applications/code.desktop e org.mozilla.firefox.desktop; xdg-mime query default; loginctl (Type=wayland); /run/user/1000/.mutter-Xwaylandauth.* e wayland-0; ps di Xwayland (-auth esplicito).
- Comportamento di delegazione VS Code/firefox a istanza viva: comportamento standard dei loro meccanismi remote/IPC.
