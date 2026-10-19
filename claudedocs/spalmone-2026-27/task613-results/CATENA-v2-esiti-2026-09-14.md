
## APPENDICE (17/09... 14/09 sera): caratterizzazione determinismo census (mandato Fedora)

Tre corse identiche di step14 -Dhrf.debug su m39-off a HEAD d7af5fb1a (una alla
volta, log: census-run1/2/3.txt accanto):

```
run1: {HRF-H1=3, HRF-VRP=2}   hard -11 -> -3 -> -3
run2: {HRF-H1=3, HRF-VRP=2}   hard -11 -> -3 -> -3
run3: {HRF-H1=3, HRF-VRP=2}   hard -11 -> -3 -> -3   (run1==run3 per diff)
```

Lo scan del HoleRelayFinisher e' DETERMINISTICO sul HEAD corrente: chiavi E
conteggi identici 3/3.

Il dato storico {HRF-H1=2} (prima corsa D2) appartiene a d276912e, il cui
albero differisce da quello attuale in 4 file main (ScheduleService catch-b,
EmptyDayFinisher flag-readers della RATIFICA 43520db3, FCC cure 42682724, HRF
tail-census 2ba9c1d8): i due census NON sono confrontabili come codice.
Congetture ordinate per economia: (1) drift di stato d'ingresso fra versioni
nel range (le 4 modifiche main; da escludere del tutto bisectando d276912e se
a Fedora serve il confronto puntuale); (2) RESIDUI di statiche/property fra
classi nella stessa JVM: debole qui, perche' -c esegue un solo metodo con
teardown che ripulisc; (3) ordine di iterazione HashMap: ESCLUSO come causa dei
CONTEGGI (puo' solo permutare l'ordine delle chiavi, mai 2<->3+2).
Consiglio operativo: fidarsi solo di census presi allo stesso commit; quello
valido per il no-op su HEAD e' {HRF-H1=3, HRF-VRP=2} (ripreso 3/3).
