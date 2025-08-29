package com.example.smartspring.web;

import com.example.smartspring.config.AppProperties;
import com.example.smartspring.oauth.TokenService;
import com.example.smartspring.service.FhirService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.time.Instant;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.HumanName;

import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Medication;

import java.util.HashMap;
import java.util.Map;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;

@Controller
public class FhirController {

    private final AppProperties props;
    private final FhirService fhir;
    private final TokenService tokens;

    public FhirController(AppProperties p, FhirService s, TokenService t) {
        this.props = p;
        this.fhir = s;
        this.tokens = t;
    }

    @GetMapping("/me")
    public Object me(Model model, HttpSession session) {
        String access = (String) session.getAttribute("access_token");
        if (access == null) return new RedirectView("/");

        // refresh if it's necessary (same as /patients)
        Long exp = (Long) session.getAttribute("token_exp");
        String refresh = (String) session.getAttribute("refresh_token");
        String tokenEndpoint = (String) session.getAttribute("token_endpoint");
        if (exp != null && exp > 0 && java.time.Instant.now().getEpochSecond() > exp - 60 && refresh != null) {
            var ts = tokens.refresh(java.net.URI.create(tokenEndpoint), props.getClientId(), refresh);
            session.setAttribute("access_token", ts.accessToken());
            session.setAttribute("refresh_token", ts.refreshToken());
            session.setAttribute("token_exp", ts.expiresEpochSeconds());
            access = ts.accessToken();
        }

        String fhirBase = (String) session.getAttribute("runtime_fhir_base");
        if (fhirBase == null || fhirBase.isBlank()) fhirBase = props.getFhirBase();

        String patientId = (String) session.getAttribute("patient_id");
        if (patientId == null || patientId.isBlank()) {
            // sin contexto → fallback a lista
            return new RedirectView("/patients");
        }

        // 1) Patient
        Patient p = fhir.readPatientById(fhirBase, access, patientId);
        String name = p.getName().isEmpty() ? "(no name)" : p.getName().get(0).getNameAsSingleString();
        String birthDate = p.hasBirthDate() ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(p.getBirthDate()) : "(unknown)";

        // 2) MedicationRequests (+ include Medication)
        var medBundle = fhir.medicationsForPatient(fhirBase, access, patientId, 50);

        // map of Medication included by reference "Medication/{id}"
        Map<String, Medication> medsByRef = new HashMap<>();
        for (var e : medBundle.getEntry()) {
            if (e.getResource() instanceof Medication m) {
                medsByRef.put(m.getResourceType().name() + "/" + m.getIdElement().getIdPart(), m);
            }
        }

        java.util.List<String> meds = new java.util.ArrayList<>();
        for (var e : medBundle.getEntry()) {
            if (e.getResource() instanceof MedicationRequest mr) {
                meds.add(FhirService.medicationDisplay(mr, medsByRef));
            }
        }

        model.addAttribute("patientId", patientId);
        model.addAttribute("name", name);
        model.addAttribute("birthDate", birthDate);
        model.addAttribute("medications", meds);

        return "me";
    }

    @GetMapping("/patients")
    public Object patients(Model model, HttpSession session) {
        String access = (String) session.getAttribute("access_token");
        Long exp = (Long) session.getAttribute("token_exp");
        String refresh = (String) session.getAttribute("refresh_token");
        String tokenEndpoint = (String) session.getAttribute("token_endpoint");
        if (access == null) return new RedirectView("/");
        if (exp != null && exp > 0 && Instant.now().getEpochSecond() > exp - 60 && refresh != null) {
            var ts = tokens.refresh(java.net.URI.create(tokenEndpoint), props.getClientId(), refresh);
            session.setAttribute("access_token", ts.accessToken());
            session.setAttribute("refresh_token", ts.refreshToken());
            session.setAttribute("token_exp", ts.expiresEpochSeconds());
            access = ts.accessToken();
        }
        String fhirBase = (String) session.getAttribute("runtime_fhir_base");
        if (fhirBase == null || fhirBase.isBlank()) fhirBase = props.getFhirBase();
        Bundle bundle = fhir.searchPatients(fhirBase, access, 5);
        var names = bundle.getEntry().stream().map(e -> e.getResource()).filter(r -> r instanceof org.hl7.fhir.r4.model.Patient)
                .map(r -> (org.hl7.fhir.r4.model.Patient) r).map(p -> p.getName().stream().findFirst().map(HumanName::getNameAsSingleString).orElse("(no name)")).collect(Collectors.toList());
        model.addAttribute("count", bundle.getEntry().size());
        model.addAttribute("names", names);
        return "patients";
    }

    @GetMapping("/api/patient/{patientId}/diagnostic-reports")
    public ResponseEntity<String> getDiagnosticReports(@PathVariable String patientId, HttpSession session) {
        IGenericClient client = makeClient(session);
        Bundle bundle = client.search()
                .forResource(DiagnosticReport.class)
                .where(DiagnosticReport.PATIENT.hasId(patientId))
                .include(DiagnosticReport.INCLUDE_RESULT) // trae Observations referenciadas por el Report
                .count(50)
                .returnBundle(Bundle.class)
                .execute();

        String json = FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    @GetMapping("/api/patient/{patientId}/observations")
    public ResponseEntity<String> getObservations(@PathVariable String patientId,
                                                  @RequestParam(required=false) String category, // ej: laboratory | vital-signs
                                                  HttpSession session) {
        IGenericClient client = makeClient(session);
        var search = client.search()
                .forResource(Observation.class)
                .where(Observation.PATIENT.hasId(patientId));

        if (category != null && !category.isBlank()) {
            // categorías estándar: http://terminology.hl7.org/CodeSystem/observation-category
            search = search.and(Observation.CATEGORY.exactly().code(category));
        }

        Bundle bundle = search.count(100).returnBundle(Bundle.class).execute();
        String json = FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    // Helper: crea cliente HAPI con token/base de tu sesión actual
    private IGenericClient makeClient(HttpSession session) {
        String base = (String) session.getAttribute("runtime_fhir_base");
        String token = (String) session.getAttribute("access_token"); // usa el nombre que guardas en tu callback
        var ctx = FhirContext.forR4();
        var client = ctx.newRestfulGenericClient(base);
        client.setEncoding(EncodingEnum.JSON);
        client.registerInterceptor(new BearerTokenAuthInterceptor(token));
        return client;
    }
}
