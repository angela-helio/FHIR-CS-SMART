package com.example.smartspring.web;

import com.example.smartspring.config.AppProperties;
import com.example.smartspring.oauth.TokenService;
import com.example.smartspring.service.FhirService;
import jakarta.servlet.http.HttpSession;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.HumanName;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

import java.time.Instant;
import java.util.stream.Collectors;

@Controller
public class FhirController {

    private final AppProperties props;
    private final FhirService fhirService;
    private final TokenService tokenService;

    public FhirController(AppProperties props, FhirService fhirService, TokenService tokenService) {
        this.props = props;
        this.fhirService = fhirService;
        this.tokenService = tokenService;
    }

    @GetMapping("/patients")
    public Object patients(Model model, HttpSession session) {
        String access = (String) session.getAttribute("access_token");
        Long exp = (Long) session.getAttribute("token_exp");
        String refresh = (String) session.getAttribute("refresh_token");
        String tokenEndpoint = (String) session.getAttribute("token_endpoint");

        if (access == null) {
            return new RedirectView("/");
        }
        // Refresh if expired (simple check; some servers don't set exp -> 0 means unknown)
        if (exp != null && exp > 0 && Instant.now().getEpochSecond() > exp - 60 && refresh != null) {
            var ts = tokenService.refresh(java.net.URI.create(tokenEndpoint), props.getClientId(), refresh);
            session.setAttribute("access_token", ts.accessToken());
            session.setAttribute("refresh_token", ts.refreshToken());
            session.setAttribute("token_exp", ts.expiresEpochSeconds());
            access = ts.accessToken();
        }

        Bundle bundle = fhirService.searchPatients(props.getFhirBase(), access, 5);
        var names = bundle.getEntry().stream()
                .map(e -> e.getResource())
                .filter(r -> r instanceof org.hl7.fhir.r4.model.Patient)
                .map(r -> (org.hl7.fhir.r4.model.Patient) r)
                .map(p -> p.getName().stream().findFirst()
                        .map(HumanName::getNameAsSingleString).orElse("(no name)"))
                .collect(Collectors.toList());

        model.addAttribute("count", bundle.getEntry().size());
        model.addAttribute("names", names);
        return "patients";
    }
}
