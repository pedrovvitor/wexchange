# wexchange Application

## Running the Application with Docker

### Prerequisites:

- Docker and Docker Compose installed on your machine.

### Steps:

1. **Clone the Repository:**
   ```sh
   git clone https://github.com/pedrovvitor/wexchange
   cd wexchange
   ```
2. **Build and Run with Docker Compose:**
   This will build the Java application Docker image and start the PostgreSQL database along with the application.
   ```sh
    docker-compose up -d
   ```

3. **Accessing the Application:**
   The application will be accessible at `http://localhost:8080`.

4. **Accessing API Documentation:
   The Swagger UI for the API documentation can be accessed at http://localhost:8080/swagger-ui/index.html. This provides a visual interface for all the RESTful endpoints in your application.

5. **Stopping the Application:**
   To stop the application and remove the containers, use:
   ```sh
    docker-compose down
   ```

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
