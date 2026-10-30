package com.sprintjudge.controller;

import com.sprintjudge.bank.BankStore;
import com.sprintjudge.service.BankQuizBuilder;
import com.sprintjudge.service.TeachNextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankControllerUnitTest {

    @Mock BankStore store;
    @Mock BankQuizBuilder builder;
    @Mock TeachNextService teachNext;

    @Test
    void builderRejectionsBecomeBadRequests() {
        when(builder.build(any())).thenThrow(new IllegalArgumentException("empty"));
        BankController controller = new BankController(store, builder);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.build(new BankQuizBuilder.BuildRequest(
                        "JAVA_PROGRAMMING", "Unit 1", 0, 1, null, 0, false)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void teachNextBlankSessionIdIs400() {
        BankController controller = new BankController(store, builder, teachNext);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.teachNext("  "));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void teachNextDelegatesToService() {
        TeachNextService.Recommendation rec = new TeachNextService.Recommendation("S", "U", "T", 3);
        when(teachNext.recommendPractice("s1")).thenReturn(rec);
        BankController controller = new BankController(store, builder, teachNext);
        assertEquals(rec, controller.teachNext("s1"));
    }

    @Test
    void teachNextUnavailableWithoutService() {
        BankController controller = new BankController(store, builder);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.teachNext("s1"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }


}
