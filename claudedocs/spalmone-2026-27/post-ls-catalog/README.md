# Catalogo stati post-LS (TASK-634)

Direttiva maintainer 20/09: "agiamo solo sulle ultime fasi della pipeline; serve davvero
ricominciare da capo ogni volta? non possiamo tenerci un catalogo di soluzioni post-LS
su cui iterare?" Estensione di ITERAZIONE SU SNAPSHOT (6/09).

## Cosa c'e' dentro

`index.json`: le voci. Ogni voce punta a payload che RESTANO dove sono (gli export
da ~107MB non si committano: GH001): path repo-relativo + sha256, quindi il puntatore
e' verificabile. Strumento: `scripts/analyze/post_ls_catalog.py` (add / status / list).

## Le tre regole non negoziabili

1. **Hash del seed USATO dal run** (`seedHash` + `seedCommit`): per i run storici si
   passa `--seed-commit <sha>` e l'hash si calcola dal commit; il default (working
   tree) vale SOLO per run fatti col seed corrente. La guardia `status` marca STALE-SEED
   le voci il cui seed diverge: replayarle contro le quote correnti produce famiglie
   fantasma (la lezione H25 del 20/09: export del 14/09 contro la cura del 18/09
   generava H25x4 inesistente).
2. **Room-faithful e' la gradazione da replay**: una voce lessons-only serve a puzzle
   e KPI, NON al replay della fase di consegna (lezione 632b: la pipe senza stanze
   uccide ogni gate di stanza).
3. **Retaining per razza di bacino, rinfrescato dai cicli nuovi**: mai un catalogo
   fisso (anti-overfit, lezione 628: i bacini differiscono e le famiglie migrano).

## Protocollo

- Ogni ciclo del wrapper cattura per tentativo: export (se possibile) + lessons.txt,
  e una voce con commit codice, seedCommit, attempt, hardScore dallo score.txt.
- Prima di ogni misura su uno stato post-LS: `post_ls_catalog.py status` e si usa
  solo cio' che e' VALID (o STALE dichiarato per lo scopo esplicito).
- Il primo popolamento (20/09) prova la guardia da solo: i tre run pre-cura-18/09
  (m39-off, m39-on, integrato-1) risultano STALE-SEED, integrato-5 VALID.

## Payload oltre il limite git (regola 21/09)

Gli export.json.gz oltre 100MB non entrano nel repo (GitHub li rifiuta: l'integrato-6 pesa 106MB; anche i m39-off/on sono per questo untracked). Per quelle voci il campo exportJsonGz e' il percorso DOVE IL FILE VIVE sulla macchina che ha prodotto il ciclo (il laptop), non un puntatore garantito nel repo: il replay usa -Dtrio.export=<percorso locale> e la guardia seed resta valida perche' legge l'hash dal catalogo, non dal file. Il file integrato-6 e' stato recuperato via scp su Fedora per il replay del 21/09 (presente, untracked).
