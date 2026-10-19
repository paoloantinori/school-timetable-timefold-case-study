#!/usr/bin/env python3
"""
TASK-468 - Misura del residuo M-TR (theory STRETTA threshold-1) + breakdown pin-status
su un dump `group|date|hour|subject|teacher|pinned` (lessons.txt construction o run, oppure
il CSV golden). STessa classificazione del vincolo M-TR (isTheoryForRule = !isLab &&
!THEORY_RULE_PRACTICAL_EXEMPT), per confronto apples-to-apples con analysis.json.

Read-only. Rigetta le righe non-pipe (artefatto "SET"), sabati, docenti placeholder
(Fasullo/Fasutto/empty) e subject Stage (off-grid). Classifica isLab via LAB_SUBJECTS +
Servizio stilistico condizionale (specchio di theory_adjacent_residual.py / DB isLab).

Usato dalla diagnosi construction-origin (Fedora verification 02:40): misura il residuo a
fine construction (target/construction-lessons.txt) vs finale run, e il breakdown pin-status
dei giorni-ripetizione (per discriminatorare M20-dominance vs construction-origin vs
promotion-pinning).

Validato: su ab-w1000-run1/lessons.txt dà giorni-2+ = 1679 / unità = 1780, identico al
vincolo (analysis.json M-TR matches=1679, score=-1780000 -> 1780 unità).

Usage: python3 theory_adjacent_residual_dump.py <dump.txt>
"""
import sys
from datetime import datetime
from collections import defaultdict

# 11 unconditional labs (mirror theory_adjacent_residual.py / DB isLab). Servizio stilistico conditional.
LAB_SUBJECTS = {
    "Barberia", "Cad", "Confezione", "Controllo numerico", "Informatica",
    "Laboratorio Termoidraulico", "Lavaggio", "Lavorazioni meccaniche",
    "Modello (Moulage)", "Servizio tecnico e stilistico", "Tecniche di saldatura",
}
SERVIZIO_STILISTICO_THEORY_GROUPS = {"G12", "G14"}
# Constraint THEORY_RULE_PRACTICAL_EXEMPT (8 authoritative DB names; "RA" non e' un subject runtime).
PRACTICAL_EXEMPT = {
    "Disegno", "Disegno Edile ed Impiantistico", "Progettazione Termoidraulica", "Elettrotecnica",
    "Metrologia e Tecnologia dei materiali", "Tecnologia Meccanica", "Taglio Metalli", "Ra lab aperto",
}
PLACEHOLDER = {"Fasullo", "Fasutto", "", "Stage"}


def is_lab(subject, group):
    if subject in LAB_SUBJECTS:
        return True
    if subject == "Servizio stilistico":
        return group not in SERVIZIO_STILISTICO_THEORY_GROUPS
    return False


def is_theory(subject, group):
    return not is_lab(subject, group) and subject not in PRACTICAL_EXEMPT


def main():
    path = sys.argv[1]
    rows = []  # (group, date, subject, pinned)
    n_total = 0
    with open(path) as f:
        for line in f:
            parts = [p.strip() for p in line.split("|")]
            if len(parts) < 6:
                continue
            group, date, hour, subject, teacher, pinned = parts[:6]
            if not subject or not date or not group:
                continue
            if teacher in PLACEHOLDER or subject in PLACEHOLDER:
                continue
            try:
                wd = datetime.strptime(date, "%Y-%m-%d").strftime("%a")
            except ValueError:
                continue
            if wd == "Sat":
                continue
            n_total += 1
            rows.append((group, date, subject, pinned == "true"))

    day_subj = defaultdict(list)  # (group, date, subject) -> list of pinned flags
    n_theory = 0
    for group, date, subject, pinned in rows:
        if not is_theory(subject, group):
            continue
        n_theory += 1
        day_subj[(group, date, subject)].append(pinned)

    giorni_2plus = 0
    pin_cat = {"fully_unpinned": 0, "fully_pinned": 0, "mixed": 0}
    units = 0
    per_subj = defaultdict(int)
    for (group, date, subject), flags in day_subj.items():
        if len(flags) < 2:
            continue
        giorni_2plus += 1
        units += len(flags) - 1
        per_subj[subject] += 1
        if all(f for f in flags):
            pin_cat["fully_pinned"] += 1
        elif not any(f for f in flags):
            pin_cat["fully_unpinned"] += 1
        else:
            pin_cat["mixed"] += 1

    print(f"file: {path}")
    print(f"rows (post-filtri, all subjects): {n_total}")
    print(f"theory lessons (STRETTA): {n_theory}")
    print(f"giorni-2+ (threshold-1, = M-TR matches): {giorni_2plus}")
    print(f"unita' (sum ore-1, = M-TR score / peso): {units}")
    print(f"pin breakdown of giorni-2+:")
    for k, v in pin_cat.items():
        pct = (100.0 * v / giorni_2plus) if giorni_2plus else 0
        print(f"  {k:18} {v:5}  ({pct:.1f}%)")
    print(f"top 10 subject (giorni-2+):")
    for s, c in sorted(per_subj.items(), key=lambda kv: -kv[1])[:10]:
        print(f"  {s:45} {c}")


if __name__ == "__main__":
    main()
