# Kubernetes

Manifestene for appen. Alt bygges med kustomize fra `kustomization.yaml` i denne
mappa.

| Fil                  | Innhold                                               |
|----------------------|-------------------------------------------------------|
| `kustomization.yaml` | Namespace og labels for Backstage og metrikker        |
| `deployment.yaml`    | Appen                                                 |
| `networkpolicy.yaml` | Default deny for innkommende trafikk                  |

## Metrikker

Appen har ingen egen Alloy. Beyla og Alloy fra repoet `fleks-grafanaslo-alloy` måler
HTTP-trafikken til podene og sender RED-metrikkene til Grafana Cloud. Podene trenger
bare labelene `backstage.io/kubernetes-id` og `team`, satt i `kustomization.yaml`.
Pods uten dem blir ikke målt. Ingen annotasjoner eller NetworkPolicy trengs, fordi
ingenting kobler seg til appen.

Labelene blir til `service` og `team` på metrikkene:

| Pod-label                    | Metrikk-label | Må stemme med i `catalog-info.yaml` |
|------------------------------|---------------|-------------------------------------|
| `backstage.io/kubernetes-id` | `service`     | `metadata.name`                     |
| `team`                       | `team`        | `spec.owner`                        |

`backstage.io/kubernetes-id` er også labelen Backstage bruker til å finne podene, så
den samme verdien knytter komponenten til både podene og metrikkene.

## Bytte navn på appen

Navnet er ikke endelig. Det står disse stedene:

- `metadata.name` og `backstage.io/kubernetes-id` i `../catalog-info.yaml`
- `namespace` og `backstage.io/kubernetes-id` i `kustomization.yaml`
- Navnet, labelene og containernavnet i `deployment.yaml`
- `app`, `kubernetes.namespace` og `kubernetes.deployment` i `../gcp-config.yaml`

Et nytt namespace må lages med `../.github/setup-wif.sh`, som også gir deploy-kontoen
tilgang til det. Workflowen kan ikke lage det selv.

Et nytt navn gir nye tidsserier i Grafana Cloud. SLO-spørringer som filtrerer på
`service` må endres, og historikken deles i to.

## Deploy

En push til `main` deployer appen med GitHub Actions. Se [.github/README.md](../.github/README.md).

Namespacet `ns-app-metrics-exporter` står ikke her. Det lages én gang av
`../.github/setup-wif.sh`, sammen med tilgangen til deploy-kontoen. Se
[.github/README.md](../.github/README.md).

For hånd gjøres det samme som workflowen. GKE-nodene er linux/amd64, så på en Apple
Silicon-Mac må plattformen oppgis, ellers krasjer podden med «exec format error».

```bash
IMAGE=europe-north1-docker.pkg.dev/gcp-fleks-grafanaslo/spire-grafana-slo/app-metrics-exporter:$(git rev-parse --short HEAD)
```

```bash
docker build --platform linux/amd64 -t "$IMAGE" . && docker push "$IMAGE"
```

Plassholderen `replaced-at-deploy` byttes ut i det rendrede resultatet, så filene i
repoet ikke endres:

```bash
kubectl kustomize k8s | sed "s|replaced-at-deploy|$IMAGE|g" | kubectl --context spire-grafana-slo apply -f -
```

```bash
kubectl --context spire-grafana-slo -n ns-app-metrics-exporter rollout status deploy/deployment-app-metrics-exporter
```

Metrikkene kommer fra Beyla og Alloy, som må være deployet fra
`fleks-grafanaslo-alloy`.
