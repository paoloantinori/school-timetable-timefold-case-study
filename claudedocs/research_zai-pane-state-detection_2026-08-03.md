# Research Report: Distinguere lo stato di zai (working/idle/queued) dal tmux capture-pane

**Date**: 2026-08-03
**Depth**: exhaustive
**Confidence**: HIGH (fonte: CLAUDE.local.md punti 1/3/4/7 + osservazione diretta sessione + conferma maintainer; documentazione ufficiale Claude Code non pubblica il detail del TUI interno)

## Executive Summary

Lo stato di zai si legge dal **footer della status line**, non dalla riga `❯`. Il footer `(Ns · ↓Xk tokens · mode)` con timer attivo = WORKING (mid-turn), a prescindere da cosa mostra il prompt. `❯` vuoto + "Press up to edit queued messages" SENZA timer nel footer = IDLE con messaggio accodato. La crescita di Ctx tra due letture a 1-2 min di distanza è la prova certa che un messaggio è stato consumato (CLAUDE.local.md punto 1).

## Findings

### Segnale di WORKING (mid-turn)
- Footer: `(Ns · ↓Xk tokens · mode)` dove Ns = secondi elapsed sul task, ↓Xk = token stream attivo, mode = thinking/Writing/Meandering/Billowing.
- OPPURE titolo task + ellipsis nel body: `Closing Kempe spike…`.
- Il `❯` può essere vuoto in questo stato: NON è un segnale di idle.
- Azione: non toccare, chiudi il tick.

### Segnale di IDLE (fine turno)
- Footer SENZA timer: solo `Model: ... | Ctx: ... | ⎇ branch | (+X,-Y)` + `⏵⏵ auto mode on`.
- `❯` libero, nessun `(Ns · · ·)`.
- Azione: consegna il prossimo mandato.

### "Press up to edit queued messages"
- Significa: c'è un messaggio accodato per la consegna a fine turno (inviato mentre zai era mid-turn).
- NON significa idle né stuck.
- Se zai è WORKING (timer nel footer): il messaggio verrà consegnato a fine turno. Non re-inviare (CLAUDE.local.md punto 3).
- Se zai è IDLE con queued e sembra bloccato: usare `send-keys Enter` per forzare, o verificare.

### Ctx come prova di consumo
- CLAUDE.local.md punto 1: "l'unica prova che il messaggio è stato consumato è che `Ctx:` CRESCE (o compare lo spinner). Un prompt che 'sembra vuoto' NON prova il fallimento."
- Pratica: due letture a 1-2 min di distanza; se Ctx cresce, zai sta elaborando.

## L'errore commesso questa sessione (lezione)
Visto `❯` vuoto + "queued" interpretato come stuck/idle, inviato C-u e controlli ripetuti che hanno disturbato un turno sano ("Closing Kempe spike… (15s · thinking)"). Correzione: leggere SEMPRE il footer `(Ns · · ·)` PRIMA di decidere. Se c'è timer attivo, zai lavora.

## Confidence Assessment
- HIGH: la regola del footer `(Ns · · ·)` come spinner, confermata da osservazione diretta (maintainer ha indicato entrambi i formati: `(15s · thinking)` e `(48s · ↓1.6k tokens · thinking)`) + CLAUDE.local.md punto 1/3.
- HIGH: "queued" = accodato non stuck (CLAUDE.local.md punto 3 esplicito).
- MEDIUM: il comportamento esatto quando IDLE+queued e apparentemente bloccato (punto 3 dice "verrà consegnato a fine turno" ma se il turno non riparte serve gestione; non osservato direttamente).

## Sources
1. `/home/user/data/repo/personal/planner/CLAUDE.local.md` punti 1, 3, 4, 7 (verità sull'invio, mid-turn queue, Enter separato, slash command after clear).
2. Osservazione diretta sessione 2026-08-03: footer `(Ns · ↓Xk · mode)` identificato come spinner da maintainer.
3. crw search "Claude Code terminal interface queued messages working indicator" (risultati: docs.anthropic.com/code.claude.com terminal guide; nessuno documenta il TUI interno a questo dettaglio, ma coerente con il comportamento).
