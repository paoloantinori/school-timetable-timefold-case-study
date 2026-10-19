#!/usr/bin/env python3
"""Generate the school-year INPUT workbook draft (TASK-445, Tappa 1).

Produces ``AnnoScolastico_<label>.xlsx`` with all 21 data sheets of the proposal
(``claudedocs/proposta-workbook-input-2026-07-28.md``) plus ``00_Istruzioni``
(legend) and ``99_Validazione`` (the 23-check contract as documentation).

Design (architecture decisions, Fedora for maintainer 2026-07-29):
- The generator and readers are PYTHON (openpyxl), offline, once a year. No new
  pom dependency (no Apache POI). Run inside the git-ignored venv at
  ``scripts/workbook/.venv``.
- Sheets populated from a verified source carry ``origine = sistema`` where the
  value is derived (not school-confirmed). Sheets that need a reorganization
  that is a later tappa (06/07 availability, 13/14/15 piano-giorni, 18/19/21)
  are present with EXACT headers plus one marker row documenting the pending
  source: no data is fabricated.
- The load-bearing correctness gate (proposal 5.2 + audit C1) lives in
  :mod:`test_materie_byte_identity`: 08_Materie must be byte-identical to
  subjects.csv + the solver-side constant snapshots. This script only EMITS the
  workbook; the gate PROVES it.

Usage::

    scripts/workbook/.venv/bin/python scripts/workbook/generate_workbook.py
    scripts/workbook/.venv/bin/python scripts/workbook/generate_workbook.py --out /tmp/x.xlsx
"""

from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation

import sources as S
import availability_compress as A
import piano_giorni_compress as P
import theory_block_policy as TBP
import cadence_reference as CREF
import concentration_policy as CP
import fixed_weekday_policy as FWP
import weekday_fill_policy as WFP
import workbook_schema as WS

OUTPUT_DIR = Path(__file__).resolve().parent / "output"

# --- styling ---------------------------------------------------------------
HEADER_FONT = Font(bold=True, color="FFFFFF")
HEADER_FILL = PatternFill("solid", fgColor="4472C4")
SISTEMA_FILL = PatternFill("solid", fgColor="FFF2CC")   # pale yellow = proposed by system
GAP_FILL = PatternFill("solid", fgColor="F2F2F2")       # grey = gap marker
WRAP = Alignment(wrap_text=True, vertical="top")

ORIGINE_SISTEMA = S.ORIGINE_SISTEMA   # hoisted to sources.py (shared with availability_compress)
ORIGINE_SCUOLA = S.ORIGINE_SCUOLA


def _bool_si(value: bool) -> str:
    """Workbook boolean convention (proposal section 2): SI/NO, never X."""
    return "SI" if value else "NO"


def _sheet(wb: Workbook, title: str, headers: list[str]) -> "Worksheet":  # noqa: F821
    """Create a sheet, write the exact headers row, freeze + filter it."""
    ws = wb.create_sheet(title)
    ws.append(headers)
    for col, _ in enumerate(headers, start=1):
        cell = ws.cell(row=1, column=col)
        cell.font = HEADER_FONT
        cell.fill = HEADER_FILL
        cell.alignment = WRAP
    ws.freeze_panes = "A2"
    ws.auto_filter.ref = f"A1:{get_column_letter(len(headers))}1"
    return ws


def _style_body(ws) -> None:
    """Width + wrap the body; tint the origine cell yellow where its row value
    is 'sistema'. Header-name driven (so a column reorder can't silently
    retarget the tint) and value driven (matching the 00_Istruzioni legend:
    yellow marks rows the system proposes and the school must confirm, not
    every row that has an origine column)."""
    origine_col = None
    for col in range(1, ws.max_column + 1):
        header = str(ws.cell(row=1, column=col).value or "")
        if header == "origine":
            origine_col = col
        h = header.lower()
        if h in ("note", "descrizione", "motivo", "regola"):
            width = 40
        elif h in ("materia", "nomecompleto", "docente", "classe"):
            width = 26
        else:
            width = 14
        ws.column_dimensions[get_column_letter(col)].width = width
    for row in range(2, ws.max_row + 1):
        for col in range(1, ws.max_column + 1):
            ws.cell(row=row, column=col).alignment = WRAP
        if origine_col and str(ws.cell(row=row, column=origine_col).value).strip() == ORIGINE_SISTEMA:
            ws.cell(row=row, column=origine_col).fill = SISTEMA_FILL
    # req4: whole-number 0-1000 validation on this sheet's numeric columns so Excel
    # rejects text (a note typed into an hours cell) at entry - the prevention layer
    # paired with V26's detection. Column set from workbook_schema (single source).
    # Blank stays allowed (empty = not applicable, the proposal convention). Summary
    # rows (12 'Totale ORE', a =SUM) are excluded so a computed total > 1000 is not
    # flagged; the total is always the last row when present.
    numeric = WS.NUMERIC_COLUMNS.get(ws.title)
    if numeric:
        total_row = next((r for r in range(2, ws.max_row + 1)
                          if str(ws.cell(row=r, column=1).value).strip() == WS.TOTAL_ROW_LABEL), None)
        last_input = (total_row - 1) if total_row else ws.max_row
        if last_input >= 2:
            header_col = {str(ws.cell(row=1, column=c).value).strip(): c
                          for c in range(1, ws.max_column + 1)}
            dv = DataValidation(type="whole", operator="between",
                                formula1=0, formula2=1000, allow_blank=True)
            dv.errorTitle = "Colonna numerica"
            dv.error = "Inserire un numero intero 0-1000 (le note vanno nella colonna note)"
            for name in numeric:
                col = header_col.get(name)
                if col is not None:
                    letter = get_column_letter(col)
                    dv.add(f"{letter}2:{letter}{last_input}")
            ws.add_data_validation(dv)


def _grey_row(ws, prefix: str, message: str) -> None:
    """Append one grey italic row; shared by _gap_marker and _caveat_note."""
    ws.append({1: f"{prefix} {message}"})
    ws.cell(row=ws.max_row, column=1).fill = GAP_FILL
    ws.cell(row=ws.max_row, column=1).font = Font(italic=True, color="808080")


def _gap_marker(ws, message: str) -> None:
    """The sheet's ONLY body row: its source is a later tappa, no data fabricated."""
    _grey_row(ws, "[GAP - Tappa successiva]", message)


# --- sheets ----------------------------------------------------------------

def sheet_00_istruzioni(wb: Workbook) -> None:
    ws = wb.create_sheet("00_Istruzioni")
    ws.column_dimensions["A"].width = 120
    lines = [
        ("AnnoScolastico workbook - input per un nuovo anno scolastico", True),
        ("", False),
        ("Bozza generata dal sistema il 2026-07-29 dai dati 2026/27 gia' in possesso", True),
        ("(seed CSV + costanti di codice). La cella origine e' GIALLA quando vale", False),
        ("sistema (proposta da confermare/correggere); scuola = confermato.", False),
        ("Le righe in GRIGIO [GAP ...] indicano un foglio la cui sorgente e' una", False),
        ("tappa successiva del TASK-445: nessun dato e' stato inventato.", False),
        ("", False),
        ("Convenzioni (valide per tutti i fogli):", True),
        ("- Una riga = un fatto. Nessuna cella con due informazioni, niente celle unite.", False),
        ("- Date in formato ISO AAAA-MM-GG.", False),
        ("- Booleani come SI/NO, mai X.", False),
        ("- Liste separate da ; senza spazi (es. informatica;meccanica).", False),
        ("- Cella vuota = non applicabile / default. Mai 0 per dire 'non so'.", False),
        ("- Niente virgole dentro i campi (il parser CSV di seed non le gestisce).", False),
        ("- Colonna origine: scuola (confermato) o sistema (proposto).", False),
        ("- Colonna note: testo libero, ignorata dal sistema.", False),
        ("", False),
        ("Fogli a sorgente diretta (popolati): 01_Anno, 02_Campanella, 03_Chiusure,", True),
        ("04_GiorniSpeciali, 05_Docenti, 06_DocentiDisponibilita, 07_DocentiEccezioni,", False),
        ("08_Materie, 09_MaterieAlias, 10_Classi, 11_ClassiCalendario, 12_Incarichi,", False),
        ("13_PianoGiorniPattern, 14_PianoGiorniEccezioni, 15_PianoGiorniGenerato,", False),
        ("16_Stage, 17_Aule, 20_GiorniProfessionalizzanti, 21_CadenzaAttesa, 22_TeoriaBlocco, 23_GiornoFisso,", False),
        ("24_ConcentrazioneMensile, 25_WeekdayFill.", False),
        ("", False),
        ("Fogli con GAP (header pronti, sorgente = tappa successiva):", True),
        ("- 18_RotazioniLab: vuoto by design nel 2026/27 (i pin arrivano dal calendario).", False),
        ("- 19_Doppio: assistenti LabAssistant (meccanismo morto, gap G6 della proposta).", False),
        ("", False),
        ("Note sui dati 2026/27 (caveat, non errori):", True),
        ("- 16_Stage: nel 2026/27 FC/G13 hanno stage reali NON modellati come", False),
        ("  StagePattern, e G04 ha ore di stage in griglia senza monte-ore (16h oltre", False),
        ("  orizzonte, TASK-434). La scuola completa le classi mancanti.", False),
        ("- 12_Incarichi: schema con colonna tipo = ordinario | esame | pool. Il", False),
        ("  generatore emette SOLO le 266 righe ordinarie (identita' dimostrata con i", False),
        ("  classificatori attuali, nessun cambio di comportamento mascherato). Le ore", False),
        ("  di Esame (124h, 6 classi) e le ore pool RA (40h, G08: ore dovute alla", False),
        ("  classe senza docente in partenza, natura diversa da ordinario ed esame) NON", False),
        ("  sono righe qui: sono un buco dichiarato con il suo numero nel foglio", False),
        ("  99_Validazione. La scuola le compilera' come righe tipo=esame | pool.", False),
        ("  (decisione maintainer 2026-07-29)", False),
        ("- 22_TeoriaBlocco: politica blocco teoria (1h/giorno vs 2h) per (classe, materia),", False),
        ("  derivata dal golden come proposta sistema. referente conferma/corregge la colonna", False),
        ("  bloccoTeoria (e l'origine passa a scuola). M-TR (TASK-468) resta flag OFF:", False),
        ("  e' input dichiarato, non adozione.", False),
        ("- 23_GiornoFisso: giorno fisso per (classe, materia) dal golden (mining 15/08):",
         False),
        ("  le 40 coppie che il golden tiene SEMPRE lo stesso giorno e che la cadenza",
         False),
        ("  (21) non implica: confermare scuola=intenzionale per deporle come input.",
         False),
        ("- 24_ConcentrazioneMensile: concentrazioni mensili forti (>=4x l'atteso) per (classe,", False),
        ("  materia) dal golden (mining r2 16/08, baseline corretta): scelta o contingenza?", False),
        ("- 25_WeekdayFill: celle (classe, weekday) che il golden riempie meno del proprio", False),
        ("  calendario (fill < 80%): voluto o contingenza?", False),
("- 21_CadenzaAttesa: cadenza settimanale (max ore/sett + settimane attive) per", False),
        ("  (classe, materia), derivata dal golden-cadence-reference.json (il riferimento", False),
        ("  M34/M35). CONDIZIONALE: la scuola la conferma solo se vuole i capi M34/M35;", False),
        ("  lasciata sistema/vuota i vincoli restano inerti (graceful, nessun danno).", False),
        ("", False),
        ("99_Validazione elenca i 23 controlli (V1..V23) come contratto: la", True),
        ("implementazione lato sistema e' la Tappa 5.", False),
    ]
    for i, (text, bold) in enumerate(lines, start=1):
        cell = ws.cell(row=i, column=1, value=text)
        cell.font = Font(bold=bold)


def sheet_01_anno(wb: Workbook) -> None:
    ws = _sheet(wb, "01_Anno", ["chiave", "valore", "origine", "note"])
    rows = [
        ("annoEtichetta", S.ANNO_ETICHETTA, ORIGINE_SCUOLA, ""),
        ("dataInizio", S.ANNO_DATA_INIZIO, ORIGINE_SCUOLA, ""),
        ("dataFine", S.ANNO_DATA_FINE, ORIGINE_SCUOLA, ""),
        ("giorniSettimana", S.ANNO_GIORNI_SETTIMANA, ORIGINE_SCUOLA, ""),
        ("settimaneDidattiche", "", ORIGINE_SISTEMA, "vuoto = calcolato dalle date meno le chiusure"),
        ("oreAnnueStandard", S.ANNO_ORE_ANNUE_STANDARD, ORIGINE_SISTEMA, "riferimento per il controllo V1"),
        ("dataEsamiDa", "", ORIGINE_SCUOLA, "fuori griglia: nessuno slot, ore contabilizzate fuori griglia"),
        ("dataEsamiA", "", ORIGINE_SCUOLA, ""),
    ]
    for r in rows:
        ws.append(r)
    _style_body(ws)


def sheet_02_campanella(wb: Workbook) -> None:
    ws = _sheet(wb, "02_Campanella", ["slot", "inizio", "fine", "fascia", "origine", "note"])
    for slot, inizio, fine, fascia in S.CAMPANELLA_SLOTS:
        ws.append([slot, inizio, fine, fascia, ORIGINE_SISTEMA, ""])
    _style_body(ws)


def sheet_03_chiusure(wb: Workbook) -> None:
    ws = _sheet(wb, "03_Chiusure", ["data", "descrizione", "tipo", "origine", "note"])
    for data, desc, tipo in S.CLOSURES:
        ws.append([data, desc, tipo, ORIGINE_SCUOLA, ""])
    _style_body(ws)


def sheet_04_giorni_speciali(wb: Workbook) -> None:
    ws = _sheet(wb, "04_GiorniSpeciali",
                ["data", "classe", "slotMin", "slotMax", "motivo", "origine", "note"])
    # First-day ramp: every group with an OPEN Monday (cell non-null) is confined
    # to slots 1-4 on 2026-09-14. Eligibility follows the calendar rule, not a
    # name, so it tracks the solver seed if a group's Monday opens or closes.
    eligible = set(S.first_day_ramp_groups())
    for g in S.load_groups():
        if g["group"] not in eligible:
            continue
        ws.append([S.GIORNI_SPECIALI_DATE, g["group"], 1, 4, S.GIORNI_SPECIALI_MOTIVO, ORIGINE_SISTEMA, ""])
    _style_body(ws)


def sheet_05_docenti(wb: Workbook) -> None:
    ws = _sheet(wb, "05_Docenti",
                ["docente", "nomeCompleto", "email", "sigla", "esterno", "maxOreGiorno", "origine", "note"])
    for t in S.load_teachers():
        ws.append([
            t["short"], t["fullname"], t["email"], t["short"],
            _bool_si(t["short"] in S.DOCENTI_ESTERNI),
            t["maxDailyHours"],
            ORIGINE_SCUOLA,
            "" if t["short"] not in S.DOCENTI_ESTERNI else "Docente esterna",
        ])
    _style_body(ws)


def sheet_06_disponibilita(wb: Workbook) -> None:
    ws = _sheet(wb, "06_DocentiDisponibilita",
                ["docente", "regola", "tipo", "giorni", "fascia", "slotMin", "slotMax",
                 "valore", "forza", "validoDa", "validoA", "origine", "note"])
    # Concise recurring rules (SOLO_GIORNI/MAI_GIORNI/SOLO_FASCIA/FINESTRA) compressed
    # from availability_rules.csv (Tappa 3), plus the weekly-count rules that today
    # live as teachers.csv columns (MAX_INIZI_PRESTO/MIN_GIORNI_LIBERI). The
    # compress/expand equivalence is gated by test_availability_roundtrip.
    rules, _noncomp = A.compress()
    for r in rules + A.weekly_count_rules():
        ws.append([r["docente"], r["regola"], r["tipo"], r["giorni"], r["fascia"],
                   r["slotMin"], r["slotMax"], r["valore"], r["forza"],
                   r["validoDa"], r["validoA"], r["origine"], r["note"]])
    _style_body(ws)


def sheet_07_eccezioni(wb: Workbook) -> None:
    ws = _sheet(wb, "07_DocentiEccezioni",
                ["docente", "dataDa", "dataA", "slotMin", "slotMax", "tipo", "motivo", "origine", "note"])
    # Dated exceptions from availability_rules.csv (e.g. T12's visits). Tappa 3.
    # (A recurring closure that fits no concise form is a vocabulary gap that fails
    # the round-trip gate loud, NOT a 07 row: 07's schema is dated.)
    for e in A.exceptions_07():
        ws.append([e["docente"], e["dataDa"], e["dataA"], e["slotMin"], e["slotMax"],
                   e["tipo"], e["motivo"], e["origine"], e["note"]])
    _style_body(ws)


def sheet_08_materie(wb: Workbook) -> None:
    """The load-bearing sheet. Flags from subjects.csv; timing from solver snapshots."""
    ws = _sheet(wb, "08_Materie",
                ["materia", "laboratorio", "professionalizzante", "tagAula", "blocco",
                 "capOreGiorno", "settimanaInizioMax", "settimanaInizioMin",
                 "primaDelloStage", "dopoLoStage", "origine", "note"])
    for sub in S.load_subjects():
        norm = S.normalize_subject(sub["subject"])
        ws.append([
            sub["subject"],
            _bool_si(bool(sub["roomTags"])),                  # laboratorio = requires a tagged room
            _bool_si(sub["professionalizzanti"]),
            ";".join(sub["roomTags"]),
            "",                                               # blocco: not seeded (defaults to 1)
            0 if norm in S.M21_EXEMPT_SUBJECTS else "",       # capOreGiorno 0 = no cap (M21 exempt)
            S.T20_EARLY_MAX_WEEK if norm in S.T20_EARLY_SUBJECTS else "",
            S.T20_LATE_MIN_WEEK if norm in S.T20_LATE_SUBJECTS else "",
            _bool_si(norm in S.PRIMA_DELLO_STAGE_SUBJECTS),
            "",                                               # dopoLoStage: per-(subject,class), no clean source yet
            ORIGINE_SISTEMA,
            "" if norm not in S.M21_EXEMPT_SUBJECTS else "M21-exempt (capOreGiorno=0)",
        ])
    _style_body(ws)


def sheet_09_alias(wb: Workbook) -> None:
    ws = _sheet(wb, "09_MaterieAlias", ["grafia", "materia", "origine", "note"])
    for grafia, canonico in S.SUBJECT_CASE_NORMALIZATION.items():
        ws.append([grafia, canonico, ORIGINE_SISTEMA, "case-duplicate normalization"])
    _style_body(ws)


def sheet_10_classi(wb: Workbook) -> None:
    ws = _sheet(wb, "10_Classi",
                ["classe", "codiceCorso", "iscritti", "dataInizio", "maxOreGiorno",
                 "oreAnnueAttese", "profDuranteStage", "pianoGiorniFine", "esenteRiempimento",
                 "tesinaDopoStage", "origine", "note"])
    ore_annue = S.ore_annue_attese()
    for g in S.load_groups():
        name = g["group"]
        ws.append([
            name,
            g["courseCode"],
            g["enrolled"],
            "",                                              # dataInizio: blank = inizio anno
            S.MAX_HOURS_PER_DAY.get(name, ""),
            ore_annue.get(name, ""),
            _bool_si(name not in S.NO_PROF_DURING_STAGE),
            _bool_si(name in S.PIANO_GIORNI_FINE_GROUPS),
            _bool_si(name in S.ESENTE_RIEMPIMENTO_GROUPS),
            _bool_si(name in S.TESINA_DOPO_STAGE_GROUPS),
            ORIGINE_SISTEMA,
            "",
        ])
    _style_body(ws)


def sheet_11_calendario(wb: Workbook) -> None:
    ws = _sheet(wb, "11_ClassiCalendario",
                ["classe", "giorno", "aperto", "slotMin", "slotMax", "origine", "note"])
    for group, *cells in S.GROUP_CALENDAR_PATTERNS:
        for giorno, cell in zip(S.CALENDAR_WEEKDAYS, cells):
            if cell is None:
                ws.append([group, giorno, "NO", "", "", ORIGINE_SISTEMA, "giorno chiuso"])
            else:
                lo, hi = cell.split("-")
                ws.append([group, giorno, "SI", int(lo), int(hi), ORIGINE_SISTEMA, ""])
    _style_body(ws)


def sheet_12_incarichi(wb: Workbook) -> None:
    ws = _sheet(wb, "12_Incarichi",
                ["docente", "materia", "classe", "ore", "tipo", "blocco", "legataQuotaLab",
                 "coDocente", "oreFuoriGriglia", "origine", "note"])
    for row in S.load_incarichi():
        if row["teacher"] in S.PSEUDO_COURSE_TEACHERS:
            continue  # Stage->16, Esame/RA->tipo, Totale ore corso->10_Classi
        ws.append([
            row["teacher"], row["subject"], row["group"], row["hours"],
            "ordinario", "", "NO", "", "",
            ORIGINE_SCUOLA, "",
        ])
    # req2: a formula-driven total row. =SUM over the ore column (D) so a school
    # edit to any ore cell flows into the total, and a total replaced by a hand-
    # typed literal is caught by V25. The label sits in the docente column; the
    # classe cell is blank so V24 (canonical-class) and the 99 SUMIFs skip it.
    last_data_row = ws.max_row
    ws.append([WS.TOTAL_ROW_LABEL, "", "", f"=SUM(D2:D{last_data_row})",
               "", "", "", "", "", ORIGINE_SISTEMA, ""])
    _style_body(ws)


def sheet_13_piano_pattern(wb: Workbook) -> None:
    ws = _sheet(wb, "13_PianoGiorniPattern",
                ["classe", "validoDa", "validoA", "giorno", "oreAula", "oreLab", "oreStage", "origine", "note"])
    # Weekly pattern per period, extracted from the per-date quota (Tappa 4). A row
    # says "in [validoDa, validoA], on weekday `giorno`, the day-plan is (oreAula,
    # oreLab, oreStage)". Periods split only where the plan changes and merge across
    # closure/no-session gaps; single-date breaks are 14, not here. The byte-identity
    # + honesty + residual gate is test_piano_giorni_roundtrip.
    patterns, _exc, _off, _metrics = P.compress()
    for p in patterns:
        ws.append([p["classe"], p["validoDa"], p["validoA"], p["giorno"],
                   p["oreAula"], p["oreLab"], p["oreStage"], p["origine"], p["note"]])
    _style_body(ws)


def sheet_14_piano_eccezioni(wb: Workbook) -> None:
    ws = _sheet(wb, "14_PianoGiorniEccezioni",
                ["classe", "data", "oreAula", "oreLab", "oreStage", "motivo", "origine", "note"])
    # The dated breaks of the pattern, each with a mandatory motivo (Tappa 4). A
    # (0, 0, 0) row with motivo is the explicit "non in sessione" declaration that
    # replaces the loader's ambiguous "no row = no session" (H26/V16).
    _patterns, exceptions, _off, _metrics = P.compress()
    for e in exceptions:
        ws.append([e["classe"], e["data"], e["oreAula"], e["oreLab"], e["oreStage"],
                   e["motivo"], e["origine"], e["note"]])
    _style_body(ws)


def sheet_15_piano_generato(wb: Workbook) -> None:
    ws = _sheet(wb, "15_PianoGiorniGenerato",
                ["classe", "data", "oreAula", "oreLab", "oreStage", "origine", "regola", "note"])
    # System-generated per-date expansion (Tappa 4): one row per in-grid school day,
    # from 13 + 14. origine = pattern | eccezione; regola names the producing row.
    # Off-grid hours (Saturdays + closures, all stage) are NOT here (Saturday is no
    # pattern weekday): declared as oreFuoriGriglia in 99 (GAP-2), never dropped.
    patterns, exceptions, _off, _metrics = P.compress()
    expanded = P.expand(patterns, exceptions)
    for (classe, date), (aula, lab, stage, origine, regola) in sorted(expanded.items()):
        ws.append([classe, date.isoformat(), aula, lab, stage, origine, regola, ""])
    _style_body(ws)


def sheet_16_stage(wb: Workbook) -> None:
    ws = _sheet(wb, "16_Stage",
                ["classe", "inizio", "fine", "giorniStage", "giorniRientro", "oreGiorno",
                 "oreTotaliAttese", "oreFuoriGriglia", "origine", "note"])
    for classe, inizio, fine, giorni, rientro, totali in S.STAGE_PATTERNS:
        ws.append([classe, inizio, fine, giorni, rientro, "", totali, "", ORIGINE_SCUOLA,
                   "oreGiorno da confermare scuola"])
    _style_body(ws)


def sheet_17_aule(wb: Workbook) -> None:
    ws = _sheet(wb, "17_Aule", ["aula", "capienza", "tag", "utilizzabile", "origine", "note"])
    for room in S.load_rooms():
        name = room["room"]
        tag = S.ROOM_TAG_MAP.get(name, "")
        zero_cap = room["capacity"] == 0
        ws.append([
            name, room["capacity"], tag, "SI", ORIGINE_SCUOLA,
            "capienza zero: segnalare alla scuola" if zero_cap else "",
        ])
    _style_body(ws)


def sheet_18_rotazioni(wb: Workbook) -> None:
    ws = _sheet(wb, "18_RotazioniLab",
                ["docente", "classe", "materia", "giorno", "slotInizio", "blocco",
                 "periodoDa", "periodoA", "tagAula", "origine", "note"])
    _gap_marker(ws, "18_RotazioniLab: vuoto by design nel 2026/27 (i pin lab arrivano "
                    "dall'import del calendario MODA + stage, non da rotazioni dichiarate).")
    _style_body(ws)


def sheet_19_doppio(wb: Workbook) -> None:
    ws = _sheet(wb, "19_Doppio", ["docente", "tagAula", "periodoDa", "periodoA", "origine", "note"])
    _gap_marker(ws, "19_Doppio: assistenti LabAssistant. Il meccanismo che li popola e' "
                    "MORTO (audit C4, famiglia M-LA non scatta mai). Da decidere se il "
                    "meccanismo deve vivere prima di chiedere i dati (gap G6 proposta).")
    _style_body(ws)


def sheet_20_professionalizzanti(wb: Workbook) -> None:
    ws = _sheet(wb, "20_GiorniProfessionalizzanti",
                ["classe", "giorno", "descrizione", "origine", "note"])
    for classe, giorno in S.PROFESSIONALIZZANTI_DAYS:
        ws.append([classe, giorno, "Giornata professionalizzante", ORIGINE_SISTEMA, ""])
    _style_body(ws)


def sheet_21_cadenza(wb: Workbook) -> None:
    """TASK-445/441: weekly cadence per (classe, materia) as DECLARED INPUT (condizionale).

    Derived from golden-cadence-reference.json (the M34/M35 reference) by
    :mod:`cadence_reference`, proposed as sistema default. In a no-golden year the JSON
    disappears and M34/M35 self-gate inert; this sheet is the declared-input survival path
    (school confirms -> origine=scuola -> caps stay active). Conditional: leave sistema/blank
    to keep M34/M35 inert. Gate: test_cadence_identity. Sort: classe then oreSettimanaMax
    desc so the high-burst pairs (where the M34 cap binds) bubble to the top of each class.
    """
    ws = _sheet(wb, "21_CadenzaAttesa",
                ["classe", "materia", "oreSettimanaMax", "settimaneAttive", "origine", "note"])
    rows = CREF.cadence_pairs()
    classe_order = {g["group"]: i for i, g in enumerate(S.load_groups())}
    rows.sort(key=lambda r: (classe_order.get(r["classe"], 999),
                             -r["oreSettimanaMax"], r["materia"]))
    for r in rows:
        if r["oreSettimanaMax"] >= CREF.HIGH_MAX_THRESHOLD:
            note = (f"golden max alto ({r['oreSettimanaMax']}h): dichiara per attivare il cap "
                    f"M34 su questa materia (condizionale)")
        else:
            note = ""
        ws.append([r["classe"], r["materia"], r["oreSettimanaMax"], r["settimaneAttive"],
                   ORIGINE_SISTEMA, note])
    _style_body(ws)


def sheet_22_teoria_blocco(wb: Workbook) -> None:
    """TASK-471: theory-block policy per (classe, materia) as DECLARED INPUT.

    Derived live from the golden (golden-runtime read, the oracle that reveals the
    school's practice per TASK-466) by :mod:`theory_block_policy`, proposed as
    ``origine=sistema`` for referente to confirm or correct into ``scuola``. The
    derivation is pinned to the Fedora-verified draft by
    :mod:`test_theory_block_identity`. This is INPUT, not adoption: M-TR
    (TASK-468) stays flag OFF and the builder honoring a declared 1h policy is a
    later tappa. Sort puts each class's decision rows (2h ammessa, then mista) on
    top so referente meets what needs his call before the trivial 1h/giorno defaults.
    """
    ws = _sheet(wb, "22_TeoriaBlocco",
                ["classe", "materia", "oreTeoria", "giorniAttivi", "giorniConBlocco",
                 "mediaOreGiorno", "bloccoTeoria", "origine", "note"])
    rows = TBP.theory_block_policy()
    classe_order = {g["group"]: i for i, g in enumerate(S.load_groups())}
    rows.sort(key=lambda r: TBP.sort_key(r, classe_order))
    for r in rows:
        if r["bloccoTeoria"] == TBP.BLOCCO:
            note = "golden usa blocchi 2h+: confermi blocco intenzionale (non fill-forzato)?"
        elif r["bloccoTeoria"] == TBP.MISTA:
            pct = 100.0 * r["giorniConBlocco"] / r["giorniAttivi"]
            note = (f"golden {pct:.0f}% giorni con 2h: confermi 1h/giorno preferito con "
                    f"eccezioni, o blocco libero?")
        else:
            note = ""
        ws.append([r["classe"], r["materia"], r["oreTeoria"], r["giorniAttivi"],
                   r["giorniConBlocco"], f"{r['avg']:.2f}", r["bloccoTeoria"],
                   ORIGINE_SISTEMA, note])
    _style_body(ws)


def sheet_23_giorno_fisso(wb: Workbook) -> None:
    """TASK golden-mining 15/08: fixed-weekday practice per (classe, materia) as DECLARED INPUT.

    Derived live from the golden by :mod:`fixed_weekday_policy` (the 40 GENUINELY NEW
    pairs of the mining split: fixed on one weekday AND not implied by the 21_CadenzaAttesa
    cadence, which already declares the implied ones). Proposed as ``origine=sistema``
    for referente to confirm into ``scuola`` or reject: the note asks the intent question.
    This is INPUT deposition (direttiva accelerazione autonomia), not adoption: no
    constraint reads this sheet today; the M32-like weekday-deviation form is a later
    tappa behind its own gate.
    """
    ws = _sheet(wb, "23_GiornoFisso",
                ["classe", "materia", "giorno", "ore", "giorniOsservati", "dominanza",
                 "origine", "note"])
    rows = FWP.fixed_weekday_pairs()
    classe_order = {g["group"]: i for i, g in enumerate(S.load_groups())}
    rows.sort(key=lambda r: FWP.sort_key(r, classe_order))
    for r in rows:
        if r["dominanza"] >= 1.0:
            note = "golden usa SEMPRE questo giorno: confermi giorno fisso intenzionale?"
        else:
            pct = 100.0 * r["dominanza"]
            note = (f"golden {pct:.0f}% delle ore di questo giorno: confermi giorno fisso "
                    f"con eccezioni, o giorno libero?")
        ws.append([r["classe"], r["materia"], r["giorno"], r["ore"],
                   r["giorniOsservati"], f"{r['dominanza']:.2f}", ORIGINE_SISTEMA, note])
    _style_body(ws)


def sheet_24_concentrazione(wb: Workbook) -> None:
    """24_ConcentrazioneMensile: strong monthly concentrations as declared input.

    Golden mining round 2 (section B, CORRECTED baseline: the first pass counted
    stage-day hours as aula+lab capacity). The STRONG set only (>= 4x the
    calendar-expected hours in that month AND >= 25% of the pair's year there):
    the 2x mandate threshold fires on multinomial noise and is deliberately not
    deposited. ``origine=sistema``: referente confirms into ``scuola`` (chosen) or
    rejects (contingency the solver recreates on its own when capacity forces
    it). INPUT deposition, not adoption: nothing reads this sheet today.
    """
    ws = _sheet(wb, "24_ConcentrazioneMensile",
                ["classe", "materia", "mese", "oreNelMese", "oreAtteseNelMese",
                 "rapporto", "oreAnno", "quotaAnno", "origine", "note"])
    rows = CP.concentration_pairs()
    classe_order = {g["group"]: i for i, g in enumerate(S.load_groups())}
    rows.sort(key=lambda r: CP.sort_key(r, classe_order))
    for r in rows:
        note = (f"golden concentra il {100 * r['quotaAnno']:.0f}% dell'anno in questo mese"
                f" ({r['rapporto']:.1f}x l'atteso del calendario): scelta o contingenza"
                f" del recupero? Se contingenza, non si dichiara.")
        ws.append([r["classe"], r["materia"], r["mese"], r["oreNelMese"],
                   round(r["oreAttese"]), f"{r['rapporto']:.1f}", r["oreAnno"],
                   f"{r['quotaAnno']:.2f}", ORIGINE_SISTEMA, note])
    _style_body(ws)


def sheet_25_weekday_fill(wb: Workbook) -> None:
    """25_WeekdayFill: (classe, weekday) cells the golden under-fills vs its own calendar.

    Golden mining round 2 section D, CORRECTED numbers (the first pass was an artifact
    of the buggy baseline: stage-day hours counted as expected aula+lab made stage-group
    Fridays look under-filled; with the correct semantics those cells are 95-100%). The
    surviving 4 cells are all G04 Mon-Thu - the per-weekday face of the 835-vs-750
    question already asked on 14/08. ``origine=sistema``: referente says whether the
    calendar overstates those days (fix the sheet) or the school keeps them short on
    purpose (declare the fill). INPUT deposition, not adoption.
    """
    ws = _sheet(wb, "25_WeekdayFill",
                ["classe", "weekday", "oreOsservate", "oreAttese", "fill",
                 "giorniAttesi", "origine", "note"])
    rows = WFP.underfill_cells()
    classe_order = {g["group"]: i for i, g in enumerate(S.load_groups())}
    rows.sort(key=lambda r: WFP.sort_key(r, classe_order))
    for r in rows:
        note = (f"l'orario riempie il {100 * r['fill']:.0f}% delle ore che il vostro"
                f" calendario prevede in questo weekday ({r['giorniAttesi']} giorni):"
                f" il calendario sovrastima, o la scuola lascia corto per scelta?")
        ws.append([r["classe"], r["weekday"], r["oreOsservate"], r["oreAttese"],
                   f"{r['fill']:.2f}", r["giorniAttesi"], ORIGINE_SISTEMA, note])
    _style_body(ws)


def sheet_99_validazione(wb: Workbook) -> None:
    ws = _sheet(wb, "99_Validazione", ["ID", "Controllo", "Regola", "Livello", "Caso reale"])
    checks = [
        ("V1", "Monte-ore classe", "SUM(incarichi ore per classe) + stage oreTotaliAttese = oreAnnueAttese", "ERRORE", "G14 120 attese vs 115 assegnate"),
        ("V2", "Incarichi contro piano-giorni", "SUM(incarichi ore per classe) = SUM(oreAula+oreLab del piano generato)", "ERRORE", "classi il cui piano non regge il monte-ore"),
        ("V3", "Stage tripla coerenza", "ore stage in piano, 16!oreTotaliAttese, 16!oreFuoriGriglia conciliano", "ERRORE", "G04 stage in griglia senza monte-ore, 16h oltre orizzonte"),
        ("V4", "Capacita' docente annua", "SUM(incarichi ore per docente) <= capacita' derivata da 06 al netto di 07", "ERRORE", "docente sovraccarico prima del solve"),
        ("V4b", "Saturazione capacita'", "rapporto ore/capacita' > 80%", "AVVISO", "T01 231h/288 = 80.2% (solo giovedi)"),
        ("V4c", "Capacita' per singolo giorno", "ore concentrabili in un weekday <= maxOreGiorno x occorrenze reali", "ERRORE", "domanda concentrata su un weekday che non ci sta"),
        ("V5", "Materie non mappate", "ogni incarichi/piano materia e' in 08 o 09", "ERRORE", "RA vs Recuperi ed Approfondimenti, 31 righe golden 2026/27"),
        ("V6", "Date fuori anno", "ogni data di 03/04/07/13/14/16/18 dentro [inizio,fine]", "ERRORE", "typo 2026-01-22 per 2027-01-22"),
        ("V7", "Ore contro finestra", "oreAula+oreLab+oreStage <= slotMax-slotMin+1 di 11", "ERRORE", "G14 171 giorni oltre capacita'"),
        ("V8", "Giorno chiuso con ore", "riga di 15 con ore > 0 su data di 03 o giorno aperto=NO di 11", "ERRORE", "contraddizione chiusure vs piano"),
        ("V9", "Cap giornaliero classe", "ore per (classe,data) di 15 <= maxOreGiorno di 10", "ERRORE", ""),
        ("V10", "Nomi non risolti", "ogni riferimento incrociato esiste nell'anagrafico", "ERRORE", "refusi nei nomi"),
        ("V11", "Chiavi duplicate", "(docente,materia,classe) in 12; (classe,giorno) in 11; (classe,data) in 14/15", "ERRORE", "oggi sommati con warning nessuno legge"),
        ("V12", "Blocco e residuo", "incarichi ore / blocco con resto != 0", "AVVISO", "corsi che non si chiudono in blocchi interi"),
        ("V13", "Capienza aula", "per ogni materia con tagAula, esiste aula con quel tag e capienza >= iscritti", "AVVISO", "R17/R18 capienza zero"),
        ("V14", "Rotazioni compatibili", "slotInizio+blocco dentro finestra 11 e docente disponibile in 06", "ERRORE", "rotazioni infattibili (oggi INFEASIBLE post pre-assegnazione)"),
        ("V15", "Stage e professionalizzanti", "se profDuranteStage=NO, nessun prof della classe solo in finestra stage", "AVVISO", ""),
        ("V16", "Copertura piano-giorni", "ogni giorno scolastico aperto ha riga in 15 (anche 0 ore con motivo)", "AVVISO", "distingue non-in-sessione da riga dimenticata"),
        ("V17", "Coerenza pattern", "ogni riga 15 con origine=eccezione ha motivo non vuoto in 14", "ERRORE", "impedisce diluizione silente del pattern"),
        ("V18", "Sigle univoche", "05!sigla senza duplicati fra i docenti a iniziali", "ERRORE", ""),
        ("V19", "Slot contigui", "02_Campanella numerata da 0 senza salti, orari crescenti", "ERRORE", ""),
        ("V20", "Alias e canonici disgiunti", "nessuna 09!grafia coincide con 08!materia", "ERRORE", "ciclo di normalizzazione"),
        ("V21", "Token e struttura coerenti", "se 06!regola compilata, produce esattamente le colonne strutturate", "ERRORE", "token che dice una cosa, colonne un'altra"),
        ("V22", "Regole non contraddittorie", "nessun docente ha SOLO_GIORNI e MAI_GIORNI insieme; disponibilita' non vuota", "ERRORE", "disponibilita' azzerata per distrazione"),
        ("V23", "Docenti esterni dichiarati", "ogni 05!esterno=SI ha almeno una regola in 06", "AVVISO", "esterno senza regole = sempre disponibile"),
        ("V24", "Classi canoniche (strutturale)", "ogni valore 'classe' in 04/10/11/12/13/14/15/16/20 e' nel set canonico (10_Classi); 10_Classi senza duplicati", "ERRORE", "refuso 'G14 2' vs 'G14', righe di classe fantasma"),
        ("V25", "Totali a formula (strutturale)", "il totale ore di 12 e le celle CALCOLO TOTALE di 99 sono formule (=SUM/SUMIF), non literal", "ERRORE", "totale sostituito con un numero a mano per nascondere uno scostamento"),
        ("V26", "Colonne numeriche (strutturale)", "le colonne ore/slot/capienza contengono solo numeri (non testo); le note nella colonna note dedicata", "ERRORE", "'rosy' / 'colloqui t14' finiti in una colonna St/La"),
    ]
    for c in checks:
        ws.append(c)
    # Gap dichiarato (decisione maintainer 2026-07-29): ore non ancora come righe
    # 12_Incarichi tipo=esame. Il generatore emette solo le 266 ordinarie per
    # mantenere l'identita' con i classificatori attuali; queste ore sono un buco
    # dichiarato con il suo numero accanto, non una riconciliazione che torna per finta.
    ws.append(["GAP-1", "Ore Esame/pool-RA non come righe 12 (tipo=esame|pool)",
               "Esame 124h (6 classi: G08 24, G11 24, G10 20, G12 20, G09 24, G13 12) "
               "+ pool RA 40h (G08, ore dovute alla classe senza docente in partenza). Totale 164h.",
               "GAP", "la scuola le compilera' come righe tipo=esame | pool nel workbook"])
    # Tappa 4: the piano-giorni model reconciliation, declared (not silently absorbed).
    _patterns, _exc, off_grid, metrics = P.compress()
    off_by_group = Counter(r["group"] for r in off_grid)
    off_hours = sum(r["stage"] for r in off_grid)
    ws.append(["GAP-2", "Ore stage fuori griglia (NON in 13/14/15)",
               f"{len(off_grid)} righe off-grid ({off_hours}h stage), tutte su sabati e chiusure "
               f"(no Timeslot, per gruppo: {dict(sorted(off_by_group.items()))}). Il sabato non e' "
               f"giorno-pattern (giorniSettimana=LUN-VEN): sono oreFuoriGriglia da riconciliare con "
               f"16_Stage.oreFuoriGriglia / 12_Incarichi.oreFuoriGriglia.",
               "GAP", "dichiarate qui, non nascoste in un pattern"])
    heavy = P.heavy_groups(metrics)
    ws.append(["PAT-1", "Piano-giorni come pattern: riconciliazione",
               f"{len(_patterns)} pattern (13) + {len(_exc)} eccezioni (14) spiegano tutte le "
               f"date in-grid (0 residui non spiegati, gate test_piano_giorni_roundtrip). "
               f"Segmentazione onesta (split sul cambio piano, mai allargata). Gruppi eccezioniosi "
               f"(fatto da dichiarare, non da nascondere): {heavy}.",
               "OK", "validato dal gate round-trip (+ negative test fail-loud)"])
    # req2: CALCOLO TOTALE - a per-class cross-check the school sees go red when
    # incarichi (12) and piano aula+lab (15) disagree beyond the band. Formulas,
    # not literals, so a school edit flows through and a hand-typed total is caught
    # by V25. The 9x G14 (piano 990 vs incarichi 115) would have shown ALARM here.
    lo, hi = WS.CALCOLO_TOTALE_BAND
    ws.append([WS.CALCOLO_TOTALE_MARKER,
               f"cross-check incarichi (12_Incarichi) vs piano aula+lab (15_PianoGiorniGenerato) "
               f"per classe; allarme se rapporto fuori [{lo:g}, {hi:g}]",
               "", "", ""])
    ws.append(["classe", "incarichi 12", "piano 15 (aula+lab)",
               "rapporto piano/incarichi", f"allarme (fuori [{lo:g}, {hi:g}])"])
    for g in S.load_groups():
        r = ws.max_row + 1
        ws.append([
            g["group"],
            f"=SUMIF('12_Incarichi'!$C:$C,$A{r},'12_Incarichi'!$D:$D)",
            f"=SUMIF('15_PianoGiorniGenerato'!$A:$A,$A{r},'15_PianoGiorniGenerato'!$C:$C)"
            f"+SUMIF('15_PianoGiorniGenerato'!$A:$A,$A{r},'15_PianoGiorniGenerato'!$D:$D)",
            f"=IF($B{r}=0,\"\",$C{r}/$B{r})",
            f"=IF($D{r}=\"\",\"\",IF(OR($D{r}>{hi:g},$D{r}<{lo:g}),\"ALARM\",\"ok\"))",
        ])
    _style_body(ws)
    ws.column_dimensions["C"].width = 60
    ws.column_dimensions["E"].width = 45


def build_workbook() -> Workbook:
    wb = Workbook()
    wb.remove(wb.active)  # drop the default empty sheet
    sheet_00_istruzioni(wb)
    sheet_01_anno(wb)
    sheet_02_campanella(wb)
    sheet_03_chiusure(wb)
    sheet_04_giorni_speciali(wb)
    sheet_05_docenti(wb)
    sheet_06_disponibilita(wb)
    sheet_07_eccezioni(wb)
    sheet_08_materie(wb)
    sheet_09_alias(wb)
    sheet_10_classi(wb)
    sheet_11_calendario(wb)
    sheet_12_incarichi(wb)
    sheet_13_piano_pattern(wb)
    sheet_14_piano_eccezioni(wb)
    sheet_15_piano_generato(wb)
    sheet_16_stage(wb)
    sheet_17_aule(wb)
    sheet_18_rotazioni(wb)
    sheet_19_doppio(wb)
    sheet_20_professionalizzanti(wb)
    sheet_21_cadenza(wb)
    sheet_22_teoria_blocco(wb)
    sheet_23_giorno_fisso(wb)
    sheet_24_concentrazione(wb)
    sheet_25_weekday_fill(wb)
    sheet_99_validazione(wb)
    return wb


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", default=str(OUTPUT_DIR / "AnnoScolastico_2026-27.xlsx"))
    args = ap.parse_args()
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    wb = build_workbook()
    wb.save(out)
    # Print a one-line-per-sheet row count so a human can eyeball completeness.
    print(f"workbook written: {out}")
    for ws in wb.worksheets:
        body = ws.max_row - 1
        print(f"  {ws.title:32s} {body:5d} righe")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
