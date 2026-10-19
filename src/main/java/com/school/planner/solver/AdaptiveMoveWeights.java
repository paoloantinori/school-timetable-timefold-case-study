package com.school.planner.solver;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLongArray;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.school.planner.domain.Room;
import com.school.planner.domain.Timeslot;

import ai.timefold.solver.core.preview.api.move.Move;

/**
 * TASK-500 (Enterprise reimplementation #3, design doc {@code
 * claudedocs/design/adaptive-move-weights.md}): Lectio-style ADAPTIVE WEIGHTS for the
 * union move selector's streams. The solver learns, DURING the run, which move streams
 * produce accepted moves and re-weighs their selection probability every segment.
 *
 * <p><b>Form (decided in the design doc).</b> Segment accounting: per-stream counters
 * (evals, accepts); every {@code segmentEvals} GLOBAL evaluations, each stream's weight
 * becomes {@code base * (EPSILON + (1 - EPSILON) * reward / bestReward)}, clamped to
 * {@code [LO * base, HI * base]}. The ratified static weights (5/3/2/1 timeslot, 2/1
 * room) stay as the BASE the adaptation multiplies: with the flag OFF nothing changes,
 * and even armed, a stream never falls below {@code EPSILON} of its base (the
 * anti-feedback-loop floor) nor above {@code HI}x (no monopolies).
 *
 * <p><b>Single-writer thread story (risk 3 of the brief).</b> Counters and weights are
 * touched ONLY by the decider's step thread: the 8 move threads evaluate moves and
 * queue results, but the accept/reject verdict and this bookkeeping run in the single
 * consumer loop ({@code MultiThreadedLocalSearchDecider}'s result loop), and the
 * probability factories are consulted by the selector machinery on that same solver
 * thread. The arrays are volatile/atomic only as defensive publication, not because
 * there is contention.
 *
 * <p><b>Statistics source (declared dependency).</b> The counters are fed from the
 * MT-LS decider only: with the multi-threaded LS off (single-threaded Timefold path)
 * no statistics flow and the weights stay at base - gracefully degraded, declared in
 * the banner. The registry is reset when the staged wiring arms the lever, so a
 * multi-start's second attempt starts from the ratified bases again.
 */
public final class AdaptiveMoveWeights {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdaptiveMoveWeights.class);

    /**
     * The union's streams, split by phase variable (timeslot vs room). ADAPTIVE marks the
     * streams the wiring can re-weigh: the pillar selectors do not support PROBABILISTIC
     * selection (Timefold: sub-pillar enumeration scales exponentially), so pillar streams
     * keep their RATIFIED fixed weight and stay outside the learning (counted for
     * observability, never re-weighed, never in the average).
     */
    public enum Stream {
        TS_CHANGE(5.0, true), TS_SWAP(3.0, true),
        TS_PILLAR_CHANGE(2.0, false), TS_PILLAR_SWAP(1.0, false),
        ROOM_CHANGE(2.0, true), ROOM_SWAP(1.0, true),
        OTHER(0.0, false);

        public final double base;
        public final boolean adaptive;

        Stream(double base, boolean adaptive) {
            this.base = base;
            this.adaptive = adaptive;
        }
    }

    /** Knobs, read once per arm (clamped): the three the design doc commits to. */
    public record Config(long segmentEvals, long minEvals, double epsilon, double lo, double hi) {
        public static Config fromProperties() {
            return new Config(
                    clamp(Long.parseLong(System.getProperty("planner.ls.adaptive-weights.segment-evals", "4000")), 100, 1_000_000),
                    clamp(Long.parseLong(System.getProperty("planner.ls.adaptive-weights.min-evals", "50")), 1, 1_000_000),
                    clampDouble(Double.parseDouble(System.getProperty("planner.ls.adaptive-weights.epsilon", "0.25")), 0.0, 1.0),
                    clampDouble(Double.parseDouble(System.getProperty("planner.ls.adaptive-weights.lo", "0.5")), 0.0, 10.0),
                    clampDouble(Double.parseDouble(System.getProperty("planner.ls.adaptive-weights.hi", "2.5")), 0.1, 10.0));
        }

        private static long clamp(long v, long min, long max) {
            return Math.max(min, Math.min(max, v));
        }

        private static double clampDouble(double v, double min, double max) {
            return Math.max(min, Math.min(max, v));
        }
    }

    private static final int N = Stream.values().length;
    private static volatile long armed = 0L;   // flag snapshot: 0 = inert (weights stay at base)
    private static volatile Config config = Config.fromProperties();
    private static final AtomicLongArray evals = new AtomicLongArray(N);
    private static final AtomicLongArray accepts = new AtomicLongArray(N);
    private static volatile long evalsSinceSegment = 0L;
    /** Published weights; element order = Stream ordinal. Volatile double[] (defensive). */
    private static volatile double[] weights = initialWeights();

    private AdaptiveMoveWeights() {
    }

    /** Zero the counters and restore the ratified base weights (used by {@link #arm}). */
    static void reset() {
        for (int i = 0; i < N; i++) {
            evals.set(i, 0L);
            accepts.set(i, 0L);
        }
        evalsSinceSegment = 0L;
        weights = initialWeights();
    }

    /** Arm the lever and RESET everything (staged wiring calls this per solver build). */
    public static void arm(boolean enabled, Config cfg) {
        armed = enabled ? 1L : 0L;
        config = cfg;
        reset();
        if (enabled) {
            // slf4j has no infof (that is the JBoss logger ScheduleService uses): format here.
            LOGGER.info(String.format(
                    "Adaptive move weights armed (decider-internal thinning): base=%s segment=%d minEvals=%d clamp=[%.2fx,%.2fx]",
                    Arrays.toString(initialWeights()), cfg.segmentEvals(), cfg.minEvals(),
                    cfg.lo(), cfg.hi()));
        }
    }

    /** Weight of a stream RIGHT NOW: the base when inert, the adapted value when armed. */
    public static double weight(Stream stream) {
        return weights[stream.ordinal()];
    }

    /**
     * TASK-504 (SPECULATIVE, DEFAULT OFF): snapshot the CURRENT adapted weights, for
     * seeding a later attempt (warm-start). The multi-start wrapper calls this at the end
     * of an attempt it considers worth inheriting from (poison-basin mitigation: only
     * best-so-far / 0-hard attempts, never failures) and hands the snapshot to
     * {@link #seed(double[])} before the next attempt's solver is built. Null/short
     * arrays and inert registries are refused (returns null): a warm-start must never
     * inject a state that was not actually learned.
     */
    public static double[] snapshotForWarmStart() {
        double[] snapshot = weights;
        if (armed == 0L || snapshot == null || snapshot.length != N) {
            return null;
        }
        for (int i = 0; i < N; i++) {
            // OTHER's base is 0.0 BY DESIGN (non-adaptive stream): a zero weight there is
            // the healthy state, only NaN/infinity/negative are corruption.
            if (Double.isNaN(snapshot[i]) || Double.isInfinite(snapshot[i]) || snapshot[i] < 0.0) {
                return null;
            }
        }
        return snapshot.clone();
    }

    /**
     * TASK-504 (SPECULATIVE, DEFAULT OFF): seed the registry with a previous attempt's
     * learned weights. Only meaningful between {@link #arm} and the solver start: sets
     * the published weights (clamped to the SAME [LO*base, HI*base] envelope a segment
     * update can reach, so an inheritance can never out-range what in-run learning
     * could). Returns false (and touches nothing) for a null/short/malformed snapshot.
     */
    public static boolean seed(double[] snapshot) {
        if (snapshot == null || snapshot.length != N) {
            return false;
        }
        double[] next = initialWeights();
        for (int i = 0; i < N; i++) {
            double v = snapshot[i];
            if (Double.isNaN(v) || Double.isInfinite(v) || v < 0.0) {
                return false; // OTHER's base 0.0 is healthy (see snapshotForWarmStart)
            }
            Stream stream = Stream.values()[i];
            double lo = config.lo() * stream.base;
            double hi = config.hi() * stream.base;
            next[i] = Math.max(lo, Math.min(hi, v));
        }
        weights = next;
        return true;
    }

    /**
     * TASK-500 forma B (decider-internal thinning): should THIS move be dispatched for
     * evaluation? The union selector still samples streams by their RATIFIED static
     * weights, so reweighting happens by THINNING: each stream's moves are dispatched
     * with probability r_i = (adaptive_i / base_i) / max_j(adaptive_j / base_j). The
     * normalization keeps the RELATIVE ratios exact while bounding every r at 1 (pure
     * thinning cannot dispatch MORE moves than the selector generates: the most-boosted
     * stream runs at full rate and the others are thinned against it). Inert (never
     * armed) dispatches everything.
     */
    public static boolean shouldEvaluate(Move<?> move) {
        if (armed == 0L) {
            return true;
        }
        Stream stream = MoveTypeClassifier.streamOf(move);
        if (!stream.adaptive) {
            return true;   // pillar/OTHER streams are outside the learning
        }
        double ratio = weights[stream.ordinal()] / stream.base;
        double maxRatio = 1.0;
        for (Stream s : Stream.values()) {
            if (s.adaptive && s.base > 0.0) {
                maxRatio = Math.max(maxRatio, weights[s.ordinal()] / s.base);
            }
        }
        if (maxRatio <= 1.0) {
            return true;   // nobody is boosted above base: nothing to thin against
        }
        return ThreadLocalRandom.current().nextDouble() < ratio / maxRatio;
    }

    /**
     * The decider's bookkeeping hook: one evaluated move, its accept verdict. Maybe
     * triggers a segment update when the global eval counter crosses the boundary.
     */
    public static void record(Move<?> move, boolean accepted) {
        if (armed == 0L) {
            return;
        }
        int idx = MoveTypeClassifier.streamOf(move).ordinal();
        evals.incrementAndGet(idx);
        if (accepted) {
            accepts.incrementAndGet(idx);
        }
        long n = ++evalsSinceSegment;
        if (n >= config.segmentEvals()) {
            updateSegment();
        }
    }

    /** One segment boundary: recompute every weight from the window's counters, reset them. */
    static void updateSegment() {
        evalsSinceSegment = 0L;
        long[] ev = snapshot(evals);
        long[] ac = snapshot(accepts);
        double[] base = new double[N];
        for (Stream s : Stream.values()) {
            base[s.ordinal()] = s.base;
        }
        double[] next = updateWeights(ev, ac, base, config);
        for (int i = 0; i < N; i++) {
            evals.set(i, 0L);
            accepts.set(i, 0L);
        }
        weights = next;
        // DEBUG not INFO: at ~100k evals/s a 4000-eval segment ticks every ~40ms, which
        // flooded the log with 11k lines in a 12-minute probe. The arming banner (INFO,
        // once per solver build) is the operator's signal; per-segment detail is diagnosis.
        LOGGER.debug("Adaptive move weights segment update: " + describe(next));
    }

    /**
     * The PURE update rule (table-testable, no state): reward_i = accepts_i/evals_i over
     * the window; avg = the BASE-WEIGHTED mean reward of the participating streams
     * (evals >= minEvals); weight_i = clamp(base_i * reward_i / avg, lo * base_i,
     * hi * base_i). Mean-preserving: a stream above the average rises (up to hi=2.5x),
     * one below falls (down to the lo=0.5x floor), and if every stream produces equally
     * the ratified bases stand unchanged.
     *
     * <p>DESIGN CORRECTION (caught by the table test): the first-draft form
     * {@code base * (eps + (1-eps) * reward/best)} has range [eps*base, base] - it can
     * only DEMOTE streams, the hi clamp was unreachable. The division-by-average form
     * replaces it (same knobs minus the now-redundant epsilon; the clamp floor IS the
     * anti-starvation valve).
     */
    static double[] updateWeights(long[] evals, long[] accepts, double[] base, Config cfg) {
        double[] rewards = new double[evals.length];
        double weightedRewardSum = 0.0;
        double baseSum = 0.0;
        boolean[] adaptive = adaptiveFlags();
        for (int i = 0; i < evals.length; i++) {
            if (!adaptive[i] || base[i] <= 0.0 || evals[i] < cfg.minEvals() || evals[i] <= 0) {
                continue;   // non-participating: pillar/OTHER, zero-base, or under-sampled
            }
            rewards[i] = (double) accepts[i] / evals[i];
            weightedRewardSum += base[i] * rewards[i];
            baseSum += base[i];
        }
        double avg = baseSum > 0.0 ? weightedRewardSum / baseSum : 0.0;
        double[] out = new double[evals.length];
        for (int i = 0; i < evals.length; i++) {
            double b = base[i];
            if (b <= 0.0 || !adaptive[i]) {
                out[i] = b;   // OTHER and pillar streams keep their declared base
                continue;
            }
            if (avg <= 0.0 || evals[i] < cfg.minEvals()) {
                out[i] = b;   // no evidence anywhere, or this stream under-sampled: base stands
                continue;
            }
            double w = b * rewards[i] / avg;
            out[i] = Math.max(cfg.lo() * b, Math.min(cfg.hi() * b, w));
        }
        return out;
    }

    private static double[] initialWeights() {
        double[] w = new double[N];
        for (Stream s : Stream.values()) {
            w[s.ordinal()] = s.base;
        }
        return w;
    }

    private static boolean[] adaptiveFlags() {
        boolean[] f = new boolean[N];
        for (Stream s : Stream.values()) {
            f[s.ordinal()] = s.adaptive;
        }
        return f;
    }

    private static long[] snapshot(AtomicLongArray a) {
        long[] out = new long[N];
        for (int i = 0; i < N; i++) {
            out[i] = a.get(i);
        }
        return out;
    }

    private static String describe(double[] w) {
        StringBuilder sb = new StringBuilder("[");
        for (Stream s : Stream.values()) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(s.name()).append(String.format(Locale.ROOT, "=%.2f", w[s.ordinal()]));
        }
        return sb.append("]").toString();
    }

    /** Classification seam: move class + planning-value runtime type -> stream. */
    static final class MoveTypeClassifier {
        private MoveTypeClassifier() {
        }

        static Stream streamOf(Move<?> move) {
            if (move == null) {
                return Stream.OTHER;
            }
            return streamOf(move.getClass(), firstValueOrNull(move));
        }

        static Stream streamOf(Class<?> moveClass, Object value) {
            boolean room = value instanceof Room;
            boolean timeslot = value instanceof Timeslot;
            String name = moveClass.getSimpleName();
            if (name.contains("PillarSwap")) {
                return timeslot ? Stream.TS_PILLAR_SWAP : Stream.OTHER;
            }
            if (name.contains("PillarChange")) {
                return timeslot ? Stream.TS_PILLAR_CHANGE : Stream.OTHER;
            }
            if (name.contains("Swap")) {
                return room ? Stream.ROOM_SWAP : timeslot ? Stream.TS_SWAP : Stream.OTHER;
            }
            if (name.contains("Change")) {
                return room ? Stream.ROOM_CHANGE : timeslot ? Stream.TS_CHANGE : Stream.OTHER;
            }
            return Stream.OTHER;
        }

        private static Object firstValueOrNull(Move<?> move) {
            var values = move.getPlanningValues();
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.iterator().next();
        }
    }
}
