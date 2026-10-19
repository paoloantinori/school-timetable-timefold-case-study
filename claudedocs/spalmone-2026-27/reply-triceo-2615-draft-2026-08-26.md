# DRAFT reply a triceo (Timefold discussion #2615) - NON INVIARE

Thread: "Timefold Discussions proposal - Phase-boundary checkpoints for long solves"
(https://github.com/TimefoldAI/timefold-solver/discussions/2615, autore della proposal: maintainer,
risposta di triceo 24/08 14:02, nessuna attivita' successiva verificata al 26/08 sera).

Risponde punto per punto: eventi instead of solve() return change; solution-only + cold start;
e AMMETTE il code smell dello stato interno (direttiva maintainer 26/08: "non vergognamoci").
Lingua inglese per l'upstream.

---

Hi @triceo, thanks for taking the time to answer in detail.

On your points, mostly agreement and one concession:

1. Events instead of changing solve(): agreed, and it is actually what we would consume. We
   currently emit our own phase-boundary markers from a no-op custom phase plus an after-phase
   listener, and we log phase id, score, step count and time manually at each boundary. A
   sanctioned event with that payload would replace the most fragile piece of our wiring.

2. Solution-only snapshots with cold restart from a given phase: we can confirm empirically
   that this design is sufficient, because it is what we already run. School timetabling,
   ~12.5k lessons, Quarkus + Timefold 2.3.0, a 5-phase custom pipeline; at every phase
   boundary we persist the solution plus score and phase id, and resume means re-entering the
   pipeline at that phase with the solution as the initial one. Late-phase A/B experiments on
   a resumed snapshot cost minutes instead of the ~90 minutes of a full cycle, and a score
   re-computation at load time (assert the score matches the checkpointed score before
   continuing) catches any serialization drift.

3. The concession: you are right that "solver-internal state" in my original explanation was
   me reaching for a shortcut, and the need for it is a code smell, not a requirement. To your
   direct question, where it was actually stored: static fields on the constraint provider and
   on a service class, populated before the solve. That is exactly the misuse you smell, and
   our own history proves your point: a few things our late phases read used to live in those
   static fields (template references, per-day targets), not in the solution, so early resume
   experiments silently diverged until we tracked it down; the medium score of a resumed run
   was off by a chunk until those fields were re-published from the checkpoint. The rule we
   adopted since is exactly your constraint turned into a discipline: anything a phase reads
   must be derivable from the solution. Shadow variables already follow that rule; user-level
   statics are the user's responsibility, and that is fine.

4. On @PlanningId: you are right there too, and I withdraw the question. I conflated
   serialization-time identity with clone-time identity: the ids ride on the entities, so a
   restored solution loads like any other solution and needs nothing special. For what it is
   worth, we round-trip solutions through JSON export/import across a multithreaded local
   search with no restore-specific identity handling, which is the empirical version of the
   same point.

If it helps the design discussion, the items that made phase-boundary checkpoints first-class
for us: the event payload above, a documented "resume from phase X" entry point, and a loud
score-assert on load. Happy to share the checkpoint format and the failure-mode list we
accumulated (statics, promoted pins, listener re-registration) if useful.

Thanks for Timefold, it carries our whole campaign.

---

## Note per la revisione (da cancellare prima dell'invio)

- FATTO VERIFICATO 26/08 via gh: la #2615 e' la proposal di maintainer (24/08), la risposta e' di
  triceo (24/08 14:02), NESSUNA attivita' successiva nel thread; l'unica discussione Timefold
  mossa oggi e' la #2584 (MirkoUgoliniDev/TomCools, niente Geoffrey). Il "Geoffrey" della
  conversazione interna non corrisponde a nessun attivita' recente trovata: se esiste un suo
  messaggio, vive fuori da GitHub (Zulip/mail) e va verificato prima di qualunque invio.
- Punto 2, numeri: ciclo completo ~1.5h (TASK-543: ciclo 1h33), banchi resume minuti
  (TASK-560/562: probe, Kempe banco 420s con timeout, sweep donor-order via resume). Il
  rapporto "1/50" e' arrotondato per difetto: dire "minutes instead of ~90 minutes" evita la
  frazione precisa.
- Punto 3, la storia delle statics: TASK-553/551 (caveat statics del resume, spiegava ~2/3
  del delta medium tra resume e consegna); la regola post-cura e' "tutto derivabile dalla
  solution" (stessa regola di M32: mappa pubblicata PRE-solve, mai a meta' solve).
- Punto 1, il wiring attuale: PhaseTransitionMarker (custom phase no-op) + listener; i
  phase-transition dumps diagnostici (-Dplanner.phase-transition-dump.enabled).
- Punto 4 (AGGIUNTO 26/08 sera su richiesta maintainer, dopo lettura integrale del commento):
  la domanda PlanningId era NELLA proposal originale, triceo l'ha smontata ("I do not see
  the relation"), la reply la ritira esplicitamente con la prova empirica del round-trip
  JSON del nostro MT-LS (export/import protocol, nessun handling identita' speciale).
- Il punto 3 ora risponde anche alla domanda diretta di triceo ("where is that actually
  stored?"): static fields sul provider e su una service class, popolate pre-solve. Il suo
  "I do smell some misuse of the APIs" merita la citazione implicita: e' quello che
  ammettiamo.
- TONO: concession senza pieta' ma con proof: e' la forma piu' forte, mostra che abbiamo gia
  pagato il prezzo dello smell. Nessuna richiesta di supporto.
- DECISIONE maintainer 26/08 (playground): saluto con "Hi @triceo" (tag GitHub); concessione
  tenuta FORTE (nessuna variante morbida); nessun rapporto 1/50 esplicito; failure-list
  tenuta. Bozza integrale con quest'unica variante applicata.
- NON linkare il repo (dati reali della scuola nel seed).
- Nessun em-dash. Invio: maintainer.
- La vecchia bozza "reply-geoffrey" (censimento custom phases + MT nondeterminismo) NON e'
  morta: e' candidata come commento sulla #2584 (progetto di scheduling open-source), dove il
  tema e' esattamente quello. Decidera' maintainer.
