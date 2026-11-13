# Alumni LinkedIn Profile Searcher

A Spring Boot REST API that finds LinkedIn profiles of alumni from a given university who currently work in a given role. It uses the **PhantomBuster API** to search LinkedIn and saves every profile it finds in a **PostgreSQL** database.

There is no user interface. Everything is done through REST calls, for example from Postman.

---

## Table of Contents

1. [What the Application Does](#1-what-the-application-does)
2. [Technology Stack](#2-technology-stack)
3. [Project Structure](#3-project-structure)
4. [How a Search Works](#4-how-a-search-works)
5. [API Reference](#5-api-reference)
6. [Error Handling](#6-error-handling)
7. [Data Model](#7-data-model)
8. [PhantomBuster Setup](#8-phantombuster-setup)
9. [Configuration](#9-configuration)
10. [Running the Application](#10-running-the-application)
11. [Testing](#11-testing)
12. [Testing with Postman](#12-testing-with-postman)
13. [Design Decisions](#13-design-decisions)
14. [Known Limitations](#14-known-limitations)
15. [Database Setup (PostgreSQL)](#15-database-setup-postgresql)

---

## 1. What the Application Does

- Accepts a **university name**, a **current designation** (job title) and an optional **pass-out year**.
- Asks PhantomBuster to run a LinkedIn people search with those details.
- Waits for PhantomBuster to finish, then reads the profiles it found.
- Saves each profile in PostgreSQL. A profile that was already saved earlier is **updated**, not duplicated.
- Returns the list of alumni profiles as JSON.
- Lists all saved alumni profiles (`GET /api/alumni/all`).

---

## 2. Technology Stack

| Area            | Choice                                     |
|-----------------|--------------------------------------------|
| Language        | Java 21                                    |
| Framework       | Spring Boot 4.1.2 (Web MVC, Data JPA, Validation, RestClient) |
| Database        | PostgreSQL                                 |
| Scraping        | PhantomBuster API v2                       |
| Build tool      | Maven (Maven Wrapper included, no install needed) |
| API docs        | springdoc-openapi 3 (Swagger UI)           |
| Testing         | JUnit 5, Mockito, Spring MockMvc, MockRestServiceServer, H2 |

---

## 3. Project Structure

The project uses a simple layered structure. Each folder has one job.

```
alumni-searcher
├── pom.xml
├── mvnw / mvnw.cmd                     Maven Wrapper (build without installing Maven)
├── postman/
│   └── Alumni-Searcher.postman_collection.json
└── src
    ├── main
    │   ├── java/com/alumni/searcher
    │   │   ├── AlumniSearcherApplication.java   Starts the application
    │   │   ├── config/
    │   │   │   └── OpenApiConfig.java            Swagger / OpenAPI documentation setup
    │   │   ├── controller/
    │   │   │   └── AlumniController.java         REST endpoints
    │   │   ├── service/
    │   │   │   └── AlumniService.java            Business logic: search, de-duplicate, save
    │   │   ├── phantombuster/
    │   │   │   ├── PhantomBusterClient.java      Talks to the PhantomBuster API
    │   │   │   ├── PhantomBusterProperties.java  PhantomBuster settings
    │   │   │   └── ScrapedProfile.java           A profile as returned by PhantomBuster
    │   │   ├── repository/
    │   │   │   └── AlumniRepository.java         Database access
    │   │   ├── entity/
    │   │   │   └── Alumni.java                   The "alumni" database table
    │   │   ├── dto/
    │   │   │   ├── AlumniSearchRequest.java      Incoming search request + validation rules
    │   │   │   ├── AlumniProfileResponse.java    One alumni profile in the reply
    │   │   │   └── ApiResponse.java              Common reply envelope (status / data / message)
    │   │   └── exception/
    │   │       ├── GlobalExceptionHandler.java   Turns every error into a clean JSON reply
    │   │       ├── PhantomBusterException.java
    │   │       └── ScrapingTimeoutException.java
    │   └── resources/
    │       └── application.properties
    └── test/java/com/alumni/searcher
        ├── AlumniApiIntegrationTest.java
        ├── config/OpenApiConfigTest.java
        ├── controller/AlumniControllerTest.java
        ├── service/AlumniServiceTest.java
        ├── repository/AlumniRepositoryTest.java
        └── phantombuster/PhantomBusterClientTest.java
```

---

## 4. How a Search Works

```
Postman ──POST /api/alumni/search──► AlumniController
                                         │  (validates the request)
                                         ▼
                                     AlumniService
                                         │  builds the search text, e.g.
                                         │  "Software Engineer" "University of XYZ" 2020
                                         ▼
                                  PhantomBusterClient
                                         │ 1. POST /agents/launch          → gets a job (container) id
                                         │ 2. GET  /containers/fetch       → repeats until the job is "finished"
                                         │ 3. GET  /containers/fetch-result-object → reads the profiles
                                         ▼
                                     AlumniService
                                         │  drops profiles with no name or link,
                                         │  removes duplicates, updates already-saved
                                         │  profiles and inserts new ones
                                         ▼
                                  PostgreSQL ("alumni" table)
                                         │
                                         ▼
                              JSON list of alumni profiles
```

The search waits for PhantomBuster to finish (up to 3 minutes by default). If it takes longer, the API replies with `504 Gateway Timeout` and the caller can simply try again.

---

## 5. API Reference

Base URL: `http://localhost:8080`

Every reply uses the same envelope:

| Field     | Meaning                                                  |
|-----------|----------------------------------------------------------|
| `status`  | `"success"` or `"error"`                                 |
| `data`    | The result (only on success)                             |
| `message` | What went wrong (only on error)                          |
| `errors`  | Field-by-field problems (only when validation fails)     |

### Interactive documentation (Swagger)

While the application is running:

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html). Both endpoints can be tried from the browser with pre-filled examples.
- **OpenAPI document (JSON):** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs). It can also be imported into Postman.

The setup lives in `config/OpenApiConfig.java`. It adds the API description, the shared error reply format, and the error codes each endpoint can return, so the controller only describes what each endpoint does.

### 5.1 Search Alumni Profiles

`POST /api/alumni/search`

**Headers:** `Content-Type: application/json`

**Request body**

```json
{
  "university": "University of XYZ",
  "designation": "Software Engineer",
  "passoutYear": "2020"
}
```

| Field         | Required | Rules                                                  |
|---------------|----------|--------------------------------------------------------|
| `university`  | Yes      | Not blank, up to 200 characters                        |
| `designation` | Yes      | Not blank, up to 150 characters                        |
| `passoutYear` | No       | A number (or a number in quotes) between 1950 and 2100 |

**Success reply — `200 OK`**

```json
{
  "status": "success",
  "data": [
    {
      "name": "John Doe",
      "currentRole": "Software Engineer",
      "university": "University of XYZ",
      "location": "New York, NY",
      "linkedinHeadline": "Passionate Software Engineer at XYZ Corp",
      "passoutYear": 2020,
      "profileUrl": "https://www.linkedin.com/in/johndoe"
    },
    {
      "name": "Jane Smith",
      "currentRole": "Data Scientist",
      "university": "University of XYZ",
      "location": "San Francisco, CA",
      "linkedinHeadline": "Data Scientist | AI Enthusiast",
      "passoutYear": 2019,
      "profileUrl": "https://www.linkedin.com/in/janesmith"
    }
  ]
}
```

If nothing is found, `data` is an empty list `[]`.

**Validation error — `400 Bad Request`**

```json
{
  "status": "error",
  "message": "Some fields are missing or invalid",
  "errors": {
    "university": "University name is required",
    "passoutYear": "Pass-out year must be 1950 or later"
  }
}
```

### 5.2 Get Saved Alumni Profiles

`GET /api/alumni/all`

Returns every alumni profile saved in the database from earlier searches. It does not call PhantomBuster.

**Success reply — `200 OK`**

```json
{
  "status": "success",
  "data": [
    {
      "name": "John Doe",
      "currentRole": "Software Engineer",
      "university": "University of XYZ",
      "location": "New York, NY",
      "linkedinHeadline": "Passionate Software Engineer at XYZ Corp",
      "passoutYear": 2020,
      "profileUrl": "https://www.linkedin.com/in/johndoe"
    }
  ]
}
```

---

## 6. Error Handling

All errors are handled in one place (`GlobalExceptionHandler`), so the caller always gets a JSON reply and never a stack trace.

| HTTP status                  | When it happens                                                        |
|------------------------------|------------------------------------------------------------------------|
| `400 Bad Request`            | Missing or invalid fields, broken JSON, pass-out year is not a number  |
| `404 Not Found`              | The URL does not exist                                                 |
| `405 Method Not Allowed`     | Wrong HTTP method, e.g. `GET` on `/api/alumni/search`                  |
| `409 Conflict`               | Two searches tried to save the same new profile at the same moment (retrying is safe) |
| `415 Unsupported Media Type` | The body is not sent as JSON                                           |
| `502 Bad Gateway`            | PhantomBuster is not configured, rejects the API key, is unreachable, the job fails, or returns unreadable results |
| `503 Service Unavailable`    | The database cannot be reached                                         |
| `504 Gateway Timeout`        | The PhantomBuster search did not finish in the allowed time            |
| `500 Internal Server Error`  | Any other unexpected problem (details are logged, not shown)           |

Example:

```json
{
  "status": "error",
  "message": "PhantomBuster rejected the API key while trying to start the search."
}
```

---

## 7. Data Model

Table **`alumni`** (created automatically by the application on first start):

| Column              | Type          | Notes                                          |
|---------------------|---------------|------------------------------------------------|
| `id`                | bigint        | Primary key, auto-generated                    |
| `name`              | varchar(255)  | Required                                       |
| `current_designation` | varchar(255) | Shown as `currentRole` in the API (`current_role` is a reserved word in PostgreSQL) |
| `university`        | varchar(255)  | Required                                       |
| `location`          | varchar(255)  |                                                |
| `linkedin_headline` | varchar(1000) |                                                |
| `passout_year`      | integer       | Filled when the search included a year         |
| `profile_url`       | varchar(500)  | Unique — used to recognise a person seen before |
| `created_at`        | timestamp     | When the profile was first saved               |
| `updated_at`        | timestamp     | When the profile was last refreshed            |

---

## 8. PhantomBuster Setup

The application drives an existing PhantomBuster **agent** (a configured phantom). Set it up once:

1. Create an account at [phantombuster.com](https://phantombuster.com).
2. Add the **LinkedIn Search Export** phantom to your dashboard.
3. Connect your LinkedIn account in the phantom setup (PhantomBuster stores the LinkedIn session cookie, so the application never handles it).
4. Save the phantom. Open it and copy the **agent id** from the browser address bar (`https://phantombuster.com/<org>/phantoms/<AGENT_ID>/setup`).
5. Go to **Workspace settings → API keys** and create an **API key**.
6. Open the phantom's setup and check the names of its settings for the **search text** and the **number of profiles**. The application sends them as `linkedInSearchUrl` and `numberOfResultsPerSearch` by default (the names used by LinkedIn Search Export). If your phantom uses other names, set `PHANTOMBUSTER_SEARCH_ARGUMENT` and `PHANTOMBUSTER_PROFILE_COUNT_ARGUMENT` to match. No code change is needed.
7. Run the phantom once from the PhantomBuster dashboard to check that the LinkedIn connection works before calling the API.

For every search, the application starts this agent and passes two extra settings for that run only (`bonusArgument`):

| Setting            | Value                                                     |
|--------------------|-----------------------------------------------------------|
| `linkedInSearchUrl` | A LinkedIn people-search URL for `"<designation>" "<university>" <passoutYear if given>`, e.g. `https://www.linkedin.com/search/results/people/?keywords=%22Software%20Engineer%22%20%22Stanford%20University%22` |
| `numberOfResultsPerSearch` | `phantombuster.profiles-per-search` (default 25)   |

All other phantom settings stay as saved in the PhantomBuster dashboard.

The application uses these PhantomBuster API v2 calls, all authenticated with the `X-Phantombuster-Key` header:

| Call | Purpose |
|------|---------|
| `POST /agents/launch` | Start the phantom with the search for this request |
| `GET /containers/fetch?id=…` | Check whether the run is `running`, `finished` or `launch error`, and its exit code |
| `GET /containers/fetch-result-object?id=…` | Read the profiles the run found |

---

## 9. Configuration

All settings are in `src/main/resources/application.properties` and can be overridden with environment variables.

| Environment variable                 | Default                                      | Meaning                          |
|--------------------------------------|----------------------------------------------|----------------------------------|
| `DB_URL`                             | `jdbc:postgresql://localhost:5432/alumni_db` | Database address                 |
| `DB_USERNAME`                        | `alumni_user`                                | Database user                    |
| `DB_PASSWORD`                        | `alumni_password`                            | Database password                |
| `PORT` / `SERVER_PORT`               | `8080`                                       | Port the API listens on (`PORT` is set automatically by hosts such as Render) |
| `PHANTOMBUSTER_API_KEY`              | *(empty)*                                    | PhantomBuster API key            |
| `PHANTOMBUSTER_AGENT_ID`             | *(empty)*                                    | Id of the LinkedIn Search Export agent |
| `PHANTOMBUSTER_PROFILES_PER_SEARCH`  | `25`                                         | How many profiles one search asks for |
| `PHANTOMBUSTER_SEARCH_ARGUMENT`      | `linkedInSearchUrl`                          | Name of the phantom setting that receives the search text |
| `PHANTOMBUSTER_PROFILE_COUNT_ARGUMENT` | `numberOfResultsPerSearch`                 | Name of the phantom setting that receives the profile count |

How long a search may wait is set by `phantombuster.poll-interval` (default `5s`) × `phantombuster.max-poll-attempts` (default `36`) = 3 minutes.

> Keep the API key out of source code. Pass it as an environment variable.

---

## 10. Running the Application

**Prerequisites**

- Java 21 or newer (`java -version`)
- PostgreSQL running, with the database created (see [section 15](#15-database-setup-postgresql))
- A PhantomBuster API key and agent id (see [section 8](#8-phantombuster-setup))

Maven does not need to be installed; the included wrapper (`mvnw`) downloads it automatically.

**macOS / Linux**

```bash
cd alumni-searcher

export PHANTOMBUSTER_API_KEY=your_api_key
export PHANTOMBUSTER_AGENT_ID=your_agent_id

./mvnw spring-boot:run
```

**Windows (PowerShell)**

```powershell
cd alumni-searcher

$env:PHANTOMBUSTER_API_KEY="your_api_key"
$env:PHANTOMBUSTER_AGENT_ID="your_agent_id"

.\mvnw.cmd spring-boot:run
```

**Or build a jar and run it**

```bash
./mvnw clean package
java -jar target/alumni-searcher-0.0.1-SNAPSHOT.jar
```

The API is ready when the log shows `Started AlumniSearcherApplication`. The `alumni` table is created automatically at that point.

---

## 11. Testing

The project was built test-first. The tests need **no PostgreSQL and no PhantomBuster account**: database tests use an in-memory H2 database, and PhantomBuster is replaced with test doubles.

```bash
./mvnw test
```

| Test class                 | What it checks                                                        |
|----------------------------|-----------------------------------------------------------------------|
| `AlumniControllerTest`     | Both endpoints: request/response format, validation messages, and the HTTP status for every kind of error (400, 404, 405, 409, 415, 502, 503, 504, 500) |
| `AlumniServiceTest`        | Search text building, saving new profiles, updating known ones, skipping incomplete and duplicate profiles, listing saved alumni |
| `AlumniRepositoryTest`     | Table mapping, timestamps, lookup by LinkedIn URL, the unique URL rule, newest-first ordering |
| `PhantomBusterClientTest`  | Launch → wait → read flow against a fake PhantomBuster server, configurable argument names, plus rejected key, server down, failed job, timeout, bad results, and missing configuration |
| `OpenApiConfigTest`        | The OpenAPI document lists both endpoints, their error codes and the error format, and Swagger UI is served |
| `AlumniApiIntegrationTest` | The whole application with a real (in-memory) database: search → saved → listed by `/all`, and a repeated search does not create duplicates |

---

## 12. Testing with Postman

1. Open Postman → **Import** → choose `postman/Alumni-Searcher.postman_collection.json`.
2. The collection uses a `baseUrl` variable set to `http://localhost:8080`; change it if you run on another port.
3. Start the application and send the requests:
   - **Search alumni (with pass-out year)**
   - **Search alumni (without pass-out year)**
   - **Get all saved alumni**
   - **Search alumni (invalid request - 400)** — shows the validation errors
   - **Search alumni (pass-out year not a number - 400)**
   - **Search alumni (wrong method - 405)**

Each request has Postman tests attached, so **Run collection** checks the status codes and reply format automatically.

A search request waits for PhantomBuster, so it can take from a few seconds up to a few minutes.

---

## 13. Design Decisions

- **Layered design (Controller → Service → Client / Repository).** Each layer has one job, which keeps classes small and easy to test.
- **Gateway / Adapter pattern for PhantomBuster.** All PhantomBuster details (URLs, headers, polling, result parsing) live in `PhantomBusterClient`. The rest of the code only sees a clean `ScrapedProfile` list, so another scraping provider could be swapped in by changing one class.
- **Repository pattern.** Spring Data JPA handles database access through `AlumniRepository`.
- **DTOs separate from the entity.** The API contract (`AlumniSearchRequest`, `AlumniProfileResponse`) is independent of the database table, so one can change without breaking the other.
- **Static factory methods** (`ApiResponse.success/error`, `AlumniProfileResponse.from`, `ScrapedProfile.fromRawFields`) keep object creation readable.
- **Centralised error handling** with `@RestControllerAdvice`, giving one consistent error format.
- **Upsert by LinkedIn profile URL.** Searching again refreshes existing records instead of creating duplicates. The URL column is also unique in the database as a safety net.
- **No long database transaction around the scrape.** The PhantomBuster call can take minutes, so it runs outside any transaction; the database is only touched after the results arrive.
- **Tolerant result mapping.** PhantomBuster phantoms have used different field names over time (`fullName` / `name` / `firstName` + `lastName`, `profileUrl` / `linkedinProfileUrl`, `headline` / `job`). The client accepts all of them.
- **Externalised configuration** through `@ConfigurationProperties` and environment variables. No secrets in code.

---

## 14. Known Limitations

- **Pass-out year:** LinkedIn search results do not include education dates. The year is added to the search text to narrow results, and the requested year is stored with the profiles found. It is not independently verified against each person's education history.
- **Current role and university:** When PhantomBuster does not return a separate job title or school, the searched designation and university are stored, since those are what the profile matched.
- **Results location:** profiles are read from the run's result object. If a phantom only writes its results to a CSV/JSON file and leaves the result object empty, the search returns an empty list.
- **Synchronous search:** The search request waits for PhantomBuster to finish. Very large searches may hit the 3-minute limit; this can be raised in the configuration.
- **LinkedIn limits:** PhantomBuster and LinkedIn apply their own daily limits on how many profiles can be searched.

---

## 15. Database Setup (PostgreSQL)

The application needs an empty PostgreSQL database and a user that can create tables in it. **The table itself is created automatically** when the application starts.

### Step 1 — Install PostgreSQL (skip if already installed)

- **macOS (Homebrew):**
  ```bash
  brew install postgresql@16
  brew services start postgresql@16
  ```
- **Windows / Linux:** download the installer from [postgresql.org/download](https://www.postgresql.org/download/) and follow the steps. Remember the password you set for the `postgres` user.

### Step 2 — Open the PostgreSQL console

```bash
psql -U postgres
```

(On macOS with Homebrew, `psql postgres` also works. On Windows, use **SQL Shell (psql)** from the Start menu. pgAdmin's Query Tool can run the same commands.)

### Step 3 — Create the user and the database

```sql
CREATE USER alumni_user WITH PASSWORD 'alumni_password';
CREATE DATABASE alumni_db OWNER alumni_user;
GRANT ALL PRIVILEGES ON DATABASE alumni_db TO alumni_user;
```

### Step 4 — Check it works

```sql
\c alumni_db alumni_user
\conninfo
\q
```

`\conninfo` should say you are connected to database `alumni_db` as user `alumni_user`.

### Step 5 — Point the application at the database

If you used the names and password above, nothing else is needed — they match the defaults in `application.properties`.

If you chose different values, set them before starting the application:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/your_database
export DB_USERNAME=your_user
export DB_PASSWORD=your_password
```

### Step 6 — Start the application and confirm the table

Start the application (see [section 10](#10-running-the-application)), then:

```bash
psql -U alumni_user -d alumni_db -c "\d alumni"
```

You should see the `alumni` table with the columns listed in [section 7](#7-data-model).

### Optional — Create the table by hand

The application creates the table itself, but if your database policy requires creating it manually, this is the equivalent SQL:

```sql
CREATE TABLE alumni (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255)  NOT NULL,
    current_designation VARCHAR(255),
    university        VARCHAR(255)  NOT NULL,
    location          VARCHAR(255),
    linkedin_headline VARCHAR(1000),
    passout_year      INTEGER,
    profile_url       VARCHAR(500)  UNIQUE,
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
```
