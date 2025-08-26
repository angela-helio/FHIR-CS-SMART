package com.example.smartspring.service;
import ca.uhn.fhir.context.FhirContext; import ca.uhn.fhir.rest.client.api.IGenericClient; import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import org.hl7.fhir.r4.model.Bundle; import org.springframework.stereotype.Service;
@Service public class FhirService {
  private final FhirContext ctx=FhirContext.forR4();
  public Bundle searchPatients(String fhirBase, String bearerToken, int count){
    IGenericClient c=ctx.newRestfulGenericClient(fhirBase); c.registerInterceptor(new BearerTokenAuthInterceptor(bearerToken));
    return c.search().forResource("Patient").count(count).returnBundle(Bundle.class).execute();
  }
}
