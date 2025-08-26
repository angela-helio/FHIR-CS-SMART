package com.example.smartspring.web;
import com.example.smartspring.config.AppProperties;
import com.example.smartspring.oauth.TokenService;
import com.example.smartspring.service.FhirService;
import jakarta.servlet.http.HttpSession; import org.springframework.stereotype.Controller; import org.springframework.ui.Model; import org.springframework.web.bind.annotation.GetMapping; import org.springframework.web.servlet.view.RedirectView;
import java.time.Instant; import org.hl7.fhir.r4.model.Bundle; import org.hl7.fhir.r4.model.HumanName; import java.util.stream.Collectors;
@Controller
public class FhirController {
  private final AppProperties props; private final FhirService fhir; private final TokenService tokens;
  public FhirController(AppProperties p, FhirService s, TokenService t){ this.props=p; this.fhir=s; this.tokens=t; }
  @GetMapping("/patients")
  public Object patients(Model model, HttpSession session){
    String access=(String)session.getAttribute("access_token"); Long exp=(Long)session.getAttribute("token_exp"); String refresh=(String)session.getAttribute("refresh_token"); String tokenEndpoint=(String)session.getAttribute("token_endpoint");
    if(access==null) return new RedirectView("/");
    if(exp!=null && exp>0 && Instant.now().getEpochSecond()>exp-60 && refresh!=null){
      var ts=tokens.refresh(java.net.URI.create(tokenEndpoint), props.getClientId(), refresh);
      session.setAttribute("access_token", ts.accessToken()); session.setAttribute("refresh_token", ts.refreshToken()); session.setAttribute("token_exp", ts.expiresEpochSeconds()); access=ts.accessToken();
    }
    String fhirBase=(String)session.getAttribute("runtime_fhir_base"); if(fhirBase==null||fhirBase.isBlank()) fhirBase=props.getFhirBase();
    Bundle bundle=fhir.searchPatients(fhirBase, access, 5);
    var names=bundle.getEntry().stream().map(e->e.getResource()).filter(r->r instanceof org.hl7.fhir.r4.model.Patient)
      .map(r->(org.hl7.fhir.r4.model.Patient)r).map(p->p.getName().stream().findFirst().map(HumanName::getNameAsSingleString).orElse("(no name)")).collect(Collectors.toList());
    model.addAttribute("count", bundle.getEntry().size()); model.addAttribute("names", names); return "patients";
  }
}
