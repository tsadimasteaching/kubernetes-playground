# nginx example: Deployment + Service + Volume

A minimal nginx web server serving a static page from a PersistentVolumeClaim.

- `pvc.yaml` – requests 100Mi of storage
- `deployment.yaml` – an init container creates `index.html` in the volume (if missing); nginx mounts it at `/usr/share/nginx/html`
- `service.yaml` – ClusterIP service exposing port 80

## Run

```bash
kubectl apply -f nginx/
kubectl get pvc,deploy,pods,svc
kubectl port-forward svc/nginx 8080:80
curl http://localhost:8080
```

## Show that the volume persists

```bash
kubectl exec deploy/nginx -- sh -c 'echo "<h1>Changed!</h1>" > /usr/share/nginx/html/index.html'
kubectl delete pod -l app=nginx      # the Deployment recreates it
kubectl port-forward svc/nginx 8080:80
curl http://localhost:8080           # still "Changed!"
```

## Clean up

```bash
kubectl delete -f nginx/
```
