# Research Report: Aggiornamenti dipendenze progetto (agosto 2026)

**Date**: 2026-08-18
**Depth**: quick (verifica versioni, non analisi approfondita)
**Confidence**: HIGH (fonti ufficiali GitHub/quarkus.io/timefold.ai concordanti)

## Executive Summary

Entrambe le dipendenze principali hanno release piu' recenti della nostra: Quarkus e' a **3.38.1**
(noi 3.31.4, LTS consigliata per produzione = 3.33.x) e Timefold Solver e' a **2.5.0** (noi 2.3.0).
Nessuna delle due e' urgente; entrambe sono candidati upgrade a basso rischio con nota tecnica.

## Findings

### Quarkus: 3.31.4 -> 3.33 LTS (consigliato) o 3.38.1 (latest)

- Quarkus consiglia per produzione **la LTS 3.33** (quarkus.io/releases). La 3.33.3.1 e' una
  emergency release che chiude la CVE-2026-16308 (DoS REST via multipart) [HIGH: quarkus.io]
- La 3.38.1 e' l'ultima release; la 3.33 LTS e' supportata fino a Mar 2027 [HIGH: eosl.date,
  concorda con eol.wiki che cita 3.36.3 come ultima non-LTS]
- Il nostro gap 3.31 -> 3.33 e' un salto di manutenzione: nessuna breaking change segnalata
  nelle note di rilascio tra 3.31 e 3.33 (bugfix + CVE). [MEDIUM: inferito dalle note, non
  testato sul nostro build]

### Timefold Solver: 2.3.0 -> 2.5.0

- La 2.5.0 e' l'ultima (GitHub releases, marcata Latest). Cambiamenti rilevanti per noi:
  - constraint-streams: switch a TreeMap solo quando serve (#2553) = possibile guadagno
    performance sul nostro constraint set grande
  - race condition in SolverWorker (#2529) = potenzialmente RILEVANTE per il nostro MT-LS
    custom che usa SolverJob/Solver ad hoc
  - profilo dei constraint meno costoso (#2544)
  - nessuna breaking-change API segnalata nella 2.5.0 [MEDIUM: note di rilascio esaminate,
    non verificato sul nostro codice reflection-pinned!]
- ATTENZIONE specifica del progetto: il wiring MT-LS (TASK-480) e' **pinnato via reflection
  agli internals di Timefold 2.3.0** (documentato nel javadoc come fragile). Qualunque upgrade
  Timefold va preceduto dalla verifica che quei simboli interni esistano ancora.

### Frontend (informativo, nessuna azione)

- React 19 e' stabile (noi su 18.3.1): upgrade NON urgente, la 18 e' ancora supportata
- Vite 5 -> 7, Tailwind 3 -> 4: major con breaking, da pianificare solo se serve una feature

## Raccomandazione (per maintainer, nessuna azione presa)

1. Quarkus 3.31.4 -> 3.33.3.1 (LTS + CVE fix): upgrade di manutenzione, basso rischio,
   un rebuild + suite. Da fare in una finestra morta del laptop.
2. Timefold 2.3.0 -> 2.5.0: potenziale interesse (race SolverWorker, performance streams),
   MA richiede prima la verifica dei simboli reflection del MT-LS. Task dedicato con gate
   "MT-LS wiring sopravvive" prima di qualunque adozione.

## Sources

1. https://github.com/quarkusio/quarkus/releases - release list
2. https://quarkus.io/releases/ - "Recommended for production: LTS 3.33"
3. https://pt.quarkus.io/blog/quarkus-3-33-3-1-released/ - CVE-2026-16308
4. https://eosl.date/eol/product/quarkus-framework/ - support dates (3.38.1 latest, 3.33 LTS)
5. https://github.com/TimefoldAI/timefold-solver/releases/tag/v2.5.0 - note 2.5.0
6. https://timefold.ai/blog/introducing-timefold-solver-2-0 - API 2.0 (background)
7. https://dev.to/gladiatorsbattle/react-19-stable-release... - React 19 stable (background)
