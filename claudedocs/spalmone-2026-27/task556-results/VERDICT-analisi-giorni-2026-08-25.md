# TASK-556 VERDICT di analisi (notte 25/08, PRIMA di qualunque codice)

## Domanda
"I giorni pieni del golden li vogliamo anche noi" (maintainer 22:35). Il gap giorni misurato
(ciclo 0hard: M20.2 -493k n=49, M22 -416k n=14 partite / 415 giorni-eccesso) e' reale o
del metro?

## Metodi
Export della consegna 0hard (ciclo-finisher-on) con quote autorevoli dal DB (righe
DailyActivityQuota nell'export); semantica letta DAL CODICE dei vincoli (M20.2:
ifNotExists qualunque lezione incluse pin e Stage; H32: floor con tolleranza zero;
M22: min-days = ceil(ore/maxHoursPerDay), esente-pinned); controprova M22 sulla griglia
del golden (lessons.csv, conteggio unpinned-equivalente).

## Risultati
1. **M22 e' FINZIONE STRUTTURALE, non un gap.** Il golden stesso, se non fosse pinnato,
   pagherebbe 262 giorni-eccesso (noi 240: siamo MIGLIORI di lui su questo asse). La
   scuola pratica giorni sottili dichiarati dal foglio (5h su ~130-172 giorni); la
   formula min-days assume packing a maxHoursPerDay. Qualunque soluzione fedele al
   foglio paga M22. Il -416k non e' recuperabile "consolidando giorni" senza rompere
   H32 (floor per giorno, tolleranza zero: svuotare un giorno dichiarato e' hard-violante).
2. **M20.2 si scompone in 49 celle: 28 FC/G14 + 21 reali.** Le 28 sono le righe quota
   che over-declarano rispetto al pool-cap forma-b (TASK-497: by design, domanda referente
   pendente, ~-293k). Le 21 (G13 5, G06 5, G09 5, G11 3, G07 2, G12 1,
   apr-giu, ~92h) sono giorni dichiarati vuoti nella nostra consegna (~-200k).
3. **Conservazione perfetta** (placed == sumQuota per gruppo, delta +21/-4): le ore NON
   esistono altrove. Riempire le 21 celle puo' attingere SOLO dal surplus sopra-quota
   (160h globali, per-gruppo stretti: G13 26 vs 25 serve, G06 19 vs 25, G09 22 vs
   25). H32 floor vieta di attingere dai giorni a quota. QuotaFillRepair GIA' usa esattamente
   quei donatori: il residuo 21 e' quanto i donatori + H1 + VRP non riescono a coprire.

## Verdetto
Il "gap giorni ~0,9M" e' per ~90% non-actionable: -416k finzione M22 (il golden e' peggio
di noi), -293k over-declare FC/G14 (by design). L'actionable reale e' ~-200k di cui
stimo chiudibile circa meta' (le celle con surplus per-gruppo sufficiente: G13 in
particolare), migliorando QuotaFillRepair nell'ordine dei donatori/scelta mosse, NON con
un DayConsolidation nuovo (la sua premessa M22 e' caduta).

## Raccomandazione a maintainer
1. La decisione "vogliamo i giorni pieni" e' GIA' soddisfatta dalla consegna attuale
   tranne le 21 celle: la percezione visiva era guidata dai giorni-corti FC/G13/MODA
   che sono la forma del foglio (e dalle 28 over-declare).
2. Se si vuole l'ultimo pezzo: sprint QFR (donor ordering + riprove), atteso -100..-150k,
   poi il residuo e' matematicamente chiuso (H32 + conservazione).
3. M22 andrebbe RICALIBRATO (min-days quota-aware: ceil(ore/media-quota-dichiarata)) o
   dichiarato finzione nel registro: oggi penalizza anche il golden. Da proporre come
   emendamento al metro (NON toccato stanotte: la macchina misura, non adotta).
4. Le domande referente (G12 voluti/subiti, RA back-load, giorni corti FC) restano la
   via per sapere se il foglio stesso vada cambiato.

## Nota processo
Analisi tutta da export + codice, nessun run, nessun DB (il volume dci-550rtf non e'
ripartito per un problema di label SELinux sul postmaster.pid: da curare, non bloccante
perche' l'export e' autosufficiente).
