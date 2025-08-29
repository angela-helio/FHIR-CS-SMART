package com.example.smartspring.web;

import com.example.smartspring.config.AppProperties;
import com.example.smartspring.oauth.PkceUtil;
import com.example.smartspring.oauth.SmartDiscoveryService;
import com.example.smartspring.oauth.TokenService;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.pkce.*;
import com.nimbusds.oauth2.sdk.id.ClientID;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/legacy")
public class AuthController {
    private final AppProperties props;
    private final SmartDiscoveryService discovery;
    private final TokenService tokenService;

    public AuthController(AppProperties p, SmartDiscoveryService d, TokenService t) {
        this.props = p;
        this.discovery = d;
        this.tokenService = t;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/ehr/launch")
    public RedirectView ehrLaunch(@RequestParam("iss") String iss, @RequestParam("launch") String launch, HttpSession session) {
        session.setAttribute("runtime_fhir_base", iss);
        session.setAttribute("runtime_launch", launch);
        RedirectView rv = new RedirectView("/auth/start");
        rv.setExposeModelAttributes(false);
        return rv;
    }

    @GetMapping("/auth/start")
    public RedirectView start(HttpSession session) {
        String fhirBase = (String) session.getAttribute("runtime_fhir_base");
        if (fhirBase == null || fhirBase.isBlank()) fhirBase = props.getFhirBase();
        String launchParam = (String) session.getAttribute("runtime_launch");
        if ((launchParam == null || launchParam.isBlank()) && props.getLaunch() != null && !props.getLaunch().isBlank())
            launchParam = props.getLaunch();
        var endpoints = discovery.discover(fhirBase);
        String verifierStr = PkceUtil.generateCodeVerifier();
        String challenge = PkceUtil.codeChallengeS256(verifierStr);
        CodeVerifier verifier = new CodeVerifier(verifierStr);
        State state = new State();
        String authorize = endpoints.authorizationEndpoint().toString()
                + "?response_type=code"
                + "&client_id=" + url(props.getClientId())
                + "&redirect_uri=" + url(props.getRedirectUri())
                + "&scope=" + url(props.getScopes())
                + "&state=" + url(state.getValue())
                + "&code_challenge=" + url(challenge)
                + "&code_challenge_method=" + CodeChallengeMethod.S256.getValue()
                + "&aud=" + url(fhirBase);
        if (launchParam != null && !launchParam.isBlank()) authorize += "&launch=" + url(launchParam);
        session.setAttribute("code_verifier", verifierStr);
        session.setAttribute("oauth_state", state.getValue());
        session.setAttribute("token_endpoint", endpoints.tokenEndpoint().toString());
        session.setAttribute("runtime_fhir_base", fhirBase);
        RedirectView rv = new RedirectView(authorize);
        rv.setExposeModelAttributes(false);
        return rv;
    }

    @GetMapping("/callback")
    public RedirectView callback(@RequestParam(name = "code", required = false) String code,
                                 @RequestParam(name = "state", required = false) String state,
                                 @RequestParam(name = "error", required = false) String error,
                                 HttpSession session) {
        if (error != null) {
            RedirectView rv = new RedirectView("/?error=" + url(error));
            rv.setExposeModelAttributes(false);
            return rv;
        }
        String expected = (String) session.getAttribute("oauth_state");
        if (expected == null || !expected.equals(state)) {
            RedirectView rv = new RedirectView("/?error=" + url("state_mismatch"));
            rv.setExposeModelAttributes(false);
            return rv;
        }
        String verifier = (String) session.getAttribute("code_verifier");
        String tokenEndpoint = (String) session.getAttribute("token_endpoint");
        var token = tokenService.exchangeCode(URI.create(tokenEndpoint), props.getClientId(), props.getRedirectUri(), new AuthorizationCode(code), new CodeVerifier(verifier));
        session.setAttribute("access_token", token.accessToken());
        session.setAttribute("refresh_token", token.refreshToken());
        session.setAttribute("token_exp", token.expiresEpochSeconds());
        if (token.patientId() != null && !token.patientId().isBlank()) {
            session.setAttribute("patient_id", token.patientId());
        }
        String next = (String) session.getAttribute("patient_id") != null ? "/me" : "/patients";
        RedirectView rv = new RedirectView(next);
        rv.setExposeModelAttributes(false);
        return rv;

    }

    static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
