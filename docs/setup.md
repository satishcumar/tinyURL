# TinyURL Setup and Local Run Guide

## 1. Purpose

This guide explains how to clone, build, run, test, and verify the existing TinyURL Spring Boot application locally.

## 2. Prerequisites

### Required

| Requirement | Version or purpose |
|---|---|
| Git | Clone and manage the repository |
| Java Development Kit | Java 21 |
| Internet access | Download Maven and project dependencies on the first build |

A separate Maven installation is not required because the repository includes the Maven Wrapper.

### Optional

| Tool | Purpose |
|---|---|
| curl, Postman, or similar client | Exercise the REST API |
| IDE such as IntelliJ IDEA, Eclipse, or VS Code | Edit and debug the application |
| Browser | Access the H2 console and Actuator endpoints |

Docker is not required for the current application.

## 3. Verify Java

Run:

```bash
java -version
javac -version
```

Both commands must report Java 21. If multiple JDKs are installed, set `JAVA_HOME` to a Java 21 JDK and ensure its `bin` directory appears first in `PATH`.

### Windows PowerShell example

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

### Linux or macOS example

Use the Java 21 installation path for your machine:

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

## 4. Clone the repository

```bash
git clone https://github.com/satishcumar/tinyURL.git
cd tinyURL
```

The default branch is `main`. To work from another branch:

```bash
git fetch origin
git switch <branch-name>
```

## 5. Maven Wrapper setup

The repository contains `mvnw` for Linux/macOS and `mvnw.cmd` for Windows. A separate Maven installation is unnecessary.

On Linux or macOS, the repository may require setting the executable bit after checkout:

```bash
chmod +x mvnw
./mvnw --version
```

If the executable bit cannot be changed:

```bash
bash mvnw --version
```

On Windows Command Prompt:

```bat
mvnw.cmd --version
```

On Windows PowerShell:

```powershell
.\mvnw.cmd --version
```

The first invocation downloads Maven and project dependencies. Later builds reuse the local Maven cache.

## 6. Build and test

Run the full verification lifecycle before starting the application.

### Linux or macOS

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

### Windows

```bat
mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

A successful build ends with `BUILD SUCCESS`. Surefire reports are written under `target/surefire-reports/`.

The current test suite includes:

- Spring context startup test
- Controller tests using MockMvc
- Service tests using Mockito
- Domain behavior test
- Short-code generator test

Run only the tests:

```bash
./mvnw test
```

Run one test class:

```bash
./mvnw -Dtest=UrlServiceImplTest test
```

Windows users can replace `./mvnw` with `mvnw.cmd`.

## 7. Start the application

### Linux or macOS

```bash
./mvnw spring-boot:run
```

Alternative when `mvnw` is not executable:

```bash
bash mvnw spring-boot:run
```

### Windows

```bat
mvnw.cmd spring-boot:run
```

The application starts at:

```text
http://localhost:8080
```

Wait until the logs show that the application has started before sending requests.

## 8. Package and run the JAR

Build the application:

```bash
./mvnw clean package
```

Run the packaged application:

```bash
java -jar target/tinyurl-0.0.1-SNAPSHOT.jar
```

Windows users can build with `mvnw.cmd clean package`. Use `Ctrl+C` to stop the process.

## 9. Database setup

No separate database installation is required. The application uses:

```text
jdbc:h2:file:./data/tinyurl;AUTO_SERVER=TRUE
```

When started from the repository root, the database file is stored under `tinyURL/data/`. The path is relative to the process working directory.

### Credentials

| Setting | Value |
|---|---|
| JDBC URL | `jdbc:h2:file:./data/tinyurl;AUTO_SERVER=TRUE` |
| Driver | `org.h2.Driver` |
| Username | `sa` |
| Password | Blank |

### H2 console

While the application is running, open [http://localhost:8080/h2-console](http://localhost:8080/h2-console) and enter the JDBC URL and credentials above.

### Data-lifecycle warning

The current configuration uses `spring.jpa.hibernate.ddl-auto=create-drop`. Hibernate creates the schema at startup and drops it during a normal shutdown. Treat the file database as local development data, not production storage.

Do not commit changing database files as part of ordinary source-code work.

## 10. Verify the application

### 10.1 Health check

```bash
curl -i http://localhost:8080/actuator/health
```

Expected status is HTTP 200 with a response resembling:

```json
{"status":"UP"}
```

### 10.2 Create a short URL

```bash
curl -i \
  -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/articles/1"}'
```

Expected status is HTTP 201. Example response:

```json
{
  "shortCode": "Ab12xYz",
  "shortUrl": "http://localhost:8080/Ab12xYz",
  "originalUrl": "https://example.com/articles/1",
  "createdAt": "2026-08-28T15:00:00Z"
}
```

Save the returned `shortCode`.

### Windows PowerShell

```powershell
$body = @{ url = "https://example.com/articles/1" } | ConvertTo-Json
$response = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/urls" -ContentType "application/json" -Body $body
$response
```

### 10.3 Test the redirect

Replace `Ab12xYz` with the returned code:

```bash
curl -i http://localhost:8080/Ab12xYz
```

Expected response:

```text
HTTP/1.1 302
Location: https://example.com/articles/1
```

Use `curl` without `-L` to inspect the TinyURL response instead of following the redirect.

### 10.4 Retrieve analytics

```bash
curl -i http://localhost:8080/api/v1/urls/Ab12xYz/analytics
```

Example response after one redirect request:

```json
{
  "shortCode": "Ab12xYz",
  "originalUrl": "https://example.com/articles/1",
  "redirectCount": 1,
  "createdAt": "2026-08-28T15:00:00Z",
  "lastAccessedAt": "2026-08-28T15:05:00Z"
}
```

### 10.5 Verify validation

```bash
curl -i \
  -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"url":"not-a-url"}'
```

Expected status is HTTP 400.

### 10.6 Verify missing-code handling

```bash
curl -i http://localhost:8080/api/v1/urls/missing/analytics
```

Expected status is HTTP 404.

## 11. Actuator endpoints

| Endpoint | URL |
|---|---|
| Health | `http://localhost:8080/actuator/health` |
| Info | `http://localhost:8080/actuator/info` |
| Metrics | `http://localhost:8080/actuator/metrics` |

The application currently has no Spring Security configuration. Do not expose Actuator endpoints or the H2 console publicly.

## 12. Configuration overrides

Spring Boot properties can be overridden through command-line arguments or environment variables.

### Change the server port

Linux or macOS:

```bash
SERVER_PORT=8081 ./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
$env:SERVER_PORT = "8081"
.\mvnw.cmd spring-boot:run
```

Command-line alternative:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

### Important base-URL limitation

`UrlServiceImpl` constructs returned short URLs using the hard-coded value `http://localhost:8080`. If the port or host is overridden, the `shortUrl` field still reports port 8080. Requests sent to the actual configured port continue to work.

### Override the database URL

Linux or macOS:

```bash
SPRING_DATASOURCE_URL='jdbc:h2:mem:tinyurl' ./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:h2:mem:tinyurl"
.\mvnw.cmd spring-boot:run
```

The application does not yet provide separate local and test profile files.

## 13. IDE setup

### IntelliJ IDEA

1. Open the repository directory.
2. Import the project as Maven when prompted.
3. Configure the project SDK as Java 21.
4. Allow Maven dependencies to download.
5. Run `com.tinyurl.TinyurlApplication`.

### Eclipse or Spring Tool Suite

1. Select **File → Import → Existing Maven Projects**.
2. Choose the repository directory.
3. Configure Java 21.
4. Run `TinyurlApplication` as a Spring Boot application.

### VS Code

1. Install Java, Maven, and Spring Boot extensions.
2. Open the repository directory.
3. Configure the Java runtime as JDK 21.
4. Run `TinyurlApplication.java`.

If IDE behavior differs from the command line, confirm the IDE uses the same Java 21 installation.

## 14. Continuous integration

The workflow is located at `.github/workflows/maven.yml`. It runs Maven `clean verify` with Java 21 and uploads Surefire reports when a build fails.

It currently triggers for:

- Pushes to `Branch1`
- Pull requests targeting `Branch1`
- Manual runs using `workflow_dispatch`

It does not automatically trigger for `main` or other feature branches.

## 15. Troubleshooting

### Wrong or missing Java version

Install a Java 21 JDK, set `JAVA_HOME`, place its `bin` directory first in `PATH`, open a new terminal, and rerun `java -version`.

### `mvnw: Permission denied`

```bash
chmod +x mvnw
./mvnw test
```

Or use `bash mvnw test`.

### Maven cannot download dependencies

Confirm the machine can reach [Maven Central](https://repo.maven.apache.org/maven2) and check proxy, VPN, firewall, and Maven `settings.xml` configuration.

After connectivity is restored:

```bash
./mvnw -U clean verify
```

### Port 8080 is already in use

Stop the process using port 8080 or start on another port:

```bash
SERVER_PORT=8081 ./mvnw spring-boot:run
```

The returned `shortUrl` remains hard-coded to port 8080.

### H2 database is locked

1. Stop all TinyURL instances and H2 clients.
2. Confirm no Java process is using the database.
3. Start one application instance from the repository root.

Do not delete a database file containing needed data.

### H2 console cannot connect

Verify:

- The application is running.
- JDBC URL is exactly `jdbc:h2:file:./data/tinyurl;AUTO_SERVER=TRUE`.
- Username is `sa`.
- Password is blank.
- The working directory is the repository root.

### API returns 404 for a short code

Create a new short URL after the current application startup and use the returned code. The current `create-drop` setting does not provide durable application data.

### Tests pass locally but CI does not run

The workflow watches `Branch1`, not every feature branch or `main`. Use a manual workflow run or update the trigger in a separately reviewed change.

## 16. Stop and clean up

Stop the application with `Ctrl+C`.

Remove generated build output:

```bash
./mvnw clean
```

Windows users can run `mvnw.cmd clean`.

The H2 database is under `data/` when the application runs from the repository root. Deleting it is irreversible unless it has been backed up.

## 17. Quick start

### Linux or macOS

```bash
git clone https://github.com/satishcumar/tinyURL.git
cd tinyURL
chmod +x mvnw
./mvnw clean verify
./mvnw spring-boot:run
```

In another terminal:

```bash
curl -i http://localhost:8080/actuator/health
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

### Windows PowerShell

```powershell
git clone https://github.com/satishcumar/tinyURL.git
Set-Location tinyURL
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

In another PowerShell window:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
```
