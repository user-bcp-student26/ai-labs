package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final String REQUEST_NULL_MESSAGE = "Request is null";
    private static final String USERNAME_REQUIRED_MESSAGE = "Username is required";
    private static final String PASSWORD_REQUIRED_MESSAGE = "Password is required";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid user %s or password";
    private static final String INTERNAL_ERROR_MESSAGE = "An unexpected error occurred";
    private static final System.Logger LOGGER = System.getLogger(AuthResource.class.getName());

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", REQUEST_NULL_MESSAGE)).build();
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", USERNAME_REQUIRED_MESSAGE)).build();
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", PASSWORD_REQUIRED_MESSAGE)).build();
        }

        try {
            LoginResponse loginResponse = authService.authenticate(request.username, request.password);
            if (loginResponse == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of("message", INVALID_CREDENTIALS_MESSAGE.formatted(request.username))).build();
            }
            return Response.ok(loginResponse).build();
        } catch (Exception error) {
            LOGGER.log(System.Logger.Level.ERROR, "Unexpected error while processing login", error);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("message", INTERNAL_ERROR_MESSAGE)).build();
        }
    }
}
