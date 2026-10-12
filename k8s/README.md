# Kubernetes

Manifestene for appen. Alt bygges med kustomize fra `kustomization.yaml` i denne
mappa.

| Fil                  | Innhold                                               |
|----------------------|-------------------------------------------------------|
| `kustomization.yaml` | Namespace og labels for Backstage og metrikker        |
| `deployment.yaml`    | Appen                                                 |
| `networkpolicy.yaml` | Default deny, med unntak for den felles Alloy-en      |

## Metrikker

Appen har ingen egen Alloy. Metrikkene skrapes av den felles Alloy-en i repoet
`fleks-grafanaslo-alloy`, som sender dem til Grafana Cloud. For å bli skrapet må
podene ha tre ting, som alle står her:

1. Annotasjonen `prometheus.io/scrape: "true"` og `prometheus.io/path` på pod-malen
   i `deployment.yaml`.
2. Labelene `backstage.io/kubernetes-id` og `team`, satt i `kustomization.yaml`.
   Pods uten dem blir ikke skrapet.
3. En NetworkPolicy som slipper Alloy fra `ns-alloy` inn på port 8080.

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
- `namespace.yaml`
- Navnet, labelene og containernavnet i `deployment.yaml`
- `podSelector` i `networkpolicy.yaml`

Et nytt navn gir nye tidsserier i Grafana Cloud. SLO-spørringer som filtrerer på
`service` må endres, og historikken deles i to.

## Deploy

Det finnes ingen workflow ennå, så deployen gjøres for hånd. Klyngen er
`spire-grafana-slo` i `gcp-fleks-grafanaslo`, og imaget legges i Artifact
Registry-repoet `spire-grafana-slo`, som deles med de andre appene.

Bygg og push imaget. GKE-nodene er linux/amd64, så på en Apple Silicon-Mac må
plattformen oppgis, ellers krasjer podden med «exec format error».

```bash
IMAGE=europe-north1-docker.pkg.dev/gcp-fleks-grafanaslo/spire-grafana-slo/demo-metrics-exporter:$(git rev-parse --short HEAD)
```

```bash
docker build --platform linux/amd64 -t "$IMAGE" . && docker push "$IMAGE"
```

`image: replaced-at-deploy` i `deployment.yaml` byttes ut med `kustomize edit` i en
kopi av `k8s/`, så filen i repoet ikke endres:

```bash
cp -R k8s /tmp/k8s-deploy && (cd /tmp/k8s-deploy && kustomize edit set image replaced-at-deploy="$IMAGE")
```

```bash
kubectl --context spire-grafana-slo apply -k /tmp/k8s-deploy
```

```bash
kubectl --context spire-grafana-slo -n ns-demo-metrics-exporter rollout status deploy/deployment-demo-metrics-exporter
```

Metrikkene sendes av den felles Alloy-en, som må være deployet fra
`fleks-grafanaslo-alloy`.
