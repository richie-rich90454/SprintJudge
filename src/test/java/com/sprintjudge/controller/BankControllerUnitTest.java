package com.sprintjudge.controller;

import com.sprintjudge.bank.BankStore;
import com.sprintjudge.service.BankQuizBuilder;
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

    @Test
    void builderRejectionsBecomeBadRequests() {
        when(builder.build(any())).thenThrow(new IllegalArgumentException("empty"));
        BankController controller = new BankController(store, builder);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.build(new BankQuizBuilder.BuildRequest(
                        "JAVA_PROGRAMMING", "Unit 1", 0, 1, null, 0, false)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
}
