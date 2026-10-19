# Byteman for coding agents: finding, simulating and pre-validating race conditions

A context document for coding agents that need to reproduce concurrency bugs
(race conditions) on the JVM deterministically. distilled from real work on
TASK-543 (planner project, 2026-08-23): the Hibernate dirty-tracker race in our
multi-threaded local search was "nearly impossible to catch live", and with
Byteman it became a test that fails/passes deterministically in 5 seconds.

## Why this tool, and why not the alternatives

- **Lincheck** (JetBrains): model checking of isolated concurrent data
  structures. Right for verifying linearizability of a structure, wrong for
  injecting scheduling into an application's own flow.
- **JCStress** (OpenJDK): memory-model micro-tests. Too low level.
- **vmlens** and similar: they detect races that happen to be observed, they do
  not force them.
- **Byteman**: injects Java code into ANY method (yours, a library's, the
  JDK's) at runtime, without recompiling, and among the possible actions is
  blocking and releasing threads. It is the right tool when the question is:
  "what happens if thread B reads EXACTLY while thread A is stopped at this
  point?".

## Verified facts (not memory): version and compatibility

- Current release: **4.0.27** (downloads.jboss.org and Maven Central,
  coordinates `org.jboss.byteman:byteman`). Living project (GitHub repo
  bytemanproject/byteman, by Andrew Dinn / Red Hat).
- **Works on JDK 21** (verified empirically on OpenJDK 21.0.11 on
  2026-08-23): premain agent, `AT ENTRY` injection into application methods,
  `waitFor` thread blocking, `signalWake` release. Jigsaw module support
  (AccessManager) active.
- With Quarkus: no problem, it is a plain `-javaagent`. Use premain loading
  (supported forever), NOT dynamic attach to a running JVM (deprecated as a
  default since JDK 21: requires -XX:+EnableDynamicAgentLoading).
- Coexists with byte-buddy and other transformers (our project also runs
  `-Dnet.bytebuddy.experimental=true`).

## The mental model: ECA rules (Event Condition Action)

A rule says: when execution reaches a point (event), if a Java condition is
true (condition), execute these Java expressions (action). The point can be
`AT ENTRY`, `AT EXIT`, `AT LINE n`, `AT INVOKE method`, and more. Actions can
read and write instance fields (`$0`), parameters (`$1`, `$2`...), call
methods, sleep, block the thread.

Example `.btm` file (the real one from our harness):

```
RULE freeze writer inside the publish-then-fill window
CLASS com.example.RaceInjectionHarness$StandInDirtyCell
METHOD beginFill
AT ENTRY
BIND key = "window-" + System.identityHashCode($0)
IF $0.raceMode == true
DO debug("BYTEMAN: writer frozen inside window"),
   $0.writerFrozen = true,
   waitFor(key, 5000)
ENDRULE
```

Note the syntax: `BIND` introduces variables, `IF` is a boolean, `DO` is a
comma-separated list of expressions.

## The thread-coordination builtins (names VERIFIED via javap)

These really exist on the 4.0.27 Helper (verify them yourself with
`javap -classpath byteman.jar org.jboss.byteman.rule.helper.Helper`):

- `waitFor(Object key)` and `waitFor(Object key, long timeoutMillis)`: blocks
  the current thread until someone signals the key (or the timeout expires).
- `signalWake(Object key)` / `signalWake(Object key, boolean)` /
  `signalKill` / `signalThrow`: wakes whoever waits on the key.
- `flag(String name)` reads a process-global flag; `flag(name) = true` sets it
  (inside actions).
- `debug(String)`: rule logging.

`signal(String)` and `setFlag` do NOT exist: if you get a name wrong, the rule
fails typechecking AT RUNTIME (stack trace on stderr) and is then silently
disabled. During development always load with
`-Dorg.jboss.byteman.verbose=true` to see parsing and typechecking.

## The traps we paid for (learn from us)

1. **BYTEMAN-38, early signals are lost**: if a thread calls
   `signalWake(key)` BEFORE anyone is in `waitFor(key)`, the signal is lost
   and the later wait blocks forever. Do not orchestrate
   signal-first-wait-later. Robust solution: `waitFor(key, TIMEOUT)` with a
   timeout, and treat the timeout as part of the test (see the pattern below).
2. **Rule errors do not fail the build**: a mistyped rule is a disabled rule,
   so the test "passes" because the injection never happened. Always assert an
   OBSERVABLE EFFECT of the injection in the test (see `writerFrozen` in the
   pattern), so a rule that never fired fails the test.
3. **debug() does not write to the test's stdout**: it writes to the agent
   log. To get traces in surefire reports use `System.out.println` in the
   action.
4. **Nested classes**: the rule CLASS name is `Outer$Inner`.

## The pattern we recommend: freeze + volatile flag + spin

To make a race DETERMINISTIC (our working harness):

```
code under test:
  publish()                 // publishes partial state
  beginFill()               // <- the rule freezes HERE (only if raceMode)
  fill(value)               // completes the state

test (writer thread):  raceMode = true; publish(); beginFill(); fill()
test (reader thread):  spin on cell.writerFrozen (with a bail-out!); readAttribute()
Byteman rule (beginFill AT ENTRY, IF raceMode):
  $0.writerFrozen = true; waitFor(key, 5000)
```

Why it works:

- the reader does not start blind: it spins on `writerFrozen`, a volatile set
  by the rule BEFORE the block. When it sees true, the writer is GUARANTEED to
  be frozen inside the window. The read lands inside the race by construction,
  not by scheduler luck.
- `writerFrozen` doubles as the health assertion: if the rule never fired, the
  spin's bail-out fails the test with a clear diagnosis ("rule not armed")
  instead of a generic join-timeout or, worse, a false green.
- the waitFor timeout (5s) keeps the test finite without depending on a
  release rule (see trap 1).

**The fix pre-validation twin test**: same class, same rule, but
`raceMode = false` (or production code that serializes access): the rule
CANNOT open the window and the test asserts the partial state is never
observable. Today the damage is reproducible, tomorrow the fix is verified by
the same harness.

## How to wire it into Maven tests without touching the normal suite

The clean way (no configuration duplication):

1. An empty-by-default property in the pom:
   `<byteman.agent.argLine></byteman.agent.argLine>`
2. The BASE surefire argLine references the property:
   `<argLine>@{argLine} -Xmx16g ${byteman.agent.argLine}</argLine>`
   (`@{argLine}` is surefire's late interpolation, `${...}` is Maven's
   property interpolation: they coexist correctly).
3. A `-Pbyteman` profile that: adds the `byteman:4.0.27` test dependency, runs
   `maven-dependency-plugin:properties` (populates
   `${org.jboss.byteman:byteman:jar}`), sets the property to
   `-javaagent:${org.jboss.byteman:byteman:jar}=script:src/test/resources/.../race-force.btm`
   plus a `byteman.agent=true` system property.
4. The test is annotated `@EnabledIfSystemProperty(named="byteman.agent",
   matches="true")`: without the profile it is skipped, the suite stays
   identical.

## Operational workflow for a race from scratch

1. **Find the window**: in the code (or the library's source, read at the
   right tag) identify the publishes-partial-state then completes-state
   sequence, and who can read in between.
2. **Write a stand-in or target the real class**: to validate the pattern a
   stand-in with the same shape is enough; for probative value point the
   rules at the real classes (our step 2).
3. **Write the freeze rule** with the pattern above and a test asserting the
   damage. It must fail-before-the-fix DETERMINISTICALLY.
4. **Apply the fix** (lock, copy, single-writer, whatever fits).
5. **The same test must pass**: the rule keeps trying the interleaving, the
   fix must make it harmless. This is the value over detection tools: you do
   not have to wait for the race to happen again.
6. Keep the test in the suite only if the cost is acceptable: our freeze costs
   5s per test; over hundreds of tests consider a dedicated profile.

## Honest limitations

- Rules are untyped strings: a rename refactor breaks the injection silently
  (again: always assert an observable effect in the test).
- Freezing changes timing: some timing-dependent races disappear when you
  slow the culprit down (heisenbug). Byteman beats printf because it freezes
  an exact point, but if the race needs two simultaneous windows you need two
  coordinated rules.
- It is not a memory-model checker: it does not prove the absence of races, it
  demonstrates the effects of the interleavings you can name.

## References

- Homepage and download: https://byteman.jboss.org/ (programmer's guide PDF
  included)
- Repo: https://github.com/bytemanproject/byteman (the tests under
  agent/src/test are the best documentation of wait/signal patterns)
- Our working harness: `src/test/java/com/school/planner/solver/raceinjection/`
  + `src/test/resources/byteman/race-force.btm` in the planner repo, invoked
  as `./scripts/core/test.sh -mva "-Pbyteman" -c BytemanRaceInjectionTest`.
