package com.sprintjudge.service;

import com.sprintjudge.TestDb;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.AdminSettingsRepository;
import com.sprintjudge.util.Json;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankSeederBreadthTest {

    @Mock
    QuizRepository quizRepository;

    @Mock
    ImportExportService importExportService;

    @InjectMocks
    BankSeeder seeder;

    @Test
    void bundleConstantPointsAtSeedResource() {
        assertEquals("seed/master-bundle.json", BankSeeder.BUNDLE);
    }

    @Test
    void skipDoesNotTouchImportService() {
        when(quizRepository.count()).thenReturn(5);
        seeder.run(null);
        verify(quizRepository).count();
        verify(importExportService, never()).importAll(anyString(), anyBoolean());
    }

    @Test
    void emptyBankSeedsWithReplaceFalse() {
        when(quizRepository.count()).thenReturn(0);
        when(importExportService.importAll(anyString(), eq(false))).thenReturn(4);
        seeder.run(null);
        verify(importExportService).importAll(anyString(), eq(false));
    }

    @Test
    void runAcceptsNonNullArguments() {
        when(quizRepository.count()).thenReturn(0);
        when(importExportService.importAll(anyString(), eq(false))).thenReturn(4);
        seeder.run(mock(org.springframework.boot.ApplicationArguments.class));
        verify(importExportService).importAll(anyString(), eq(false));
    }

    @Test
    void importFailureNeverPropagates() {
        when(quizRepository.count()).thenReturn(0);
        when(importExportService.importAll(anyString(), eq(false)))
                .thenThrow(new IllegalStateException("bad bundle"));
        assertDoesNotThrow(() -> seeder.run(null));
    }

    @Test
    void classpathBundleContentIsForwardedToImport() {
        when(quizRepository.count()).thenReturn(0);
        when(importExportService.importAll(anyString(), eq(false))).thenReturn(2);
        seeder.run(null);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(importExportService).importAll(json.capture(), eq(false));
        assertDoesNotThrow(() -> Json.readTree(json.getValue()));
    }

    @Test
    void emptyTestBundleLeavesBankUntouchedWithoutThrowing() throws Exception {
        DSLContext dsl = TestDb.inMemory();
        QuizRepository realQuizzes = new QuizRepository(dsl);
        ImportExportService realImport = new ImportExportService(
                realQuizzes, new QuestionRepository(dsl), new AdminSettingsRepository(dsl));
        BankSeeder realSeeder = new BankSeeder(realQuizzes, realImport);
        assertDoesNotThrow(() -> realSeeder.run(null));
        assertEquals(0, realQuizzes.count());
    }
}
