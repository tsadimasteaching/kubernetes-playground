"""
Architecture diagrams of the playground, drawn with icons (https://diagrams.mingrammer.com/).

Each function draws one PNG in docs/images/. Run ./render.sh (Docker) instead of calling this directly,
so Graphviz and the Python package do not have to be installed on your machine.

Icons: the Kubernetes, Traefik, nginx, Vue, Spring, PostgreSQL, cert-manager and Let's Encrypt icons ship
with the `diagrams` package. The Keycloak icon (icons/keycloak.png) is from the CNCF artwork repository
(https://github.com/cncf/artwork/tree/main/projects/keycloak).
"""

from pathlib import Path

from diagrams import Cluster, Diagram, Edge
from diagrams.custom import Custom
from diagrams.k8s.compute import Deploy, StatefulSet
from diagrams.k8s.network import Ingress, Service
from diagrams.k8s.others import CRD
from diagrams.k8s.podconfig import ConfigMap, Secret
from diagrams.k8s.storage import PVC
from diagrams.onprem.certificates import CertManager, LetsEncrypt
from diagrams.onprem.client import Client
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.network import Nginx, Traefik
from diagrams.programming.framework import Spring, Vue

HERE = Path(__file__).parent
OUT = HERE.parent / "images"
KEYCLOAK_ICON = str(HERE / "icons" / "keycloak.png")

# splines=spline: the default "ortho" routing places edge labels next to the wrong edges.
GRAPH = {"splines": "spline", "pad": "0.4", "nodesep": "0.55", "ranksep": "0.9", "fontsize": "22", "bgcolor": "white"}
NODE = {"fontsize": "13"}
EDGE = {"fontsize": "12"}


def diagram(name, title, direction="LR"):
    return Diagram(title, filename=str(OUT / name), outformat="png", show=False, direction=direction,
                   graph_attr=GRAPH, node_attr=NODE, edge_attr=EDGE)


def Keycloak(label):
    return Custom(label, KEYCLOAK_ICON)


def http(label="", **kw):
    return Edge(label=label, color="#0969da", **kw)


def keys(label="public keys (JWKS)"):
    return Edge(label=label, color="#9a6700", style="dashed")


def db(label="JDBC :5432"):
    return Edge(label=label, color="#1a7f37")


# --------------------------------------------------------------------------------------------- README.md
def architecture():
    with diagram("architecture", "Tickets app on Kubernetes"):
        browser = Client("browser")
        with Cluster("namespace traefik"):
            traefik = Traefik("Traefik\n(ingress controller)")
        with Cluster("namespace default"):
            with Cluster("frontend (vue/)"):
                vue = Vue("vue-deployment\nnginx :8080")
                vue_svc = Service("vue-svc :9000")
            with Cluster("backend (spring/)"):
                spring_svc = Service("spring :8080")
                spring = Spring("spring-deployment")
            with Cluster("database (postgres/)"):
                pg_svc = Service("postgres :5432")
                pg = PostgreSQL("postgres-0\n(StatefulSet)")
            with Cluster("login (keycloak/)"):
                kc_svc = Service("keycloak :8080")
                kc = Keycloak("keycloak")
                kc_pg = PostgreSQL("keycloak-postgres-0\n(StatefulSet)")

        browser >> http("HTTP / HTTPS") >> traefik
        traefik >> http("vue.localhost\nor public name") >> vue_svc >> vue
        vue >> http("/api/*") >> spring_svc >> spring
        spring >> db() >> pg_svc >> pg
        traefik >> http("keycloak.localhost\n(login page, tokens)") >> kc_svc >> kc
        kc >> db() >> kc_pg
        spring >> keys() >> kc_svc


# ------------------------------------------------------------------------------------ spring/README.md
def spring():
    with diagram("spring", "Spring backend: Deployment + Service"):
        browser = Client("browser")
        traefik = Traefik("Traefik")
        vue = Nginx("vue pod\n(nginx)")
        with Cluster("spring/"):
            svc = Service("Service spring\n:8080")
            pod = Spring("Pod spring\n(spring-deployment)")
        with Cluster("postgres/"):
            pg_svc = Service("Service postgres\n:5432")
            pg = PostgreSQL("postgres-0")
            pvc = PVC("data-postgres-0")

        browser >> http() >> traefik >> http() >> vue >> http("/api/*") >> svc >> pod
        pod >> db() >> pg_svc >> pg
        pg - pvc   # separate statement: "-" binds tighter than ">>"


# --------------------------------------------------------------------------------------- vue/README.md
def vue():
    with diagram("vue", "Vue frontend: Ingress + Service + Deployment"):
        browser = Client("browser")
        with Cluster("namespace traefik"):
            traefik = Traefik("Traefik\n(ingress controller)")
        with Cluster("vue/"):
            ing = Ingress("vue-ingress\nvue-ingress-letsencrypt")
            svc = Service("vue-svc\n:9000 → 8080")
            pod = Vue("Pod vue :8080\n(nginx + static files)")
        with Cluster("spring/"):
            spring_svc = Service("Service spring\n:8080")
            spring = Spring("Pod spring")

        browser >> http("Host: vue.localhost / vue.local\nor the public name") >> traefik
        traefik >> Edge(label="reads the rules", style="dotted", color="gray") >> ing
        traefik >> http("routes by Host") >> svc >> pod
        pod >> http("/api/*  (proxy_pass ${BACKEND_URL})") >> spring_svc >> spring


# -------------------------------------------------------------------------------------- cert/README.md
def cert_manager():
    with diagram("cert-manager", "Let's Encrypt certificate with cert-manager"):
        ing = Ingress("vue-ingress-letsencrypt\n(annotation + spec.tls)")
        cm = CertManager("cert-manager")
        with Cluster("cert-manager resources"):
            cert = CRD("Certificate")
            req = CRD("CertificateRequest")
            order = CRD("Order")
            challenge = CRD("Challenge\n(HTTP-01)")
        issuer = CRD("ClusterIssuer\nletsencrypt-prod")
        le = LetsEncrypt("Let's Encrypt")
        secret = Secret("vue-letsencrypt-tls\n(tls.crt, tls.key)")
        traefik = Traefik("Traefik")
        browser = Client("browser")

        ing >> Edge(label="notices the annotation") >> cm >> cert
        cert >> req >> order >> challenge
        req >> Edge(label="uses", style="dashed") >> issuer
        challenge >> Edge(label="http://<host>/.well-known/\nacme-challenge/<token>", color="#1a7f37") << le
        cert >> Edge(label="stores the signed certificate", color="#9a6700") >> secret
        secret >> Edge(label="TLS", color="#9a6700") >> traefik
        traefik >> http("https://tickets.tsadimas.dns-cloud.net") << browser


# ----------------------------------------------------------------------------------------- Keycloak.md
def keycloak_overview():
    with diagram("keycloak-overview", "Login with Keycloak: who talks to whom"):
        browser = Client("browser\n(Vue app)")
        traefik = Traefik("Traefik :8080\n(kubectl port-forward\n-n traefik svc/traefik 8080:80)")
        with Cluster("cluster"):
            with Cluster("login"):
                kc_svc = Service("keycloak :8080")
                kc = Keycloak("Keycloak\nrealm tickets")
                kc_pg = PostgreSQL("keycloak-postgres")
            with Cluster("app"):
                vue_svc = Service("vue-svc")
                nginx = Nginx("nginx\n/ , /config.json")
                spring = Spring("spring")
                pg = PostgreSQL("postgres")

        browser >> http("1  keycloak.localhost:8080\nlogin page, tokens") >> traefik
        browser >> http("2  vue.localhost:8080\napp + API (Bearer token)") >> traefik
        traefik >> http("Host keycloak.localhost") >> kc_svc >> kc >> db("") >> kc_pg
        traefik >> http("Host vue.localhost") >> vue_svc >> nginx >> http("/api/*") >> spring >> db("") >> pg
        spring >> keys("3  public keys (JWKS)\nonce, cluster-internal\nhttp://keycloak:8080") >> kc_svc


def login_flow():
    with diagram("login-flow", "Authorization Code flow with PKCE"):
        browser = Client("browser\n(Vue app + keycloak-js)")
        kc = Keycloak("Keycloak\nrealm tickets")
        spring = Spring("Spring API\n(resource server)")

        browser >> http('1  "Log in": redirect to /auth\nclient_id=tickets-frontend, code_challenge') >> kc
        kc >> Edge(label="2  login page (alice / password),\nredirect back with #code=...", color="#1a7f37") >> browser
        browser >> http("3  POST /token\ncode + code_verifier") >> kc
        kc >> Edge(label="4  access token + refresh token", color="#1a7f37") >> browser
        browser >> http("5  GET /api/tickets\nAuthorization: Bearer eyJ...") >> spring
        spring >> keys("6  verify the signature\nwith Keycloak's public key\n(keys fetched once)") >> kc
        spring >> Edge(label="7  200 [tickets]", color="#1a7f37") >> browser


# ------------------------------------------------------------------------------- keycloak/README.md
def keycloak_components():
    with diagram("keycloak-components", "keycloak/: the Kubernetes objects"):
        browser = Client("browser")
        traefik = Traefik("Traefik")
        with Cluster("keycloak/"):
            ing = Ingress("keycloak-ingress\nkeycloak.localhost")
            svc = Service("Service keycloak\n:8080")
            with Cluster("Deployment keycloak"):
                kc = Keycloak("Keycloak 26\nstart --import-realm")
            cm = ConfigMap("keycloak-realm\n(tickets-realm.json)")
            secret = Secret("keycloak-secrets\nadmin + DB credentials")
            pg_svc = Service("keycloak-postgres\n(headless) :5432")
            pg = PostgreSQL("keycloak-postgres-0\n(StatefulSet)")
            pvc = PVC("data-keycloak-postgres-0\nrealm, users, sessions")
        with Cluster("app"):
            nginx = Nginx("vue pod (nginx)")
            spring = Spring("spring pod")

        browser >> http("keycloak.localhost:8080") >> traefik
        traefik >> Edge(style="dotted", color="gray") >> ing
        traefik >> http() >> svc >> kc
        cm >> Edge(label="mounted in\n/opt/keycloak/data/import", style="dashed") >> kc
        secret >> Edge(label="env", style="dashed") >> kc
        secret >> Edge(label="env", style="dashed") >> pg
        kc >> db() >> pg_svc >> pg
        pg - pvc
        browser >> http("vue.localhost:8080") >> nginx >> http("/api/*") >> spring
        spring >> keys("public keys (JWKS)\ncluster-internal") >> svc


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for draw in (architecture, spring, vue, cert_manager, keycloak_overview, login_flow, keycloak_components):
        draw()
        print("drew", draw.__name__)
