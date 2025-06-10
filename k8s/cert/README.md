## install helm

```bash
curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3

chmod 700 get_helm.sh

./get_helm.sh
```
## install cert-manager

```bash
helm repo add jetstack https://charts.jetstack.io --force-update

helm install \
cert-manager jetstack/cert-manager \
--namespace cert-manager \
--create-namespace \
--set installCRDs=true
```

## create cert-issuer

replace email in k8s/cert-issuer/cert-issuer.yaml file and apply
```bash
kubectl apply -f k8s/cert/cert-issuer.yaml
```