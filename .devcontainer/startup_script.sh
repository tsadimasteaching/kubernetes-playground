#!/usr/bin/env bash
# kubectl, helm, minikube and docker are installed by the devcontainer features (see devcontainer.json).

curl -sS https://webi.sh/k9s | sh

curl -s "https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh" | bash
sudo mv kustomize /usr/local/bin/

sudo apt-get update && sudo apt-get install -y fzf

echo "✅ k9s, kustomize and fzf installed successfully"

cat >> "$HOME/.bashrc" <<'EOF'
alias c=clear
alias k="kubectl"
alias kga="kubectl get all"
alias kgn="kubectl get all --all-namespaces"
alias kdel="kubectl delete"
alias kd="kubectl describe"
alias kg="kubectl get"
EOF

echo "✅ The following aliases were added to ~/.bashrc:"
echo "alias c=clear"
echo "alias k=kubectl"
echo "alias kga=kubectl get all"
echo "alias kgn=kubectl get all --all-namespaces"
echo "alias kdel=kubectl delete"
echo "alias kd=kubectl describe"
echo "alias kg=kubectl get"
