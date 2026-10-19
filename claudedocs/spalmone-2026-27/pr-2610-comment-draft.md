# Draft comment for TimefoldAI/timefold-solver#2610

Posted as a PR comment (not inline: it is design-level feedback, exactly the
"do we want these moves" discussion the description asks for). Sender: maintainer,
after review. Do NOT post without his explicit go.

Linked issue: #2254 "Feat: Neighborhoods - Pillar move support" (OPEN, minimal,
the feature tracker). Two-step plan: the full comment below goes on the PR while
the move-shape discussion is live; afterwards a short echo on #2254 keeps the
custom-key request alive past this PR's lifetime:

  "We have a concrete use case for pillars keyed on a custom domain key
  (teacher-class pairs in school timetabling); details in the PR #2610
  comment. +1 for exposing the grouping key function."

---

Thank you for this. We run Timefold (2.3.0 today) on a real school timetabling
domain: ~12,500 planning entities (lessons) with two basic planning variables
(timeslot, room), a five-phase solver with custom construction and deterministic
repair phases, and a multithreaded local search built on the move-stream
internals. So the coarse-grained moves here are directly interesting to us, and
we read the pillar/subpillar providers first as recommended.

**One design gap from our side: pillars are keyed on a shared planning value,
and our strongest use case needs a custom grouping key.**

The pair-form use case: in our domain, a "pair" (teacher, class) tends to own a
stable set of (weekday, slot) cells; the school's own hand-made timetable holds
about 73% of a pair's lessons on those cells. We can build that form in
construction, but plain single-entity local search erodes it down to ~40% by the
end of the solve, because a change/swap move breaks the shape one lesson at a
time. The natural cure is a coarse move keyed on the pair: move the pair's
lessons together, or at least select them as a unit. `PillarChangeMoveProvider`
almost gives us this, but it groups entities by the value they currently share;
our entities almost never share a timeslot, so value-keyed pillars are near-empty
sets for us. The building block is already there: `build()` does a
`groupBy((solutionView, entity) -> value)`. Exposing that key function (or a
sibling provider taking a user key function over the entity) would make pillars
usable for every domain where the natural cohesion is a domain key (teacher,
class, vehicle, crew) rather than a shared value. We would use it immediately;
today we are heading toward hand-rolled moves for exactly this.

Related small observation on the same providers: the subpillar sampler is where
a domain key matters too. A sampled subset of a value-keyed pillar is less
interesting to us than a sampled subset of a domain-keyed group.

**Second, smaller point: please keep runtime-adaptive weighting of providers in
scope for this API.**

We measured that a fixed dispatch across move streams wastes a lot of wall
clock on streams that stopped paying off. An adaptive per-stream weight (we
thinned dispatch by reward/average, clamped 0.5x..2.5x, recompute every few
thousand evaluations) cut the slowest runs to roughly a quarter of the time
with no quality loss. In the old XML config there was no clean place for this;
we implemented it around the move streams. If the neighborhoods API is the
future of move selection, a documented hook to reweight providers (or even just
to observe per-provider acceptance) would let that technique live in user code
instead of forks.

Happy to provide more detail on either point if useful. Thanks again; the
direction is very welcome.
