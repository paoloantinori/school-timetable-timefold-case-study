# Piano d'azione: recovery dalla contaminazione JAR-worktree (12/09, maintainer)

Contesto: 17 consegne laptop esposte dal 29/08 (finestra di vita del JAR-worktree); 2 void
meccanicamente provate (control-11sep, spread-11sep: pool G08 626, zero Recuperi), 4+
pulite per propria evidenza (v12/v132/notte-4: dati SurplusHarvest; slotretry: banner
verificato + bocciatura da banco Fedora), ~10 sconosciute. Guard anti-worktree
committata (c6ae19e4); banco M39 in volo verificato-pulito. Audit tracciato in TASK-605.

## FASE 0: fatto oggi (non ripetere)

- Guard anti-worktree in prod/dev-start (PRIMA di ogni side effect; override esplicito).
- Incidente braccio A M39: rilevato (cwd + lsof + banner), corretto (cd assoluto +
  guard), banco rilanciato pulito e verificato indipendentemente da Fedora.
- Memoria launch-guard (cwd + banner a PRE-lancio) + discriminante RA a valle (pool
  G08: 626 = stantio, 667 = giusto) registrate.
- TASK-605 aperto con la classificazione fino a qui.

## FASE 1: notte e mattina (laptop, in corso, zero azioni richieste)

Il banco M39 (braccio A in volo, consegna ~19:50; braccio B con banner-gate, ~03:50) e'
anche il PRIMO CICLO PULITO end-to-end del quadro attuale. Alla consegna leggere TRE
cose dal REPORT (watchdog gia' programmato):
1. Il delta M39 OFF vs ON (lo scopo del banco: la predizione e' appaiata, l'assoluto
   H34 si ribasina sul braccio A).
2. Il primo vettore VERO del quadro ratificato completo (RA seed + pool-cap-fill +
   spread): ogni classificazione precedente va riletta contro questo.
3. La cura RA misurata end-to-end PER LA PRIMA VOLTA: verificare pool G08 = 667 e
   righe Recuperi presenti nel lessons.txt del braccio A (la discriminante, applicata
   a noi stessi).

DECISIONE (maintainer, domani): ratifica o bocciatura M39 dal REPORT. Nessuna urgenza oltre.

## FASE 2: domani (Fedora, sola lettura, nessun ciclo)

TASK-605 eseguito per intero:
1. Classificare le ~10 sconosciute con marker per-data (dati di meccanismi post-29/08
   nei REPORT/analysis: SurplusHarvest, banner M38 con valori, banner pool-cap, firme
   di formato) piu' il principio di severita' (la contaminazione voida solo dove il
   meccanismo o il quadro misurato differisce fra il 29/08 e l'HEAD dichiarato).
2. Correggere il record: REPORT e VERDICT dei 2 void ricevono un avviso in testa (i
   dati restano validi come stato, non come evidenza di quadro); le classificazioni
   di control-10sep, control-11sep e spread-11sep vengono riscritte col caveat; la
   voce CLAUDE.md di TASK-599 viene annotata (gamba gate-ciclo contaminata, banco
   locale e regola umana salde); memoria e checkpoint corretti.
3. Chiusura meccanica a valle: aggiungere al capture-doctor (579.01) il check "pool
   G08 = 667 e righe Recuperi > 0 nel lessons.txt, altrimenti DOCTOR-WARN
   JAR-STALE": la discriminante diventa un gate automatico di ogni consegna futura,
   non una verifica manuale.
4. Riscrivere la lettura varianza-bacino (-13/-17/-26) alla luce della
   classificazione (quali braccia erano stesso-codice).

## FASE 3: punti decisionali (maintainer, quando i dati ci sono)

1. Gate pulito TASK-599 (braccio spread-OFF sul quadro attuale): SOLO se l'audit
   svelasse che anche braccia di supporto erano contaminate, o se vuoi la forma
   completa. Altrimenti il banco locale, l'iter-11 e il braccio A di stanotte (spread
   ON nel quadro vero) bastano. Raccomandazione: non serve.
2. Ratifica M39: dal REPORT di Fase 1.
3. Residual ledger: nessuna azione (fixture content-addressed valide come stati);
   solo la provenance dei 2 void viene corretta in Fase 2.

## FASE 4: differito (gia' tracciato, sbloccato da Fase 1 e 2)

- TASK-596 (gate adozione): la consegna di riferimento diventa il braccio A di
  stanotte (primo vettore pulito); si legge sul vettore-famiglie come gia' deciso.
- TASK-584 (affidabilita' produzione): dati riclassificati dopo l'audit, poi parte.
- TASK-600 (banco end-state): se M39 vince, il trade H34/H32 si misura sul quadro
  vero; se perde, parkato con verdetto.

## Cosa NON fare

- Nessun re-run di massa dei cicli passati: l'audit e' sola-lettura; i verdetti si
  riclassificano dai dati esistenti.
- Nessun revert di ratifiche in Fase 0-2: l'unica candidata (TASK-599) ha le gambe
  rimanenti salde; decisione esplicita solo se l'audit sorprende.
- Nessun nuovo ciclo laptop oltre M39 finche' l'audit non e' chiuso (il quadro di
  riferimento deve essere certo prima di misurare sopra).
