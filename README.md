# Kubernetes Playground

Hands-on Kubernetes manifests for a three-tier application — **PostgreSQL**, a **Spring Boot** backend and a **Vue** frontend — running on [minikube](https://minikube.sigs.k8s.io/), plus a [Kustomize](kustomization/README.md) base/overlay demo.

## Repository structure

```text
.
├── .devcontainer/      # Dev container / GitHub Codespaces setup
├── cert/               # cert-manager ClusterIssuer (self-signed)
├── postgres/           # PVC, Deployment, Service for PostgreSQL
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

---

## 1. Environment setup

### Option A — Dev container / Codespaces (recommended)

Open the repository in GitHub Codespaces or in VS Code with *Dev Containers: Reopen in Container*. The container:

- is based on **Ubuntu 24.04** (`devcontainers/base:ubuntu-24.04`) and installs `docker` (docker-in-docker), `kubectl` 1.37, `helm` and `minikube` via devcontainer features
- installs `k9s`, `kustomize` and `fzf` ([startup_script.sh](.devcontainer/startup_script.sh))
- runs `minikube start --kubernetes-version=v1.37.0` automatically on attach
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

In both cases, enable the NGINX ingress addon:

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
kubectl get pods -w          # wait until postgres is Running

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

## Links

- [Set up Ingress on minikube](https://kubernetes.io/docs/tasks/access-application-cluster/ingress-minikube/)
- [cert-manager installation with Helm](https://cert-manager.io/docs/installation/helm/)
- [Troubleshooting cert-manager](https://cert-manager.io/docs/troubleshooting/)
- [Pull an image from a private registry](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/)
- [Spring Boot health probes](https://www.baeldung.com/spring-liveness-readiness-probes)
- [Kustomize](https://kustomize.io/)
