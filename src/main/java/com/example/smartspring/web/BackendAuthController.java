package com.example.smartspring.web;

import com.example.smartspring.config.AppProperties;
import com.example.smartspring.oauth.BackendServiceAuth;
import com.example.smartspring.oauth.SmartDiscoveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpSession;

/**
 * Controller for backend service authentication (System-type FHIR apps)
 */
@Controller
@RequestMapping("/backend")
public class BackendAuthController {

    private final AppProperties props;
    private final SmartDiscoveryService discovery;
    private final BackendServiceAuth backendAuth;
    private final JwksController jwksController;

    @Value("${smart.backend.enabled:false}")
    private boolean backendEnabled;

    @Value("${smart.backend.scopes:system/*.read}")
    private String backendScopes;

    public BackendAuthController(AppProperties props, SmartDiscoveryService discovery, 
                               BackendServiceAuth backendAuth, JwksController jwksController) {
        this.props = props;
        this.discovery = discovery;
        this.backendAuth = backendAuth;
        this.jwksController = jwksController;
    }

    @GetMapping("/auth")
    public String backendAuthPage(Model model) {
        if (!backendEnabled) {
            model.addAttribute("error", "Backend authentication is not enabled");
            return "error";
        }
        
        model.addAttribute("fhirBase", props.getFhirBase());
        model.addAttribute("clientId", props.getClientId());
        model.addAttribute("scopes", backendScopes);
        model.addAttribute("jwksUrl", "http://127.0.0.1:8080/.well-known/jwks.json");
        return "backend-auth";
    }

    @PostMapping("/authenticate")
    public String authenticate(HttpSession session, Model model) {
        if (!backendEnabled) {
            model.addAttribute("error", "Backend authentication is not enabled");
            return "error";
        }

        try {
            // Discover token endpoint
            var endpoints = discovery.discover(props.getFhirBase());
            String tokenUrl = endpoints.tokenEndpoint().toString();

            // Generate JWT assertion
            String jwtAssertion = backendAuth.generateJwtAssertion(
                tokenUrl, 
                props.getClientId(), 
                jwksController.getPrivateKey(), 
                jwksController.getKeyId()
            );

            // Get access token
            var tokenResponse = backendAuth.getAccessToken(
                tokenUrl, 
                props.getClientId(), 
                jwtAssertion, 
                backendScopes
            );

            // Store token in session
            session.setAttribute("backend_access_token", tokenResponse.accessToken());
            session.setAttribute("backend_token_type", tokenResponse.tokenType());
            session.setAttribute("backend_expires_in", tokenResponse.expiresIn());
            session.setAttribute("runtime_fhir_base", props.getFhirBase());

            return "redirect:/patients";
        } catch (Exception e) {
            model.addAttribute("error", "Backend authentication failed: " + e.getMessage());
            return "backend-auth";
        }
    }
}
