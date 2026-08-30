package com.sprintjudge.controller;

import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.repository.QuizRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicControllerTest {

    @Mock QuizRepository quizRepository;

    @InjectMocks PublicController controller;

    @Test
    void listQuizzesReturnsRepositoryContents() {
        when(quizRepository.findAll()).thenReturn(List.of(new Quiz("q1", "T", null, null, null, false)));
        assertEquals(1, controller.listQuizzes().size());
    }
}
