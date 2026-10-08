# app1-grafana-metrics

Spring Boot-app som produserer metrikker til Prometheus-datakilden i Grafana Cloud, for å
teste SLO-pluginen.

Appen kaller sine egne endepunkter kontinuerlig over HTTP. Da lager Spring de ekte
`http_server_requests_seconds_*`-metrikkene, og SLO-ene kan bygges på dem.

## Kjøre lokalt

```bash
cd app && mvn spring-boot:run
```

Metrikkene ligger på <http://localhost:8080/actuator/prometheus>.

## Simulerte endepunkter

Hvert endepunkt har fast typisk latency og feilrate:

| Endepunkt                   | Typisk latency | Feilrate |
|-----------------------------|----------------|----------|
| `/api/workload/delay/short` | 250 ms         | 1 %      |
| `/api/workload/delay/long`  | 750 ms         | 5 %      |

Latency varierer mellom halv og halvannen gang typisk verdi. 5 % av requestene tar i
tillegg fire ganger så lang tid. Feil returneres som HTTP 500 (`outcome="SERVER_ERROR"`).

## Tilstand som kan endres mens appen kjører

Appen har to ting som kan endres mens den kjører: hvor mye trafikk den sender til seg
selv, og om en simulert hendelse (chaos) pågår.

### Trafikknivå

| Nivå   | Requests per sekund, totalt |
|--------|-----------------------------|
| `OFF`  | 0                           |
| `LOW`  | 2                           |
| `HIGH` | 20                          |

Nivået ved oppstart settes med `traffic.level` i `application.yml` (standard `LOW`) eller
miljøvariabelen `TRAFFIC_LEVEL`. Hver replika sender sin egen trafikk.

```bash
curl -X PUT localhost:8080/api/traffic -H 'Content-Type: application/json' -d '{"level": "HIGH"}'
```

```bash
curl localhost:8080/api/traffic
```

### Chaos: simulere hendelser

Brukes til å teste burn rate-alarmer. Gir alle endepunktene ekstra feil og/eller latency
i et gitt antall minutter. Alle felt er valgfrie.

```bash
curl -X POST localhost:8080/api/chaos -H 'Content-Type: application/json' \
  -d '{"errorRate": 0.3, "extraLatencyMs": 500, "minutes": 15}'
```

```bash
curl localhost:8080/api/chaos
```

```bash
curl -X DELETE localhost:8080/api/chaos
```

Metrikken `chaos_active` er `1` mens en hendelse pågår, og kan brukes til annotasjoner i
dashboards.

## SLI-spørringer

Histogrammet har bare bøttene `0.3`, `0.5`, `1` og `2` sekunder, for å holde antall aktive
serier nede.

Tilgjengelighet:

```promql
sum(rate(http_server_requests_seconds_count{uri="/api/workload/delay/short", outcome!="SERVER_ERROR"}[$__rate_interval]))
/
sum(rate(http_server_requests_seconds_count{uri="/api/workload/delay/short"}[$__rate_interval]))
```

Latency (andel under 300 ms):

```promql
sum(rate(http_server_requests_seconds_bucket{uri="/api/workload/delay/short", le="0.3"}[$__rate_interval]))
/
sum(rate(http_server_requests_seconds_count{uri="/api/workload/delay/short"}[$__rate_interval]))
```