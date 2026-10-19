# TASK-650 screenshot prima/dopo (23/09 mattina, laptop, PASSO 1 coda)

Screenshot PRIMA dall'app pre-rebuild (giro integrato-7, 1ec04297 = testi
pre-unslop); DOPO dal rebuild su 7e7e15cb (be1fc8a2, ~90 stringhe unslop).
Stessi quattro tag: cima Docs, card H34, card H17, pagina Phases completa.

Verifiche meccaniche dal DOM renderizzato:
- 68 vincoli distinti (26H/31M/11S), tre sezioni: invariato.
- H17 en: "Student groups MUST have contiguous lessons..." (prima "should"):
  modale corretta al tier hard.
- H34 en: "an empty declared day costs a flat penalty" (prima "presence
  costs"): descrizione invertita corretta (il difetto e' la vuotezza).
- Nessuna pagina bianca: /docs e /phases renderizzano; CORS pubblicata
  coperta in suite (CorsPublishedOriginRegressionTest).
- NOTA (lezione CLAUDE.md ribattuta): dopo il rebuild il browser ha servito
  il VECCHIO bundle dalla cache al primo reload ("should" ancora visibile);
  cache-bust e il testo nuovo e' comparso.
- Sito pubblico: catena 200 via WAN hairpin con SNI dal laptop ricostruito;
  LAN-direct verso .201 resta la questione edge-box nota.

File: screenshots/{prima,dopo}-{docs-top,card-H34,card-H17,phases}.png
