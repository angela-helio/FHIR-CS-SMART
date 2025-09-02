# Veradigm/Allscripts SMART on FHIR Setup Guide

## Overview
This application is configured to work with Veradigm's (formerly Allscripts) SMART on FHIR implementation using their sandbox environment.

## Prerequisites

### 1. Developer Account Registration
1. Sign up at **https://developer.veradigm.com/**
2. Complete required information and accept User Agreement
3. Confirm email address to receive developer credentials
4. Contact VeradigmConnect@veradigm.com for questions

### 2. Patient Portal Access
Patients must be registered with either:
- **FollowMyHealth** portal  
- **AHC** (Allscripts Healthcare Community) portal

## Application Registration Process

### 1. Register FHIR Application
1. Access **Veradigm Developer Program portal**
2. Go to **My Dashboard** page
3. On **My FHIR Applications** tile, click **+** to add new application
4. Complete the FHIR App registration form:

#### Required Information:
- **App Name**: Clear company/product identifier (appears in client License Management Portal)
- **App Type**: Select primary audience
  - `Patient`: Patient-facing applications
  - `Provider`: Physician/healthcare provider applications  
  - `System`: External system integration (e.g., insurance companies)
- **App Description**: Detailed usage description
- **Additional Info Link**: Marketing website or documentation URL
- **JWKS URL**: Backend authentication public key endpoint
- **Redirect URLs**: Up to 5 redirect URIs
  - Desktop apps: Include `urn:ietf:wg:oauth:2.0:oob`
  - Web apps: Use callback URLs like `https://yourdomain.com/callback`
- **Launch URLs**: SMART app launch URLs (up to 3)
- **Client Type**: `Confidential Client` (trusted) or `Public Client` (not trusted)
- **App Type**: `Native App` (desktop) or `Web App` (mobile)

#### Scope Selection:
- Select only required scopes for your app type
- Patient apps: Use `patient/*` scopes
- Provider apps: Use `user/*` scopes  
- Include `offline_access` if refresh tokens needed

5. **Save** to generate OAuth credentials:
   - Client ID
   - Client Secret
   - Secret Expiration Date

### 2. Additional Configuration
1. Select **Purpose of Use** for the application
2. Verify scope selection matches app functionality
3. Complete testing before requesting production access
4. Click **Request Production Access** when ready

⚠️ **Important**: App name, type, and Purpose of Use cannot be changed after production approval

### 2. Environment Configuration
1. Copy `.env.example` to `.env`
2. Fill in your actual Veradigm credentials:
   ```bash
   VERADIGM_CLIENT_ID=your_actual_client_id
   VERADIGM_CLIENT_SECRET=your_actual_client_secret
   ```

## Local Development with ngrok

For local development and testing with external EHR systems, use **ngrok** to expose your local application to the internet.

### 1. Install ngrok
```bash
# Download from https://ngrok.com/download
# Or install via package manager
npm install -g ngrok
# or
brew install ngrok
```

### 2. Start ngrok Tunnel
```bash
# Start your Spring Boot application first
mvn spring-boot:run

# In another terminal, start ngrok
ngrok http 8080
```

### 3. Configure ngrok URLs
After starting ngrok, you'll get a public URL like `https://abc123.ngrok.io`. Update your configuration:

#### In Veradigm Connect Portal:
- **Redirect URLs**: `https://abc123.ngrok.io/callback`
- **Launch URLs**: `https://abc123.ngrok.io/ehr/launch`
- **JWKS URL**: `https://abc123.ngrok.io/.well-known/jwks.json`

#### In your `.env` file:
```bash
SMART_REDIRECT_URI=https://abc123.ngrok.io/callback
NGROK_URL=https://abc123.ngrok.io
```

### 4. Test URLs
- **Standalone Launch**: `https://abc123.ngrok.io`
- **EHR Launch**: `https://abc123.ngrok.io/ehr/launch?iss={fhir_base}&launch={launch_token}`
- **Backend Auth**: `https://abc123.ngrok.io/backend/auth`
- **JWKS Endpoint**: `https://abc123.ngrok.io/.well-known/jwks.json`

### 5. ngrok Best Practices
- **Free tier**: URLs change on restart - update Veradigm registration each time
- **Paid tier**: Use custom subdomain for consistent URLs
- **Security**: ngrok URLs are public - don't expose sensitive data
- **HTTPS**: ngrok provides HTTPS by default (required for SMART on FHIR)

### 3. Application Properties
The application is pre-configured with:
- **FHIR Base URL**: `https://fhir.fhirpoint.open.allscripts.com/fhirroute/fhir/CP00101/`
- **Discovery Endpoints**: 
  - Primary: `/.well-known/smart-configuration` (R4)
  - Fallback: `/metadata` (CapabilityStatement)
- **Scopes**: `launch/patient patient.read openid fhirUser offline_access`

## Launch Modes

### 1. EHR Launch
For integration with Veradigm EHR systems:
- Requires **Veradigm Connect Integrator tier** or above
- Launch URL: `http://127.0.0.1:8080/ehr/launch?iss={fhir_base}&launch={launch_token}`

### 2. Standalone Launch
For patient portal or standalone testing:
- Launch URL: `http://127.0.0.1:8080/auth/start`
- Uses configured FHIR base URL and client credentials

## Authentication Flow

1. **Discovery**: App discovers OAuth endpoints via SMART configuration
2. **Authorization**: Redirects to Veradigm OAuth server with PKCE
3. **Token Exchange**: Exchanges authorization code for access token
4. **FHIR Access**: Uses access token to make FHIR API calls

## Supported Features

- ✅ **PKCE (Proof Key for Code Exchange)**: Enhanced security for public clients
- ✅ **Refresh Tokens**: Long-term access with `offline_access` scope
- ✅ **Patient Context**: Automatic patient ID extraction from token response
- ✅ **Provider Authentication**: Support for `fhirUser` scope
- ✅ **EHR Launch**: Full support for EHR-initiated launches

## Testing

### 1. Sandbox Environment Setup
- **Request credentials**: Complete [sandbox form](https://developer.veradigm.com/testing) for provider credentials
- **FHIR Sandboxes**: View endpoint information on Veradigm Connect portal
- **Patient Registration**: Patients must be registered in FollowMyHealth or AHC portal before testing

### 2. Testing with Postman (Patient/Provider Apps)
Create environment variables:
- `FhirURL`: FHIR server URL
- `AuthURL`: Authorization server URL (ends in `/authorize`)
- `CallbackURL`: Your callback URL (e.g., `http://localhost/callback`)
- `TokenURL`: Token server URL (ends in `/token`)
- `ClientID`: Your FHIR application Client ID
- `ClientSecret`: Your FHIR application Client Secret
- `Scope`: Your application scopes

### 3. Start the Application
```bash
mvn spring-boot:run
```

### 4. Test Standalone Launch
1. Navigate to `http://127.0.0.1:8080`
2. Click "Start Authorization"
3. **Provider apps**: Login with EHR credentials
4. **Patient apps**: Login with patient portal credentials
5. Grant permissions and access patient data

### 5. Test EHR Launch
- **Requirements**: Veradigm Connect Integrator tier or above
- **Launch URL**: `http://127.0.0.1:8080/ehr/launch?iss={fhir_base}&launch={launch_token}`
- **Sandbox testing**: Submit support ticket for launch button configuration

### 6. Test Backend Service Authentication
1. Enable backend auth: `BACKEND_AUTH_ENABLED=true`
2. Navigate to `http://127.0.0.1:8080/backend/auth`
3. Click "Authenticate" to obtain system access token
4. Access FHIR resources with system-level permissions

## Troubleshooting

### Common Issues

1. **Invalid Client Credentials**
   - Verify Client ID/Secret in Veradigm Connect portal
   - Ensure credentials match environment variables

2. **Redirect URI Mismatch**
   - Verify redirect URI is registered in Veradigm Connect
   - Check for exact match including protocol and port

3. **Patient Not Found**
   - Ensure patient is registered in FollowMyHealth or AHC portal
   - Verify patient has granted access to your application

4. **Scope Permissions**
   - Confirm requested scopes are supported by Veradigm
   - Check if additional scopes require special approval

### Debug Endpoints

- **Discovery**: `GET /.well-known/smart-configuration`
- **Metadata**: `GET /metadata`
- **Token Endpoint**: Check logs for token exchange details

## Production Deployment

### 1. Environment Variables
Set production environment variables:
```bash
SPRING_PROFILES_ACTIVE=prod
VERADIGM_CLIENT_ID=prod_client_id
VERADIGM_CLIENT_SECRET=prod_client_secret
PRODUCTION_REDIRECT_URI=https://yourdomain.com/callback
```

### 2. HTTPS Requirements
- Production deployments must use HTTPS
- Register HTTPS redirect URIs in Veradigm Connect
- Consider using reverse proxy (nginx, Apache) for SSL termination

### 3. Security Considerations
- Store client secrets securely (environment variables, key vault)
- Implement proper session management
- Use secure cookie settings for production
- Consider implementing rate limiting

## Important Limitations & Considerations

### API Limitations
- ⚠️ **Read-Only Access**: Veradigm FHIR API is limited to read-only operations (no write-backs)
- ⚠️ **SSO Support**: Single Sign-On for FHIR R4 not supported in Sunrise/Paragon EHRs
  - Veradigm EHR version 24.5+ supports SSO
  - Use Unity API for earlier versions requiring app launch
- ⚠️ **DSTU2 Deprecation**: Support ends 6/1/2025 - migrate to R4

### Integration Options
- **FHIR R4**: Read-only patient data access
- **Unity API**: Bidirectional integration (reads + writes) for deeper EHR integration
- **Practice Management**: Must use Unity API for demographics, appointments, financial data

### Client Licensing
- Clients must activate applications through **License Management Portal (LMP)**
- Developers cannot license applications for clients
- Clients need Veradigm/Altera Client Portal credentials

## Client EHR Configuration

### TouchWorks EHR Setup (Client-side)
1. Login with administrative privileges
2. **Site Map** > **TW Administration** > **General** > **Site Map Config**
3. Click **Add Site Map Entry**
4. Configure properties:
   - **Display Name**: Your app name
   - **Content Type**: `SMART on FHIR R4`
   - **Address**: Your launch URL
   - **Launch Target**: Embedded Workspace Page
5. Set security permissions and activate entry

### Veradigm EHR Setup (Client-side)
1. Login with Administration module access
2. **Administration Module** > **Site Settings** > **SMART on FHIR Apps**
3. Click **Insert** or green plus sign
4. Enter app name, description, and launch URL
5. Ensure **Enabled** is selected

### Sunrise Clinical Manager Setup (Client-side)
1. Open **Sunrise Configuration tool**
2. Click **Clinical Manager Definition** icon
3. **Add Tab** with:
   - **Tab Name**: Your app name
   - **Assembly**: `Sunrise.Clinicals.WebTab.dll`
   - **Class**: `Sunrise.Clinicals.WebTab.WebTabController`
4. Configure URL template: `{launch_url}?launch=<PatientGuid>&iss=<FHIRUrl>`

## Backend Service Authentication (System Apps)

### Setup Requirements
1. Enable backend authentication:
   ```bash
   BACKEND_AUTH_ENABLED=true
   BACKEND_SCOPES=system/*.read
   ```

2. Register JWKS URL in Veradigm Connect:
   - Development: `http://127.0.0.1:8080/.well-known/jwks.json`
   - Production: `https://yourdomain.com/.well-known/jwks.json`

3. Access backend auth page: `http://127.0.0.1:8080/backend/auth`

### Authentication Flow
1. **JWT Generation**: Create signed JWT assertion (2-20 min expiration)
2. **Token Request**: POST to token endpoint with:
   - `client_assertion`: Your signed JWT
   - `client_assertion_type`: `urn:ietf:params:oauth:client-assertion-type:jwt-bearer`
   - `grant_type`: `client_credentials`
   - `scope`: Requested scopes (e.g., `system/*.read`)
3. **Access Token**: Use returned token for FHIR API calls

### Security Notes
- **Private keys**: Generated automatically (store securely in production)
- **Certificate updates**: Processed nightly by Veradigm infrastructure
- **JWT expiration**: 2-20 minutes (single use only)
- **Production**: Use HSM or key vault for private key storage

## Resources

- [Veradigm Developer Portal](https://developer.veradigm.com/)
- [Veradigm Connect Portal](https://connect.veradigm.com/)
- [SMART on FHIR Specification](https://hl7.org/fhir/smart-app-launch/)
- [Partner Testing Environments](https://developer.veradigm.com/testing)
- Contact: VeradigmConnect@veradigm.com
