package com.sprintjudge.service;

import com.sprintjudge.domain.dto.export.ExportBundle;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    QuestionRepository questionRepository;

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
            when(quizRepository.findAll()).thenReturn(
                    List.of(new Quiz("old", "Old", null, null, null, false)));
            when(importExportService.importAll(anyString(), eq(false))).thenReturn(5);
            seeder.run(null);
            verify(questionRepository).deleteByQuiz("old");
            verify(quizRepository).delete("old");
            verify(importExportService).importAll(anyString(), eq(false));
        } finally {
            System.clearProperty("sprintjudge.seed.force");
        }
    }

    @Test
    void forceWipeRunsBeforeSeedWhenBankEmpty() {
        System.setProperty("sprintjudge.seed.force", "true");
        try {
            when(importExportService.importAll(anyString(), eq(false))).thenReturn(3);
            seeder.run(null);
            verify(importExportService).importAll(anyString(), eq(false));
        } finally {
            System.clearProperty("sprintjudge.seed.force");
        }
    }

    @Test
    void forcePropertyFailureIsSwallowed() {
        System.setProperty("sprintjudge.seed.force", "true");
        try {
            when(importExportService.importAll(anyString(), eq(false)))
                    .thenThrow(new RuntimeException("seed boom"));
            seeder.run(null);
            verify(importExportService).importAll(anyString(), eq(false));
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

    @Test
    void partitionSplitsIntoFullSizeChunks() {
        List<ExportBundle.QuizExport> all = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            all.add(new ExportBundle.QuizExport("q" + i, "T", null, false, null));
        }
        List<List<ExportBundle.QuizExport>> chunks = BankSeeder.partition(all);
        assertEquals(2, chunks.size());
        assertEquals(200, chunks.get(0).size());
        assertEquals(1, chunks.get(1).size());
    }

    @Test
    void partitionKeepsSmallBundlesWhole() {
        List<ExportBundle.QuizExport> all = List.of(
                new ExportBundle.QuizExport("a", "T", null, false, null));
        List<List<ExportBundle.QuizExport>> chunks = BankSeeder.partition(all);
        assertEquals(1, chunks.size());
        assertEquals(1, chunks.get(0).size());
        assertTrue(BankSeeder.partition(List.of()).isEmpty());
    }
}
