# Container Registry (github packages)

## Github Packages
* create personal access token (settings --> Developer settings -- > Personal Access Tokens), select classic
* select read:packages
* save the token to a file
* to see packages, go to your github profile and select tab Packages
* tag an image
```bash
docker build -t ghcr.io/<GITHUB-USERNAME>/ds-spring:latest -f nonroot.Dockerfile .
```
* login to docker registry
```bash
cat ~/github-image-repo.txt | docker login ghcr.io -u <GITHUB-USERNAME> --password-stdin
```
* push image
```bash
docker push ghcr.io/<GITHUB-USERNAME>/ds-spring:latest
```
## create docker login secret

* create a .dockerconfigjson file, like this
```json
{
    "auths": {
        "https://ghcr.io":{
            "username":"REGISTRY_USERNAME",
            "password":"REGISTRY_TOKEN",
            "email":"REGISTRY_EMAIL",
            "auth":"BASE_64_BASIC_AUTH_CREDENTIALS"
    	}
    }
}
```


* create <BASE_64_BASIC_AUTH_CREDENTIALS> from the command
```bash
echo -n <USER>:<TOKEN> | base64
```

## create dockercongig secret
```bash
kubectl create secret docker-registry registry-credentials --from-file=.dockerconfigjson=.dockerconfig.json
```


# Deployment

## minikube

```bash
minikube addons enable ingress
```




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


# Links
* [cert-manager](https://cert-manager.io/docs/installation/helm/)
* [Troubleshooting cert-manager](https://cert-manager.io/docs/troubleshooting/)
* [Spring boot health probes](https://www.baeldung.com/spring-liveness-readiness-probes)