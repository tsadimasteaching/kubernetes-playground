# Diagrams

The architecture pictures in the READMEs (`docs/images/*.png`) are generated from code with [diagrams](https://diagrams.mingrammer.com/), a Python library that draws with real icons (Kubernetes objects, Traefik, nginx, Vue, Spring, PostgreSQL, cert-manager, Let's Encrypt) and lays them out with Graphviz.

| File | What |
| --- | --- |
| [diagrams_src.py](diagrams_src.py) | one function per diagram |
| [render.sh](render.sh) | renders all of them in a throw-away `python:3.13-slim` container (Graphviz + `diagrams==0.25.1`), so only Docker is needed |
| [icons/keycloak.png](icons/keycloak.png) | Keycloak icon (not part of `diagrams`), from the [CNCF artwork repository](https://github.com/cncf/artwork/tree/main/projects/keycloak) |

After changing a diagram:

```bash
bash docs/diagrams/render.sh
```

| Image | Used in |
| --- | --- |
| [architecture.png](../images/architecture.png) | [README.md](../../README.md) |
| [cert-manager.png](../images/cert-manager.png) | [cert/README.md](../../cert/README.md) |
| [spring.png](../images/spring.png) | [spring/README.md](../../spring/README.md) |
| [vue.png](../images/vue.png) | [vue/README.md](../../vue/README.md) |
| [keycloak-overview.png](../images/keycloak-overview.png), [login-flow.png](../images/login-flow.png) | [Keycloak.md](../../Keycloak.md) |
| [keycloak-components.png](../images/keycloak-components.png) | [keycloak/README.md](../../keycloak/README.md) |

Edge colors: blue = HTTP, green = database / responses, dashed brown = Keycloak's public keys (JWKS).
