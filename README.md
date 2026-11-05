# Kubernetes Playground

Hands-on Kubernetes manifests for a three-tier application — **PostgreSQL**, a **Spring Boot** backend and a **Vue** frontend — running on [minikube](https://minikube.sigs.k8s.io/), plus a [Kustomize](kustomization/README.md) base/overlay demo.

## Repository structure

```text
.
├── .devcontainer/      # Dev container / GitHub Codespaces setup
├── cert/               # cert-manager ClusterIssuers — see cert/README.md
├── nginx/              # Minimal Deployment + Service + PVC example — see nginx/README.md
├── postgres/           # StatefulSet and Services for PostgreSQL — see postgres/README.md
├── spring/             # Spring Boot Deployment, Service, Ingress (HTTP and HTTPS) — see spring/README.md
├── vue/                # Vue frontend Deployment, Service, Ingress — see vue/README.md
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
- on every container start, once Docker is ready, starts minikube (Kubernetes v1.37.0), makes sure minikube's NGINX ingress addon is disabled and installs the Traefik ingress controller with Helm ([start_minikube.sh](.devcontainer/start_minikube.sh)). If the cluster is not up, check the log with *Codespaces: View Creation Log* or run `bash .devcontainer/start_minikube.sh`
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

### Install the Traefik ingress controller

An `Ingress` is only a set of routing rules. An **ingress controller** reads those rules and actually serves the traffic. This repository uses [Traefik](https://doc.traefik.io/traefik/), and every Ingress here sets `ingressClassName: traefik`.

In the dev container Traefik is installed automatically. On a local machine:

```bash
helm repo add traefik https://traefik.github.io/charts --force-update

helm upgrade --install traefik traefik/traefik \
  --namespace traefik \
  --create-namespace \
  --wait
```

Verify:

```bash
kubectl get pods -n traefik          # traefik-xxxxx   1/1   Running
kubectl get ingressclass             # traefik   traefik.io/ingress-controller
kubectl get svc -n traefik traefik   # type LoadBalancer, ports 80 and 443
```

The chart's defaults are enough here:

| Default | Meaning |
| --- | --- |
| IngressClass `traefik`, marked as default | Ingresses with `ingressClassName: traefik`, or with no class at all, are served by Traefik |
| entrypoint `web` → port 80 | HTTP. Selected with the annotation `traefik.ingress.kubernetes.io/router.entrypoints: web` |
| entrypoint `websecure` → port 443 | HTTPS. Selected with `router.entrypoints: websecure` and `router.tls: "true"` |
| Service type `LoadBalancer` | on minikube it gets an external IP only while `minikube tunnel` runs (see [section 4](#4-access-the-application)) |

> Use only **one** ingress controller. If you enabled the NGINX addon earlier (`minikube addons enable ingress`), disable it. Otherwise two controllers compete for the same Ingresses. The dev container does this automatically. On a local machine:
>
> ```bash
> minikube addons disable ingress
> ```
>
> To re-run the dev container's setup by hand (start minikube, disable NGINX, install Traefik): `bash .devcontainer/start_minikube.sh`.

To remove Traefik: `helm uninstall traefik -n traefik`.

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

### Per-component details

Each directory has its own README with prerequisites, testing and troubleshooting:

| Component | README | Highlights |
| --- | --- | --- |
| PostgreSQL | [postgres/README.md](postgres/README.md) | why it runs as a **StatefulSet**, connecting with `psql`, data persistence |
| Spring backend | [spring/README.md](spring/README.md) | probes, how the HTTP/HTTPS **Ingresses** work and how to test them |
| Vue frontend | [vue/README.md](vue/README.md) | Ingress for `vue.local`, testing with and without Traefik |
| TLS | [cert/README.md](cert/README.md) | cert-manager step by step |
| nginx (standalone) | [nginx/README.md](nginx/README.md) | Deployment + PVC example, no Ingress |

---

## 4. Access the application

Traefik routes requests by **hostname**:

| Host | Ingress | Service |
| --- | --- | --- |
| `spring.local` | `spring-ingress` (HTTP) or `spring-ingress-ssl` (HTTPS) | `spring` (port 8080) |
| `vue.local` | `vue-ingress` (HTTP) | `vue-svc` (port 9000) |

### Option A — Port-forward to Traefik (works everywhere, including Codespaces)

```bash
kubectl port-forward -n traefik svc/traefik 8080:80 8443:443
```

In a second terminal, point the hostnames at your machine and send requests through Traefik:

```bash
echo "127.0.0.1 spring.local vue.local" | sudo tee -a /etc/hosts

curl http://spring.local:8080/actuator/health
curl http://vue.local:8080/
```

On a local machine you can also open <http://vue.local:8080> in the browser.

> In Codespaces, the forwarded URL (`https://<name>-8080.app.github.dev`) sends a different `Host` header, so Traefik's host rules do not match. Test from the Codespace terminal with `curl` as above, or use Option C.

### Option B — `minikube tunnel` (local machine, standard ports)

```bash
minikube tunnel                       # keep it running in its own terminal
kubectl get svc -n traefik traefik    # note the EXTERNAL-IP
echo "<EXTERNAL-IP> spring.local vue.local" | sudo tee -a /etc/hosts
curl http://spring.local/actuator/health
```

### Option C — Without an Ingress

Port-forward directly to the Services, bypassing Traefik. Each command keeps running, so use a separate terminal for each, and stop the Traefik port-forward first because it also uses 8080:

```bash
kubectl port-forward svc/spring 8080:8080      # terminal 1 → http://localhost:8080/actuator/health
kubectl port-forward svc/vue-svc 9000:9000     # terminal 2 → http://localhost:9000/
```

---

## 5. HTTPS with cert-manager

The step-by-step guide is in **[cert/README.md](cert/README.md)**: installing cert-manager with Helm, the ClusterIssuer, issuing a certificate through an Ingress, testing and troubleshooting. In short:

```bash
helm repo add jetstack https://charts.jetstack.io --force-update
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --version v1.21.3 --set crds.enabled=true

kubectl apply -f cert/cluster-issuer.yaml                    # self-signed ClusterIssuer

kubectl delete -f spring/spring-ingress.yaml --ignore-not-found
kubectl apply -f spring/spring-ingress-ssl.yaml              # HTTPS Ingress for spring.local
kubectl get certificate                                      # spring-tls-secret → READY True

kubectl port-forward -n traefik svc/traefik 8443:443 &
curl -k --resolve spring.local:8443:127.0.0.1 https://spring.local:8443/actuator/health
```

---

## 6. Kustomize demo

The [kustomization/](kustomization/) directory has a standalone base/overlay example (dev and production) with its own [README](kustomization/README.md).

```bash
kubectl kustomize kustomization/overlays/dev     # preview
kubectl apply -k kustomization/overlays/dev      # apply
```

> The demo's Ingress uses the same Traefik controller (`ingressClassName: traefik`). The production overlay serves HTTPS only and gets its certificate from cert-manager ([cert/README.md](cert/README.md#optional--kustomize-production-overlay)). Test it through the Traefik port-forward, e.g. `curl --resolve dev.webapp.example.com:8080:127.0.0.1 http://dev.webapp.example.com:8080/`.

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

- [Traefik: install with Helm](https://doc.traefik.io/traefik/getting-started/install-traefik/)
- [Traefik: Kubernetes Ingress provider and annotations](https://doc.traefik.io/traefik/providers/kubernetes-ingress/)
- [cert-manager installation with Helm](https://cert-manager.io/docs/installation/helm/)
- [Troubleshooting cert-manager](https://cert-manager.io/docs/troubleshooting/)
- [Pull an image from a private registry](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/)
- [StatefulSets](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
- [Spring Boot health probes](https://www.baeldung.com/spring-liveness-readiness-probes)
- [Kustomize](https://kustomize.io/)
