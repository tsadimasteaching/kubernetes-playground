# Backend: Tickets API (Spring Boot)

A REST API for a small ticketing system. It is built with Spring Boot 4, Java 25 and Maven, and stores data in PostgreSQL.

```
src/main/java/gr/hua/dit/tickets
├── TicketsApplication.java
├── model/        Ticket (@Entity), TicketStatus, TicketPriority
├── repository/   TicketRepository (Spring Data JPA)
├── service/      TicketService (business rules per role)
├── web/          TicketController, MeController, request DTOs
└── config/       SecurityConfig (Spring Security), OpenApiConfig (Swagger), DataSeeder
```

## Roles and users

Users are kept in memory ([SecurityConfig.java](src/main/java/gr/hua/dit/tickets/config/SecurityConfig.java)) and log in with **HTTP Basic**. The password for every user is `password`.

| User            | Role  | Permissions                                                                  |
|-----------------|-------|------------------------------------------------------------------------------|
| `alice`, `bob`  | USER  | create tickets; list / read / update / delete **own** tickets while `OPEN`    |
| `agent`         | AGENT | list / read **all** tickets; assign to self (`IN_PROGRESS`); resolve (`RESOLVED`) |

On first start, when the database is empty, [DataSeeder](src/main/java/gr/hua/dit/tickets/config/DataSeeder.java) inserts 3 demo tickets.

## API

| Method | Path                         | Role        | Description                                   |
|--------|------------------------------|-------------|-----------------------------------------------|
| GET    | `/api/me`                    | any         | current user and roles                        |
| GET    | `/api/tickets?status=OPEN`   | USER, AGENT | USER: own tickets, AGENT: all (`status` optional) |
| GET    | `/api/tickets/{id}`          | USER, AGENT | one ticket (USER: only own, else 404)         |
| POST   | `/api/tickets`               | USER        | create `{title, description, priority}`       |
| PUT    | `/api/tickets/{id}`          | USER        | update own `OPEN` ticket                      |
| DELETE | `/api/tickets/{id}`          | USER        | delete own `OPEN` ticket                      |
| POST   | `/api/tickets/{id}/assign`   | AGENT       | assign to me → `IN_PROGRESS`                  |
| POST   | `/api/tickets/{id}/resolve`  | AGENT       | `{resolution}` → `RESOLVED`                   |
| GET    | `/actuator/health`           | public      | health check (used by Docker `HEALTHCHECK`)   |

**Swagger UI:** <http://localhost:8080/swagger-ui.html>. Click **Authorize** and enter a user. The OpenAPI JSON is at `/v3/api-docs`.

Example with curl:

```bash
curl -u alice:password http://localhost:8080/api/tickets

curl -u alice:password -H 'Content-Type: application/json' \
  -d '{"title":"Laptop is slow","priority":"HIGH"}' http://localhost:8080/api/tickets

curl -u agent:password -X POST http://localhost:8080/api/tickets/4/assign

curl -u agent:password -H 'Content-Type: application/json' \
  -d '{"resolution":"Removed startup apps"}' http://localhost:8080/api/tickets/4/resolve
```

## Configuration

Configuration comes from **environment variables, read when the app starts**. The image never contains the DB address or password, so the same image works with any database.

### 1. Placeholders with defaults

[application.properties](src/main/resources/application.properties) uses `${NAME:default}` placeholders:

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:tickets}
spring.datasource.username=${DB_USER:tickets}
spring.datasource.password=${DB_PASSWORD:tickets}
```

`${DB_HOST:localhost}` means "the environment variable `DB_HOST`, or `localhost` if it is not set". With the defaults, `mvn spring-boot:run` works against a local Postgres container without any setup.

| Variable      | Default     | Set to, in Docker                                       |
|---------------|-------------|---------------------------------------------------------|
| `DB_HOST`     | `localhost` | name of the Postgres container/service (`tickets-db`, `db`) |
| `DB_PORT`     | `5432`      |                                                         |
| `DB_NAME`     | `tickets`   | same as `POSTGRES_DB` of the db container               |
| `DB_USER`     | `tickets`   | same as `POSTGRES_USER`                                 |
| `DB_PASSWORD` | `tickets`   | same as `POSTGRES_PASSWORD`                             |

### 2. Overriding any Spring property (relaxed binding)

Without any placeholder, Spring Boot maps environment variables to properties: uppercase, `.` → `_`, `-` removed.

| Environment variable           | Property                         |
|--------------------------------|----------------------------------|
| `SERVER_PORT=9090`             | `server.port`                    |
| `SPRING_JPA_SHOW_SQL=true`     | `spring.jpa.show-sql`            |
| `SPRING_DATASOURCE_URL=jdbc:...` | `spring.datasource.url` (replaces the whole URL) |
| `LOGGING_LEVEL_ORG_HIBERNATE_SQL=debug` | `logging.level.org.hibernate.SQL` |

Precedence, highest first: command-line args (`--server.port=9090`) → environment variables → `application.properties`.

### 3. Passing the variables

```bash
# locally (Linux/macOS shell)
DB_PASSWORD=secret SPRING_JPA_SHOW_SQL=true mvn spring-boot:run

# docker run: one by one, or from a file
docker run -e DB_HOST=tickets-db -e DB_PASSWORD=secret ... tickets-backend:multistage
docker run --env-file backend.env ... tickets-backend:multistage   # KEY=value lines, e.g. DB_PASSWORD=secret

# Kubernetes: the env: section of spring/spring-deployment.yaml
```

### 4. JVM options

[Dockerfile.multistage](Dockerfile.multistage) sets a default with `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"`. The JVM reads this variable automatically, so the heap scales with the container memory limit (`docker run -m 512m ...`). Override it at start-up to add options:

```bash
docker run -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -Duser.timezone=Europe/Athens" ...
```

> Spring does **not** read a `.env` file. For `mvn spring-boot:run`, use the defaults or export the variables in your shell.

## Run locally (without building an image)

Requirements: JDK 25 and Maven 3.9+ on your machine (not included in this repository's dev container, which is set up for Kubernetes; to just build the image you only need Docker), plus a running PostgreSQL:

```bash
docker run -d --name tickets-db \
  -e POSTGRES_DB=tickets -e POSTGRES_USER=tickets -e POSTGRES_PASSWORD=tickets \
  -p 5432:5432 postgres:18-alpine

mvn spring-boot:run
```

## Tests

The tests use an in-memory **H2** database ([src/test/resources/application.properties](src/test/resources/application.properties)), so PostgreSQL is **not** needed:

```bash
mvn test
```

| Test class                                                                              | Type                                  |
|-----------------------------------------------------------------------------------------|---------------------------------------|
| [TicketServiceTest](src/test/java/gr/hua/dit/tickets/service/TicketServiceTest.java)     | unit test, Mockito, no Spring context |
| [TicketRepositoryTest](src/test/java/gr/hua/dit/tickets/repository/TicketRepositoryTest.java) | `@DataJpaTest` slice (JPA only)  |
| [TicketControllerTest](src/test/java/gr/hua/dit/tickets/web/TicketControllerTest.java)   | `@SpringBootTest` + MockMvc, incl. security |
| [TicketsApplicationTests](src/test/java/gr/hua/dit/tickets/TicketsApplicationTests.java) | context loads                         |

## Docker

### Build the image: [Dockerfile.multistage](Dockerfile.multistage)

```bash
docker build -f Dockerfile.multistage -t tickets-backend:multistage .
```

- **Build stage** (`maven:3.9-eclipse-temurin-25`): copies `pom.xml` first and runs `dependency:go-offline`, so that layer stays cached. Then it copies `src/` and runs `mvn package`.
- **Runtime stage** (`eclipse-temurin:25-jre-alpine`): only the JRE and `app.jar`, about 430 MB instead of the 1 GB a single-stage Maven image would be.
- Creates the user `spring` and switches to it with `USER`, so the app does **not** run as root.
- Sets a `HEALTHCHECK` on `/actuator/health`, and `-XX:MaxRAMPercentage` so the JVM respects the container memory limit.

### Run it

The backend needs PostgreSQL on the same Docker network:

```bash
docker network create tickets-net
docker run -d --name tickets-db --network tickets-net \
  -e POSTGRES_DB=tickets -e POSTGRES_USER=tickets -e POSTGRES_PASSWORD=tickets \
  postgres:18-alpine

docker run -d --name tickets-backend --network tickets-net \
  -e DB_HOST=tickets-db -p 8080:8080 \
  tickets-backend:multistage

docker ps                      # STATUS shows (healthy) after ~20s
docker logs -f tickets-backend
docker exec tickets-backend id # uid=100(spring)
```

## On Kubernetes

[spring/spring-deployment.yaml](../spring/spring-deployment.yaml) runs this image as the Deployment `spring-deployment` behind the Service `spring`, with `DB_HOST=postgres` and the credentials of the PostgreSQL StatefulSet. See [spring/README.md](../spring/README.md).
