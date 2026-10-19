# PREDICTION pre-registrata (PASSO 4, 22/09, scritta PRIMA del lancio dei bracci)

Braccio A task637b-off (flag assente) vs braccio B task637b-on
(`-Dplanner.construction.theory-spread.enabled=true` sulla riga `java -jar`).
Entrambi: profilo fresco, costruzione deterministica (attempt 1, grasp off),
MT-LS ON (default), heap 8g, engine Timefold 2.7.0-rc-1, checkpoint capture ON
(default), capture-run completa. Criteri: GATE-CRITERI.md (pre-registrati 21/09).

Predizione:

1. M-TR (criterio 3): ON < OFF sulle celle teoriche 2+ same-day del consegnato.
   La diagnosi TASK-468 fissa che i blocchi 2h teoria NASCONO in costruzione
   (residuo fine-construction ~1672, quasi identico al finale); il lever agisce
   su template builder (pass-2 weekday-spread) + fill tail, quindi aspetto un
   calo VISIBILE ma non la chiusura a zero (quota-aware fill tail e M15 possono
   ancora creare il secondo blocco).
2. Hard (criterio 1): entrambi i bracci in [0 .. -3] (variabilita' race 628 su
   consegne fresh-profile). "ON mai peggiore di OFF" e' il criterio, non una
   certezza fisica: se la race morde, il criterio puo' fallire per sfortuna e
   va dichiarato come race, non nascosto.
3. Ricorrenza (criterio 2): >= 68 entrambi. Rischio dichiarato della famiglia
   599/636 (spostare placement per lo spread costa ricorrenza); scendere sotto
   68 e' il segnale che il lever paga troppo.
4. Full-day (criterio 4): >= 93 entrambi (il lever e' preferenza-mai-forza:
   non tocca cap ne' quote).
5. Banner (criterio 5): ON arma il tail (armed=true) con theoryHoldFills>0 gia'
   dal primo construction; OFF resta armed=false theoryHoldFills=0.

Esito complessivo atteso: 5/5 VERDI, rischio principale su 1 (race) e 2
(ricorrenza). Nessun flip di produzione: la decisione di adozione e' di maintainer.
