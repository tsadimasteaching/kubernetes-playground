# Kubernetes Playground

Hands-on Kubernetes manifests for a three-tier application — **PostgreSQL**, a **Spring Boot** backend and a **Vue** frontend — running on [minikube](https://minikube.sigs.k8s.io/), plus a [Kustomize](kustomization/README.md) base/overlay demo.

## Repository structure

```text
.
├── .devcontainer/      # Dev container / GitHub Codespaces setup
├── cert/               # cert-manager ClusterIssuer (self-signed)
├── postgres/           # StatefulSet and Services for PostgreSQL
├── spring/             # Spring Boot Deployment, Service, Ingress (HTTP and HTTPS)
├── vue/                # Vue frontend Deployment, Service, Ingress
└── kustomization/      # Kustomize base/overlay demo — see its own README
```

## Contents

1. [Environment setup](#1-environment-setup)
2. [Container registry (GitHub Packages)](#2-container-registry-github-packages)
3. [Deploy the application](#3-deploy-the-application)
4. [Access the application](#4-access-the-application)
5. [HTTPS with cert-manager](#5-https-with-cert-manager)
6. [Kustomize demo](#6-kustomize-demo)
7. [Useful tools](#7-useful-tools)

---

## 1. Environment setup

### Option A — Dev container / Codespaces (recommended)

Open the repository in GitHub Codespaces or in VS Code with *Dev Containers: Reopen in Container*. The container:

- is based on **Ubuntu 24.04** (`devcontainers/base:ubuntu-24.04`) and installs `docker` (docker-in-docker), `kubectl` 1.37, `helm` and `minikube` via devcontainer features
- installs `k9s`, `kustomize` and `fzf` ([startup_script.sh](.devcontainer/startup_script.sh))
- starts minikube (Kubernetes v1.37.0) with the ingress addon on every container start, once Docker is ready ([start_minikube.sh](.devcontainer/start_minikube.sh)). If the cluster is not up, check the log with *Codespaces: View Creation Log* or run `bash .devcontainer/start_minikube.sh`
- adds these shell aliases:

| Alias | Command |
| --- | --- |
| `k` | `kubectl` |
| `kg` | `kubectl get` |
| `kd` | `kubectl describe` |
| `kdel` | `kubectl delete` |
| `kga` | `kubectl get all` |
| `kgn` | `kubectl get all --all-namespaces` |

> The container needs at least 4 CPUs, 8 GB RAM and 32 GB disk.

### Option B — Local machine

Install [docker](https://docs.docker.com/get-docker/), [kubectl](https://kubernetes.io/docs/tasks/tools/), [minikube](https://minikube.sigs.k8s.io/docs/start/) and [helm](https://helm.sh/docs/intro/install/):

```bash
curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
chmod 700 get_helm.sh
./get_helm.sh
```

Then start minikube:

```bash
minikube start --kubernetes-version=v1.37.0
```

### Enable the ingress controller

In the dev container this is done automatically. On a local machine, enable the NGINX ingress addon:

```bash
minikube addons enable ingress
kubectl get pods -n ingress-nginx   # wait until the controller is Running
```

---

## 2. Container registry (GitHub Packages)

The Spring and Vue images are pulled from `ghcr.io`. If the images are private (or you push your own), the cluster needs credentials.

### Create a personal access token

1. GitHub → *Settings* → *Developer settings* → *Personal access tokens* → *Tokens (classic)*
2. Select the scopes:
   - `read:packages` — enough to **pull** images
   - `write:packages` — needed to **push** images
3. Save the token to a file, e.g. `~/github-image-repo.txt`

Your packages are listed under the *Packages* tab of your GitHub profile.

### Build and push an image

Run these from the **application's** repository (where the Dockerfile lives), not from this one:

```bash
# build and tag
docker build -t ghcr.io/<GITHUB-USERNAME>/ds-spring:latest -f nonroot.Dockerfile .

# log in to the registry
cat ~/github-image-repo.txt | docker login ghcr.io -u <GITHUB-USERNAME> --password-stdin

# push
docker push ghcr.io/<GITHUB-USERNAME>/ds-spring:latest
```

### Create the image pull secret

The Spring and Vue Deployments reference a secret named `registry-credentials` via `imagePullSecrets`.

**Quick way** — let `kubectl` build it:

```bash
kubectl create secret docker-registry registry-credentials \
  --docker-server=ghcr.io \
  --docker-username=<GITHUB-USERNAME> \
  --docker-password="$(cat ~/github-image-repo.txt)"
```

**Manual way** — useful to understand what is stored in the secret. Create a file named `.dockerconfig.json` (it is git-ignored):

```json
{
  "auths": {
    "https://ghcr.io": {
      "username": "REGISTRY_USERNAME",
      "password": "REGISTRY_TOKEN",
      "email": "REGISTRY_EMAIL",
      "auth": "BASE_64_BASIC_AUTH_CREDENTIALS"
    }
  }
}
```

Generate `BASE_64_BASIC_AUTH_CREDENTIALS` with:

```bash
echo -n <USER>:<TOKEN> | base64
```

and create the secret from the file:

```bash
kubectl create secret docker-registry registry-credentials --from-file=.dockerconfigjson=.dockerconfig.json
```

---

## 3. Deploy the application

Apply the tiers in order — the database first, since Spring connects to it on startup:

```bash
kubectl apply -f postgres/
kubectl rollout status statefulset/postgres   # wait until postgres-0 is Ready

kubectl apply -f spring/spring-deployment.yaml -f spring/spring-svc.yaml -f spring/spring-ingress.yaml
kubectl apply -f vue/
```

> `spring/` contains two Ingresses for the same host: `spring-ingress.yaml` (HTTP) and `spring-ingress-ssl.yaml` (HTTPS, see [section 5](#5-https-with-cert-manager)). Apply only one of them.

Check that everything is up:

```bash
kubectl get all
kubectl get ingress
kubectl logs deploy/spring-deployment
```

The Spring pod becomes *Ready* only once its readiness probe (`/actuator/health/readiness`) succeeds, which can take a minute.

> **Note:** the database credentials are written in plain text in the manifests to keep the example simple. In a real deployment, store them in a `Secret` and reference them with `valueFrom.secretKeyRef`.

### Why PostgreSQL runs as a StatefulSet

Spring and Vue are **stateless**: any pod is as good as any other, so a `Deployment` can create, replace and scale them freely. A database is **stateful**: the pod is tied to its data on disk. A `StatefulSet` gives exactly the guarantees a database needs:

| | Deployment | StatefulSet |
| --- | --- | --- |
| **Pod name** | random, changes on every restart (`postgres-deployment-7c9f…`) | stable and ordered (`postgres-0`, `postgres-1`, …) |
| **Storage** | all replicas share the PVC you create by hand | each pod gets **its own** PVC from `volumeClaimTemplates` (`data-postgres-0`), re-attached to the same pod after restarts |
| **Updates / restarts** | rolling update starts the new pod **before** stopping the old one — two Postgres processes on the same data directory can corrupt it | the old pod is stopped **before** the new one starts; never two pods with the same identity |
| **Network identity** | only through the Service | every pod also has its own DNS name via a headless Service (`postgres-0.postgres-headless`) |
| **Deleting it** | a hand-made PVC stays, but nothing links it to a pod | PVCs are **kept** when the StatefulSet is deleted, so the data survives |

The stable names and per-pod storage are also what makes database replication possible (e.g. `postgres-0` as primary, `postgres-1` as replica). For production, an operator such as [CloudNativePG](https://cloudnative-pg.io/) builds on these primitives and adds backups, failover and upgrades.

Inspect it:

```bash
kubectl get statefulset,pods,pvc -l app=postgres
kubectl exec -it postgres-0 -- psql -U dbuser -d dsdb
```

To remove the database **including its data**, delete the PVC explicitly:

```bash
kubectl delete -f postgres/
kubectl delete pvc data-postgres-0
```

---

## 4. Access the application

The Ingresses route by hostname:

| Host | Service |
| --- | --- |
| `spring.local` | `spring` (port 8080) |
| `vue.local` | `vue-svc` (port 9000) |

Point the hostnames at the cluster in `/etc/hosts`:

```bash
echo "$(minikube ip) spring.local vue.local" | sudo tee -a /etc/hosts
```

> With the docker driver on macOS/Windows, `minikube ip` is not reachable from the host. Run `minikube tunnel` in a separate terminal and use `127.0.0.1` in `/etc/hosts` instead.

Then:

```bash
curl http://spring.local/actuator/health
```

and open <http://vue.local> in the browser.

**Without an Ingress**, use port forwarding:

```bash
kubectl port-forward svc/spring 8080:8080
kubectl port-forward svc/vue-svc 9000:9000
```

---

## 5. HTTPS with cert-manager

### Install cert-manager

```bash
helm repo add jetstack https://charts.jetstack.io --force-update

helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --set crds.enabled=true

kubectl get pods -n cert-manager   # wait until all pods are Running
```

### Create the ClusterIssuer

[cert/Cluster-issuer.yaml](cert/Cluster-issuer.yaml) defines a **self-signed** issuer named `selfsigned-issuer`:

```bash
kubectl apply -f cert/Cluster-issuer.yaml
kubectl get clusterissuer
```

### Switch Spring to HTTPS

```bash
kubectl delete -f spring/spring-ingress.yaml
kubectl apply -f spring/spring-ingress-ssl.yaml
```

The `cert-manager.io/cluster-issuer` annotation on the Ingress makes cert-manager issue a certificate into the `spring-tls-secret` secret:

```bash
kubectl get certificate
curl -k https://spring.local/actuator/health   # -k: the certificate is self-signed
```

---

## 6. Kustomize demo

The [kustomization/](kustomization/) directory has a standalone base/overlay example (dev and production) with its own [README](kustomization/README.md).

```bash
kubectl kustomize kustomization/overlays/dev     # preview
kubectl apply -k kustomization/overlays/dev      # apply
```

> The demo's Ingress targets **Traefik**, not the NGINX ingress that minikube enables. The Deployment and Service work on minikube as is. To use the Ingress, install Traefik (`helm install traefik traefik/traefik` from the `https://traefik.github.io/charts` repo) or change the ingress class to `nginx`.

---

## 7. Useful tools

Tools that make working with a cluster easier than plain `kubectl`. All of them read your kubeconfig (`~/.kube/config`), so they work with minikube out of the box. Tools marked with ✅ are already installed in the dev container.

### Terminal

| Tool | What it does | Install / run |
| --- | --- | --- |
| [k9s](https://k9scli.io/) ✅ | Full-screen terminal UI: browse resources, view logs, exec into pods, port-forward, edit and delete — all with keyboard shortcuts | `k9s` (`:pods`, `:svc`, `:deploy`, `?` for help) |
| [k10s](https://github.com/p10node/k10s) | k9s-inspired terminal UI with mouse support, on-screen action hints and instant search (`ctrl+p`); optional AI assistant with your own API key. New project | `go install github.com/p10node/k10s@latest` |
| [sofka](https://github.com/nklmilojevic/sofka) | Rust terminal UI, a reimagining of k9s: bulk actions, background port-forwards, read-only mode, Helm inspector, Flux / Argo CD views. New project | `cargo install sofka` or prebuilt binary from GitHub releases |
| [kubectx / kubens](https://github.com/ahmetb/kubectx) | Switch quickly between clusters (contexts) and namespaces | `kubectx minikube`, `kubens kube-system` |
| [stern](https://github.com/stern/stern) | Tail logs from many pods at once, color-coded per pod | `stern spring` |
| [krew](https://krew.sigs.k8s.io/) | Plugin manager for `kubectl` | `kubectl krew install <plugin>` |
| [kube-ps1](https://github.com/jonmosco/kube-ps1) | Shows the current context and namespace in the shell prompt — helps avoid running commands against the wrong cluster | add to `~/.bashrc` |
| [fzf](https://github.com/junegunn/fzf) ✅ | Fuzzy finder; makes `kubectx` / `kubens` interactive | — |

### Graphical / web UIs

| Tool | What it does | Install / run |
| --- | --- | --- |
| [Headlamp](https://headlamp.dev/) | Open-source Kubernetes UI from Kubernetes SIG UI; desktop app or in-cluster web app, extensible with plugins | desktop app, or Helm chart in the cluster |
| [Radar](https://github.com/skyhook-io/radar) | Open-source web UI with a topology view, event timeline, resource browser, Helm and GitOps (Argo CD / Flux) views | `kubectl krew install radar` then `kubectl radar` (opens <http://localhost:9280>) |
| [Lens](https://k8slens.dev/) | Popular desktop IDE for Kubernetes; free for personal and education use, paid subscription for commercial use | desktop app |
| [Freelens](https://freelens.app/) | Free, open-source fork of Lens | desktop app |
| [Kubernetes Dashboard](https://github.com/kubernetes/dashboard) | The official web UI, shipped as a minikube addon | `minikube dashboard` (in Codespaces: `minikube dashboard --url`) |

> Desktop apps (Headlamp, Lens, Freelens) run on your own machine, so they cannot see a minikube cluster that lives inside a Codespace. There, prefer the terminal tools, Radar or `minikube dashboard` — Codespaces forwards their ports to the browser.

---

## Links

- [Set up Ingress on minikube](https://kubernetes.io/docs/tasks/access-application-cluster/ingress-minikube/)
- [cert-manager installation with Helm](https://cert-manager.io/docs/installation/helm/)
- [Troubleshooting cert-manager](https://cert-manager.io/docs/troubleshooting/)
- [Pull an image from a private registry](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/)
- [StatefulSets](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
- [Spring Boot health probes](https://www.baeldung.com/spring-liveness-readiness-probes)
- [Kustomize](https://kustomize.io/)
