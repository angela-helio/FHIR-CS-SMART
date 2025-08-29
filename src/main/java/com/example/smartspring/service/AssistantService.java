package com.example.smartspring.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AssistantService {

    private static final FhirContext CTX = FhirContext.forR4();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    public record PatientSummary(String id, String display) {}
    public record ObsView(String id, String code, String category, String value, String issuedOrEffective) {}
    public record ReportWithObservations(String id, String status, String code, String when, String performer,
                                         List<ObsView> observations) {}

    private IGenericClient client(HttpSession session) {
        String base = (String) session.getAttribute("runtime_fhir_base");
        String token = (String) session.getAttribute("access_token");
        var c = CTX.newRestfulGenericClient(base);
        c.setEncoding(EncodingEnum.JSON);
        c.registerInterceptor(new BearerTokenAuthInterceptor(token));
        return c;
    }

    public List<PatientSummary> listPatients(HttpSession session, int max) {
        var bundle = client(session).search()
                .forResource(Patient.class)
                .count(max)
                .returnBundle(Bundle.class)
                .execute();
        return bundle.getEntry().stream()
                .map(e -> (Patient) e.getResource())
                .map(this::toPatientSummary)
                .collect(Collectors.toList());
    }

    public PatientSummary getPatientSummary(HttpSession session, String patientId) {
        var p = client(session).read().resource(Patient.class).withId(patientId).execute();
        return toPatientSummary(p);
    }

    private PatientSummary toPatientSummary(Patient p) {
        String name = p.getName().isEmpty() ? "(no name)" :
                p.getNameFirstRep().getNameAsSingleString();
        return new PatientSummary(p.getIdElement().getIdPart(), name);
    }

    public List<ReportWithObservations> getReportsForPatient(HttpSession session, String patientId,
                                                             String categoryFilter, LocalDate from, LocalDate to,
                                                             int maxReports) {
        var c = client(session);
        final int limit = Math.min(50, Math.max(1, maxReports));

        // Normaliza ventana: si no pasan fechas, no filtres primero (muchos datos en non-prod son antiguos).
        LocalDate fromUse = from;
        LocalDate toUse   = to;

        // Helper para ejecutar una búsqueda DiagnosticReport con un nombre de parámetro (patient|subject)
        LocalDate finalFromUse = fromUse;
        LocalDate finaltoUse = toUse;
        java.util.function.Function<String, Bundle> runSearch = (paramName) -> {
            var search = c.search().forResource(DiagnosticReport.class);

            // patient=Patient/{id}  ó  subject=Patient/{id}
            if ("patient".equals(paramName)) {
                search = search.where(DiagnosticReport.PATIENT.hasId("Patient/" + patientId));
            } else {
                search = search.where(DiagnosticReport.SUBJECT.hasId("Patient/" + patientId));
            }

            // Fechas si el usuario las pidió
            if (finalFromUse != null) search = search.and(DiagnosticReport.DATE.afterOrEquals().day(DAY.format(finalFromUse)));
            if (finaltoUse   != null) search = search.and(DiagnosticReport.DATE.beforeOrEquals().day(DAY.format(finaltoUse)));

            // Categoría opcional (Ontada usa 'LAB' para laboratorio)
            if (categoryFilter != null && !categoryFilter.isBlank()) {
                String cat = categoryFilter.equalsIgnoreCase("laboratory") ? "LAB" : categoryFilter;
                search = search.and(DiagnosticReport.CATEGORY.exactly().code(cat));
            }

            return search.sort().descending("date").count(limit).returnBundle(Bundle.class).execute();
        };

        // 1) patient=...
        Bundle bundle = runSearch.apply("patient");

        // 2) si vacío, intenta subject=...
        if (bundle.getEntry().isEmpty()) {
            bundle = runSearch.apply("subject");
        }

        // 3) si sigue vacío y NO había fechas, amplía a 2 años y reintenta
        if (bundle.getEntry().isEmpty() && from == null && to == null) {
            fromUse = LocalDate.now().minusYears(2);
            toUse   = null;
            bundle  = runSearch.apply("patient");
            if (bundle.getEntry().isEmpty()) {
                bundle = runSearch.apply("subject");
            }
        }

        // --- mapear reports ---
        var reports = bundle.getEntry().stream()
                .map(e -> e.getResource())
                .filter(r -> r instanceof DiagnosticReport)
                .map(r -> (DiagnosticReport) r)
                .toList();

        var out = new ArrayList<ReportWithObservations>();

        // 4) Si hay reports, resuelve sus Observations por ID (sin _include)
        if (!reports.isEmpty()) {
            for (var dr : reports) {
                List<String> obsIds = dr.getResult().stream()
                        .map(ref -> ref.getReferenceElement().toUnqualifiedVersionless().getIdPart())
                        .filter(Objects::nonNull).distinct().toList();

                List<Observation> observations = List.of();
                if (!obsIds.isEmpty()) {
                    var obsBundle = c.search().forResource(Observation.class)
                            .where(Observation.RES_ID.exactly().codes(obsIds))
                            .returnBundle(Bundle.class).execute();
                    observations = obsBundle.getEntry().stream()
                            .map(e -> (Observation) e.getResource()).toList();
                }

                // Filtro de categoría para las Observations (laboratory, vital-signs, etc.)
                final String wantedCat = (categoryFilter == null) ? null :
                        (categoryFilter.equalsIgnoreCase("LAB") ? "laboratory" : categoryFilter);

                var obsViews = new ArrayList<ObsView>();
                for (var o : observations) {
                    String cat = firstCategoryCode(o);
                    if (wantedCat != null && (cat == null || !wantedCat.equalsIgnoreCase(cat))) continue;
                    obsViews.add(new ObsView(
                            o.getIdElement().getIdPart(),
                            codeDisplay(o.getCode()),
                            cat == null ? "" : cat,
                            valueDisplay(o),
                            whenDisplay(o)
                    ));
                }

                out.add(new ReportWithObservations(
                        dr.getIdElement().getIdPart(),
                        dr.getStatus() == null ? "" : dr.getStatus().toCode(),
                        codeDisplay(dr.getCode()),
                        whenDisplay(dr),
                        performerDisplay(dr),
                        obsViews
                ));
            }

            return out;
        }

        // 5) Fallback: sin DiagnosticReport, muestra Observations directas (para no dejar “No reports.”)
        //    Usa ventana amplia si el usuario no puso fechas.
        if (from == null && to == null) {
            fromUse = LocalDate.now().minusYears(2);
        }
        var obsSearch = c.search().forResource(Observation.class)
                .where(Observation.PATIENT.hasId(patientId))
                .sort().descending("_lastUpdated")
                .count(limit);
        if (fromUse != null) obsSearch = obsSearch.and(Observation.DATE.afterOrEquals().day(DAY.format(fromUse)));
        if (toUse   != null) obsSearch = obsSearch.and(Observation.DATE.beforeOrEquals().day(DAY.format(toUse)));
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            obsSearch = obsSearch.and(Observation.CATEGORY.exactly().code(categoryFilter));
        }
        var obsBundle = obsSearch.returnBundle(Bundle.class).execute();

        var obsViews = obsBundle.getEntry().stream()
                .map(e -> (Observation) e.getResource())
                .map(o -> new ObsView(
                        o.getIdElement().getIdPart(),
                        codeDisplay(o.getCode()),
                        Optional.ofNullable(firstCategoryCode(o)).orElse(""),
                        valueDisplay(o),
                        whenDisplay(o)
                )).toList();

        // Renderízalo como una “sección” única
        out.add(new ReportWithObservations(
                "obs-only", "", "Observations (no DiagnosticReport)", "", "", new ArrayList<>(obsViews)
        ));

        return out;
    }



    // Helpers de presentación
    private String codeDisplay(CodeableConcept cc) {
        if (cc == null) return "";
        if (cc.hasText()) return cc.getText();
        if (!cc.getCoding().isEmpty()) {
            var cd = cc.getCodingFirstRep();
            if (cd.hasDisplay()) return cd.getDisplay();
            if (cd.hasCode()) return cd.getCode();
        }
        return "";
    }
    private String performerDisplay(DiagnosticReport dr) {
        if (dr.getPerformer().isEmpty()) return "";
        return dr.getPerformer().stream()
                .map(ref -> ref.getDisplay())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }
    private String whenDisplay(DiagnosticReport dr) {
        if (dr.hasEffectiveDateTimeType()) return dr.getEffectiveDateTimeType().asStringValue();
        if (dr.hasEffectivePeriod()) {
            var p = dr.getEffectivePeriod();
            return (p.hasStart()? p.getStartElement().asStringValue() : "") + " - " +
                    (p.hasEnd()? p.getEndElement().asStringValue() : "");
        }
        if (dr.hasIssued()) return dr.getIssuedElement().asStringValue();
        return "";
    }
    private String whenDisplay(Observation o) {
        if (o.hasEffectiveDateTimeType()) return o.getEffectiveDateTimeType().asStringValue();
        if (o.hasEffectiveInstantType()) return o.getEffectiveInstantType().asStringValue();
        if (o.hasIssued()) return o.getIssuedElement().asStringValue();
        return "";
    }
    private String firstCategoryCode(Observation o) {
        for (var cc : o.getCategory()) {
            for (var cd : cc.getCoding()) {
                if (cd.hasCode()) return cd.getCode();
            }
        }
        return null;
    }
    private String valueDisplay(Observation o) {
        if (o.hasValueQuantity()) {
            var q = o.getValueQuantity();
            var v = q.getValue() == null ? "" : q.getValue().toPlainString();
            var u = q.hasUnit() ? (" " + q.getUnit()) : "";
            return (v + u).trim();
        }
        if (o.hasValueCodeableConcept()) return codeDisplay(o.getValueCodeableConcept());
        if (o.hasValueStringType()) return o.getValueStringType().asStringValue();
        if (o.hasValueIntegerType()) return o.getValueIntegerType().asStringValue();
        if (o.hasValueBooleanType()) return o.getValueBooleanType().asStringValue();
        if (o.hasValueTimeType()) return o.getValueTimeType().asStringValue();
        if (o.hasValueDateTimeType()) return o.getValueDateTimeType().asStringValue();
        if (o.hasValueSampledData()) return "[sampled data]";
        if (o.hasValueRatio()) {
            var r = o.getValueRatio();
            return (r.getNumerator().getValue() + "/" + r.getDenominator().getValue());
        }
        return "";
    }
}
