# CRITERI pre-registrati: A/B di ratifica M-BF a ciclo completo (17/09 sera)

Scritti PRIMA del primo lancio. Emendabili solo con dichiarazione esplicita
nel VERDICT (fede al protocollo predizione-prima).

Setup: due cicli freschi completi su wt/mbf-proto (main e83c49d2 + prototipo
default-OFF), solve-staged di produzione, un solve per volta.
- BRACCIO A (controllo): catena di produzione, M-BF OFF (default).
- BRACCIO B (trattamento): identico A, -Dplanner.constraints.mbf.enabled=true
  sulla riga java -jar (PLANNER_JAVA_OPTS, regola MAVEN_OPTS).
Appaiamento: profili freschi entrambi, attempt-1 deterministico identico.
Ordine: A poi B (scelta laptop: il controllo prima fissa la base della serie).
Banner-check obbligatorio per entrambi: "M-BF ... armed=false" (A) /
"armed=true" (B) nel log di boot.

## Criteri (Fedora, registrati nel mandato)

a) HARD CONSEGNA: B non peggiore di A (0-hard binario; A=0 e B=0 pari;
   se A>0 allora B <= A).
b) RICORRENZA (il decisore): B >= 69 (soglia REVISIT: sotto 69 la forma del
   peso e' il problema; si va di sweep peso, NON di direzione).
c) FULL-DAY: B in banda 91-95.
d) INTERLEAVED multi-docente a consegna: B < A di ALMENO 20 giorni (l'effetto
   del banco dev' arrivare vivo alla consegna; metrica:
   scripts/analyze/lab-block-form-kpi.py sulle due lessons.txt).
e) SANITY: nessuna cella rossa NUOVA in B oltre la classe giorni-corti nota
   (coda anno / H33-scope / buco interno isolato).

EXTRA braccio B: e' anche il n=4 della serie full-chain (-4, -1, 0, B).

## Predizioni registrate PRIMA dei lanci

- Fedora: A atteso [-4,0] (serie), B pari ad A sull'hard con interleaved
  decisamente sotto e ricorrenza nel dubbio 69-73 (M-BF combatte S13; se
  B < 69 il criterio b boccia e si apre lo sweep peso).
- laptop (aggiunta): A converge come integrato-3 (0-hard probabile al primo
  tentativo, bacino deterministico buono); B idem sull'hard. Ricorrenza A
  attesa 70-73, B attesa 70-72 (il template M32/S13 vivo protegge molto piu'
  dell'harness; il -6.3pp del banco era artefatto della cecita' al template).
  Interleaved: A ~54-60, B < 34 (criterio d con margine).
