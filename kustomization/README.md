# Kustomize Demo

A working example of [Kustomize](https://kustomize.io/) demonstrating base/overlay patterns with `commonLabels`, `namePrefix`, `nameSuffix`, `configMapGenerator` and patches.

The sample application is a plain `nginx:1.25-alpine` web service, independent of the [nginx/](../nginx/README.md) example. It is exposed through the Traefik ingress controller, with separate overlays for **dev** and **production**.

All commands below are run from the **repository root**, like in the other READMEs.

---

## Prerequisites

- A Kubernetes cluster with Traefik installed as the ingress controller (see [main README → Install the Traefik ingress controller](../README.md#install-the-traefik-ingress-controller))
- For the production overlay's TLS certificate: cert-manager and the `selfsigned-issuer` ClusterIssuer (see [cert/README.md](../cert/README.md)). Without them Traefik serves its built-in default certificate
- `kubectl` configured to point at the target cluster

---

## Installing Kustomize

### Option 1 — Standalone binary (recommended)

The standalone binary always ships the latest version of Kustomize and supports the full feature set including exec plugins and the `kustomize fn` pipeline. It is already installed in the dev container.

```bash
# Latest release (Linux amd64)
curl -s "https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh" | bash
sudo mv kustomize /usr/local/bin/

# Or via Homebrew (macOS / Linux)
brew install kustomize

# Verify
kustomize version
```

Usage:

```bash
kustomize build kustomization/overlays/dev | kubectl apply -f -
kustomize build kustomization/overlays/production | kubectl apply -f -
```

### Option 2 — `kubectl kustomize` (built-in)

`kubectl` ships with a bundled version of Kustomize (`kubectl kustomize` / `kubectl apply -k`). No extra installation is required if you already have `kubectl`.

```bash
kubectl kustomize kustomization/overlays/dev          # preview rendered manifests
kubectl apply -k kustomization/overlays/dev           # render and apply in one step
```

### Key differences

| | Standalone `kustomize` | `kubectl kustomize` / `apply -k` |
| --- | --- | --- |
| **Version** | Always latest | Bundled with kubectl, often lags 1–2 minor versions |
| **Features** | Full feature set, exec/go plugins, `kustomize fn` | Core features only; some newer fields unsupported |
| **Installation** | Separate binary required | Ships with `kubectl` |
| **Workflow** | `kustomize build … \| kubectl apply -f -` | `kubectl apply -k …` (one step) |
| **Dry-run** | `kustomize build …` (inspect output) | `kubectl apply -k … --dry-run=client` |
| **Recommended for** | CI pipelines, plugin use, GitOps | Quick local iteration |

> **Rule of thumb:** use the standalone binary in CI and wherever you need reproducible, version-pinned output. Use `kubectl apply -k` for interactive day-to-day work.

---

## Directory structure

```text
kustomization/
├── base/                        # Environment-agnostic manifests
│   ├── kustomization.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml             #   Traefik Ingress (ingressClassName: traefik, entrypoint web)
└── overlays/
    ├── dev/                     # Development overlay
    │   ├── kustomization.yaml   #   namePrefix, commonLabels, configMapGenerator, host patch
    │   ├── namespace.yaml       #   creates the dev namespace
    │   └── app.env              #   dev-specific env vars
    └── production/              # Production overlay
        ├── kustomization.yaml   #   nameSuffix, commonLabels, configMapGenerator, HTTPS/TLS patch
        ├── namespace.yaml       #   creates the production namespace
        ├── app.env              #   production env vars
        └── patch-replicas.yaml  #   strategic merge patch for resources/replicas
```

---

## Features demonstrated

| Feature | Where |
| --- | --- |
| `commonLabels` | base + both overlays |
| `namePrefix` | `overlays/dev` → resources become `dev-webapp`, `dev-webapp-config-<hash>`, … |
| `nameSuffix` | `overlays/production` → resources become `webapp-prod`, `webapp-config-<hash>-prod`, … |
| `configMapGenerator` (env file + literals) | both overlays |
| Strategic merge patch | `overlays/production/patch-replicas.yaml` |
| JSON 6902 patch | inline in overlay `kustomization.yaml` (Ingress host; production: HTTPS entrypoint, TLS, cert-manager annotation) |
| Namespace scoping | `namespace:` field in both overlays |

| Overlay | Namespace | Ingress host | Protocol |
| --- | --- | --- | --- |
| dev | `dev` | `dev.webapp.example.com` | HTTP (Traefik `web`, port 80) |
| production | `production` | `webapp.example.com` | HTTPS only (Traefik `websecure`, port 443), certificate `webapp-tls` from cert-manager |

---

## Usage

Preview rendered output without applying:

```bash
kustomize build kustomization/overlays/dev
kustomize build kustomization/overlays/production
```

Apply to a cluster:

```bash
kubectl apply -k kustomization/overlays/dev
kubectl apply -k kustomization/overlays/production
```

Check what was created:

```bash
kubectl get all,ingress,configmap -n dev
kubectl get all,ingress,configmap,certificate -n production
```

Diff against live cluster state:

```bash
kubectl diff -k kustomization/overlays/production
```

---

## Testing through Traefik

The same way as in [spring/README.md](../spring/README.md#testing). Forward Traefik's ports and leave the command running:

```bash
kubectl port-forward -n traefik svc/traefik 8080:80 8443:443
```

In a second terminal:

```bash
# dev: HTTP
curl --resolve dev.webapp.example.com:8080:127.0.0.1 http://dev.webapp.example.com:8080/

# production: HTTPS only (-k: self-signed certificate)
curl -k --resolve webapp.example.com:8443:127.0.0.1 https://webapp.example.com:8443/

# production over plain HTTP → 404, because the router listens only on websecure
curl -i --resolve webapp.example.com:8080:127.0.0.1 http://webapp.example.com:8080/
```

`--resolve` avoids editing `/etc/hosts`. Alternatively, add `127.0.0.1 dev.webapp.example.com webapp.example.com` to `/etc/hosts`.

---

## How `configMapGenerator` works

Kustomize generates a ConfigMap and appends a content hash to its name (e.g. `dev-webapp-config-6g7k9m2t`). Any Deployment or Pod that references that ConfigMap by its original name is automatically updated to use the hashed name. The practical effect: every config change produces a new ConfigMap name, which triggers a rolling update of the Deployment, so Pods always run with the config they were deployed with.

```text
base deployment  →  envFrom.configMapRef.name: webapp-config
                              ↓  kustomize rewrites automatically
dev output       →  envFrom.configMapRef.name: dev-webapp-config-6g7k9m2t
```

---

## Clean up

```bash
kubectl delete -k kustomization/overlays/dev
kubectl delete -k kustomization/overlays/production
```

This also deletes the `dev` and `production` namespaces, because each overlay creates its own.
