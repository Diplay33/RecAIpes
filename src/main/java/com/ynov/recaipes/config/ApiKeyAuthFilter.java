package com.ynov.recaipes.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment; // Import de l'objet Environment
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    // On injecte l'objet Environment de Spring, qui contient toutes les propriétés de configuration.
    private final Environment environment;

    // Utilisation de l'injection par constructeur (bonne pratique)
    public ApiKeyAuthFilter(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // On récupère la clé requise depuis l'environnement au moment de la requête.
        final String requiredApiKey = environment.getProperty("api.secret.key");
        final String providedApiKey = request.getHeader("X-API-Key");

        // Si aucune clé n'est configurée dans le back-end, la sécurité est désactivée (pratique pour le dev local)
        if (requiredApiKey == null || requiredApiKey.isEmpty()) {
            System.err.println("ATTENTION: La sécurité par clé API est désactivée car 'api.secret.key' n'est pas configurée.");
            filterChain.doFilter(request, response);
            return;
        }

        if (!requiredApiKey.equals(providedApiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Error: Invalid or Missing API Key");
            return; // On arrête la requête ici.
        }

        // Si la clé est valide, on continue.
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // On protège uniquement les routes /api/
        return !request.getRequestURI().startsWith("/api/");
    }
}