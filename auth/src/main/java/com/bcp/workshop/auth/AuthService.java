package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

@ApplicationScoped
public class AuthService {
    private static final String AUTHENTICATED_FLOW = "authenticated-with-legacy-flow";

    private final LegacyDatabase database;
    private final TokenService tokenService;

    @Inject
    public AuthService(LegacyDatabase database, TokenService tokenService) {
        this.database = database;
        this.tokenService = tokenService;
    }

    public LoginResponse authenticate(String username, String password) {
        UserRecord user = database.findByCredentials(username, password);
        if (user == null) {
            return null;
        }

        LoginResponse.User responseUser = new LoginResponse.User(
                user.username(),
                StringEscapeUtils.escapeHtml4(user.displayName()),
                user.role());
        return new LoginResponse(
                tokenService.create(user.username(), user.role()),
                TokenService.TOKEN_EXPIRY_SECONDS,
                responseUser,
                AUTHENTICATED_FLOW);
    }
}