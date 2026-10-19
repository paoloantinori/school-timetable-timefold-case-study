# Research Report: Orchestrazione tmux di Claude Code su laptop remoto

**Date**: 2026-08-05
**Depth**: exhaustive
**Confidence**: HIGH (fonti: Claude Code docs ufficiali, GitHub repos, community skills, blog posts tecnici)

## Executive Summary

Il pattern tmux + paste-buffer che già usi è **corretto ed è la best practice documentata** dalla community. I problemi ricorrenti (Enter perso, Escape che interrompe, idle vs working ambiguo) sono noti e hanno soluzioni specifiche. Esiste un'alternativa non-interattiva (`claude -p --output-format stream-json`) che elimina i problemi TUI ma perde session continuity e mid-flight interaction, rendendola inadatta al tuo caso d'uso (orchestrazione notturna con follow-up). La soluzione ottimale è raffinare il pattern tmux esistente con gli accorgimenti identificati, non abbandonarlo.

## Findings

### 1. Il pattern paste-buffer è confermato best practice (HIGH confidence)

Tutte le fonti concordano: `load-buffer` + `paste-buffer -p` è l'unico modo affidabile per iniettare prompt multilinea in Claude Code via tmux. `send-keys` rompe su newline, quote, $, e metacaratteri shell. Fonti: [primeline-ai/claude-tmux-orchestration](https://github.com/primeline-ai/claude-tmux-orchestration), [vellum headless skill](https://www.vellum.ai/skills/headless-claude-code), CLAUDE.local.md.

```bash
# ✅ CORRETTO (già usi questo)
echo "prompt" > /tmp/prompt.txt
tmux load-buffer /tmp/prompt.txt
tmux paste-buffer -p -t session
tmux send-keys -t session Enter
```

### 2. Il problema "Enter perso / Press up to edit queued messages" (HIGH confidence)

**Causa identificata**: Claude Code usa un input buffer con stato. Quando il pane mostra "Press up to edit queued messages", il messaggio è nel buffer ma non è stato submitted. Il `Enter` via `send-keys` a volte viene assorbito dal TUI menu di completamento (l'Enter conferma la selezione del menu, non fa submit del prompt).

**Soluzioni**:
- **Verifica il marker `[Pasted text #N]` PRIMA di Enter** (già nel CLAUDE.local.md punto 4/7). Se il marker non c'è, ripaste.
- **NON usare Escape per forzare il submit**: Escape interrompe il turno corrente (causa confermata dal mio errore al tick #199). Questo è il rischio più grave.
- **Pattern robusto da primeline**: dopo `paste-buffer -p`, attendi 2s, verifica `capture-pane | grep "Pasted text"`, THEN `send-keys Enter`. Se il marker non appare dopo 2 retry, il pane potrebbe essere in uno stato TUI che richiede `Ctrl-j` ( newline submit) invece di `Enter` (M polyline).
- **Alternative submit**: alcuni reportano che `send-keys C-m` (carriage return) è più affidabile di `send-keys Enter` in alcuni terminali, perché `Enter` può essere interpretato come keypad-enter da alcuni emulatori.

### 3. Idle vs Working vs Queued: la regex del footer (HIGH confidence)

Il footer della status line di Claude Code ha un formato predicibile:
- **WORKING**: `<task description>... (<Ns . ↓Xk tokens . mode>)` o `<spinner emoji> <task>... (Ns)`
- **IDLE**: footer senza timer, solo `Model: ... | Ctx: ... | (+X,-Y)`
- **QUEUED**: `Press up to edit queued messages` nel prompt area

**La regola operativa** (già salvata in memoria `laptop-monitor-via-api-sh-not-just-pane`): leggere il footer `(Ns · mode)` PRIMA di decidere. Se c'è un timer attivo, zai sta lavorando, indipendentemente dal prompt. Ctx che cresce tra due letture a 1-2 min di distanza è la prova certa di elaborazione.

### 4. /clear: la disciplina di pre-verifica (HIGH confidence)

**Il rischio**: `/clear` distrugge il contesto inclusi mandate ricevuti ma non ancora elaborati. La regola già salvata in memoria (`autonomous-laptop-overnight-ops`, sezione CRITICAL /clear discipline) è corretta: verificare SEMPRE (a) nessuna azione in corso (footer senza timer) E (b) ogni stato intermedio è persistito (commit+push) PRIMA di `/clear`.

**Accorgimento aggiuntivo dalla ricerca**: primeline usa un heartbeat loop che non fa MAI /clear automatico. Invece monitora `CONTEXT_LOW_CONTINUE` (quando Ctx è alto) e inserisce il messaggio "consider summarizing your work" come prompt, lasciando che zai decida quando fare /clear autonomamente. Questo è più sicuro del /clear programmatico.

### 5. Alternative: claude -p (print mode) per task one-shot (MEDIUM confidence)

`claude -p "prompt" --output-format stream-json --verbose` è la modalità non-interattiva ufficiale. Risolve tutti i problemi TUI (nessun Enter perso, nessun Escape, nessun idle vs working ambiguo) MA:
- **Bufferizza tutto l'output fino al completion** (no streaming visibile nel pane)
- **Nessuna session continuity**: ogni invocazione è fresh, no memoria
- **Nessuna mid-flight interaction**: non puoi aggiungere contesto o correggere rotta
- **`--output-format stream-json`** con `--verbose` streama JSON newline-delimited (tool calls, messaggi, progress), parseable con jq. Questo è il pattern per CI/automazione one-shot.

**Quando usare -p invece di tmux**: per task one-shot ben definiti (es. "run test.sh and report exit code"), non per orchestrazione notturna con follow-up. Per il tuo caso d'uso (pilota zai con mandate che possono evolvere, follow-up, /clear gestiti), tmux interactive è la scelta giusta.

### 6. Claude Code Agent Teams / Dynamic Workflows (MEDIUM confidence, emerging)

Claude Code v2.1+ ha [Agent Teams](https://code.claude.com/docs/en/agent-teams) (dietro `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`) e [Dynamic Workflows](https://code.claude.com/docs/en/workflows) (orchestrazione subagent fino a 16 concurrent, 1000 per run). Questi sono la prima parte nativa per orchestrazione multi-agent, ma:
- Agent Teams è ancora experimental
- Workflows richiede la Workflow tool (disponibile in questa sessione)
- Entrambi sono locali (non cross-SSH)

Per cross-SSH orchestration (Fedora che pilota laptop), tmux resta l'unico pattern affidabile.

### 7. Background sessions (HIGH confidence, nuovo)

Claude Code v2.1+ ha background sessions native:
- `claude agents --json`: lista sessioni attive come JSON (scriptable!)
- `claude attach <id>`: attacca a una sessione background
- `claude logs <id>`: stampa output recente di una sessione background
- `claude respawn <id>`: restarta una sessione con conversazione intatta

Questo è un pattern alternativo a tmux per il monitoraggio: invece di `capture-pane`, usare `claude agents --json` per leggere stato sessione come JSON strutturato. Ma richiede che la sessione sia lanciata come background session nativa, non in tmux.

## Confidence Assessment

- **HIGH**: paste-buffer è best practice; Enter perso è causato da TUI menu absorption; Escape interrompe turni; /clear richiede pre-verifica; -p mode è per one-shot non per orchestrazione continua.
- **MEDIUM**: stream-json come alternativa per monitoring strutturato; background sessions native come alternativa a tmux; C-m vs Enter affidabilità.
- **LOW**: Agent Teams/Workflows come futuro pattern nativo (ancora experimental).

## Raccomandazioni operative (per la tua configurazione Fedora→laptop)

1. **Mantieni tmux + paste-buffer**: è la best practice, non cambiarlo.
2. **Aggiungi `C-m` come fallback submit**: se `Enter` non funna dopo 2 retry con marker verificato, prova `send-keys C-m` invece di `send-keys Enter`.
3. **MAI usare Escape per forzare submit**: interrompe il turno. Se il prompt è queued e non elaborato, attendi (zai lo elabora a fine turno) o usa un prompt breve di nudge invece di Escape.
4. **Verifica `claude agents --json` sul laptop**: se disponibile (v2.1+), potrebbe dare stato sessione più strutturato di capture-pane per il monitoring.
5. **Per task one-shot futuri**: considera `claude -p --output-format stream-json` per task ben definiti che non richiedono follow-up (es. "run test.sh, report exit code"), eliminando il overhead TUI.
6. **Non adottare Agent Teams/Workflows ancora**: experimental, e comunque non cross-SSH.

## Sources

1. [Claude Code CLI Reference](https://docs.claude.com/en/docs/claude-code/cli-reference) - flag ufficiali, -p mode, --output-format
2. [primeline-ai/claude-tmux-orchestration](https://github.com/primeline-ai/claude-tmux-orchestration) - pattern tmux completo, heartbeat loop, worker states
3. [Vellum Headless Claude Code Skill](https://www.vellum.ai/skills/headless-claude-code) - paste-buffer best practice, onboarding pre-seed, perché non -p
4. [Claude Code SDK Headless](https://docs.anthropic.com/en/docs/claude-code/sdk/sdk-headless) - stream-json format, programmatic usage
5. [Anthropic Claude Code Overview](https://docs.anthropic.com/en/docs/claude-code/overview) - interactive mode, slash commands
6. [primeline.cc/blog/tmux-orchestration](https://primeline.cc/blog/tmux-orchestration) - blog post con context aggiornato (subagent hanno assorbito molto del bisogno tmux)
7. [Claude Code Terminal Config](https://code.claude.com/docs/en/terminal-config) - tmux display issues, Vim keys
8. [JacobFV gist: Using Claude Code Programmatically](https://gist.github.com/JacobFV/2c4a75bc6a835d2c1f6c863cfcbdfa5a) - --print --output-format=json, programmatic access patterns
