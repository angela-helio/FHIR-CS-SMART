# SMART on FHIR — Spring Boot (Java 21)

A minimal, **senior-grade** structure to run a SMART on FHIR client in Java with **Spring Boot 3**, **PKCE**, and **HAPI FHIR**.

## Features
- SMART discovery via `/.well-known/smart-configuration` with `/metadata` fallback
- Authorization Code + **PKCE** (public client)
- Simple session storage for `state`, `code_verifier`, and token set
- HAPI FHIR client usage with `Bearer` token (example: fetch first 5 `Patient` resources)
- Friendly Thymeleaf pages (`/`, `/patients`)

## Quick start

1) Edit `src/main/resources/application.yml`:
```yaml
smart:
  fhirBase: "https://launch.smarthealthit.org/v/r4/sim/<YOUR>/fhir"
  clientId: "YOUR_CLIENT_ID"
  redirectUri: "http://127.0.0.1:8080/callback"
  scopes: "launch/patient patient.read openid fhirUser offline_access"
```

2) Build & run:
```bash
mvn -q -DskipTests spring-boot:run
# or
mvn -q -DskipTests package
java -jar target/smart-fhir-springboot-1.0.0.jar
```

3) Open http://127.0.0.1:8080 and click **Connect with SMART**.

> ℹ️ For a quick sandbox, use the SMART App Launcher to get a `client_id` and consistent scopes. Register your `redirectUri` there as well.

## Notes
- If your server issues a `refresh_token`, the app will automatically refresh the access token when close to expiry.
- For **EHR Launch** (with `iss`/`launch`), you can pass a `launch` value via `application.yml` and the app will include it in the authorize URL.
- Replace or enhance the in-session token storage with a persistent store for multi-user / production scenarios.
- The sample queries 5 `Patient` resources; tailor `FhirService` to your needs.

## Structure
```
src/main/java/com/example/smartspring
├─ SmartSpringApplication.java
├─ config/AppProperties.java
├─ oauth/
│  ├─ PkceUtil.java
│  ├─ SmartDiscoveryService.java
│  └─ TokenService.java
├─ service/FhirService.java
└─ web/
   ├─ AuthController.java
   └─ FhirController.java
src/main/resources
├─ application.yml
└─ templates/
   ├─ index.html
   └─ patients.html
```

---

## License
MIT (sample code)
