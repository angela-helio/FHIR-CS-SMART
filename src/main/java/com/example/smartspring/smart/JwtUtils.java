package com.example.smartspring.smart;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;

public final class JwtUtils {
    public static String tryExtractPatient(String idToken){
        try{
            if (idToken == null || !idToken.contains(".")) return null;
            String[] p = idToken.split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(p[1]));
            Object claim = new ObjectMapper().readValue(payload, Map.class).get("patient");
            return claim != null ? claim.toString() : null;
        }catch(Exception e){ return null; }
    }
}
