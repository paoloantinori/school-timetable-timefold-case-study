# DRAFT reply a Geoffrey (Timefold discussion #2615) - NON INVIARE

**AVVISO per maintainer**: il testo esatto della domanda di Geoffrey NON e' nel repo (vive nella
discussione upstream #2615 e/o nella tua inbox): questa bozza e' costruita sul contesto che
abbiamo (la discussione sulle custom phase e il multithreaded LS, il censimento TASK-547,
le nostre misure TASK-543). I segnaposto [?] vanno riempiti con la sua domanda vera prima
dell'invio; se la domanda fosse un'altra, il materiale sotto e' comunque il nostro contributo
censito. Lingua inglese per l'upstream.

---

Subject: Re: [?] (custom phases / multithreaded incremental solving extensibility)

Hi Geoffrey,

thanks for [? - his point]. We have been running a school timetabling solver (Quarkus +
Timefold 2.3.0, ~12.5k planning entities, 5-phase custom pipeline) in production-shaped
campaigns for a few weeks and we have two data points you might find useful for [? - the
discussion]:

1. Custom phases are indeed the least-documented, most-coupled corner of the API, as the
   discussion already noted. An independent census we ran over ~232 community discussions
   and two verified community projects (24/08) found exactly one independent port
   (TimefoldSharp) and it stops at an empty CustomPhaseConfig shell (NotImplementedException).
   On our side we wired a custom local-search decider (multithreaded, community edition)
   via reflection into SolverFactory internals: it works, it is pinned to 2.3.0, and we
   treat it as fragile-by-design with guard tests. A sanctioned extension point for custom
   LS phases (even an unstable SPI) would remove the whole class of reflection wiring.

2. On multithreaded incremental solving specifically, we measured the community decider's
   shared-queue dispatch producing pathological nondeterminism between two runs from a
   byte-identical construction: 12.542 of 12.821 placements differing in the FIRST local
   search phase. We rebuilt the dispatch as single-writer per-thread channels (one private
   queue per move thread, never cleared, step-bounded barrier): the race component halved
   (6.041 differing placements on the same protocol) with a wall-time win (LS ~62min vs
   ~70min, 8 threads). Two concrete traps we hit and fixed, in case they belong in docs:
   a poison-pill TOCTOU between the solver thread and the barrier drain, and a
   step-chasing desync caused by dropping unconsumed apply results on queue clear.

Happy to share the census notes or the dispatch protocol details if useful. Thanks for
Timefold, it carries our whole campaign.

maintainer

---

## Note per la revisione (da cancellare prima dell'invio)
- Il punto 2 riassume TASK-543 F2 (ratificato 24/08): i numeri sono nel registro
  iterations-542.md alla voce RATIFICA TASK-543.
- Il punto 1 riassume TASK-547 (task547-community-projects-analysis.md) e il javadoc del
  decider (wiring via reflection pinned a 2.3.0).
- Tono volutamente contributivo, nessuna richiesta di supporto: e' una reply da citizen,
  non un ticket.
- Se Geoffrey avesse chiesto altro (es. del fork enterprise, o della discussione
  research_timefold_enterprise_fork), il materiale pertinente e' in
  claudedocs/research_timefold_enterprise_fork_2026-08-12.md.
