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

### Notes:

- Ensure that your PostgreSQL container is running and accessible.
- The application uses port 8080, so make sure this port is not already in use on your machine.
- The docker-compose up --build command will build the Docker image for your application using the Dockerfile. It's important to build the JAR file before running this command, as the Dockerfile copies the JAR into the image.
