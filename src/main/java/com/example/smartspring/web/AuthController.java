package com.example.smartspring.web;

import com.example.smartspring.config.AppProperties;
import com.example.smartspring.oauth.PkceUtil;
import com.example.smartspring.oauth.SmartDiscoveryService;
import com.example.smartspring.oauth.TokenService;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.State;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Controller
public class AuthController {

    private final AppProperties props;
    private final SmartDiscoveryService discovery;
    private final TokenService tokenService;

    public AuthController(AppProperties props, SmartDiscoveryService discovery, TokenService tokenService) {
        this.props = props;
        this.discovery = discovery;
        this.tokenService = tokenService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/auth/start")
    public RedirectView start(HttpSession session) {
        var endpoints = discovery.discover(props.getFhirBase());

        // PKCE
        String verifierStr = PkceUtil.generateCodeVerifier();
        String challenge = PkceUtil.codeChallengeS256(verifierStr);
        CodeVerifier verifier = new CodeVerifier(verifierStr);

        // State
        State state = new State();

        // Build authorize URL
        String scopes = props.getScopes();
        String authorize = endpoints.authorizationEndpoint().toString()
                + "?response_type=code"
                + "&client_id=" + url(props.getClientId())
                + "&redirect_uri=" + url(props.getRedirectUri())
                + "&scope=" + url(scopes)
                + "&state=" + url(state.getValue())
                + "&code_challenge=" + url(challenge)
                + "&code_challenge_method=" + CodeChallengeMethod.S256.getValue()
                + "&aud=" + url(props.getFhirBase());

        if (props.getLaunch() != null && !props.getLaunch().isBlank()) {
            authorize += "&launch=" + url(props.getLaunch());
        }

        // Save to session
        session.setAttribute("code_verifier", verifierStr);
        session.setAttribute("oauth_state", state.getValue());
        session.setAttribute("token_endpoint", endpoints.tokenEndpoint().toString());

        RedirectView rv = new RedirectView(authorize);
        rv.setExposeModelAttributes(false);
        return rv;
    }

    @GetMapping("/callback")
    public RedirectView callback(@RequestParam(name="code", required=false) String code,
                                 @RequestParam(name="state", required=false) String state,
                                 @RequestParam(name="error", required=false) String error,
                                 HttpSession session) {
        if (error != null) {
            RedirectView rv = new RedirectView("/?error=" + url(error));
            rv.setExposeModelAttributes(false);
            return rv;
        }
        String expectedState = (String) session.getAttribute("oauth_state");
        if (expectedState == null || !expectedState.equals(state)) {
            RedirectView rv = new RedirectView("/?error=" + url("state_mismatch"));
            rv.setExposeModelAttributes(false);
            return rv;
        }
        String verifier = (String) session.getAttribute("code_verifier");
        String tokenEndpoint = (String) session.getAttribute("token_endpoint");
        var tokenSet = tokenService.exchangeCode(URI.create(tokenEndpoint), props.getClientId(), props.getRedirectUri(),
                new AuthorizationCode(code), new CodeVerifier(verifier));

        // Store in session
        session.setAttribute("access_token", tokenSet.accessToken());
        session.setAttribute("refresh_token", tokenSet.refreshToken());
        session.setAttribute("token_exp", tokenSet.expiresEpochSeconds());

        RedirectView rv = new RedirectView("/patients");
        rv.setExposeModelAttributes(false);
        return rv;
    }

    static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
