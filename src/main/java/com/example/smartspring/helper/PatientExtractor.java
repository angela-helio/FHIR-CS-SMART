package com.example.smartspring.helper;

// PatientExtractor.java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class PatientExtractor {
    private static final ObjectMapper OM = new ObjectMapper();

    public static String fromTokenResponseRaw(String raw){
        if (raw == null) return null;
        try {
            JsonNode n = OM.readTree(raw);
            // 1) top-level
            String p = txt(n.path("patient"));
            if (p != null) return stripRef(p);
            // 2) SMART v2: context.patient / context.patientId / context.patient_id
            p = txt(n.at("/context/patient"));
            if (p != null) return stripRef(p);
            p = txt(n.at("/context/patientId"));
            if (p != null) return stripRef(p);
            p = txt(n.at("/context/patient_id"));
            if (p != null) return stripRef(p);
            // 3) Algunos EHR: launch_response.patient
            p = txt(n.at("/launch_response/patient"));
            if (p != null) return stripRef(p);
            // 4) Otros alias: patient_id
            p = txt(n.path("patient_id"));
            if (p != null) return stripRef(p);
            return null;
        } catch(Exception e){ return null; }
    }

    public static String fromUserInfoRaw(String raw){
        return fromTokenResponseRaw(raw); // misma lógica
    }

    private static String txt(JsonNode x){ return (x == null || x.isMissingNode() || x.isNull()) ? null : x.asText(null); }
    private static String stripRef(String s){ return s != null && s.startsWith("Patient/") ? s.substring(8) : s; }
}

