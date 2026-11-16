# Running the playground on MicroK8s

[MicroK8s](https://microk8s.io/) is a lightweight, single-package Kubernetes distribution from Canonical. It is an alternative to minikube when you work on a **Linux machine or VM** (e.g. an Ubuntu server or a cloud VM) and want a cluster that runs directly on the host, without Docker or an extra VM.

The manifests in this repository work unchanged on MicroK8s. Only the cluster setup and the way you reach Traefik differ from the minikube instructions in the [main README](README.md).

## Contents

1. [Install MicroK8s](#1-install-microk8s)
2. [Use kubectl and helm](#2-use-kubectl-and-helm)
3. [Enable the addons](#3-enable-the-addons)
4. [Install the Traefik ingress controller](#4-install-the-traefik-ingress-controller)
5. [Deploy and access the application](#5-deploy-and-access-the-application)
6. [Differences from minikube](#6-differences-from-minikube)
7. [Stop, reset and uninstall](#7-stop-reset-and-uninstall)

---

## 1. Install MicroK8s

### Ubuntu / Linux (snap)

MicroK8s is distributed as a snap. Pick the channel that matches the Kubernetes version used in this repository (1.37):

```bash
sudo snap install microk8s --classic --channel=1.37/stable
```

> `snap info microk8s` lists the available channels. Use `latest/stable` if you just want the newest release.

Allow your user to run `microk8s` without `sudo` and give it access to the kubeconfig directory:

```bash
sudo usermod -a -G microk8s $USER
mkdir -p ~/.kube
sudo chown -R $USER ~/.kube
newgrp microk8s          # or log out and back in
```

Wait until the cluster is ready:

```bash
microk8s status --wait-ready
```

### Windows / macOS

MicroK8s needs Linux. On Windows and macOS it runs inside a [Multipass](https://multipass.run/) VM:

```bash
# macOS
brew install ubuntu/microk8s/microk8s
microk8s install

# Windows: download and run the installer from https://microk8s.io/
```

Alternatively, create an Ubuntu VM yourself (`multipass launch --name microk8s-vm --cpus 2 --memory 4G --disk 20G`), open a shell in it (`multipass shell microk8s-vm`) and follow the Linux steps above.

> Recommended resources: at least 2 CPUs, 4 GB RAM and 20 GB disk.

---

## 2. Use kubectl and helm

MicroK8s ships its own `kubectl` and `helm`:

```bash
microk8s kubectl get nodes
microk8s helm version
```

The commands in this repository use plain `kubectl` and `helm`. Choose **one** of these options:

**Option A — aliases** (simplest, nothing else to install):

```bash
echo "alias kubectl='microk8s kubectl'" >> ~/.bashrc
echo "alias helm='microk8s helm'" >> ~/.bashrc
source ~/.bashrc
```

**Option B — your own kubectl/helm** (needed for tools such as k9s, kubectx or Headlamp, which read `~/.kube/config`). Export the MicroK8s kubeconfig:

```bash
microk8s config > ~/.kube/config     # overwrites an existing config!
kubectl get nodes
```

> If you already have a kubeconfig (e.g. for minikube), merge instead of overwriting:
>
> ```bash
> microk8s config > ~/.kube/microk8s.config
> KUBECONFIG=~/.kube/config:~/.kube/microk8s.config kubectl config view --flatten > ~/.kube/merged
> mv ~/.kube/merged ~/.kube/config
> kubectl config use-context microk8s
> ```

---

## 3. Enable the addons

A fresh MicroK8s cluster is minimal. Enable the addons this repository needs:

```bash
microk8s enable dns                 # CoreDNS — Services are found by name (e.g. spring → postgres)
microk8s enable hostpath-storage    # default StorageClass — required by the postgres StatefulSet and the nginx PVC
microk8s enable helm3               # only if microk8s helm is not already available
```

Verify:

```bash
kubectl get pods -n kube-system     # coredns and hostpath-provisioner are Running
kubectl get storageclass            # microk8s-hostpath (default)
```

Optional addons:

| Addon | When you need it |
| --- | --- |
| `metallb` | to give the Traefik `LoadBalancer` Service a real external IP (see [section 5](#5-deploy-and-access-the-application)) |
| `dashboard` | the Kubernetes Dashboard web UI |
| `metrics-server` | `kubectl top pods` / `kubectl top nodes` |

> **Do not enable the `ingress` addon.** Since MicroK8s 1.35 it installs Traefik (older releases installed the NGINX ingress controller), but as a separate instance that is managed by MicroK8s. This guide installs Traefik with Helm in [section 4](#4-install-the-traefik-ingress-controller), the same way as on minikube and Codespaces. Two controllers would compete for the same Ingresses. If you enabled it already: `microk8s disable ingress`.
>
> The `cert-manager` addon exists too, but the [cert/README.md](cert/README.md) instructions install cert-manager with Helm — follow those so the version matches.

---

## 4. Install the Traefik ingress controller

Same as on minikube:

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
kubectl get svc -n traefik traefik   # type LoadBalancer
```

Without MetalLB the Traefik Service's `EXTERNAL-IP` stays `<pending>`. That is expected — see the next section for ways to reach it.

---

## 5. Deploy and access the application

Create the image pull secret ([README section 2](README.md#2-container-registry-github-packages)) and deploy the tiers exactly as in [README section 3](README.md#3-deploy-the-application).

Then pick one way to reach Traefik:

### Option A — Port-forward (works everywhere)

Identical to [README section 4, Option A](README.md#option-a--port-forward-to-traefik-works-everywhere-including-codespaces):

```bash
kubectl port-forward -n traefik svc/traefik 8080:80 8443:443
echo "127.0.0.1 spring.local vue.local" | sudo tee -a /etc/hosts
curl http://spring.local:8080/actuator/health
```

### Option B — MetalLB (replaces `minikube tunnel`)

MetalLB hands out IPs from a range you choose to `LoadBalancer` Services. Pick a few free addresses on the host's network (or the VM's network), for example:

```bash
microk8s enable metallb:10.64.140.43-10.64.140.49
kubectl get svc -n traefik traefik      # EXTERNAL-IP is now e.g. 10.64.140.43
echo "10.64.140.43 spring.local vue.local" | sudo tee -a /etc/hosts
curl http://spring.local/actuator/health
```

> On a laptop, any unused range in the host's subnet works. In a Multipass VM, use addresses from the VM's subnet (`multipass info microk8s-vm` shows its IP) and add the `/etc/hosts` entry on the machine where you run `curl` or the browser.

### Option C — NodePort

Expose Traefik directly on the node's IP, on ports 30000–32767:

```bash
helm upgrade --install traefik traefik/traefik -n traefik \
  --set service.type=NodePort \
  --set ports.web.nodePort=30080 \
  --set ports.websecure.nodePort=30443

curl --resolve spring.local:30080:127.0.0.1 http://spring.local:30080/actuator/health
```

---

## 6. Differences from minikube

| Topic | minikube | MicroK8s |
| --- | --- | --- |
| Runs on | Docker container or VM, any OS | Directly on Linux (snap); Multipass VM on Windows/macOS |
| Start / stop | `minikube start` / `minikube stop` | `microk8s start` / `microk8s stop` |
| kubectl | your own `kubectl` | `microk8s kubectl`, or export `microk8s config` |
| Default StorageClass | `standard` (built in) | `microk8s-hostpath` — enable `hostpath-storage` |
| DNS | built in | enable `dns` |
| LoadBalancer IP | `minikube tunnel` | `metallb` addon |
| Dashboard | `minikube dashboard` | `microk8s enable dashboard` then `microk8s dashboard-proxy` |
| Local images | `minikube image load <img>` | `docker save <img> \| microk8s ctr image import -` |
| Multi-node | `minikube start --nodes 2` | `microk8s add-node` on one machine, `microk8s join ...` on the others |

---

## 7. Stop, reset and uninstall

```bash
microk8s stop                    # stop the cluster (frees CPU/RAM); microk8s start to resume
microk8s reset                   # remove all workloads and addons, keep MicroK8s installed
sudo snap remove microk8s        # uninstall completely
```

---

## Links

- [MicroK8s: getting started](https://microk8s.io/docs/getting-started)
- [MicroK8s: addons](https://microk8s.io/docs/addons)
- [MicroK8s: MetalLB addon](https://microk8s.io/docs/addon-metallb)
- [MicroK8s: working with kubectl](https://microk8s.io/docs/working-with-kubectl)
- [MicroK8s on Windows / macOS](https://microk8s.io/docs/install-alternatives)
