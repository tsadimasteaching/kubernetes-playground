# cert-manager: TLS certificates for Ingresses

[cert-manager](https://cert-manager.io/) is a Kubernetes controller that **issues and renews TLS certificates automatically**. You describe *who* signs certificates (an `Issuer` / `ClusterIssuer`). You then ask for a certificate, either with a `Certificate` resource or with an annotation on an Ingress. cert-manager stores the result in a `Secret` that the ingress controller (Traefik) uses for HTTPS.

```text
Ingress (annotation + spec.tls)
        │  cert-manager notices the annotation
        ▼
Certificate ──► CertificateRequest ──► ClusterIssuer (selfsigned-issuer)
        │
        ▼
Secret spring-tls-secret (tls.crt, tls.key) ──► used by Traefik for https://spring.local
```

## Files in this directory

| File | What it is | Where it works |
| --- | --- | --- |
| [cluster-issuer.yaml](cluster-issuer.yaml) | `ClusterIssuer` **selfsigned-issuer**: every certificate signs itself | everywhere, including minikube and Codespaces (no internet or DNS needed) |
| [letsencrypt-staging-issuer.yaml](letsencrypt-staging-issuer.yaml) | `ClusterIssuer` **letsencrypt-staging**: real ACME certificates from Let's Encrypt, HTTP-01 challenge through Traefik | only on clusters with a public IP and a real DNS name. Does **not** work on minikube |

Used by:

- [spring/spring-ingress-ssl.yaml](../spring/spring-ingress-ssl.yaml): `selfsigned-issuer` → secret `spring-tls-secret`
- [kustomization/overlays/production](../kustomization/overlays/production/kustomization.yaml): `selfsigned-issuer` → secret `webapp-tls`

---

## Prerequisites

- A running cluster, e.g. minikube. In the dev container it starts automatically.
- `kubectl` and `helm`.
- The **Traefik** ingress controller (see [main README → Install the Traefik ingress controller](../README.md#install-the-traefik-ingress-controller)). Check it:

  ```bash
  kubectl get ingressclass          # should list "traefik"
  ```

- The Spring application deployed (see [main README → Deploy the application](../README.md#3-deploy-the-application)).

Run all commands below from the **repository root**.

---

## Step 1 — Add the Helm repository

```bash
helm repo add jetstack https://charts.jetstack.io --force-update
helm search repo jetstack/cert-manager      # shows the latest chart version
```

## Step 2 — Install cert-manager

```bash
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --version v1.21.3 \
  --set crds.enabled=true
```

- `--set crds.enabled=true` also installs the **CRDs** (`Certificate`, `Issuer`, `ClusterIssuer`, …). Without them Kubernetes does not know these resource types. Older guides use `installCRDs=true`, which is deprecated.
- `--version` pins the version so every student gets the same result. Drop it to get the latest.

## Step 3 — Verify the installation

```bash
kubectl get pods -n cert-manager
```

Wait until all three pods are `Running` and `1/1`:

```text
NAME                                       READY   STATUS
cert-manager-xxxxxxxxxx-xxxxx              1/1     Running
cert-manager-cainjector-xxxxxxxxxx-xxxxx   1/1     Running
cert-manager-webhook-xxxxxxxxxx-xxxxx      1/1     Running
```

```bash
kubectl get crds | grep cert-manager.io     # certificates, issuers, clusterissuers, ...
```

> If Step 4 fails with `failed calling webhook "webhook.cert-manager.io"`, the webhook is not ready yet. Wait 30 seconds and try again.

## Step 4 — Create the ClusterIssuer

A `ClusterIssuer` can be used from **any namespace**. An `Issuer` only works in its own namespace.

```bash
kubectl apply -f cert/cluster-issuer.yaml
kubectl get clusterissuer
```

```text
NAME                READY   AGE
selfsigned-issuer   True    5s
```

## Step 5 — Request a certificate through an Ingress

[spring/spring-ingress-ssl.yaml](../spring/spring-ingress-ssl.yaml) is the HTTPS version of the Spring Ingress. The parts that matter:

```yaml
metadata:
  annotations:
    cert-manager.io/cluster-issuer: selfsigned-issuer        # cert-manager: which issuer signs
    traefik.ingress.kubernetes.io/router.entrypoints: websecure   # Traefik: listen on 443
    traefik.ingress.kubernetes.io/router.tls: "true"              # Traefik: terminate TLS
spec:
  ingressClassName: traefik
  tls:
    - hosts:
        - spring.local               # the certificate is issued for this name
      secretName: spring-tls-secret  # cert-manager writes the certificate here
```

Replace the HTTP Ingress with the HTTPS one, since both use the same host:

```bash
kubectl delete -f spring/spring-ingress.yaml --ignore-not-found
kubectl apply -f spring/spring-ingress-ssl.yaml
```

## Step 6 — Check the certificate

cert-manager created a `Certificate` named after the secret:

```bash
kubectl get certificate
```

```text
NAME                READY   SECRET              AGE
spring-tls-secret   True    spring-tls-secret   10s
```

```bash
kubectl describe certificate spring-tls-secret     # events: Issuing → Issued
kubectl get secret spring-tls-secret               # type kubernetes.io/tls
```

## Step 7 — Test HTTPS

Forward the Traefik HTTPS port to your machine:

```bash
kubectl port-forward -n traefik svc/traefik 8443:443
```

In a second terminal:

```bash
# --resolve maps spring.local to 127.0.0.1 without editing /etc/hosts
# -k accepts the self-signed certificate
curl -k --resolve spring.local:8443:127.0.0.1 https://spring.local:8443/actuator/health

# show the certificate that Traefik serves
openssl s_client -connect 127.0.0.1:8443 -servername spring.local </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates
```

The subject should contain `spring.local`. A browser shows a warning, because a self-signed certificate is not trusted by any CA. That is expected.

---

## Optional — Kustomize production overlay

The production overlay of the [Kustomize demo](../kustomization/README.md) uses the same issuer:

```bash
kubectl apply -k kustomization/overlays/production
kubectl get certificate -n production       # webapp-tls → READY True
```

## Optional — Let's Encrypt on a real cluster

On a cluster with a public IP, where a DNS name such as `app.example.com` points at the Traefik LoadBalancer:

1. Put your email in [letsencrypt-staging-issuer.yaml](letsencrypt-staging-issuer.yaml) and apply it:

   ```bash
   kubectl apply -f cert/letsencrypt-staging-issuer.yaml
   ```

2. In the Ingress, set `cert-manager.io/cluster-issuer: letsencrypt-staging` and use the real hostname in `spec.tls` and `spec.rules`.
3. When the staging certificate is issued, switch the issuer's `server` to `https://acme-v02.api.letsencrypt.org/directory` (production). Staging exists because production has strict rate limits.

---

## Troubleshooting

Follow the chain from top to bottom. The first resource that is not `Ready` tells you where the problem is:

```bash
kubectl get certificate,certificaterequest,order,challenge -A
kubectl describe certificate <name>
kubectl logs -n cert-manager deploy/cert-manager
```

| Symptom | Likely cause |
| --- | --- |
| No `Certificate` appears | annotation missing or misspelled, or `spec.tls` missing from the Ingress |
| `Certificate` stuck at `READY False` | issuer name wrong (`kubectl get clusterissuer`), or the issuer itself is not Ready |
| `failed calling webhook` on apply | cert-manager webhook not ready yet. Wait and retry |
| Browser or curl shows `TRAEFIK DEFAULT CERT` | the certificate is not issued yet (`kubectl get certificate`), or the Ingress was applied before cert-manager was installed. Delete and re-apply the Ingress |
| Let's Encrypt `Challenge` pending | DNS does not point at Traefik, or port 80 is not reachable from the internet |

See also the official [troubleshooting guide](https://cert-manager.io/docs/troubleshooting/).

## Uninstall

```bash
kubectl delete -f spring/spring-ingress-ssl.yaml --ignore-not-found
kubectl delete -f cert/cluster-issuer.yaml
helm uninstall cert-manager -n cert-manager
kubectl delete namespace cert-manager
```

> `helm uninstall` keeps the CRDs, to protect existing certificates. To remove them as well, run `kubectl get crds -o name | grep cert-manager.io | xargs kubectl delete`. This deletes **every** cert-manager resource in the cluster.

## Links

- [cert-manager: install with Helm](https://cert-manager.io/docs/installation/helm/)
- [cert-manager: securing Ingress resources](https://cert-manager.io/docs/usage/ingress/)
- [cert-manager: troubleshooting](https://cert-manager.io/docs/troubleshooting/)
- [Traefik: Kubernetes Ingress provider](https://doc.traefik.io/traefik/providers/kubernetes-ingress/)
- [Let's Encrypt: staging environment](https://letsencrypt.org/docs/staging-environment/)
