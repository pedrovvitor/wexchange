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

4. **Stopping the Application:**
   To stop the application and remove the containers, use:
   ```sh 
    docker-compose down
   ```

5**Accessing the Application:**
The application will be accessible at `http://localhost:8080`.

### Notes:

- Ensure that your PostgreSQL container is running and accessible.
