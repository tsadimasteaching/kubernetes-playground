# Spring Boot backend: Deployment + Service + Ingress

The Spring Boot REST backend of the application. It stores its data in the PostgreSQL database from [postgres/](../postgres/README.md) and is exposed outside the cluster through the Traefik ingress controller.

- `spring-deployment.yaml` – the Deployment: image `ghcr.io/tsadimas/ds-lab-2024`, database connection through environment variables, liveness/readiness probes on Spring Boot Actuator, resource limits, `imagePullSecrets: registry-credentials`
- `spring-svc.yaml` – ClusterIP Service `spring`, port 8080 (port name `spring`)
- `spring-ingress.yaml` – **HTTP** Ingress for `spring.local` (Traefik entrypoint `web`, port 80)
- `spring-ingress-ssl.yaml` – **HTTPS** Ingress for `spring.local` (Traefik entrypoint `websecure`, port 443) with a certificate from cert-manager

```text
client ──► Traefik (Ingress controller) ──► Service spring:8080 ──► Pod spring ──► Service postgres:5432 ──► postgres-0
            Host: spring.local
```

## Prerequisites

| What | Why | How to check |
| --- | --- | --- |
| PostgreSQL running | Spring connects to `jdbc:postgresql://postgres:5432/dsdb` on startup and fails without it | `kubectl rollout status statefulset/postgres` ([postgres/README.md](../postgres/README.md)) |
| Secret `registry-credentials` | needed only if the image is **private**. For a public image Kubernetes logs a warning and pulls anonymously | `kubectl get secret registry-credentials` ([main README → Container registry](../README.md#2-container-registry-github-packages)) |
| Traefik ingress controller | serves the Ingresses (`ingressClassName: traefik`) | `kubectl get ingressclass` ([main README → Install Traefik](../README.md#install-the-traefik-ingress-controller)) |
| cert-manager + `selfsigned-issuer` | only for the **HTTPS** Ingress | `kubectl get clusterissuer` ([cert/README.md](../cert/README.md)) |

## Deploy

Apply the Deployment, the Service and **one** of the two Ingresses. Not the whole directory, because both Ingresses claim the same host:

```bash
kubectl apply -f spring/spring-deployment.yaml -f spring/spring-svc.yaml
kubectl apply -f spring/spring-ingress.yaml          # HTTP

kubectl rollout status deployment/spring-deployment
kubectl get deploy,pods,svc -l app=spring
kubectl get ingress
```

The pod becomes `READY 1/1` only after the **readiness probe** (`/actuator/health/readiness`) succeeds. Both probes start after 30 s (`initialDelaySeconds`), so allow about a minute.

| Probe | Path | If it fails |
| --- | --- | --- |
| readiness | `/actuator/health/readiness` | the pod is removed from the Service endpoints, so it gets no traffic. It is **not** restarted |
| liveness | `/actuator/health/liveness` | after 20 failures (≈ 200 s) the container is **restarted** |

```bash
kubectl logs deploy/spring-deployment -f       # startup log, DB connection errors
kubectl get endpoints spring                   # empty until the pod is Ready
```

---

## How the Ingresses work

An **Ingress** is a set of HTTP routing rules: *requests for host X and path Y go to Service Z*. It does nothing on its own. The **ingress controller** (Traefik, running in namespace `traefik`) watches the Ingresses with `ingressClassName: traefik` and configures itself from them.

### `spring-ingress.yaml` (HTTP)

```yaml
metadata:
  annotations:
    traefik.ingress.kubernetes.io/router.entrypoints: web   # listen on port 80 only
spec:
  ingressClassName: traefik      # which controller serves this Ingress
  rules:
    - host: spring.local         # match the Host header of the request
      http:
        paths:
          - path: /
            pathType: Prefix     # "/" and everything below it
            backend:
              service:
                name: spring     # forward to Service spring ...
                port:
                  name: spring   # ... on its port named "spring" (8080)
```

Traefik routes by the **`Host` header**. A request to the right IP and port but with a different host gets `404 page not found` from Traefik, not from Spring.

### `spring-ingress-ssl.yaml` (HTTPS)

Same rules, plus:

```yaml
metadata:
  annotations:
    cert-manager.io/cluster-issuer: selfsigned-issuer            # cert-manager creates the certificate
    traefik.ingress.kubernetes.io/router.entrypoints: websecure  # listen on port 443
    traefik.ingress.kubernetes.io/router.tls: "true"             # terminate TLS in Traefik
spec:
  tls:
    - hosts: [spring.local]
      secretName: spring-tls-secret   # where cert-manager stores the certificate, read by Traefik
```

TLS ends at Traefik (*TLS termination*). Traffic from Traefik to the Spring pod is plain HTTP inside the cluster, so Spring needs no certificate configuration.

### Switching between HTTP and HTTPS

```bash
# HTTP → HTTPS
kubectl delete -f spring/spring-ingress.yaml --ignore-not-found
kubectl apply -f spring/spring-ingress-ssl.yaml

# HTTPS → HTTP
kubectl delete -f spring/spring-ingress-ssl.yaml --ignore-not-found
kubectl apply -f spring/spring-ingress.yaml
```

---

## Testing

### 1. Spring without the Ingress

First make sure the application itself works, by port-forwarding straight to the Service:

```bash
kubectl port-forward svc/spring 8080:8080
curl http://localhost:8080/actuator/health          # {"status":"UP",...}
```

If this fails, the problem is in the pod or the database, not in the Ingress. Check `kubectl logs deploy/spring-deployment`.

### 2. HTTP Ingress through Traefik

Forward Traefik's ports to your machine and leave it running:

```bash
kubectl port-forward -n traefik svc/traefik 8080:80 8443:443
```

In a second terminal, choose one of these ways to send the right `Host`:

```bash
# a) set the Host header explicitly
curl -H "Host: spring.local" http://localhost:8080/actuator/health

# b) let curl resolve spring.local to 127.0.0.1 (no /etc/hosts change)
curl --resolve spring.local:8080:127.0.0.1 http://spring.local:8080/actuator/health

# c) add spring.local to /etc/hosts once
echo "127.0.0.1 spring.local" | sudo tee -a /etc/hosts
curl http://spring.local:8080/actuator/health
```

Check that host-based routing really works:

```bash
curl -i -H "Host: wrong.local" http://localhost:8080/   # 404 page not found, from Traefik
```

> Port 8080 is used for both checks: in step 1 it goes to Spring directly, here it goes to Traefik. Stop the first port-forward before starting the second.

### 3. HTTPS Ingress

With `spring-ingress-ssl.yaml` applied and cert-manager installed:

```bash
kubectl get certificate spring-tls-secret          # READY True

# -k: accept the self-signed certificate
curl -k --resolve spring.local:8443:127.0.0.1 https://spring.local:8443/actuator/health

# which certificate does Traefik serve?
openssl s_client -connect 127.0.0.1:8443 -servername spring.local </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates
```

The subject should contain `spring.local`. If you see `TRAEFIK DEFAULT CERT`, the certificate is not ready yet (see [cert/README.md → Troubleshooting](../cert/README.md#troubleshooting)).

With the HTTPS Ingress the router listens **only** on `websecure`, so plain HTTP on port 8080 now returns 404.

### 4. Standard ports with `minikube tunnel` (local machine)

```bash
minikube tunnel                                  # in its own terminal
kubectl get svc -n traefik traefik               # EXTERNAL-IP
curl --resolve spring.local:80:<EXTERNAL-IP> http://spring.local/actuator/health
```

> **Codespaces:** the forwarded URL (`https://<name>-8080.app.github.dev`) sends its own `Host` header, so it does not match `spring.local`. Test from the Codespace terminal with `curl` as shown above.

### Inspect what Traefik sees

```bash
kubectl describe ingress spring-ingress          # rules, backend, events
kubectl get ingress -A                           # all Ingresses and their class
kubectl logs -n traefik deploy/traefik           # errors such as "service not found"
```

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| Pod `ImagePullBackOff` | private image and missing or wrong `registry-credentials` secret. Check with `kubectl describe pod -l app=spring` |
| Pod `Running` but `0/1`, restarts | readiness/liveness failing. Usually the DB is not reachable. Check `kubectl logs` and that `postgres-0` is Ready |
| `404 page not found` | wrong `Host` header, the wrong Ingress is applied (HTTP vs HTTPS), or `ingressClassName` does not match an installed controller |
| `503 Service Unavailable` / `no available server` | Ingress is fine but the Service has no ready pods: `kubectl get endpoints spring` is empty |
| `connection refused` on localhost | the Traefik port-forward is not running |

## Clean up

```bash
kubectl delete -f spring/ --ignore-not-found
```
