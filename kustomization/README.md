# Kustomize Demo

A working example of [Kustomize](https://kustomize.io/) demonstrating base/overlay patterns with `commonLabels`, `namePrefix`, `nameSuffix`, and `configMapGenerator`.

The sample application is a simple nginx-based web service exposed via [Traefik](https://traefik.io/) ingress, with separate overlays for **dev** and **production**.

---

## Installing Kustomize

### Option 1 — Standalone binary (recommended)

The standalone binary always ships the latest version of Kustomize and supports the full feature set including exec plugins and the `kustomize fn` pipeline.

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
kustomize build overlays/dev | kubectl apply -f -
kustomize build overlays/production | kubectl apply -f -
```

---

### Option 2 — `kubectl kustomize` (built-in)

`kubectl` ships with a bundled version of Kustomize (`kubectl kustomize` / `kubectl apply -k`). No extra installation is required if you already have `kubectl`.

```bash
kubectl kustomize overlays/dev          # preview rendered manifests
kubectl apply -k overlays/dev           # render and apply in one step
```

---

### Key differences

| | Standalone `kustomize` | `kubectl kustomize` / `apply -k` |
|---|---|---|
| **Version** | Always latest | Bundled with kubectl — often lags 1–2 minor versions |
| **Features** | Full feature set, exec/go plugins, `kustomize fn` | Core features only; some newer fields unsupported |
| **Installation** | Separate binary required | Ships with `kubectl` |
| **Workflow** | `kustomize build … \| kubectl apply -f -` | `kubectl apply -k …` (one step) |
| **Dry-run** | `kustomize build …` (inspect output) | `kubectl apply -k … --dry-run=client` |
| **Recommended for** | CI pipelines, plugin use, GitOps | Quick local iteration |

> **Rule of thumb:** use the standalone binary in CI and wherever you need reproducible, version-pinned output. Use `kubectl apply -k` for interactive day-to-day work.

---

## Directory structure

```
.
├── base/                        # Environment-agnostic manifests
│   ├── kustomization.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml
└── overlays/
    ├── dev/                     # Development overlay
    │   ├── kustomization.yaml   #   namePrefix, commonLabels, configMapGenerator
    │   ├── namespace.yaml       #   creates the dev namespace
    │   └── app.env              #   dev-specific env vars
    └── production/              # Production overlay
        ├── kustomization.yaml   #   nameSuffix, commonLabels, configMapGenerator
        ├── namespace.yaml       #   creates the production namespace
        ├── app.env              #   production env vars
        └── patch-replicas.yaml  #   strategic merge patch for resources/replicas
```

---

## Features demonstrated

| Feature | Where |
|---|---|
| `commonLabels` | base + both overlays |
| `namePrefix` | `overlays/dev` → resources become `dev-webapp`, `dev-webapp-config-<hash>`, … |
| `nameSuffix` | `overlays/production` → resources become `webapp-prod`, `webapp-config-<hash>-prod`, … |
| `configMapGenerator` (env file + literals) | both overlays |
| Strategic merge patch | `overlays/production/patch-replicas.yaml` |
| JSON 6902 patch | inline in overlay `kustomization.yaml` |
| Namespace scoping | `namespace:` field in both overlays |

---

## Usage

Preview rendered output without applying:

```bash
kustomize build overlays/dev
kustomize build overlays/production
```

Apply to a cluster:

```bash
kubectl apply -k overlays/dev
kubectl apply -k overlays/production
```

Diff against live cluster state:

```bash
kubectl diff -k overlays/production
```

---

## How `configMapGenerator` works

Kustomize generates a ConfigMap and appends a content hash to its name (e.g. `dev-webapp-config-6g7k9m2t`). Any Deployment or Pod that references that ConfigMap by its original name is automatically updated to use the hashed name. The practical effect: every config change produces a new ConfigMap name, which triggers a rolling update of the Deployment — ensuring Pods always run with the config they were deployed with.

```
base deployment  →  envFrom.configMapRef.name: webapp-config
                              ↓  kustomize rewrites automatically
dev output       →  envFrom.configMapRef.name: dev-webapp-config-6g7k9m2t
```

---

## Prerequisites

- A Kubernetes cluster with Traefik installed as the ingress controller
- `kubectl` configured to point at the target cluster
- DNS entries (or `/etc/hosts` for local) pointing overlay hostnames at your cluster
