#!/usr/bin/env bash
# Starts minikube once the Docker daemon (docker-in-docker) is ready,
# then makes sure the Traefik ingress controller is installed.
# Runs on every container start (postStartCommand in devcontainer.json).

K8S_VERSION="${K8S_VERSION:-v1.37.0}"

echo "⏳ Waiting for Docker daemon..."
for _ in $(seq 1 60); do
  docker info >/dev/null 2>&1 && break
  sleep 2
done

if ! docker info >/dev/null 2>&1; then
  echo "❌ Docker is not running — start minikube manually with: minikube start --kubernetes-version=${K8S_VERSION}"
  exit 1
fi

if minikube status >/dev/null 2>&1; then
  echo "✅ minikube is already running"
else
  minikube start --kubernetes-version="${K8S_VERSION}" || exit 1
  echo "✅ minikube (Kubernetes ${K8S_VERSION}) is running"
fi

# Only one ingress controller: make sure minikube's NGINX addon is off
# (older versions of this script enabled it). Safe to run when already disabled.
minikube addons disable ingress >/dev/null 2>&1 && echo "✅ minikube NGINX ingress addon is disabled (Traefik is used instead)"

if helm status traefik -n traefik >/dev/null 2>&1; then
  echo "✅ Traefik is already installed"
else
  helm repo add traefik https://traefik.github.io/charts --force-update
  helm upgrade --install traefik traefik/traefik --namespace traefik --create-namespace --wait \
    && echo "✅ Traefik ingress controller installed (IngressClass: traefik)"
fi
