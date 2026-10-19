# app-metrics-exporter

Spring Boot-app med simulerte endepunkter, for å teste SLO-pluginen i Grafana Cloud.

Appen kaller sine egne endepunkter kontinuerlig over HTTP. Beyla i klyngen måler
requestene og lager `http_server_request_duration_seconds_*`, som SLO-ene bygges på.
Se [`fleks-grafanaslo-alloy`](https://github.com/bekk/fleks-grafanaslo-alloy).

## Kjøre lokalt

```bash
cd app && mvn spring-boot:run
```

Appens egne Spring-metrikker ligger på <http://localhost:8080/actuator/prometheus>.
De sendes ikke til Grafana Cloud.

## Simulerte endepunkter

Hvert endepunkt har fast typisk latency og feilrate:

| Endepunkt                   | Typisk latency | Feilrate |
|-----------------------------|----------------|----------|
| `/api/workload/delay/short` | 250 ms         | 1 %      |
| `/api/workload/delay/long`  | 750 ms         | 5 %      |

Latency varierer mellom halv og halvannen gang typisk verdi. 5 % av requestene tar i
tillegg fire ganger så lang tid. Feil returneres som HTTP 500.

## Tilstand som kan endres mens appen kjører

Appen har to ting som kan endres mens den kjører: hvor mye trafikk den sender til seg
selv, og om en simulert hendelse (chaos) pågår.

### Trafikknivå

| Nivå   | Requests per sekund, totalt |
|--------|-----------------------------|
| `OFF`  | 0                           |
| `LOW`  | 2                           |
| `HIGH` | 5                           |

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

Metrikken `chaos_active` er `1` mens en hendelse pågår. Den er en Spring-metrikk, og
sendes ikke til Grafana Cloud.

## SLI-spørringer

`http_route` er ruten Beyla henter ut av koden. Bucket-grensene står i
`fleks-grafanaslo-alloy`: `0.05`, `0.1`, `0.2`, `0.3`, `0.5`, `1`, `2` og `5` sekunder.

Tilgjengelighet:

```promql
sum(rate(http_server_request_duration_seconds_count{service="app-metrics-exporter", http_route="/api/workload/delay/short", http_response_status_code!~"5.."}[$__rate_interval]))
/
sum(rate(http_server_request_duration_seconds_count{service="app-metrics-exporter", http_route="/api/workload/delay/short"}[$__rate_interval]))
```

Latency (andel under 300 ms):

```promql
sum(rate(http_server_request_duration_seconds_bucket{service="app-metrics-exporter", http_route="/api/workload/delay/short", le="0.3"}[$__rate_interval]))
/
sum(rate(http_server_request_duration_seconds_count{service="app-metrics-exporter", http_route="/api/workload/delay/short"}[$__rate_interval]))
```

Oppetid for hele appen, fra proben i Alloy (se [k8s/README.md](k8s/README.md#oppetid)):

```promql
avg_over_time(probe_success{service="app-metrics-exporter"}[$__rate_interval])
```