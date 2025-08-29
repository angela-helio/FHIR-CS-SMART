package com.example.smartspring.web;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import com.example.smartspring.config.SmartConfig;
import com.example.smartspring.helper.PatientExtractor;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.smartspring.smart.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Controller
public class FhirController {
    private final SmartDiscoveryClient discovery;
    private final OAuthService oauth;
    private final FhirContext fhirContext;

    @Value("${smart.client-id}") String clientId;
    @Value("${smart.redirect-uri}") String redirectUri;
    @Value("${smart.scopes}") String scopes;

    public FhirController(SmartDiscoveryClient discovery, OAuthService oauth, FhirContext fhirContext) {
        this.discovery = discovery; this.oauth = oauth; this.fhirContext = fhirContext;
    }

    @GetMapping("/ehr/launch")
    public void ehrLaunch(@RequestParam String iss, @RequestParam String launch,
                          HttpServletResponse res, HttpSession session) throws Exception {

        SmartConfig cfg = discovery.discover(iss);
        session.setAttribute("iss", iss);
        session.setAttribute("tokenEndpoint", cfg.tokenEndpoint());
        session.setAttribute("userinfoEndpoint", cfg.userinfoEndpoint());

        Pkce pkce = Pkce.generate();
        session.setAttribute("codeVerifier", pkce.verifier());

        String authUrl = cfg.authorizationEndpoint()
                + "?response_type=code"
                + "&client_id=" + url(clientId)
                + "&redirect_uri=" + url(redirectUri)
                + "&scope=" + url(scopes) // "openid fhirUser launch/patient patient/*.read"
                + "&aud=" + url(iss)
                + "&state=" + url(UUID.randomUUID().toString())
                + "&nonce=" + url(UUID.randomUUID().toString())
                + "&launch=" + url(launch)
                + "&code_challenge=" + url(pkce.challenge())
                + "&code_challenge_method=S256";

        res.sendRedirect(authUrl);
    }

    @GetMapping("/ehr/callback")
    public String ehrCallback(@RequestParam(required=false) String code,
                              @RequestParam(required=false) String state,
                              @RequestParam(required=false) String error,
                              @RequestParam(required=false, name="error_description") String errorDesc,
                              HttpSession session, Model model) {

        // A) Errores directos del /authorize
        if (error != null) {
            model.addAttribute("error", "Authorize error: " + error + (errorDesc != null ? " - " + errorDesc : ""));
            return "error"; // crea templates/error.html simple
        }

        // B) Validar sesión
        String iss = (String) session.getAttribute("iss");
        String tokenEndpoint = (String) session.getAttribute("tokenEndpoint");
        String userinfoEndpoint = (String) session.getAttribute("userinfoEndpoint");
        String codeVerifier = (String) session.getAttribute("codeVerifier");
        if (iss == null || tokenEndpoint == null || codeVerifier == null) {
            model.addAttribute("error", "Session expired or missing context (iss/tokenEndpoint/codeVerifier). Please relaunch.");
            return "error";
        }

        try {
            // C) Token exchange (maneja 400s y loguea cuerpo)
            var token = oauth.exchangeAuthorizationCode(tokenEndpoint, clientId, code, codeVerifier, redirectUri);
            String accessToken = token.accessToken();

            // 1) id_token → 2) token response → 3) /userinfo (fallback)

            String patientId = JwtUtils.tryExtractPatient(token.idToken());
            if (patientId == null) patientId = token.patient();
            if (patientId == null) patientId = PatientExtractor.fromTokenResponseRaw(token.rawJson());

            if (patientId == null) {
                var ui = oauth.userinfoRaw(userinfoEndpoint, accessToken); // devuelve String
                patientId = PatientExtractor.fromUserInfoRaw(ui);
            }

            if (patientId == null) {
                model.addAttribute("error", "No patient context. Abre la app desde un **patient chart** o verifica scopes 'launch' y 'launch/patient'.");
                return "error";
            }

            // E) Llamadas FHIR (con try/catch para mostrar mensaje claro si falla)
            IGenericClient fhir = fhirContext.newRestfulGenericClient(iss);
            fhir.registerInterceptor(new BearerTokenAuthInterceptor(accessToken));

            Patient p = fhir.read().resource(Patient.class).withId(patientId).execute();

            Bundle meds = fhir.search()
                    .forResource(MedicationRequest.class)
                    .where(MedicationRequest.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class)
                    .execute();

            model.addAttribute("name", p.getNameFirstRep().getNameAsSingleString());
            model.addAttribute("birthDate", p.getBirthDateElement().asStringValue());
            model.addAttribute("meds", meds);
            return "patient";

        } catch (Exception ex) {
            // F) Evita Whitelabel: muestra error legible y loguea stack
            String msg = (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            model.addAttribute("error", "Callback failed: " + msg);
            return "error";
        }
    }


    private static String url(String v){ return URLEncoder.encode(v, StandardCharsets.UTF_8); }
}
