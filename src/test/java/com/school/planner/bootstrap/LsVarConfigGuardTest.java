package com.school.planner.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard for the guarded duplication {@code src/test/resources/lsvar-live-solver-config.xml}:
 * it must stay byte-identical to the MAIN {@code src/main/resources/solverConfig.xml}
 * (the seed-CSV duplication pattern: a copy is acceptable only while a mechanical
 * contract keeps it from rotting). The LS-variance bench needs the production phase
 * structure (CompactionPhase split anchor, live terminations) on the TEST classpath,
 * where the 10s smoke {@code solverConfig.xml} shadows it.
 */
class LsVarConfigGuardTest {

    @Test
    void benchConfigIsByteIdenticalToTheMainSolverConfig() throws Exception {
        Path main = Path.of("src/main/resources/solverConfig.xml");
        Path copy = Path.of("src/test/resources/lsvar-live-solver-config.xml");
        assertTrue(Files.isRegularFile(main), "main solver config missing: " + main);
        assertTrue(Files.isRegularFile(copy), "bench copy missing: " + copy
                + " - re-copy it from the main config");
        byte[] a = Files.readAllBytes(main);
        byte[] b = Files.readAllBytes(copy);
        assertTrue(java.util.Arrays.equals(a, b), "lsvar-live-solver-config.xml diverged from"
                + " src/main/resources/solverConfig.xml (" + a.length + " vs " + b.length
                + " bytes): re-copy the main config into the test copy (the bench measures"
                + " the LIVE phase structure, a stale copy silently measures another solver)");
    }
}
