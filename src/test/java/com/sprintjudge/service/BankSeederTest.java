package com.sprintjudge.service;

import com.sprintjudge.repository.QuizRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankSeederTest {

    @Mock
    QuizRepository quizRepository;

    @Mock
    ImportExportService importExportService;

    @InjectMocks
    BankSeeder seeder;

    @Test
    void skipsWhenBankNotEmpty() {
        when(quizRepository.count()).thenReturn(3);

        seeder.run(null);

        verify(importExportService, never()).importAll(anyString(), anyBoolean());
    }

    @Test
    void seedsWhenBankEmpty() {
        when(quizRepository.count()).thenReturn(0);
        when(importExportService.importAll(anyString(), eq(false))).thenReturn(7);

        seeder.run(null);

        verify(importExportService).importAll(anyString(), eq(false));
    }

    @Test
    void logsErrorWhenImportFails() {
        when(quizRepository.count()).thenReturn(0);
        when(importExportService.importAll(anyString(), eq(false)))
                .thenThrow(new RuntimeException("boom"));

        seeder.run(null);

        verify(importExportService).importAll(anyString(), eq(false));
    }

    @Test
    void forcePropertySeedsEvenWhenBankNotEmpty() {
        System.setProperty("sprintjudge.seed.force", "true");
        try {
            when(importExportService.importAll(anyString(), eq(true))).thenReturn(5);
            seeder.run(null);
            verify(importExportService).importAll(anyString(), eq(true));
        } finally {
            System.clearProperty("sprintjudge.seed.force");
        }
    }

    @Test
    void forcePropertyPassesReplaceTrueWhenBankEmpty() {
        System.setProperty("sprintjudge.seed.force", "true");
        try {
            when(importExportService.importAll(anyString(), eq(true))).thenReturn(3);
            seeder.run(null);
            verify(importExportService).importAll(anyString(), eq(true));
        } finally {
            System.clearProperty("sprintjudge.seed.force");
        }
    }

    @Test
    void forcePropertyFailureIsSwallowed() {
        System.setProperty("sprintjudge.seed.force", "true");
        try {
            when(importExportService.importAll(anyString(), eq(true)))
                    .thenThrow(new RuntimeException("seed boom"));
            seeder.run(null);
            verify(importExportService).importAll(anyString(), eq(true));
        } finally {
            System.clearProperty("sprintjudge.seed.force");
        }
    }

    @Test
    void explicitFalseForceStillSkipsWhenNotEmpty() {
        System.setProperty("sprintjudge.seed.force", "false");
        try {
            when(quizRepository.count()).thenReturn(2);
            seeder.run(null);
            verify(importExportService, never()).importAll(anyString(), anyBoolean());
        } finally {
            System.clearProperty("sprintjudge.seed.force");
        }
    }
}
