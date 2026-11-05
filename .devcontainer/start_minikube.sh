#!/usr/bin/env bash
# Starts minikube once the Docker daemon (docker-in-docker) is ready.
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
  exit 0
fi

minikube start --kubernetes-version="${K8S_VERSION}" && minikube addons enable ingress \
  && echo "✅ minikube (Kubernetes ${K8S_VERSION}) is running with the ingress addon"
