# wexchange Application

## Running the Application with Docker

### Prerequisites:

- Docker and Docker Compose installed on your machine.

### Steps:

1. **Clone the repository:**
   ```sh
   git clone https://github.com/pedrovvitor/wexchange
   cd wexchange
   ```
2. **Provide credentials:**
   ```sh
   cp .env.example .env
   ```
   Edit `.env` and set a real `DATABASE_POSTGRES_PASSWORD`. `.env` is
   git-ignored; nothing you put there is ever committed. Compose reads it both
   to fill in the application's database configuration and to set the local
   Postgres container's own password from the same value, so it only needs to
   be set once.

3. **Build and start the stack, waiting for both services to be healthy:**
   ```sh
   docker compose up --build --wait
   ```
   `--wait` blocks until the application's `/actuator/health` check and
   Postgres's `pg_isready` check both pass, so the command only returns once
   the stack is actually usable - not just started.

4. **Access the application:**
   `http://localhost:8080`. The Swagger UI is at
   `http://localhost:8080/swagger-ui/index.html`.

5. **Access Postgres from the host, if you need to** (a database client, a
   manual query): it is bound to `127.0.0.1:5432`, reachable from your machine
   but from nowhere else on the network.

6. **Stop the stack:**
   ```sh
   docker compose down
   ```
   The application container handles `SIGTERM` directly (an exec-form
   entrypoint, and `server.shutdown: graceful` in `application.yml`), so
   in-flight requests are given a chance to finish rather than being cut off.

### A note on the committed `.env.example` value

`.env.example`'s `DATABASE_POSTGRES_PASSWORD` is a placeholder
(`change-me-locally`), never a real credential, and is meant to be overridden
in your own `.env` before starting the stack. If a *real* password was ever
reused across environments starting from an earlier version of this
repository, rotate it: a value that once existed in git history should be
treated as compromised even after being removed from the working tree.

## Runtime profiles

The application activates **no profile by default**. Starting it without one
gives you `application.yml` alone, which expects `DATABASE_POSTGRES_URL`,
`DATABASE_POSTGRES_USERNAME`, and `DATABASE_POSTGRES_PASSWORD` in the
environment. Selecting a profile is always a deliberate act.

| Profile | Where it lives | Used by |
| --- | --- | --- |
| `development` | `src/main/resources/application-development.yml` | local runs against a real PostgreSQL |
| `production` | `src/main/resources/application-production.yml` | the Docker image and any deployment |
| `test` | `src/test/resources/application-test.yml` | automated tests only; never packaged |

Choose one explicitly:

```sh
SPRING_PROFILES_ACTIVE=development ./gradlew bootRun
java -jar build/libs/wexchange-1.0-SNAPSHOT.jar --spring.profiles.active=production
```

`docker-compose.yml` sets `SPRING_PROFILES_ACTIVE=production`, and the Dockerfile
carries the same value as its default.

## Building and checking

The build declares its own Java 17 toolchain, so no particular `JAVA_HOME` is
required and no locale flags are needed.

```sh
./gradlew clean check
```

That is the complete gate: formatting, static analysis, the unit, integration,
and architecture suites, coverage verification, and mutation testing. See
[docs/engineering/test-taxonomy.md](docs/engineering/test-taxonomy.md) for what
each threshold is and which task owns it.

### Notes:

- Ensure that your PostgreSQL container is running and accessible.
- The application uses port 8080, so make sure this port is not already in use on your machine.
- The docker-compose up --build command will build the Docker image for your application using the Dockerfile. It's important to build the JAR file before running this command, as the Dockerfile copies the JAR into the image.
