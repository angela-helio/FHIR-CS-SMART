package com.example.smartspring.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Service;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.model.api.Include;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Medication;

@Service
public class FhirService {
    private final FhirContext ctx = FhirContext.forR4();

    public Bundle searchPatients(String fhirBase, String bearerToken, int count) {
        IGenericClient c = ctx.newRestfulGenericClient(fhirBase);
        c.registerInterceptor(new BearerTokenAuthInterceptor(bearerToken));
        return c.search().forResource("Patient").count(count).returnBundle(Bundle.class).execute();
    }

    public Patient readPatientById(String fhirBase, String bearerToken, String patientId) {
        IGenericClient client = ctx.newRestfulGenericClient(fhirBase);
        client.registerInterceptor(new BearerTokenAuthInterceptor(bearerToken));
        return client.read().resource(Patient.class).withId(patientId).execute();
    }

    public Bundle medicationsForPatient(String fhirBase, String bearerToken, String patientId, int count) {
        IGenericClient client = ctx.newRestfulGenericClient(fhirBase);
        client.registerInterceptor(new BearerTokenAuthInterceptor(bearerToken));

        // MedicationRequest?subject=Patient/{id} + include Medication
        return client.search()
                .forResource(MedicationRequest.class)
                .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
                .include(new Include("MedicationRequest:medication"))
                .count(count)
                .returnBundle(Bundle.class)
                .execute();
    }

    // Helper: obtain "display" legible of medicament
    public static String medicationDisplay(MedicationRequest mr, java.util.Map<String, Medication> medsById) {
        // medicationCodeableConcept
        if (mr.hasMedicationCodeableConcept()) {
            var cc = mr.getMedicationCodeableConcept();
            if (cc.hasText()) return cc.getText();
            if (cc.hasCoding() && cc.getCodingFirstRep().hasDisplay())
                return cc.getCodingFirstRep().getDisplay();
            if (cc.hasCoding() && cc.getCodingFirstRep().hasCode())
                return cc.getCodingFirstRep().getCode();
            return "Medication (CodeableConcept)";
        }
        // medicationReference -> Medication resource included
        if (mr.hasMedicationReference() && mr.getMedicationReference().hasReference()) {
            String ref = mr.getMedicationReference().getReference(); // e.g. "Medication/123"
            Medication m = medsById.get(ref);
            if (m != null && m.hasCode()) {
                var code = m.getCode();
                if (code.hasText()) return code.getText();
                if (code.hasCoding() && code.getCodingFirstRep().hasDisplay())
                    return code.getCodingFirstRep().getDisplay();
            }
            return ref;
        }
        return "Medication (unknown)";
    }
}
