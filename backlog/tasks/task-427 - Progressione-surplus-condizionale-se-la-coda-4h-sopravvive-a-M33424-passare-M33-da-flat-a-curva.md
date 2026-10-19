---
id: TASK-427
title: >-
  Progressione-surplus (condizionale): se la coda +4h sopravvive a M33+424,
  passare M33 da flat a curva
status: Done
assignee: []
created_date: '2026-07-27 04:45'
updated_date: '2026-07-27 15:39'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: low
ordinal: 250000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TRIGGER: dopo l'adozione di TASK-424, rimisurare l'istogramma profondita' surplus sui run nuovi (script inline documentato in TASK-423 note). Se la coda +3/+4h resta ~10x il golden (golden {1:41,2:25,3:3,4:1}; pre-424 noi {1:87-93,2:9-15,3:5,4:10-11}), M33 passa dal flat 100/h a una curva progressiva (stessa logica di m20DeficitCost, pesi da benchmark). Se la coda crolla con 424, chiudere senza azione.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
RISOLTO non-triggerato (2026-07-27). Post-424, il surplus e' STABILE e leggero: M33 -12.200/73 match (run1 m-12) = ~122 ore-excess su 73 giorni (avg 1,67h, comparable al train2 -11.200/69). La coda profonda +3/+4h NON e' esplosa (avg basso = maggioranza +1/+2h). Il trigger di 427 ('se la coda resta ~10x golden -> M33 da flat a curva') non si attiva: M33 flat 100/h resta appropriato. CHIUSO senza azione (come da spec 'se la coda crolla con 424, chiudere').

RIAPERTO da verifica critica Fedora (2026-07-27 ~13:00): la chiusura usava la base M33 (media eccesso 1,67h/giorno) ma il TRIGGER della spec e' l'istogramma RAW vs golden (script inline in TASK-423 note). Misura raw su m12-run1: {+1:50, +2:9, +3:2, +4:12} vs golden {+1:41, +2:25, +3:3, +4:1} - la coda +4h e' 12x il golden, INVARIATA rispetto a pre-424 (10-11): il trigger E' scattato. PERO' prima della decisione flat->curva serve l'ANATOMIA dei 12 giorni +4: se sono giorni dove i PIN da soli superano il foglio (tensione due-fogli FC/import calendario), sono artefatti dati non comportamento solver, e la curva M33 non li toccherebbe (M33 vede solo l'eccesso unpinned sopra la quota netta - coerente con la media bassa misurata da zai). Anatomia richiesta: per ciascuno dei 12 giorni, composizione pin/unpinned e quota raw vs netta; se >=10/12 sono pin-driven -> chiudere di nuovo senza azione CON questa motivazione; se unpinned-driven -> attivare la progressione. Entrambe le misure (raw mia, M33 zai) sono corrette sulle rispettive basi: la spec citava la raw.

CHIUSO DEFINITIVO con anatomia (Fedora 2026-07-27 ~13:30): gli 11 giovedi' G10 a +4h sono TUTTI lo stesso meccanismo - T01 (esterna, H5 la vincola al solo giovedi') insegna Servizio tecnico e stilistico slots 12-15 SOPRA le 4h mattutine del foglio; il 12o giorno e' G12 ultimo giorno d'anno. E' surplus FORZATO da vincolo hard (l'alternativa e' H0 unassigned): nessuna curva M33 lo muoverebbe, la penalita' aggiuntiva sarebbe solo rumore di score. La progressione flat->curva NON serve. Collegamento: e' la discrepanza NOTA di datagenerator-protection.md (monte-ore T28/T01 244 vs 192 CSV, '11 blocchi in 9 slot IMPOSSIBLE') -> domanda scuola, aggiunta a TASK-428. Entrambe le chiusure precedenti avevano un pezzo: zai (media bassa = M33 sano) + trigger raw scattato (Fedora) - la sintesi e' che il trigger scattava per ore constraint-forced fuori dalla portata di M33.

CORREZIONE della chiusura (inferenza golden, ~14:10): la lettura 'constraint-forced' era SBAGLIATA - il golden colloca le stesse 65h di T01 su 9 giovedi' MONOGRAFICI dic-giu (5-8h/giorno) che il FOGLIO stesso prevede a 5/7/8h: surplus zero, fattibile. Il nostro surplus +4h sugli 11 giovedi' autunnali e' MISALLOCAZIONE di costruzione (stamp cronologico esaurisce le ore prima dei giovedi' giusti), non forzatura hard. La CONCLUSIONE di 427 resta valida (la curva M33 non serve: lo score gia' preferisce la forma golden, 22.000 di deficit M20 contro 400 di M33 - il blocco e' la ricerca/costruzione), ma per la ragione giusta. Leva reale -> TASK-432.
<!-- SECTION:NOTES:END -->
