package com.sprintjudge.exception;

import com.sprintjudge.domain.dto.ErrorMessage;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    static class Dummy {
        void m(String s) { }
    }

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBadRequestReturns400() {
        var resp = handler.handleBadRequest(new IllegalArgumentException("bad input"));
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("ERROR", resp.getBody().type());
        assertEquals("bad input", resp.getBody().message());
    }

    @Test
    void handleValidationReturns400WithFieldDetails() throws Exception {
        Method method = Dummy.class.getDeclaredMethod("m", String.class);
        MethodParameter mp = new MethodParameter(method, 0);
        Object target = new Object();
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(target, "obj");
        br.addError(new FieldError("obj", "title", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mp, br);

        var resp = handler.handleValidation(ex);
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("ERROR", resp.getBody().type());
        assertNotNull(resp.getBody().message());
        org.assertj.core.api.Assertions.assertThat(resp.getBody().message()).contains("title: must not be blank");
    }

    @Test
    void handleUnreadableReturns400() {
        HttpInputMessage nullBody = null;
        var resp = handler.handleUnreadable(new HttpMessageNotReadableException("boom", nullBody));
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("Malformed JSON body", resp.getBody().message());
    }

    @Test
    void handleConflictReturns409() {
        var resp = handler.handleConflict(new IllegalStateException("busy"));
        assertEquals(409, resp.getStatusCode().value());
        assertEquals("busy", resp.getBody().message());
    }

    @Test
    void handleNoResourceReturns404() {
        var resp = handler.handleNoResource(new NoResourceFoundException(HttpMethod.GET, "/favicon.ico", "not found"));
        assertEquals(404, resp.getStatusCode().value());
        assertEquals("Not found", resp.getBody().message());
    }

    @Test
    void handleGenericReturns500() {
        var resp = handler.handleGeneric(new RuntimeException("kaboom"));
        assertEquals(500, resp.getStatusCode().value());
        assertEquals("Internal error", resp.getBody().message());
    }
}
