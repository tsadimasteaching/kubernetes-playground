# PostgreSQL: StatefulSet + Services + Volume

A single-instance PostgreSQL 16 database running as a StatefulSet with persistent storage.

- `postgres-statefulset.yaml` – the StatefulSet (`postgres-0`) with a `volumeClaimTemplate` (1Gi, default StorageClass), probes (`pg_isready`) and resource limits
- `postgres-svc.yaml` – two Services:
  - `postgres` (ClusterIP): what applications use, e.g. `jdbc:postgresql://postgres:5432/dsdb`
  - `postgres-headless` (`clusterIP: None`): required by the StatefulSet; gives the pod a stable DNS name `postgres-0.postgres-headless`

Database `dsdb`, user `dbuser`, password `pass123` (plain text for simplicity; use a `Secret` with `secretKeyRef` in real deployments).

## Deploy

```bash
kubectl apply -f postgres/
kubectl rollout status statefulset/postgres
kubectl get statefulset,pods,pvc,svc -l app=postgres
```

## Connect

```bash
kubectl exec -it postgres-0 -- psql -U dbuser -d dsdb

# from your machine
kubectl port-forward svc/postgres 5432:5432
psql -h localhost -U dbuser -d dsdb
```

## Show that the data persists

```bash
kubectl exec -it postgres-0 -- psql -U dbuser -d dsdb -c "create table demo(id int); insert into demo values (1);"
kubectl delete pod postgres-0          # the StatefulSet recreates it with the same PVC
kubectl exec -it postgres-0 -- psql -U dbuser -d dsdb -c "select * from demo;"
```

## Notes

- `PGDATA` points to a subdirectory of the volume, because some storage backends create `lost+found` at the volume root, which makes `initdb` fail.
- Why a StatefulSet and not a Deployment: see [the main README](../README.md#why-postgresql-runs-as-a-statefulset).

## Clean up

The PVC is **not** deleted with the StatefulSet, so delete it explicitly to remove the data:

```bash
kubectl delete -f postgres/
kubectl delete pvc data-postgres-0
```
