#!/usr/bin/env bash
# Renders the icon diagrams (diagrams_src.py) into docs/images/*.png.
# Runs in a throw-away Python container, so only Docker is needed.
#
#   bash docs/diagrams/render.sh
set -euo pipefail

cd "$(dirname "$0")/.."     # docs/

docker run --rm \
  -v "$PWD":/docs \
  -w /docs/diagrams \
  -e HOME=/tmp \
  python:3.13-slim sh -c "
    apt-get update -qq >/dev/null &&
    apt-get install -y -qq --no-install-recommends graphviz fonts-dejavu-core >/dev/null &&
    pip install -q --root-user-action=ignore diagrams==0.25.1 &&
    python diagrams_src.py &&
    chown $(id -u):$(id -g) /docs/images/*.png
  "

ls -l images/
