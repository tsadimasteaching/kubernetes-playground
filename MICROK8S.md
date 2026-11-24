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

MicroK8s is distributed as a snap. This guide uses the 1.36 channel (the minikube and Codespaces setups in this repository use Kubernetes 1.37):

```bash
sudo snap install microk8s --classic --channel=1.36/stable
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
microk8s enable hostpath-storage    # default StorageClass — required by the postgres StatefulSet
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
| `metrics-server` | `kubectl top pods` / `kubectl top nodes` |

For a web UI, install [Headlamp](https://headlamp.dev/) with Helm. The `dashboard` addon is not available from MicroK8s 1.36 on, and the Kubernetes Dashboard project was archived in January 2026; Headlamp, from Kubernetes SIG UI, is its successor.

```bash
helm repo add headlamp https://kubernetes-sigs.github.io/headlamp/ --force-update
helm upgrade --install headlamp headlamp/headlamp --namespace kube-system
kubectl create token headlamp -n kube-system          # paste this token into the login page
kubectl port-forward -n kube-system svc/headlamp 8081:80
# open http://localhost:8081
```

The chart binds the `headlamp` ServiceAccount to `cluster-admin`, which is fine for a lab cluster. Port 8081 leaves 8080 free for the Traefik port-forward of section 5.

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
kubectl port-forward -n traefik svc/traefik 8080:80 8443:443     # keep it running
```

In a second terminal:

```bash
echo "127.0.0.1 vue.local" | sudo tee -a /etc/hosts
curl -si http://vue.local:8080/api/me | head -1   # 401 + WWW-Authenticate: Bearer = Spring answered (login: Keycloak.md)
```

### Option B — MetalLB (replaces `minikube tunnel`)

MetalLB hands out IPs from a range you choose to `LoadBalancer` Services. Traefik's Service is already of that type, so the address range is the only thing to configure.

First find the node's subnet:

```bash
ip -4 addr show      # e.g. inet 192.168.1.50/24 on wlan0 → subnet 192.168.1.0/24
ip route             # default via 192.168.1.1 dev wlan0
```

Pick a few addresses in that subnet that nothing else uses and that are outside the router's DHCP pool (often .100–.200, so e.g. .240–.250). `ping -c1 192.168.1.240` should get no reply. Then:

```bash
microk8s enable metallb:192.168.1.240-192.168.1.250
kubectl get svc -n traefik traefik      # EXTERNAL-IP is now e.g. 192.168.1.240
echo "192.168.1.240 vue.local" | sudo tee -a /etc/hosts
curl -si http://vue.local/api/me | head -1   # 401 + WWW-Authenticate: Bearer = Spring answered (login: Keycloak.md)
```

The addon also creates the `IPAddressPool` and `L2Advertisement` (layer 2 mode) in the `metallb-system` namespace, so nothing else is needed.

> - Use the subnet of *your* network. An address outside it can still answer on the MicroK8s host itself, but other machines cannot reach it.
> - In a Multipass VM, use addresses from the VM's subnet (`multipass info microk8s-vm` shows its IP).
> - Add the `/etc/hosts` entry on the machine where you run `curl` or the browser.
> - Some Wi-Fi access points and university or corporate networks block the ARP replies that layer 2 mode relies on. If the IP works on the host but not from other devices, that is the likely cause.
> - To change the range later: `microk8s disable metallb`, then enable it again with the new range.
> - **Not on cloud VMs** (GCP, AWS, Azure): the cloud network only delivers traffic to IPs it assigned to the VM and ignores MetalLB's ARP replies. Use [Option C](#option-c--cloud-vm-node-ip-as-external-ip) instead. Never give MetalLB the node's own IP.

### Option C — Cloud VM: node IP as external IP

On a cloud VM (a `/32` address on `ens4`/`eth0` in `ip -4 addr show` is a typical sign), skip MetalLB (`microk8s disable metallb` if you enabled it) and let Traefik's Service accept traffic sent to the VM's internal IP. kube-proxy then forwards ports 80 and 443 on that IP to Traefik:

```bash
ip -4 addr show                         # e.g. inet 10.166.0.2/32 on ens4

helm upgrade --install traefik traefik/traefik -n traefik \
  --set "service.spec.externalIPs={10.166.0.2}" \
  --wait

kubectl get svc -n traefik traefik      # EXTERNAL-IP shows 10.166.0.2
curl -si -H "Host: vue.local" http://10.166.0.2/api/me | head -1   # 401 + WWW-Authenticate: Bearer = Spring answered (login: Keycloak.md)
```

> Extra Service fields go under `service.spec` — the chart rejects `service.externalIPs` or `service.type`.

To reach it from your laptop:

1. Allow `tcp:80,443` to the VM in the cloud firewall (GCP: tick *Allow HTTP/HTTPS traffic* on the VM, or add a firewall rule).
2. Use the VM's **external** IP, which the cloud maps to the internal one:

```bash
echo "<VM external IP> vue.local" | sudo tee -a /etc/hosts
curl -si http://vue.local/api/me | head -1   # 401 + WWW-Authenticate: Bearer = Spring answered (login: Keycloak.md)
```

With a DNS name pointing at the VM's external IP you can also serve the app publicly over HTTPS with a Let's Encrypt certificate: see [cert/README.md](cert/README.md).

### Option D — NodePort

Expose Traefik directly on the node's IP, on ports 30000–32767:

```bash
helm upgrade --install traefik traefik/traefik -n traefik \
  --set service.spec.type=NodePort \
  --set ports.web.nodePort=30080 \
  --set ports.websecure.nodePort=30443

curl -si --resolve vue.local:30080:127.0.0.1 http://vue.local:30080/api/me | head -1   # 401 + WWW-Authenticate: Bearer = Spring answered (login: Keycloak.md)
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
| LoadBalancer IP | `minikube tunnel` | `metallb` addon (on cloud VMs: the node IP as `externalIPs`) |
| Web UI | `minikube dashboard` | Headlamp with Helm (see [section 3](#3-enable-the-addons)) |
| Local images | `minikube image build` / `minikube image load <img>` | build with Docker (on the host or elsewhere), then `docker save <img> \| microk8s ctr image import -`. MicroK8s itself does not include Docker |
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
