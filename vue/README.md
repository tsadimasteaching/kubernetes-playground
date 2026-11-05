# Vue frontend: Deployment + Service + Ingress

The Vue single-page frontend of the application, served by a web server on port 80 inside the container and exposed through the Traefik ingress controller.

- `vue-deployment.yaml` – the Deployment: image `ghcr.io/tsadimas/ds-vue:latest`, resource limits, `imagePullSecrets: registry-credentials`
- `vue-svc.yaml` – ClusterIP Service `vue-svc`, port **9000** → container port 80 (port name `vue`)
- `vue-ingress.yaml` – **HTTP** Ingress for `vue.local` (Traefik entrypoint `web`, port 80)

```text
browser ──► Traefik (Ingress controller) ──► Service vue-svc:9000 ──► Pod vue:80
             Host: vue.local
```

## Prerequisites

| What | Why | How to check |
| --- | --- | --- |
| Secret `registry-credentials` | needed only if the image is **private** | `kubectl get secret registry-credentials` ([main README → Container registry](../README.md#2-container-registry-github-packages)) |
| Traefik ingress controller | serves the Ingress (`ingressClassName: traefik`) | `kubectl get ingressclass` ([main README → Install Traefik](../README.md#install-the-traefik-ingress-controller)) |
| [Spring backend](../spring/README.md) | the frontend calls its API. The page loads without it, but API calls fail | `kubectl get pods -l app=spring` |

## Deploy

```bash
kubectl apply -f vue/
kubectl rollout status deployment/vue-deployment
kubectl get deploy,pods,svc -l app=vue
kubectl get ingress vue-ingress
```

## Testing

How the Ingress works (host-based routing, entrypoints) is explained in [spring/README.md → How the Ingresses work](../spring/README.md#how-the-ingresses-work). The Vue Ingress is the same, for host `vue.local`.

### 1. Without the Ingress

Forward the port and leave the command running:

```bash
kubectl port-forward svc/vue-svc 9000:9000
```

In a second terminal:

```bash
curl http://localhost:9000/          # the index.html of the app
```

### 2. Through Traefik

Stop the previous port-forward, then forward Traefik's port and leave it running:

```bash
kubectl port-forward -n traefik svc/traefik 8080:80
```

In a second terminal:

```bash
curl --resolve vue.local:8080:127.0.0.1 http://vue.local:8080/
```

On a local machine, add `127.0.0.1 vue.local` to `/etc/hosts` and open <http://vue.local:8080> in the browser.

> **Codespaces:** the forwarded URL sends its own `Host` header, so it does not match `vue.local`. Test with `curl` from the Codespace terminal, or open the forwarded port of step 1 (`vue-svc`) in the browser.

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| Pod `ImagePullBackOff` | private image and missing or wrong `registry-credentials` secret |
| `404 page not found` | wrong `Host` header, or `ingressClassName` does not match an installed controller |
| `503` / `no available server` | the Service has no ready pods: `kubectl get endpoints vue-svc` |
| Page loads, but data does not | the Spring backend is not running or not reachable from the browser |

## Clean up

```bash
kubectl delete -f vue/
```
