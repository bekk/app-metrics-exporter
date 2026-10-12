# CI/CD

En push til `main` deployer appen til GKE-klyngen `spire-grafana-slo` i
`gcp-fleks-grafanaslo`. Mønsteret er det samme som i Regelrett.

| Hendelse                       | Jobber            | Gjør                                                      |
|--------------------------------|-------------------|-----------------------------------------------------------|
| Push til `main`                | `test` → `deploy` | Tester, bygger, publiserer og deployer                    |
| `workflow_dispatch` med `tag`  | `test` → `deploy` | Deployer en tag som alt er publisert, uten å bygge        |

`deploy` venter på `test` (`needs: test`). Feiler en test eller et manifest, når
koden aldri klyngen. `test` kjører `mvn -B verify` og bygger manifestene med
`kubectl kustomize`.

Rene endringer i dokumentasjon og `catalog-info.yaml` deployes ikke.

Image-taggen er de sju første tegnene av commitens SHA. Imaget får også taggen
`latest`.

Prosjekt, region, klynge, namespace og navn leses fra [`gcp-config.yaml`](../gcp-config.yaml),
ikke fra workflowen.

## Ingen Secrets

Verken GitHub eller appens namespace har hemmeligheter. Legitimasjonen til Grafana
Cloud ligger hos den felles Alloy-en i `fleks-grafanaslo-alloy`, som deployes for
seg.

## Autentisering

Workflowen har ingen nøkkelfil. Den bytter et OIDC-token fra GitHub inn i et
kortlevd Google-token gjennom Workload Identity Federation.

```bash
.github/setup-wif.sh
```

Kjøres én gang, av noen med `roles/owner` i prosjektet. Skriptet er idempotent, så
det er trygt å kjøre det igjen.

| Ressurs                     | Navn                            | Delt?                      |
|-----------------------------|---------------------------------|----------------------------|
| Workload identity pool      | `github`                        | Ja, med de andre repoene   |
| OIDC-provider               | `app-metrics-exporter-oidc`     | Nei, låst til dette repoet |
| Service-konto               | `sa-github-deploy-app-metrics`  | Nei                        |
| Rolle på registry-repoet    | `roles/artifactregistry.writer` |                            |
| Rolle på prosjektet         | `roles/container.clusterViewer` |                            |
| Namespace i klyngen         | `ns-app-metrics-exporter`       | Nei                        |
| RoleBinding i namespacet    | `rolebinding-github-deploy`     | Nei                        |

Attributt-betingelsen `assertion.repository == 'bekk/app-metrics-exporter'` er det som
hindrer andre GitHub-repoer i å hente et token. GitHub-repoet leses fra git-remoten, så
betingelsen kan ikke skrives feil for hånd.

### Tilgang bare i eget namespace

De andre deploy-kontoene i prosjektet har `roles/container.developer`. Den gjelder
alle namespaces i alle klynger i prosjektet, også Secretene til de andre appene.
Denne kontoen har det ikke:

- **`roles/container.clusterViewer`** gir akkurat nok til `get-credentials`, men ingen
  tilgang til objektene i klyngen.
- **RoleBindingen** gir kontoen Kubernetes-rollen `edit`, bare i
  `ns-app-metrics-exporter`. `edit` dekker Deployments og NetworkPolicies, men ikke
  Roles og RoleBindings, så kontoen kan ikke gi seg selv mer.

Prisen er at namespacet ikke kan deployes av workflowen. Det lages av `setup-wif.sh`
og står ikke i `k8s/`.

Registry-repoet `spire-grafana-slo` deles fortsatt. Artifact Registry har ikke
tilgangsstyring per image, så kontoen kan skrive til alle imagene der.

## Variabler

Alt ligger på environmentet `gcp-app-metrics-exporter` (Settings → Environments).
Environmentet må finnes før variablene kan legges inn.

| Navn                             | Verdi                                                                                          |
|----------------------------------|------------------------------------------------------------------------------------------------|
| `GCP_PROJECT_ID`                 | `gcp-fleks-grafanaslo`                                                                         |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/<nummer>/locations/global/workloadIdentityPools/github/providers/app-metrics-exporter-oidc` |
| `GCP_SERVICE_ACCOUNT`            | `sa-github-deploy-app-metrics@gcp-fleks-grafanaslo.iam.gserviceaccount.com`                   |

Alle tre er variables, ikke secrets. `setup-wif.sh` skriver ut ferdige
`gh variable set`-kommandoer til slutt.

## Rollback

Normalveien er en revert på `main`:

```bash
git revert <sha>
```

Raskest er å deploye en tag som alt er publisert. Bare imaget rulles tilbake;
manifestene hentes fra `main` slik den står nå.

```bash
gh workflow run deploy-main.yml -f tag=<tidligere-korte-sha>
```

## Hva CI ikke gjør

| Oppgave                                   | Hvorfor ikke                                               |
|-------------------------------------------|------------------------------------------------------------|
| Opprette klynge og Artifact Registry      | Engangsoppsett, delt med de andre appene                   |
| Opprette namespacet                       | Krever rettigheter på klyngenivå. Lages av `setup-wif.sh`  |
| Deploye Alloy                             | Eget repo, `fleks-grafanaslo-alloy`                        |
| Slette namespace eller klynge             | Destruktivt. Service-kontoen har ikke rett til det         |
