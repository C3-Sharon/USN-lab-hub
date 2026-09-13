package com.usn.labhub.user.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AuthResponseWriter {

    private final ObjectMapper objectMapper;

    public AuthResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, int status, String message, AuthReason reason) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.error(status, message, reason.name()));
    }
}
