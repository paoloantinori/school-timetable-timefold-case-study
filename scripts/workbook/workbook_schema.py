"""Structural schema of the input workbook, shared by generator + validator.

The seed (``sources.py``) is the school's DATA. This module is the workbook's
SHAPE: which columns must hold a number and which sheets carry a ``classe``
column to validate against the canonical group set. It is the single source of
truth for both:

- ``generate_workbook._style_body`` attaches an openpyxl ``DataValidation``
  (whole 0-1000) to every numeric column, so the school cannot type text into an
  hours slot in the first place (req4, prevention layer).
- ``validazione.v26_colonne_numeriche`` checks the COMPILED workbook keeps those
  columns numeric (req4, detection layer; catches 'rosy' / 'colloqui
  t14' typed into a St/La column, the real 2026/27 failure).
- ``validazione.v24_classi_canoniche`` validates the ``classe`` column of every
  class-bearing sheet against the canonical group set (req1; catches 'G14 2'
  vs 'G14' and phantom-class rows).

Header-NAME driven (not index driven): a column reorder in a sheet function can
never silently retarget the validation, the same robustness rule
``generate_workbook._style_body`` already follows for the origine tint.
"""

from __future__ import annotations

# Columns that must hold a NUMBER when non-empty, per sheet title. Blank is
# allowed (empty cell = not applicable / default, the proposal's convention); a
# non-empty cell that is text is the defect. Scope is the columns where a textual
# typo is a real break: hours, slots, capacity, counts. 'blocco' (12_Incarichi)
# is intentionally OUT: blank-by-default and the solver defaults it, so flagging
# it would noise the gate without catching a real failure mode. 22_TeoriaBlocco is
# intentionally OUT too: its numeric columns (oreTeoria, giorni*) are sistema-
# derived read-only evidence (the school edits the bloccoTeoria string, not the
# numbers), and mediaOreGiorno is a decimal the whole-0-1000 DataValidation cannot
# express. (Unlike 13/14/15 whose sistema-derived oreAula/oreLab/oreStage are all
# whole hours; 22 mixes in a float.)
NUMERIC_COLUMNS: dict[str, list[str]] = {
    "02_Campanella": ["slot"],
    "04_GiorniSpeciali": ["slotMin", "slotMax"],
    "05_Docenti": ["maxOreGiorno"],
    "06_DocentiDisponibilita": ["slotMin", "slotMax", "valore"],
    "07_DocentiEccezioni": ["slotMin", "slotMax"],
    "08_Materie": ["capOreGiorno", "settimanaInizioMax", "settimanaInizioMin"],
    "10_Classi": ["iscritti", "maxOreGiorno", "oreAnnueAttese"],
    "11_ClassiCalendario": ["slotMin", "slotMax"],
    "12_Incarichi": ["ore", "oreFuoriGriglia"],
    "13_PianoGiorniPattern": ["oreAula", "oreLab", "oreStage"],
    "14_PianoGiorniEccezioni": ["oreAula", "oreLab", "oreStage"],
    "15_PianoGiorniGenerato": ["oreAula", "oreLab", "oreStage"],
    "16_Stage": ["oreGiorno", "oreTotaliAttese", "oreFuoriGriglia"],
    "17_Aule": ["capienza"],
    "21_CadenzaAttesa": ["oreSettimanaMax", "settimaneAttive"],
}

# Sheets whose 'classe' column must reference a canonical group (10_Classi is the
# anagraphic; the others reference it). req1 validates each value here against the
# groups.csv set; 10_Classi is also checked for duplicate class rows.
CLASS_SHEETS: list[str] = [
    "04_GiorniSpeciali",
    "10_Classi",
    "11_ClassiCalendario",
    "12_Incarichi",
    "13_PianoGiorniPattern",
    "14_PianoGiorniEccezioni",
    "15_PianoGiorniGenerato",
    "16_Stage",
    "20_GiorniProfessionalizzanti",
    "22_TeoriaBlocco",
    "21_CadenzaAttesa",
    # TASK golden-mining 15/08: classe-bearing like 21/22 (references 10_Classi).
    # NUMERIC_COLUMNS intentionally OUT, same rationale as 22_TeoriaBlocco: ore and
    # giorniOsservati are sistema-derived read-only evidence and dominanza is a
    # 2-decimal float the whole-0-1000 DataValidation cannot express; the school
    # edits the giorno/origine strings, not the numbers.
    "23_GiornoFisso",
]

# req2 markers shared by generate_workbook (emits) and validazione.v25 (locates),
# so the validator finds the totals by label without a fragile row index.
# - TOTAL_ROW_LABEL: the 12_Incarichi summary row (label in the docente column).
# - CALCOLO_TOTALE_MARKER: the 99_Validazione cross-check section title cell.
# - CALCOLO_TOTALE_BAND: alarm when the piano/incarichi ratio leaves this band.
TOTAL_ROW_LABEL = "Totale ORE"
CALCOLO_TOTALE_MARKER = "CALCOLO TOTALE"
CALCOLO_TOTALE_BAND = (0.8, 1.2)

