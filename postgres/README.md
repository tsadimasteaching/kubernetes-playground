# PostgreSQL: StatefulSet + Services + Volume

A single-instance PostgreSQL 16 database running as a StatefulSet with persistent storage. The [Spring backend](../spring/README.md) uses it.

- `postgres-statefulset.yaml` – the StatefulSet (`postgres-0`) with a `volumeClaimTemplate` (1Gi, default StorageClass), probes (`pg_isready`) and resource limits
- `postgres-svc.yaml` – two Services:
  - `postgres` (ClusterIP): what applications use, e.g. `jdbc:postgresql://postgres:5432/dsdb`
  - `postgres-headless` (`clusterIP: None`): required by the StatefulSet; gives the pod a stable DNS name `postgres-0.postgres-headless`

Database `dsdb`, user `dbuser`, password `pass123` (plain text for simplicity; use a `Secret` with `secretKeyRef` in real deployments).

## Prerequisites

- A running cluster with a default StorageClass (`kubectl get storageclass`; on minikube: `standard`)

## Deploy

```bash
kubectl apply -f postgres/
kubectl rollout status statefulset/postgres
kubectl get statefulset,pods,pvc,svc -l app=postgres
```

## Connect

```bash
kubectl exec -it postgres-0 -- psql -U dbuser -d dsdb
```

From your machine, forward the port and leave the command running:

```bash
kubectl port-forward svc/postgres 5432:5432
```

In a second terminal:

```bash
psql -h localhost -U dbuser -d dsdb
```

## Show that the data persists

```bash
kubectl exec -it postgres-0 -- psql -U dbuser -d dsdb -c "create table demo(id int); insert into demo values (1);"
kubectl delete pod postgres-0          # the StatefulSet recreates it with the same PVC
kubectl exec -it postgres-0 -- psql -U dbuser -d dsdb -c "select * from demo;"
```

## Why a StatefulSet and not a Deployment

Spring and Vue are **stateless**: any pod is as good as any other, so a `Deployment` can create, replace and scale them freely. A database is **stateful**: the pod is tied to its data on disk. A `StatefulSet` gives exactly the guarantees a database needs:

| | Deployment | StatefulSet |
| --- | --- | --- |
| **Pod name** | random, changes on every restart (`postgres-deployment-7c9f…`) | stable and ordered (`postgres-0`, `postgres-1`, …) |
| **Storage** | all replicas share the PVC you create by hand | each pod gets **its own** PVC from `volumeClaimTemplates` (`data-postgres-0`), re-attached to the same pod after restarts |
| **Updates / restarts** | rolling update starts the new pod **before** stopping the old one, so two Postgres processes on the same data directory can corrupt it | the old pod is stopped **before** the new one starts; never two pods with the same identity |
| **Network identity** | only through the Service | every pod also has its own DNS name via a headless Service (`postgres-0.postgres-headless`) |
| **Deleting it** | a hand-made PVC stays, but nothing links it to a pod | PVCs are **kept** when the StatefulSet is deleted, so the data survives |

The stable names and per-pod storage are also what makes database replication possible (e.g. `postgres-0` as primary, `postgres-1` as replica). For production, an operator such as [CloudNativePG](https://cloudnative-pg.io/) builds on these primitives and adds backups, failover and upgrades.

Compare with the [nginx/](../nginx/README.md) example, a Deployment with a hand-made PVC.

## Notes

- `PGDATA` points to a subdirectory of the volume, because some storage backends create `lost+found` at the volume root, which makes `initdb` fail.

## Clean up

The PVC is **not** deleted with the StatefulSet, so delete it explicitly to remove the data:

```bash
kubectl delete -f postgres/
kubectl delete pvc data-postgres-0
```
