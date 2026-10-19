# Verifica DataGenerator - 26 Febbraio 2026

## Sommario

Verifica comparativa tra lo Spalmone (orario validato dalla scuola) e i Conti (DataGenerator.java) per identificare discordanze nei dati.

## Script di Verifica

```bash
python3 scripts/verify-datagenerator.py
```

## Risultati Principali

### 1. Ore Totali
- **Spalmone**: 10.437h
- **Conti**: 10.846h
- **Differenza**: Conti ha 409h IN PIÙ

### 2. Docenti Mancanti nello Spalmone

| Docente | Ore nei Conti | Note |
|---------|---------------|------|
| T31 | 235h | Tutti i lab FB - completamente assente dallo Spalmone |
| TBD1 | 130h | Segnaposto (G12 + G13) |
| TBD2 | 50h | Segnaposto (G04) |
| TBD3 | 40h | Segnaposto (G08) |

### 3. Principali Discordanze

| Docente | Gruppo | Spalmone | Conti | Diff | % |
|---------|--------|----------|-------|------|---|
| T03 | G11 | 67h | 20h | +47h | +235% |
| T03 | G03 | 170h | 149h | +21h | +14% |
| T03 | G07 | 56h | 32h | +24h | +75% |
| T29 | G03 | 59h | 80h | -21h | -26% |
| T15 | G04 | 382h | 360h | +22h | +6% |

### 4. Il Problema T28 (IMPOSSIBILITÀ MATEMATICA)

T28 è un docente esterno disponibile **solo il giovedì**.

| Gruppo | Spalmone | Conti | Blocchi Necessari | Slot Disponibili | Status |
|--------|----------|-------|-------------------|------------------|--------|
| G10 | 48h | 64h | 10,7 | ~9 (Periodo 3) | **IMPOSSIBILE** |
| G12 | 36h | 48h | 8 | ~9 | Verificare |

**Analisi**: 64h ÷ 6h = 10,7 blocchi necessari, ma nel Periodo 3 (aprile-giugno) ci sono solo circa 9 giovedì disponibili. Questo rende l'assegnazione **matematicamente impossibile** con i dati attuali.

## Azioni Richieste

1. **NON MODIFICARE DataGenerator.java** senza conferma esplicita
2. Verificare con la scuola le ore corrette per:
   - T28 (G10, G12)
   - T03 (FB)
   - T31 (tutti i lab FB)
3. Risolvere i placeholder TBD1/TBD2/TBD3

## Modifiche al Codice

### GreedyRotationAssigner.java
Aggiunta analisi diagnostica nei messaggi di errore:
- Analisi capacità: blocchi necessari vs slot disponibili per docente
- Rilevamento vincoli di periodo (festività, giorni disponibili)
- Suggerimenti azionabili per risolvere i problemi

### Nuovo Script: verify-datagenerator.py
Script Python che:
- Parsing di DataGenerator.java per estrarre le ore dei corsi
- Parsing di spalmone.csv per estrarre le lezioni
- Confronto incrociato docente/gruppo/materia
- Rilevamento impossibilità matematiche

## Collegamenti

- Task correlati: task-124 (verifica), task-125 (reporting)
- Script: `scripts/verify-datagenerator.py`
- Dati: `scripts/data/spalmone.csv`
