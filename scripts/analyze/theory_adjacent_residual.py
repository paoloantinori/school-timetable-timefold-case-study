#!/usr/bin/env python3
"""
TASK-468 AC#1 - Misura del residuo golden del vincolo M candidato
"non ci devono essere 2 ore vicine della stessa materia teorica"
(tab "Vincoli DA ANALIZZARE" del golden xlsx).

Read-only. Input: il golden `seed/lessons.csv`. Classificazione lab/theory
autorevole dal DB (course.islab), con l'eccezione che "Servizio stilistico" e'
lab per 1/2/G10 ma THEORY per G12/G14 (ospiti nel perimetro variante b).

Conforme al protocollo trappole-golden di CLAUDE.md: esclude sabati, docente
Fasullo/Fasutto (segnaposto) e subject Stage (off-grid).

Due definizioni di "teoria":
  LARGA   = non-lab (islab=false nel seed)
  STRETTA = non-lab e non pratico-tecnico (esclude disegno/informatica/
            progettazione/elettrotecnica/RA, per l'esenzione del tab:
            "i laboratori, COMPRESO DISEGNO O INFORMATICA, possono avere
            piu' ore vicine"). Il confine esatto e' decisione di design (AC#2).

Due forme candidate (TASK-468 description):
  - threshold-1 (M21-style a soglia 1): conta i giorni con 2+ ore della stessa
    materia teorica --> "giorni-2+"
  - coppia adiacente: conta le coppie same-subject same-day a slot adiacenti
    (entrambe teoria) --> "coppie-adj"
"""
import csv
from datetime import datetime
from collections import defaultdict

GOLDEN = "src/main/resources/seed/lessons.csv"

# 11 lab incondizionati (dal DB, islab=true). Servizio stilistico e' condizionale.
LAB_SUBJECTS = {
    "Barberia", "Cad", "Confezione", "Controllo numerico",
    "Informatica", "Laboratorio Termoidraulico", "Lavaggio",
    "Lavorazioni meccaniche", "Modello (Moulage)",
    "Servizio tecnico e stilistico", "Tecniche di saldatura",
}
# Servizio stilistico: theory (non-lab) solo per gli ospiti G12 / G14;
# lab per i titolari 1/2/G10.
SERVIZIO_STILISTICO_THEORY_GROUPS = {"G12", "G14"}

# Tab vincoli: "...i laboratori (COMPRESO DISEGNO O INFORMATICA) possono avere
# piu' ore vicine". Nel seed Disegno/Disegno Edile/Progettazione/Elettrotecnica/
# RA sono islab=FALSE, ma la regola li esente come i lab. Soglia "tecnica".
PRACTICAL_EXEMPT = {
    "Disegno", "Disegno Edile ed Impiantistico", "Progettazione Termoidraulica",
    "Elettrotecnica", "Metrologia e Tecnologia dei materiali", "Tecnologia Meccanica",
    "Taglio Metalli", "Ra lab aperto", "RA",
}

PLACEHOLDER_TEACHERS = {"Fasullo", "Fasutto", ""}
MODES = ["larga", "stretta"]


def is_lab(subject, group):
    if subject in LAB_SUBJECTS:
        return True
    if subject == "Servizio stilistico":
        return group not in SERVIZIO_STILISTICO_THEORY_GROUPS
    return False


def is_theory(subject, group, mode):
    if is_lab(subject, group):
        return False
    if mode == "stretta" and subject in PRACTICAL_EXEMPT:
        return False
    return True


def parse_slot_start(slot):
    start = slot.split()[0]
    h, m = start.split(":")
    return int(h) * 60 + int(m)


def main():
    rows = []
    with open(GOLDEN, newline="") as f:
        for r in csv.DictReader(f):
            teacher = r["teacher"].strip()
            subject = r["subject"].strip()
            group = r["group"].strip()
            date = r["date"].strip()
            if teacher in PLACEHOLDER_TEACHERS:
                continue
            if subject == "Stage":
                continue
            wd = datetime.strptime(date, "%Y-%m-%d").strftime("%a")
            if wd == "Sat":
                continue
            rows.append((date, wd, r["slot"].strip(), group, subject, teacher))

    print("=" * 72)
    print("TASK-468 AC#1 - residuo golden 'no 2 ore vicine stessa materia teorica'")
    print("=" * 72)
    print(f"golden lessons (post-filtri trappole): {len(rows)}")
    print()

    for mode in MODES:
        day_subject_slots = defaultdict(list)
        for date, wd, slot, group, subject, teacher in rows:
            if not is_theory(subject, group, mode):
                continue
            day_subject_slots[(group, date, subject)].append(parse_slot_start(slot))

        giorni_2plus = 0
        coppie_adiacenti = 0
        per_gs = defaultdict(lambda: {"giorni_2plus": 0, "coppie": 0})

        for (group, date, subject), starts in day_subject_slots.items():
            if len(starts) < 2:
                continue
            giorni_2plus += 1
            per_gs[(group, subject)]["giorni_2plus"] += 1
            s = sorted(starts)
            for i in range(len(s) - 1):
                if s[i + 1] - s[i] == 60:
                    coppie_adiacenti += 1
                    per_gs[(group, subject)]["coppie"] += 1

        print(f"### TEORIA {mode.upper()} ###")
        print(f"  forma threshold-1 (giorni con 2+ ore stessa teoria): {giorni_2plus}")
        print(f"  forma coppia adiacente (coppie same-subject adiacenti): {coppie_adiacenti}")
        if giorni_2plus:
            print(f"  coppie/giorni = {coppie_adiacenti / giorni_2plus:.2f} "
                  f"(>1 = alcuni giorni hanno 3+ ore in blocco)")
        print()
        per_subj = defaultdict(lambda: {"g2": 0, "cp": 0})
        for (group, subject), v in per_gs.items():
            per_subj[subject]["g2"] += v["giorni_2plus"]
            per_subj[subject]["cp"] += v["coppie"]
        print(f"  --- top 12 subject ({mode}) ---")
        print(f"  {'subject':45} {'giorni-2+':>9} {'coppie-adj':>11}")
        for subject in sorted(per_subj, key=lambda s: -(per_subj[s]["g2"] + per_subj[s]["cp"]))[:12]:
            v = per_subj[subject]
            print(f"  {subject:45} {v['g2']:>9} {v['cp']:>11}")
        print()
        print(f"  --- top 12 (group, subject) ({mode}) ---")
        print(f"  {'group':8} {'subject':45} {'giorni-2+':>9} {'coppie-adj':>11}")
        for (group, subject), v in sorted(per_gs.items(), key=lambda kv: -kv[1]["giorni_2plus"])[:12]:
            print(f"  {group:8} {subject:45} {v['giorni_2plus']:>9} {v['coppie']:>11}")
        print()


if __name__ == "__main__":
    main()
