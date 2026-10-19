# Research Report: OOM del fork surefire (exit 137) su fedora-box

**Date**: 2026-08-29
**Depth**: exhaustive
**Confidence**: HIGH (meccanismo: il log kernel e' ground truth; sub-claim G1: MEDIUM)

## Executive Summary

Il fork surefire muore con exit 137 perche' il **kernel OOM killer globale** lo sceglie come vittima: la macchina (31G RAM + 16G swapfile, NON zram) era al muro (swap 100% pieno, RAM libera ~220MB al momento del kill, file cache gia' spolpata), e il processo java del fork portava due aggravanti: RSS in rapida crescita (6.4GB -> 8.6GB tra i due tentativi, col meccanismo JVM grow-before-collect su un max heap da 16G) e **`oom_score_adj=200` ereditato da `herdr.service`** (che dichiara `OOMScoreAdjust=200`: tutte le sessioni agente sono vittime sacrificali by design). Il "6G available" letto da `free` prima del lancio era una stima che non sopravvive al secondo in cui il fork inizia a espandersi.

## Findings

### 1. Chi uccide: kernel OOM globale, NON systemd-oomd (HIGH)

Prova locale decisiva (journalctl -k, 23:20:23 e 23:24:25):

```
gvfs-afc-volume invoked oom-killer: gfp_mask=0x140cca(GFP_HIGHUSER_MOVABLE|__GFP_COMP), order=0
oom-kill:constraint=CONSTRAINT_NONE,...,global_oom,task_memcg=/user.slice/.../herdr.service,task=java
Out of memory: Killed process (java) total-vm:21194812kB, anon-rss:8592868kB, oom_score_adj:200
```

- `journalctl -u systemd-oomd` = nessuna voce: systemd-oomd NON c'e'entra [8].
- `CONSTRAINT_NONE` + `global_oom` = esaurimento di sistema, NON limite di cgroup [1][4].
- L'allocazione fallita e' order-0 (pagina singola): il caso piu' permissivo possibile; fallisce solo quando non c'e' piu' nulla da reclamare.

### 2. Lo stato di memoria al kill: il box era GIA' al muro (HIGH)

Il dump kernel al 23:24:25 mostra:

- `free:56175` pagine ≈ **220MB di RAM libera totale** (Node 0 Normal: 62MB, SOTTO il watermark `min:62.892kB`);
- ~28.5GB di memoria anonima attiva (active 13.9G + inactive 14.6G);
- file cache residua ~190MB con `all_unreclaimable? yes`: il kernel aveva gia' reclamato tutto cio' che era reclamabile;
- swapfile /var/swap/swapfile 16G **USED 16G** (100%: nessuna testata di swap; NON e' zram, e' un file su disco).

`free -g` poco prima mostrava "available ~6G": quella stima include page cache reclamabile e headroom di swap; con lo swap pieno e la cache gia' spolpata, la stima e' ottimista e non prevede che un nuovo processo possa consumarla in secondi [2][3]. Consumatori stabiti: due daemon Gradle/Kotlin di un altro pane (~3.5G), Ferdium, due processi claude, GNOME, e la sessione stessa.

### 3. Perche' la vittima e' il fork: oom_score_adj=200 by design di herdr (HIGH)

- `systemctl --user show herdr.service -p OOMScoreAdjust` = **200**. Tutti i processi delle sessioni agente (pani herdr) ereditano l'adjustment: figli incluse le JVM di maven/surefire [1][5].
- `oom_score_adj` +200 polarizza la selezione: il killer preferisce quel processo anche contro processi con RSS simile o maggiore [1][4]. La policy e' sensata per agenti usa-e-getta (il desktop sopravvive, gli agenti muoiono prima) ma rende OGNI test JVM della sessione la vittima designata di qualunque pressione globale.
- Corrobora: il blog di Ian Paterson descrive esattamente questo pattern ("every victim carried oom_score_adj:200, which my own automation had set") [6].

### 4. Perche' il fork cresce a 6-8GB in avvio: grow-before-collect su max heap 16G (MEDIUM)

- Il pom hardcoda `<argLine>@{argLine} -Xmx16g ...</argLine>` ("unica sorgente di verita' per l'argLine").
- La JVM con -Xmx16g e -Xms basso NON raccoglie appena puo': espande l'heap finche' il costo di GC resta sotto soglia (comportamento documentato: heap cresce verso Xmx, GC solo quando conveniente) [9][10]. In avvio (classe loading + infrastruttura Quarkus test nel classpath) le allocazioni spingono G1 a espandere; con 16G di tetto l'espansione trova spazio solo togliendo le ultime pagine al sistema.
- Tra i due tentativi (23:20 e 23:24) l'RSS al kill sale da 6.4GB a 8.6GB: crescita coerente con espansione libera, non con un test che "usa" 8GB (MultiStartRunnerTest e' plain JUnit e in serata, a box scarico, girava in ~30s con memoria modesta).
- Sub-claim MEDIUM: meccanicamente coerente e corroborato dalla letteratura, ma senza GC log del fork non e' provato al 100% (alternativa residua: bootloader Quarkus che tocca memoria in avvio; comunque il quadro kill-non-dipende-da-questo-dettaglio).

### 5. La forma dell'errore surefire e' il sintomo standard (HIGH)

"The forked VM terminated without properly saying goodbye / Error occurred in starting fork / Exit Code 137": 137 = 128+SIGKILL; surefire riporta cosi' qualunque morte esterna del fork (kill, crash); e' il pattern noto con OOM-kill del fork [11][12][13].

## Mitigazioni (riferite, non applicate: decisione di maintainer)

1. **Property-izzare l'heap del fork** (best practice argLine-proprieta' [14][15]): `<surefire.heap>16g</surefire.heap>` in pom + `-Xmx${surefire.heap}` nell'argLine; a box affollato `test.sh -mva "-Dsurefire.heap=3g"` senza toccare la "sorgente unica". NOTA tecnica: l'override via `-DargLine` NON funziona qui perche' il -Xmx16g e' literal nel pom e l'ordine dei flag JVM farebbe vincere comunque il 16g: serve la property dedicata.
2. **Alternativa strutturale**: `-XX:MaxRAMPercentage=40` invece di un -Xmx fisso (il fork si autolimita alla quota della macchina reale) [16].
3. **Operativo notturno**: quando un altro pane builda, lanciare i test con heap ridotto o attendere; `free` non basta, guardare anche `swapon --show` (swap pieno = nessuna testata).
4. **Il policy herdr (OOMScoreAdjust=200) resta corretto** per il suo scopo; documentarlo nel manuale: le JVM di test della sessione sono sacrificali per design.

## Confidence Assessment

- HIGH: identificazione del killer (kernel globale, non oomd, non cgroup); stato di memoria al kill; il ruolo di oom_score_adj=200; il sintomo surefire.
- MEDIUM: il meccanismo esatto dell'RSS 6-8GB in avvio (grow-before-collet piu' probabile; GC log assenti).
- Non verificato: quale specifica allocazione del fork toccasse le pagine (servirebbe -Xlog:gc sul fork riproducendo la pressione).

## Sources

1. https://unseel.com/cs/out-of-memory-killer - come il kernel sceglie la vittima (oom_score_adj +/- range e polarizzazione)
2. https://serverfault.com/questions/917938/ - OOM-killer nonostante "available" alto (stessa trappola della stima)
3. https://cmdmemo.com/linux/swap-zram-and-memory-pressure/ - diagnosticare con available/free, swap e PSI
4. https://prepare.sh/tutorial/the-oom-killer-why-the-kernel-kills-your-process - score, adj, watermarks
5. https://ianlpaterson.com/blog/oom-lockout-ssh-survival-hardening/ - vittime con adj=200 impostati dall'automazione
6. https://stackoverflow.com/questions/26403536/ - java invoked oom-killer (pattern dump)
7. https://bugzilla.redhat.com/show_bug.cgi?id=1373339 - kill premature su Fedora
8. https://discussion.fedoraproject.org/t/psa-transitioning-from-zram-swap-to-zswap/138256 - configurazioni swap Fedora (qui: file, non zram)
9. https://www.baeldung.com/gc-release-memory - heap cresce da -Xms verso -Xmx, commit progressivo
10. https://stackoverflow.com/questions/5355933/ - JVM espande l'heap prima di raccogliere
11. https://stackoverflow.com/questions/23260057/ - "forked VM terminated" (cause, incl. System.exit/crash)
12. https://maven.apache.org/surefire/maven-surefire-plugin/faq.html - FAQ ufficiale sul messaggio
13. https://www.codestudy.net/blog/maven-error-occurred-in-starting-fork-check-output-in-log/ - "Error occurred in starting fork"
14. https://devll.org/blog/2020/java/jacoco-argline.html - argLine via user property (pattern jaCoCo/late binding)
15. https://www.javathinking.com/blog/how-to-pass-jvm-arguments-via-maven/ - property per configurazione per-ambiente
16. https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-garbage-collector-tuning.html - tuning G1 (percentuali RAM)

Prove locali primarie: `journalctl -k --since 23:10` (dump OOM completi 23:20:23 e 23:24:25), `systemctl --user show herdr.service -p OOMScoreAdjust` (200), `swapon --show` (16G/16G usati), pom.xml:221 (argLine hardcoded).
