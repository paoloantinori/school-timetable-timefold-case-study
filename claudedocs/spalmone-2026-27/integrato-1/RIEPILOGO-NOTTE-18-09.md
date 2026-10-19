# RIEPILOGO NOTTE 18-19/09 (laptop, mandato Fedora "andate avanti da soli tutta la notte")

## Cosa ho fatto (in ordine)

### 1. Resoconto coda interrotta (pre-migrazione)
- SWEEP PESO M-BF: nessun artifact, morto alla nascita col restart (rifatto, vedi sotto).
- INTEGRATO-5: nessuna dir/profilo, morto alla nascita col restart (rifatto, vedi sotto).
- Nessun processo java residuo. Podman machine riavviato (era giu' dal reboot).

### 2. TASK-625 "LS-less delivery" (branch wt/625-lsless)
- Banco: ciclo staged con -Dplanner.staged.skip-timeslot-ls.enabled=true, profilo fresco,
  banner verificato. Predizione scritta prima.
- **ESITO: SMENTITA MASSIVA.** Consegna [-144/0]hard con 106 lezioni non collocate (H0 -106).
  La timeslot LS e' VALORE essenziale, non erosione: chiude il gap tra template stamp e
  griglia deliverable. Wall risparmiato ~32 min/ciclo ma costo qualita' inaccettabile.
- Verdetto: flag resta prototipo opt-in, NESSUNA adozione.
- Commit bfd9a23d (push fallito per payload grande, commit locale).

### 3. TASK-626 "M32-solo prune" (branch wt/626-prune)
- A/B appaiato: M32 ON vs M32 OFF su profili freschi identici.
- **A: [-8/0]hard, M32=-140.410/912, ricorrenza 73,1%. B: [-14/0]hard, M32=0, ricorrenza 70,8%.**
- **ESITO: M32 NON SI RIMUOVE.** -6 hard e -2,3pp ricorrenza nel braccio OFF; il guadagno
  medium ~160k non li giustifica. M32 non e' solo tassa: e' GUIDA di ricerca.
- Predizione "hard invariato" SMENTITA (il census medium non vede l'effetto search-guide).
- Commit af4f5c50 pushato.
- Bracci M-LF/M15/M20family: differiti (il verdetto M32 chiude la domanda singola).

### 4. TASK-624 "replay soglie predictor" (branch wt/626-prune)
- 4 cicli: deterministico + 3 forced-GRASP seeds rappresentativi.
- **Det: [-12/0] >> GRASP: seed3 [-63], seed9 [-125], seed4 [-187].**
- **ESITO DISCRIMINATO (nota metodologica Fedora)**: h17_holes NON predice qualita'
  individuale GRASP (ordinamento non-monotono: seed3=313holes consegna -63, seed9=240holes
  consegna -125). La soglia 150 e' accidentalmente corretta a livello GRUPPO (tutti i GRASP
  >> deterministic), ma per il motivo sbagliato (il metro non c'e' entra).
- Proposta per maintainer: testare soglie DENTRO un multi-start di produzione, oppure semplificare
  il predictor a "salta tutti i GRASP" (nessun GRASP batte il deterministico).
- Commit 137525a0 pushato.

### 5. Sweep peso M-BF (rifatto, su main)
- A/B: W=20 (default) vs W=40, profili freschi.
- W=20: [-21/0]hard, M-BF -40.800/17. W=40: [-10/0]hard, M-BF -86.400/18.
- **ESITO: W=20 OTTIMALE.** Criterio -15% interleaved NON centrato (17->18, non ridotti).
  Il peso non e' il lever: gli interleaved sono strutturali (mosse non disponibili).
  Hard -10 vs -21 entro variabilita' n=1. Medium peggiore a W=40 (2x costo, 0 beneficio).
- W=80/160 non eseguiti (tempo, verdetto gia' leggibile).
- Commit 5279ea85 pushato su main.

### 6. INTEGRATO-5 (in corso)
- Ciclo produzione: profilo fresco, max-attempts=5 (default), M-BF ON a W=20.
- Lanciato ~05:30, in volo al momento della stesura.

## Cosa resta
- INTEGRATO-5: in volo, risultato al mattino.
- Bracci prune M15/M20family/M-LF-peso-500: differiti (il verdetto M32 suggerisce che
  nessun blocco medium si rimuove singolarmente senza costi hard; da rivalutare solo
  se maintainer chiede esplicitamente).
- Push wt/625-lsless (bfd9a23d): fallito per payload grande. Commit sicuro in locale.

## Domande per maintainer (per la discussione del mattino)
1. **Predictor**: visto che nessun GRASP batte il deterministico, semplificare il
   early-doom a "salta tutti i GRASP" (risparmia valutazione) o tenerlo per il giorno
   in cui un GRASP sara' competitivo?
2. **M-BF peso**: confermato 20 ottimale (sweep). Procedere con la cura non-nascita
   (TASK-615) per gli interleaved invece del peso?
3. **M32**: il verdetto "guida di ricerca" suggerisce che la pila medium va pensata
   come un sistema di indirizzamento, non come un elenco di costi: approcciare il
   task-626 sweep residuo con questa lente o chiuderlo?

## Stato Git
- main (feature/cartesian-block-entity): 5279ea85 (pushato)
- wt/626-prune: 137525a0 (pushato)
- wt/625-lsless: bfd9a23d (LOCALE, push fallito payload grande)
