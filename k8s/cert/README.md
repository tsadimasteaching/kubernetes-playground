## install helm

```bash
curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3

chmod 700 get_helm.sh

./get_helm.sh
```
## install cert-manager

```bash
helm install \
cert-manager oci://quay.io/jetstack/charts/cert-manager \
--version v1.19.2 \
--namespace cert-manager \
--create-namespace \
--set crds.enabled=true
```

## Patch traefik
* get private ip of VM, e.g. 172.17.0.5
* patch traefik svc to this external ip
```bash
kubectl patch svc traefik -n traefik -p '{"spec":{"externalIPs":["172.17.0.5"]}}'
```

## create cert-issuer

replace email in k8s/cert-issuer/cert-issuer.yaml file and apply
```bash
kubectl apply -f k8s/cert/cert-issuer.yaml
```