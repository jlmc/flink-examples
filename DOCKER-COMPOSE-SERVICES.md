### Docker Compose Services Guide

This document explains the services defined in `docker-compose.yaml` at the project root and how to use them during development.

#### Quick start

Start Kafka, create topics, and launch the Kafka UI:

```
docker compose up -d kafka kafka-init kafka-ui
```

Start everything (except services gated by profiles) in detached mode:

```
docker compose up -d
```

Include profile-gated services (e.g., Redis under the `dev` profile):

```
docker compose --profile dev up -d
```

Stop services:

```
docker compose down
```

#### Network

- All services join the `infra_network` bridge network to communicate with each other.

---

### 1) Kafka (Confluent KRaft)

- Service name: `kafka`
- Image: `confluentinc/cp-kafka:7.7.2`
- Ports:
  - `9092:9092` (external client access on localhost)
- Listeners:
  - Internal: `PLAINTEXT://kafka:19092`
  - External: `PLAINTEXT_HOST://localhost:9092`
- Key env vars:
  - `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` (topics auto-creation enabled)
  - `KAFKA_PROCESS_ROLES=broker,controller` (KRaft mode)
  - `KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:19093`
- Healthcheck: waits for broker API to be available on `localhost:9092`.

Usage notes:
- Inside the `infra_network`, use `kafka:19092`.
- From your host, use `localhost:9092`.

Common CLI examples (from host, if you have Kafka CLI):

```
# List topics
kafka-topics --bootstrap-server localhost:9092 --list

# Create a topic (if auto-create is disabled for some scenario)
kafka-topics --bootstrap-server localhost:9092 --create --topic example --partitions 1 --replication-factor 1

# Produce
kafka-console-producer --bootstrap-server localhost:9092 --topic example

# Consume
kafka-console-consumer --bootstrap-server localhost:9092 --topic example --from-beginning
```

---

### 1.1) Kafka Init

- Service name: `kafka-init`
- Image: `confluentinc/cp-kafka:7.7.2`
- Purpose: runs `docker/volumes/kafka/init/create-topics.sh` after Kafka is healthy to create required topics.
- Dependency: waits for `kafka` health.
- Lifecycle: one-shot (no restart).

Run together with Kafka when you need topics pre-created:

```
docker compose up -d kafka kafka-init
```

Script location:
- `./docker/volumes/kafka/init/create-topics.sh`

---

### 2) Kafka UI

- Service name: `kafka-ui`
- Image: `provectuslabs/kafka-ui:v0.7.2`
- Ports: `8085:8080`
- URL: http://localhost:8085
- Configured cluster name: `local-kafka`
- Bootstrap servers (internal): `kafka:19092`

Start UI alongside Kafka:

```
docker compose up -d kafka kafka-ui
```

---

### 3) Redis (dev profile)

- Service name: `redis`
- Image: `redis:3.2-alpine`
- Ports: `6379:6379`
- Profile: `dev` (only runs when `--profile dev` is used)

Run with profile:

```
docker compose --profile dev up -d redis
```

Test connectivity from host:

```
redis-cli -h localhost -p 6379 ping
```

---

### 4) LocalStack (AWS mocks: S3, SQS, Lambda)

- Service name: `localstack`
- Image: `localstack/localstack:4.11.1`
- Ports:
  - Gateway: `4566` exposed as `127.0.0.1:4566`
  - Services port range: `4510-4559` exposed as `127.0.0.1:4510-4559`
  - Web UI: `8083:8080` → http://localhost:8083
- Env vars:
  - `SERVICES=s3,sqs,lambda`
  - `AWS_ACCESS_KEY_ID=test`, `AWS_SECRET_ACCESS_KEY=test`, `AWS_DEFAULT_REGION=us-east-1`
  - `LOCALSTACK_UI=1`, optional `LOCALSTACK_API_KEY`
- Volumes and init:
  - `docker/volumes/localstack/init/init-aws.sh` runs on ready
  - Persistent data under `docker/volumes/localstack/data`

Usage examples (using `awslocal`, if installed):

```
# S3
awslocal s3 mb s3://demo-bucket
awslocal s3 ls

# SQS
awslocal sqs create-queue --queue-name demo-queue
awslocal sqs list-queues
```

AWS SDK endpoint hints:
- Set endpoint to `http://localhost:4566` for S3/SQS/Lambda.

---

### 5) PostgreSQL (PostGIS)

- Service name: `postgresql`
- Image: `postgis/postgis:15-3.3-alpine`
- Ports: `5432:5432`
- Env vars: `POSTGRES_USER=user`, `POSTGRES_PASSWORD=pw`, `POSTGRES_DB=db`

Connection string examples:

```
postgresql://user:pw@localhost:5432/db
```

CLI example:

```
PGPASSWORD=pw psql -h localhost -U user -d db -c "SELECT version();"
```

---

### 6) MongoDB (replica set for transactions)

- Service names: `mongo1`, `mongo2`, and `mongodb` (init-orchestrator)
- Image: `mongo:6.0.13`
- Ports:
  - `mongo1` → `27017:27017`
  - `mongo2` → `27018:27017`
- Replica set name: `dataDemos`
- Commands:
  - `mongo1`: `mongod --replSet dataDemos --bind_ip localhost,mongo1`
  - `mongo2`: `mongod --replSet dataDemos --bind_ip localhost,mongo2`
- Initialization:
  - The `mongodb` service runs an init script after a short delay to initiate the replica set and seed data.
  - Script path (mounted): `docker/volumes/mongo/init/mongo-init.js`
  - Compose runs: `mongosh mongodb://mongo1:27017 -f /docker-entrypoint-initdb.d/mongo-init.js`
 
Connection strings:
 
- From another container on the same network:
 
```
mongodb://mongo1:27017,mongo2:27017/?replicaSet=dataDemos
```
 
- From the host (macOS/Linux):
 
```
# Connect to primary via localhost ports (replica set aware)
mongodb://localhost:27017,localhost:27018/?replicaSet=dataDemos
```
 
Replica set status (from a Mongo shell):
 
```
mongosh "mongodb://localhost:27017,localhost:27018/?replicaSet=dataDemos" --eval 'rs.status()'
```

---

### 7) RabbitMQ

- Service name: `rabbitmq`
- Image: `rabbitmq:4.2.1-management`
- Ports:
  - AMQP: `5672:5672`
  - Management UI: `15672:15672` → http://localhost:15672
- Volumes:
  - `docker/volumes/rabbitmq/rabbitmq.config` → `/etc/rabbitmq/rabbitmq.config:ro`
  - `docker/volumes/rabbitmq/rabbitmq-definitions.json` → `/etc/rabbitmq/definitions.json:ro`
- Healthcheck: `rabbitmq-diagnostics -q ping`

Default credentials (unless overridden): `guest` / `guest`

---

### 8) WireMock (mock REST APIs)

- Service name: `wiremock`
- Image: `wiremock/wiremock:3.13.2`
- Ports: `9069:9069`
- Command flags:
  - `--port=9069`, `--verbose`, `--global-response-templating`
- Volumes:
  - `docker/volumes/wiremock/mappings` → `/home/wiremock/mappings`
  - `docker/volumes/wiremock/__files` → `/home/wiremock/__files`
- Healthcheck: HTTP GET `http://localhost:9069/__admin/`

Useful endpoints:

- Admin: `GET http://localhost:9069/__admin/`
- List mappings: `GET http://localhost:9069/__admin/mappings`

### Helpful commands

Logs for a service:

```
docker compose logs -f <service>
```

Recreate a service after changing configuration:

```
docker compose up -d --force-recreate --no-deps <service>
```

Check health status for all services:

```
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Prune dangling resources (be careful):

```
docker system prune -f
```

---

### URLs summary

- Kafka UI: http://localhost:8085
- LocalStack UI: http://localhost:8083
- RabbitMQ Management: http://localhost:15672
- WireMock Admin: http://localhost:9069/__admin/
- PostgreSQL: postgresql://user:pw@localhost:5432/db
- MongoDB (replica set): mongodb://localhost:27017,localhost:27018/?replicaSet=dataDemos

---

### Notes and caveats

- Kafka: When running applications inside the same compose network, use `kafka:19092`. From the host, use `localhost:9092`.
- LocalStack: Point AWS SDK clients to `http://localhost:4566` and use the default `test/test` credentials unless overridden.
- MongoDB: Replica set name is `dataDemos`. The init script is mounted from `docker/volumes/mongo/init/mongo-init.js` and executed by the `mongodb` service.
- RabbitMQ: Default credentials are `guest` / `guest`. Management UI at `http://localhost:15672`.
- WireMock: Mappings live under `docker/volumes/wiremock/mappings` and static files in `docker/volumes/wiremock/__files`.
