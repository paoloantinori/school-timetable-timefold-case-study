# Pubblicazione del sito: planner.example.org

Runbook della catena di pubblicazione e dei suoi vincoli. Scritto dopo l'incidente della
pagina bianca su Chrome (17/08/2026), perche' la catena ha tre anelli in tre macchine e
ognuno puo' rompersi in silenzio.

## Topologia

```
Chrome/Firefox (LAN 10.20.0/24)
  -> https://planner.example.org
     edge-box: nginx-manager (openresty), proxy_host/33.conf
       auth_basic (satisfy any con allow LAN: 10.20.0/24, 10.30.0/24)
       proxy_pass -> http://laptop.lan:8080
         laptop: Quarkus (target/quarkus-app/quarkus-run.jar), UI Quinoa cotta nel JAR
```

- Config nginx su edge-box: `~/containers_config/nginx-manager/data/nginx/proxy_host/33.conf`
  (gestita da nginx-proxy-manager: le modifiche fatte a mano possono essere sovrascritte
  dall'interfaccia NPM).
- L'istanza pubblicata e' l'app del laptop, qualunque profilo giri su :8080.

## L'incidente del 17/08 (per non ripeterlo)

Sintomo: pagina bianca su Chrome, tutto ok su Firefox.
Causa: `quarkus.http.cors.origins` conteneva solo `http://localhost:3000` (il dev server).
Il filtro CORS di Quarkus risponde **403** a ogni richiesta che porta un header `Origin`
fuori allowlist. Chrome invia `Origin` sui module script con attributo `crossorigin` e sui
CSS in modalita' cors anche same-origin; Firefox su same-origin non lo manda. Quindi: stesso
server, Firefox vedeva la UI, Chrome no (i tre `.js` a 403 body vuoto nella HAR).
Fix: commit `9248451e` (origine pubblica + laptop.lan:8080 nell'allowlist).

### Le trappole diagnostiche che questo incidente ha insegnato

1. Nelle HAR, le 403 dal backend NON hanno l'header `x-served-by` che hanno i 200: nginx lo
   aggiunge senza `always` e su 403 non viene applicato. Non e' un indizio di un layer diverso.
2. Il log NPM mostra `[Sent-to laptop.lan]` sulle richieste proxiate: e' il modo per distinguere
   un 403 di nginx da uno del backend.
3. I CSS nella HAR sembravano ok ma erano `_fromCache: disk`: a fresco anche loro 403.

## Regole per non regredire

1. **Il test**: `CorsPublishedOriginRegressionTest` (2 casi: origine pubblicata servita,
   origine sconosciuta rifiutata). Qualunque modifica a `quarkus.http.cors.origins` che
   tolga l'origine pubblica fa rosso il CI. Se il dominio pubblicato cambia, va aggiornato
   INSIEME proprieta' e test.
2. **La proprieta' e' cotta nel JAR**: un commit su `application.properties` non e' online
   finche' l'app del laptop non viene ricostruita e riavviata (`git pull` + `./scripts/core/rebuild.sh`).
   "Committato" non vuol dire "pubblicato".
3. **Verifica post-deploy** (due livelli, entrambi richiesti):
   - backend: sul laptop, richiedere un asset con l'header Origin e aspettarsi 200:
     `python3 -c "import urllib.request; r=urllib.request.Request('http://localhost:8080/'); r.add_header('Origin','https://planner.example.org'); print(urllib.request.urlopen(r).status)"`
   - browser Chromium (Playwright): caricare la URL pubblica e verificare che la UI React sia
     renderizzata (bottoni Solve/Analyze presenti), non solo il titolo della pagina.
4. **Il rebuild del laptop e' anche un deploy pubblico**: coordinarsi con cio' che zai sta
   facendo prima di uccidere l'app (nessun solve attivo = sicuro).

## Note aperte

- Le modifiche manuali a 33.conf possono essere sovrascritte da nginx-proxy-manager.
- L'auth basic e' soddisfatta anche solo dall'IP LAN (satisfy any): da fuori LAN serve credenziale.
