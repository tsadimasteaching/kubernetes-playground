# nginx example: Deployment + Service + Volume

A minimal nginx web server serving a static page from a PersistentVolumeClaim. It is a standalone example, not part of the Spring/Vue application. It has **no Ingress**, so you reach it with `kubectl port-forward`. For Ingress examples see [spring/](../spring/README.md) and [vue/](../vue/README.md).

- `pvc.yaml` – requests 100Mi of storage
- `deployment.yaml` – an init container creates `index.html` in the volume (if missing); nginx mounts it at `/usr/share/nginx/html`
- `service.yaml` – ClusterIP service exposing port 80

## Prerequisites

- A running cluster with a default StorageClass (`kubectl get storageclass`; on minikube: `standard`)

## Run

```bash
kubectl apply -f nginx/
kubectl get pvc,deploy,pods,svc
```

Forward the port and leave the command running:

```bash
kubectl port-forward svc/nginx 8080:80
```

In a second terminal:

```bash
curl http://localhost:8080
```

> Port 8080 is also used by the Traefik port-forward in the other READMEs. Stop that one first, or pick another local port, e.g. `kubectl port-forward svc/nginx 8081:80`.

## Show that the volume persists

```bash
kubectl exec deploy/nginx -- sh -c 'echo "<h1>Changed!</h1>" > /usr/share/nginx/html/index.html'
kubectl delete pod -l app=nginx      # the Deployment recreates it
kubectl rollout status deployment/nginx
```

Restart the port-forward, because it was bound to the deleted pod, then:

```bash
curl http://localhost:8080           # still "Changed!"
```

> A Deployment with one hand-made PVC is fine for a single replica of a static site. For a database, use a StatefulSet. See [postgres/README.md → Why a StatefulSet](../postgres/README.md#why-a-statefulset-and-not-a-deployment).

## Clean up

```bash
kubectl delete -f nginx/
```
