package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.school.planner.service.SubjectRegistry.SubjectFlags;
import com.school.planner.util.SeedCsv;
import com.school.planner.util.SubjectNormalization;

/**
 * Guards the data-driven subject taxonomy (TASK-437). subjects.csv is GENERATED
 * from the old DataGenerator prefix matchers, so the byte-identity contract is:
 * (a) all 52 normalized subjects load; (b) every subject generateCourses would
 * actually create from incarichi.csv is classified; (c) the classifications
 * match the documented intent, including the non-obvious cases (Informatica is
 * a lab but NOT professionalizzante) and the referente/FASE-C de-tags; (d) the
 * per-(group, subject) overlay mechanism works for the pending G14 answer.
 *
 * <p>Plain JUnit (no @QuarkusTest): constructs the bean and calls package-private
 * {@link SubjectRegistry#load()} directly, mirroring the DataGeneratorSeedTest
 * convention.</p>
 */
class SubjectRegistryTest {

    /** Mirrors DataGenerator.PSEUDO_COURSE_TEACHERS (package-private, other package). */
    private static final Set<String> PSEUDO_TEACHERS = Set.of("Stage", "Esame", "RA", "Totale ore corso");

    private SubjectRegistry reg() {
        SubjectRegistry r = new SubjectRegistry();
        r.load();
        return r;
    }

    @Test
    void loadsAll52Subjects() {
        assertEquals(52, reg().subjectCount(), "subjects.csv must classify all 52 normalized 2026/27 subjects");
    }

    @Test
    void everyIncarichiSubjectIsClassified() {
        // generateCourses skips pseudo-TEACHER rows (col 0); every remaining
        // subject (col 1) must have a row, else seeding would fail-loud.
        SubjectRegistry r = reg();
        Set<String> seen = new HashSet<>();
        for (String[] row : SeedCsv.load("incarichi.csv", 4)) {
            if (PSEUDO_TEACHERS.contains(row[0].trim())) {
                continue;
            }
            String subject = row[1].trim();
            if (subject.isEmpty() || subject.equals("None")) {
                continue;
            }
            seen.add(SubjectNormalization.normalize(subject));
        }
        for (String subject : seen) {
            r.flagsFor(subject); // throws on an unclassified subject
        }
    }

    @Test
    void unclassifiedSubjectFailsLoud() {
        SubjectRegistry r = reg();
        assertThrows(RuntimeException.class, () -> r.flagsFor("Materia Inesistente 9999"),
                "an unclassified subject must fail-loud, not default silently");
    }

    @Test
    void roomTagsAndLabClassificationMatchIntent() {
        SubjectRegistry r = reg();
        // subject -> its single required room tag (every lab subject has exactly one today)
        Map<String, String> tagged = new LinkedHashMap<>();
        tagged.put("Laboratorio Termoidraulico", "termoidraulica");
        tagged.put("Lavorazioni meccaniche", "meccanica");
        tagged.put("Tecniche di saldatura", "saldatura");
        tagged.put("Informatica", "informatica");
        tagged.put("Cad", "informatica");
        tagged.put("Controllo numerico", "informatica");
        tagged.put("Confezione", "moda");
        tagged.put("Modello (Moulage)", "moda");
        tagged.put("Lavaggio", "acconciatura");
        tagged.put("Barberia", "acconciatura");
        tagged.put("Servizio stilistico", "acconciatura");
        tagged.put("Servizio tecnico e stilistico", "acconciatura");
        tagged.forEach((s, tag) -> {
            assertEquals(List.of(tag), r.flagsFor(s).roomTags(), "roomTags for " + s);
            assertTrue(r.flagsFor(s).isLab(), s + " should be a lab");
        });
        // referente 29/07 + FASE C de-tags: these stay NON-lab (empty tags) but are still professionalizzanti.
        for (String s : List.of("Taglio Metalli", "Progettazione Termoidraulica", "Tecnologia tessile")) {
            assertTrue(r.flagsFor(s).roomTags().isEmpty(), s + " must be de-tagged (aula)");
            assertFalse(r.flagsFor(s).isLab(), s + " must not be a lab");
        }
    }

    @Test
    void professionalizzantiMatchesIntent() {
        SubjectRegistry r = reg();
        for (String s : List.of("Cad", "Controllo numerico", "Sicurezza", "Taglio Metalli",
                "Disegno Edile ed Impiantistico", "Merceologia")) {
            assertTrue(r.flagsFor(s).professionalizzanti(), s + " should be professionalizzante");
        }
        // Informatica is a LAB but NOT professionalizzante (the non-obvious case).
        assertFalse(r.flagsFor("Informatica").professionalizzanti(), "Informatica is a lab but not professionalizzante");
        for (String s : List.of("Matematica", "Lingua Italiana", "Disegno")) {
            assertFalse(r.flagsFor(s).professionalizzanti(), s + " should not be professionalizzante");
        }
    }

    @Test
    void groupSubjectOverlayDetagsForOneGroupOnly() {
        // TASK-455 v1 (2026-07-30): G14 and G12 Servizio stilistico are de-tagged
        // in seed/subject_overrides.csv (evidence-based: they drive the golden's 42
        // acconciatura overloads). The CSV override loads here, so they read non-lab;
        // G06 / G10 are untouched (still lab).
        SubjectRegistry r = reg();
        assertFalse(r.flagsFor("Servizio stilistico", "G14").isLab(),
                "v1 CSV de-tags G14 Servizio stilistico");
        assertFalse(r.flagsFor("Servizio stilistico", "G12").isLab(),
                "v1 CSV de-tags G12 Servizio stilistico");
        assertTrue(r.flagsFor("Servizio stilistico", "G06").isLab(),
                "G06 still lab (no CSV override)");
        // TASK-550 G14 cure (maintainer 25/08 08:10, closing the half-done Q19 de-tag of
        // 01/08 - TASK-477b was tracked and never executed): Confezione is de-tagged
        // for G14 too, so the 18 held hours can sit in a normal classroom
        assertFalse(r.flagsFor("Confezione", "G14").isLab(),
                "TASK-550: G14 Confezione de-tagged (the row the 01/08 de-tag missed)");
        assertTrue(r.flagsFor("Confezione", "G05").isLab(),
                "other groups' Confezione stays lab (moda tag untouched)");

        // The override MECHANISM (putOverrideForTest) on a group NOT already in the CSV:
        // G06 is lab, de-tag it in-memory, G10 stays untouched (scoped to one group).
        r.putOverrideForTest("G06", "Servizio stilistico", "NONE", "");
        SubjectFlags FA2 = r.flagsFor("Servizio stilistico", "G06");
        SubjectFlags FA3 = r.flagsFor("Servizio stilistico", "G10");
        assertFalse(FA2.isLab(), "NONE forces a de-tag for G06 only");
        assertTrue(FA2.professionalizzanti(), "blank prof cell inherits the base value");
        assertTrue(FA3.isLab(), "the override is scoped to G06, G10 is untouched");
    }

    @Test
    void groupSubjectOverlayCanFlipProfessionalizzanti() {
        SubjectRegistry r = reg();
        assertFalse(r.flagsFor("Informatica", "G03").professionalizzanti());
        r.putOverrideForTest("G03", "Informatica", "", "true");
        SubjectFlags f = r.flagsFor("Informatica", "G03");
        assertTrue(f.professionalizzanti(), "prof override applies; blank roomTags inherits");
        assertEquals(List.of("informatica"), f.roomTags());
    }
}
