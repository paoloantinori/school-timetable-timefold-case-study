# Processo pianificazione / sviluppo / test: valutazione e ottimizzazioni

Data: 2026-09-04. Domanda: il processo e' ottimizzato e seguibile anche da modelli di classe GLM-5.3? Metodo: estrazione delegata (agente Sonnet) su DoD, skill, hook, test, con verifica a campione eseguita di persona. Compagno di `audit-ultimi-2-mesi-2026-09-04.md`.

**Verdetto in un paragrafo.** Il processo e' *documentalmente* ricco e in gran parte sano: la piramide dei test localizzati e' eccellente, i wrapper scripts sono maturi, la catena di ratifiche con DoD testuale funziona sui commit grossi. Ma c'e' un **gap sistematico tra processo dichiarato e processo meccanicamente enforced**: tre meccanismi dichiarati come automatici (pre-commit hook, check-background-pipes, banner-check) risultano non attivi o non cablati. Per un modello meno potente questo gap e' il rischio principale: un GLM-5.3 segue le regole che *legge*, e se la regola dice "il hook blocca" ma il hook non esiste, il modello si fida e il gate salta. Le ottimizzazioni sotto puntano a chiudere quel gap e a rendere il processo *meccanicamente* seguibile, non solo *documentalmente* descritto.

---

## 1. Cosa funziona gia' bene (da non toccare)

- **Piramide dei test localizzati** (`claudedocs/localized-testing-guidelines.md`): la matrice tipo-di-modifica -> catena minima con runtime misurati (L0 verifier 3,3s ... L5 ciclo A/B laptop ~1,5h) e' il pezzo piu' forte del processo. Regola chiara: sempre il livello piu' basso che copre la modifica. Un modello debole la segue perche' e' una tabella, non un giudizio.
- **Wrapper scripts obbligatori** (CLAUDE.md "Use Wrapper Scripts"): 214 script contro 177 file di test. L'enforcement via hook PreToolUse (`check-bash-suggestions.sh`) intercetta `curl`/`mvn`/`podman` e suggerisce il wrapper. Funziona perche' e' meccanico.
- **Catena di ratifiche con DoD testuale**: i commit `feat(TASK-...)` portano nel body la DoD (/simplify, code-review, suite EXIT 0). Leggibili e tracciabili.
- **Tre gate laptop operativi (TASK-579)**: preflight/doctor/mandate cablati sul percorso obbligato (prod-start esegue il preflight da solo). Nati dal censimento di 27 incidenti in 6 famiglie.
- **Localized testing come gate di adozione**: il ciclo completo resta solo il gate finale sul laptop; tutto il resto si valida a monte a costo basso. Questo e' cio' che rende il processo sostenibile.

---

## 2. I tre gap tra dichiarato e reale (da chiudere)

Questi sono i punti dove il processo *dice* una cosa e il disco ne fa un'altra. Verificati di persona.

1. **Il pre-commit hook dei gate marker NON esiste.** Il CLAUDE.md globale dichiara: "The git pre-commit hook will block commits lacking gate markers". In questo checkout `.git/hooks/` non ha alcun `pre-commit` (solo il `.sample`) e `core.hooksPath` non e' impostato. Misura: su 1076 commit dal 01/08, il marker `Gates:` compare in **13 (1,2%)**, `gate-exempt` in 3. Il gate dichiarato non blocca nulla. *Verificato di persona e dall'estrattore.*
2. **`check-background-pipes.sh` NON e' cablato.** Esiste in `.claude/hooks/`, ma non ha alcun match in `.claude/settings.json` ne' in `settings.local.json` (grep = 0). CLAUDE.md dichiara "Protected by: check-background-pipes.sh automatically blocks dangerous patterns". La protezione dichiarata non e' attiva. *Verificato di persona (grep 0 su entrambi i settings).*
3. **Il banner non e' pinnato da test.** Il rebase di M38 (commit 9a9175c1) ha cancellato in silenzio la riga banner `surplus-harvest=%b`; il pass girava col flag attivo ma invisibile nel log. Fix puntuale (cce67200), ma il "pin dei banner via test di frase" e' annotato come follow-up TASK-579 e **non implementato**. *Fonte: report processo, commit cce67200.*

**Pattern comune:** il progetto ha sviluppato *regole testuali* eccellenti ma non sempre le ha *rese meccaniche*. Per un modello potente che capisce l'intento questo e' tollerabile; per un modello che esegue alla lettera e' una trappola, perche' la regola letta non corrisponde al comportamento reale.

---

## 3. Ottimizzazioni per renderlo seguibile da GLM-5.3

Ordinate per rapporto valore/costo. Le prime quattro chiudono i gap del punto 2; le altre riducono la dipendenza dal giudizio.

### 3.1 Rendere meccanici i gate gia' dichiarati (priorita' massima)

- **Installare il pre-commit hook** che il CLAUDE.md globale dichiara. Comando: scrivere `.git/hooks/pre-commit` (o usare `core.hooksPath` verso una dir versionata) che blocca commit di codice senza marker `Gates:`/`gate-exempt` nel messaggio. Questo allinea il disco alla regola. Senza di esso la DoD resta una convenzione che un modello debole puo' dimenticare.
- **Cablare `check-background-pipes.sh`** in `.claude/settings.json` come hook PreToolUse su Bash (accanto a `check-bash-suggestions.sh`), oppure rimuovere la frase "automatically blocks" dal CLAUDE.md. Una delle due: la regola e la realta' devono coincidere.
- **Pinnare i banner via test di frase** (follow-up TASK-579): un test che asserisce che ogni flag DEFAULT ON compare nel banner di `refreshSolverStatics`. Costa poco e impedisce la regressione silenziosa gia' vista due volte (jar-stantio, rebase M38).

### 3.2 Ridurre la dipendenza dal giudizio del modello

- **Trasformare la DoD in un check meccanico, non in una checklist letta.** Oggi la DoD e' testuale in 3 posti (backlog/config.yml, CLAUDE.md progetto, CLAUDE.md globale) e i gate 1-2 (/simplify, code-review) si appoggiano a skill **non presenti nel repo** (vengono dalla config utente). Per un GLM-5.3 lanciato in un contesto dove quelle skill non sono caricate, il gate e' invisibile. Azione: uno script `./scripts/core/dod-check.sh` che verifica meccanicamente "diff -> /simplify eseguita? code-review eseguita? test EXIT 0?" e stampa un verdetto. Il modello esegue lo script invece di ricordare la checklist.
- **Completare la skill `autotune`**: ha 4 file md sciolti e **nessun SKILL.md** (frontmatter assente), quindi non e' invocabile come le altre. O si scrive lo SKILL.md o si rimuove la dir. Una skill a meta' e' rumore per un modello che le elenca.
- **Documentare le skill di sessione nel repo.** `/simplify`, `code-review`, `review-local`, `pa:reflect` sono richiamate dai gate ma non esistono in `.claude/skills/`. Se devono essere usabili da un modello debole in questo progetto, va detto dove vivono e come si invocano, altrimenti il gate 1-2 e' inapplicabile.

### 3.3 Igiene del backlog (riduce il carico cognitivo)

- **Sweep di chiusura dei task gia' ratificati.** 118 task aperti, ma molti (532, 535, 536, 542, 556, 558, 560...) risultano "To Do" pur essendo ratificati nei commit. Per un modello che deve decidere "cosa faccio dopo", un backlog che sovrastima il lavoro e mescola fatto e da-fare e' una fonte di errori. Azione: uno sweep che allinea status backlog allo stato dei commit.

### 3.4 Proteggere la catena di verifica dai confound gia' visti

Le regole "checkpoint-first" e "banner-check" (memoria `checkpoint-first-e-banner-check`) sono nate da incidenti reali (jar-stantio scoperto due volte). Sono gia' nel processo come regole testuali; il passo in piu' e' che il `launch-preflight.sh` (esistente, TASK-579) le renda obbligatorie prima di ogni ciclo lungo, non raccomandate.

---

## 4. Valutazione complessiva

**Per un modello potente (Opus/Fable):** il processo e' gia' buono. La ricchezza documentale (piramide test, wrapper, ratifiche, gate laptop) e' un asset, e un modello forte naviga le piccole incoerenze senza cadere.

**Per un modello di classe GLM-5.3:** il processo e' *quasi* pronto, ma i tre gap del punto 2 sono trappole reali. La differenza critica e' che un modello debole **non puo' distinguere una regola enforced da una regola solo dichiarata**. Il lavoro di ottimizzazione piu' prezioso non e' aggiungere documentazione, e' **rendere meccanico cio' che oggi e' solo scritto**: pre-commit hook, check-background-pipes cablato, banner pinnati, DoD come script. Fatto questo, il processo diventa seguibile perche' il modello esegue script e legge verdetti, invece di dover ricordare e interpretare checklist.

**Priorita' unica se si fa una sola cosa:** installare il pre-commit hook dei gate marker. E' il punto dove il processo dichiara un enforcement che non esiste, e dove un modello debole ha la probabilita' piu' alta di saltare un gate fidandosi della documentazione.

---

## Nota metodologica

Estrazione processo da agente Sonnet con obbligo di citazione. Verifiche di persona: (a) pre-commit hook assente (`ls .git/hooks`, `core.hooksPath` rc=1); (b) `check-background-pipes` con 0 match in entrambi i settings; (c) `/simplify` e `code-review` assenti da `.claude/skills/`; (d) rapporto 214 script / 177 test. Conteggi marker: 13 `Gates:` e 3 `gate-exempt` su 1076 commit dal 01/08 (grep su `git log`). Ultimo EXIT 0 di suite completa documentato: 02/09, suite 1251/0/0/41.
