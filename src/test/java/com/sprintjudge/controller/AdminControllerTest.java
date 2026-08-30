package com.sprintjudge.controller;

import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.domain.models.User;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.UserRepository;
import com.sprintjudge.service.AdminSettingsService;
import com.sprintjudge.service.GameRoomManager;
import com.sprintjudge.service.ImportExportService;
import com.sprintjudge.service.MetricsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock QuizRepository quizRepository;
    @Mock QuestionRepository questionRepository;
    @Mock AdminSettingsService settingsService;
    @Mock ImportExportService importExportService;
    @Mock UserRepository userRepository;
    @Mock GameRoomManager roomManager;
    @Mock MetricsService metricsService;

    @InjectMocks AdminController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void metricsReturnsSnapshot() {
        when(metricsService.snapshot()).thenReturn(Map.of("uptime", 1));
        assertEquals(1, controller.metrics().get("uptime"));
    }

    @Test
    void listQuizzes() {
        when(quizRepository.findAll()).thenReturn(List.of(new Quiz("q1", "T", null, null, null, false)));
        assertEquals(1, controller.quizzes().size());
    }

    @Test
    void createQuiz() {
        when(quizRepository.create(any())).thenReturn(new Quiz("q1", "T", null, null, null, false));
        assertNotNull(controller.createQuiz(new Quiz(null, "T", null, null, null, false)));
    }

    @Test
    void deleteQuiz() {
        controller.deleteQuiz("abc");
        verify(quizRepository).delete("abc");
    }

    @Test
    void questionsByQuiz() {
        when(questionRepository.findByQuiz("q1"))
                .thenReturn(List.of(new Question("qid", "q1", "Q", null, "MCQ", null, 30, 10, "{}", 0, null)));
        assertEquals(1, controller.questions("q1").size());
    }

    @Test
    void addQuestion() {
        when(questionRepository.save(any())).thenReturn(new Question("qid", "q1", "Q", null, "MCQ", null, 30, 10, "{}", 0, null));
        Question in = new Question(null, "q1", "Q", null, "MCQ", null, 30, 10, "{}", 0, null);
        assertNotNull(controller.addQuestion("q1", in));
    }

    @Test
    void updateQuestion() {
        when(questionRepository.save(any())).thenReturn(new Question("qid", "q1", "Q", null, "MCQ", null, 30, 10, "{}", 0, null));
        Question in = new Question("qid", "q1", "Q", null, "MCQ", null, 30, 10, "{}", 0, null);
        assertNotNull(controller.updateQuestion("qid", in));
    }

    @Test
    void deleteQuestion() {
        controller.deleteQuestion("qid");
        verify(questionRepository).delete("qid");
    }

    @Test
    void settingsReturnsMap() {
        when(settingsService.asMap()).thenReturn(Map.of("theme", "dark"));
        assertEquals("dark", controller.settings().get("theme"));
    }

    @Test
    void createGameWithNoAuthenticationUsesDefaults() {
        User host = new User("host", "a@b.c", "Al", null, null, null);
        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        when(userRepository.upsertByEmail(email.capture(), name.capture(), any())).thenReturn(host);
        when(roomManager.createRoom(eq("quiz1"), eq("host")))
                .thenReturn(new GameSession("gs", "quiz1", "123", "host", "LOBBY", 0, null, null, null, null));
        GameSession gs = controller.createGame(Map.of("quizId", "quiz1"));
        assertNotNull(gs);
        assertEquals("system@sprintjudge.local", email.getValue());
        assertEquals("System", name.getValue());
        verify(roomManager).createRoom("quiz1", "host");
    }

    @Test
    void createGameWithOAuth2PrincipalUsesAttributes() {
        User host = new User("host", "a@b.c", "Al", null, null, null);
        OAuth2User oauth = org.mockito.Mockito.mock(OAuth2User.class);
        when(oauth.getAttribute("email")).thenReturn("a@b.c");
        when(oauth.getAttribute("name")).thenReturn("Al");
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth);
        SecurityContextHolder.getContext().setAuthentication(auth);

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        when(userRepository.upsertByEmail(email.capture(), name.capture(), any())).thenReturn(host);
        when(roomManager.createRoom(eq("quiz1"), eq("host")))
                .thenReturn(new GameSession("gs", "quiz1", "123", "host", "LOBBY", 0, null, null, null, null));
        assertNotNull(controller.createGame(Map.of("quizId", "quiz1")));
        assertEquals("a@b.c", email.getValue());
        assertEquals("Al", name.getValue());
    }

    @Test
    void createGameWithOAuth2PrincipalNullAttributesUsesDefaults() {
        User host = new User("host", "a@b.c", "Al", null, null, null);
        OAuth2User oauth = org.mockito.Mockito.mock(OAuth2User.class);
        when(oauth.getAttribute("email")).thenReturn(null);
        when(oauth.getAttribute("name")).thenReturn(null);
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth);
        SecurityContextHolder.getContext().setAuthentication(auth);

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        when(userRepository.upsertByEmail(email.capture(), name.capture(), any())).thenReturn(host);
        when(roomManager.createRoom(eq("quiz1"), eq("host")))
                .thenReturn(new GameSession("gs", "quiz1", "123", "host", "LOBBY", 0, null, null, null, null));
        assertNotNull(controller.createGame(Map.of("quizId", "quiz1")));
        assertEquals("system@sprintjudge.local", email.getValue());
        assertEquals("System", name.getValue());
    }

    @Test
    void createGameWithOAuth2PrincipalBlankAttributesUsesDefaults() {
        User host = new User("host", "a@b.c", "Al", null, null, null);
        OAuth2User oauth = org.mockito.Mockito.mock(OAuth2User.class);
        when(oauth.getAttribute("email")).thenReturn("  ");
        when(oauth.getAttribute("name")).thenReturn("");
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth);
        SecurityContextHolder.getContext().setAuthentication(auth);

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        when(userRepository.upsertByEmail(email.capture(), name.capture(), any())).thenReturn(host);
        when(roomManager.createRoom(eq("quiz1"), eq("host")))
                .thenReturn(new GameSession("gs", "quiz1", "123", "host", "LOBBY", 0, null, null, null, null));
        assertNotNull(controller.createGame(Map.of("quizId", "quiz1")));
        assertEquals("system@sprintjudge.local", email.getValue());
        assertEquals("System", name.getValue());
    }

    @Test
    void createGameWithNonOAuth2PrincipalUsesDefaults() {
        User host = new User("host", "a@b.c", "Al", null, null, null);
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new Object());
        SecurityContextHolder.getContext().setAuthentication(auth);

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        when(userRepository.upsertByEmail(email.capture(), name.capture(), any())).thenReturn(host);
        when(roomManager.createRoom(eq("quiz1"), eq("host")))
                .thenReturn(new GameSession("gs", "quiz1", "123", "host", "LOBBY", 0, null, null, null, null));
        assertNotNull(controller.createGame(Map.of("quizId", "quiz1")));
        assertEquals("system@sprintjudge.local", email.getValue());
        assertEquals("System", name.getValue());
    }

    @Test
    void updateSettingsIteratesEntries() {
        controller.updateSettings(Map.of("theme", "dark", "lang", "en"));
        verify(settingsService).set("theme", "dark");
        verify(settingsService).set("lang", "en");
    }

    @Test
    void exportBank() {
        when(importExportService.exportAll()).thenReturn("EXPORTED");
        assertEquals("EXPORTED", controller.exportBank());
    }

    @Test
    void importBankReplaceTrue() {
        when(importExportService.importAll(eq("{}"), eq(true))).thenReturn(3);
        assertEquals(3, controller.importBank(Map.of("json", "{}", "replace", true)).get("importedQuestions"));
    }

    @Test
    void importBankReplaceFalse() {
        when(importExportService.importAll(eq("{}"), eq(false))).thenReturn(2);
        assertEquals(2, controller.importBank(Map.of("json", "{}")).get("importedQuestions"));
    }
}
