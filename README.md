# SMART on FHIR — Spring Boot (EHR + Standalone)

Sample implementing **SMART App Launch** with **EHR Launch** and **Standalone Launch** using Spring Boot 3, Java 21, PKCE and HAPI FHIR.

## Quick Start

### Standalone
1. Edit `src/main/resources/application.yml` (set `smart.fhirBase`, `clientId`, `redirectUri`, `scopes`).
2. Run: `mvn -q -DskipTests spring-boot:run` and open http://127.0.0.1:8080 → **Standalone Launch**.

### EHR Launch
1. Host the app and set your `redirectUri` in your client registration.
2. Configure the EHR (or the SMART App Launcher) to open:
   ```
   https://yourapp/ehr/launch?iss=<FHIR_BASE>&launch=<LAUNCH_TOKEN>
   ```
3. The app will discover SMART endpoints from `iss`, build the authorize request with `aud=<iss>` and `launch=<LAUNCH_TOKEN>`, then exchange the code and query FHIR.

## Notes
- Discovery via `/.well-known/smart-configuration` with `/metadata` fallback.
- PKCE (no client secret), token refresh if `refresh_token` is issued.
- HAPI FHIR used to query 5 `Patient` resources.
