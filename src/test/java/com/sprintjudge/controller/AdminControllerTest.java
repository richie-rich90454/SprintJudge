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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        Question in = new Question("qid", "q1", "Q", null, "MCQ", null, 30, 10, "{}", 0, null);
        when(questionRepository.findById("qid")).thenReturn(Optional.of(in));
        when(questionRepository.save(any())).thenReturn(new Question("qid", "q1", "Q", null, "MCQ", null, 30, 10, "{}", 0, null));
        assertNotNull(controller.updateQuestion("qid", in));
    }

    @Test
    void updateQuestionKeepsStoredQuizId() {
        Question stored = new Question("qid", "q1", "Q", null, "MCQ", null, 30, 10, "{}", 0, null);
        when(questionRepository.findById("qid")).thenReturn(Optional.of(stored));
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Question body = new Question("qid", "evil-quiz", "Q", null, "MCQ", null, 30, 10, "{}", 0, null);
        assertEquals("q1", controller.updateQuestion("qid", body).quizId());
    }

    @Test
    void updateMissingQuestionIs404() {
        when(questionRepository.findById("nope")).thenReturn(Optional.empty());
        Question in = new Question("nope", "q1", "Q", null, "MCQ", null, 30, 10, "{}", 0, null);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuestion("nope", in));
    }

    @Test
    void addQuestionWithBadTypeIs400() {
        Question in = new Question(null, "q1", "Q", null, "NOPE", null, 30, 10, "{}", 0, null);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.addQuestion("q1", in));
    }

    @Test
    void createDuplicateQuizIs409() {
        when(quizRepository.findById("q1"))
                .thenReturn(Optional.of(new Quiz("q1", "T", null, null, null, false)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.createQuiz(new Quiz("q1", "T", null, null, null, false)));
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
        when(roomManager.createRoom(eq("quiz1"), eq("host"), any()))
                .thenReturn(new GameSession("gs", "quiz1", "123", "host", "LOBBY", 0, null, null, null, null));
        GameSession gs = controller.createGame(Map.of("quizId", "quiz1"));
        assertNotNull(gs);
        assertEquals("system@sprintjudge.local", email.getValue());
        assertEquals("System", name.getValue());
        verify(roomManager).createRoom("quiz1", "host", com.sprintjudge.service.GameRoom.GameMode.STANDARD);
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
        when(roomManager.createRoom(eq("quiz1"), eq("host"), any()))
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
        when(roomManager.createRoom(eq("quiz1"), eq("host"), any()))
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
        when(roomManager.createRoom(eq("quiz1"), eq("host"), any()))
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
        when(roomManager.createRoom(eq("quiz1"), eq("host"), any()))
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

    @Test
    void updateQuizHappyPath() {
        Quiz existing = new Quiz("q1", "Old", "olddesc", "u1", null, false);
        when(quizRepository.findById("q1")).thenReturn(Optional.of(existing));
        when(quizRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        Quiz got = controller.updateQuiz("q1", Map.of("title", "New", "description", "newdesc"));
        assertEquals("New", got.title());
        assertEquals("newdesc", got.description());
        assertEquals("q1", got.id());
    }

    @Test
    void updateQuizKeepsExistingWhenBodyEmpty() {
        Quiz existing = new Quiz("q1", "Old", "olddesc", "u1", null, false);
        when(quizRepository.findById("q1")).thenReturn(Optional.of(existing));
        when(quizRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        Quiz got = controller.updateQuiz("q1", Map.of());
        assertEquals("Old", got.title());
        assertEquals("olddesc", got.description());
    }

    @Test
    void updateMissingQuizIs404() {
        when(quizRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuiz("nope", Map.of("title", "T")));
    }

    @Test
    void updateQuizBlankTitleIs400() {
        Quiz existing = new Quiz("q1", "Old", null, "u1", null, false);
        when(quizRepository.findById("q1")).thenReturn(Optional.of(existing));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuiz("q1", Map.of("title", "  ")));
    }

    @Test
    void updateQuizNullTitleIs400() {
        Quiz existing = new Quiz("q1", "Old", null, "u1", null, false);
        when(quizRepository.findById("q1")).thenReturn(Optional.of(existing));
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("title", null);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuiz("q1", body));
    }

    @Test
    void updateQuizLongTitleIs400() {
        Quiz existing = new Quiz("q1", "Old", null, "u1", null, false);
        when(quizRepository.findById("q1")).thenReturn(Optional.of(existing));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuiz("q1", Map.of("title", "t".repeat(201))));
    }

    @Test
    void updateQuizTitleBoundary200Ok() {
        Quiz existing = new Quiz("q1", "Old", null, "u1", null, false);
        when(quizRepository.findById("q1")).thenReturn(Optional.of(existing));
        when(quizRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(200, controller.updateQuiz("q1", Map.of("title", "t".repeat(200))).title().length());
    }

    @Test
    void updateQuizLongDescriptionIs400() {
        Quiz existing = new Quiz("q1", "Old", null, "u1", null, false);
        when(quizRepository.findById("q1")).thenReturn(Optional.of(existing));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuiz("q1", Map.of("description", "d".repeat(4001))));
    }

    @Test
    void createQuizWithFreshIdCreates() {
        when(quizRepository.findById("fresh")).thenReturn(Optional.empty());
        when(quizRepository.create(any())).thenReturn(new Quiz("fresh", "T", null, null, null, false));
        assertEquals("fresh", controller.createQuiz(new Quiz("fresh", "T", null, null, null, false)).id());
    }

    @Test
    void createQuizNullIdSkipsConflictCheck() {
        when(quizRepository.create(any())).thenReturn(new Quiz("gen", "T", null, null, null, false));
        assertEquals("gen", controller.createQuiz(new Quiz(null, "T", null, null, null, false)).id());
        verify(quizRepository, org.mockito.Mockito.never()).findById(anyString());
    }

    @Test
    void addQuestionLowercaseTypeAccepted() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Question in = new Question(null, "q1", "Q", null, "mcq", null, 30, 10, "{}", 0, null);
        assertNotNull(controller.addQuestion("q1", in));
    }

    @Test
    void addQuestionBindsPathQuizId() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Question in = new Question(null, "other", "Q", null, "MCQ", null, 30, 10, "{}", 0, null);
        assertEquals("q1", controller.addQuestion("q1", in).quizId());
    }

    @Test
    void updateQuestionBadTypeIs400() {
        Question stored = new Question("qid", "q1", "Q", null, "MCQ", null, 30, 10, "{}", 0, null);
        when(questionRepository.findById("qid")).thenReturn(Optional.of(stored));
        Question body = new Question("qid", "q1", "Q", null, "NOPE", null, 30, 10, "{}", 0, null);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuestion("qid", body));
    }

    @Test
    void createGameMissingQuizIdIs400() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.createGame(Map.of()));
    }

    @Test
    void createGameBlankQuizIdIs400() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.createGame(Map.of("quizId", "  ")));
    }

    @Test
    void createGameUnknownModeIs400() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.createGame(Map.of("quizId", "q1", "gameMode", "CHAOS")));
    }

    @Test
    void createGameLowercaseModeAccepted() {
        User host = new User("host", "a@b.c", "Al", null, null, null);
        when(userRepository.upsertByEmail(anyString(), anyString(), any())).thenReturn(host);
        when(roomManager.createRoom(eq("q1"), eq("host"), any()))
                .thenReturn(new GameSession("gs", "q1", "123", "host", "LOBBY", 0, null, null, null, null));
        assertNotNull(controller.createGame(Map.of("quizId", "q1", "gameMode", "battle")));
        verify(roomManager).createRoom("q1", "host", com.sprintjudge.service.GameRoom.GameMode.BATTLE);
    }

    @Test
    void createGameWithUserDetailsPrincipal() {
        User host = new User("host", "u@x.y", "U", null, null, null);
        var details = org.springframework.security.core.userdetails.User.withUsername("u@x.y")
                .password("p").roles("ADMIN").build();
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
        org.mockito.ArgumentCaptor<String> email = org.mockito.ArgumentCaptor.forClass(String.class);
        when(userRepository.upsertByEmail(email.capture(), anyString(), any())).thenReturn(host);
        when(roomManager.createRoom(eq("q1"), eq("host"), any()))
                .thenReturn(new GameSession("gs", "q1", "123", "host", "LOBBY", 0, null, null, null, null));
        assertNotNull(controller.createGame(Map.of("quizId", "q1")));
        assertEquals("u@x.y", email.getValue());
    }

    @Test
    void createGameWithStringPrincipal() {
        User host = new User("host", "s@x.y", "S", null, null, null);
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("s@x.y");
        SecurityContextHolder.getContext().setAuthentication(auth);
        org.mockito.ArgumentCaptor<String> email = org.mockito.ArgumentCaptor.forClass(String.class);
        when(userRepository.upsertByEmail(email.capture(), anyString(), any())).thenReturn(host);
        when(roomManager.createRoom(eq("q1"), eq("host"), any()))
                .thenReturn(new GameSession("gs", "q1", "123", "host", "LOBBY", 0, null, null, null, null));
        assertNotNull(controller.createGame(Map.of("quizId", "q1")));
        assertEquals("s@x.y", email.getValue());
    }

    @Test
    void createGameWithBlankStringPrincipalUsesDefaults() {
        User host = new User("host", "a@b.c", "Al", null, null, null);
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("   ");
        SecurityContextHolder.getContext().setAuthentication(auth);
        org.mockito.ArgumentCaptor<String> email = org.mockito.ArgumentCaptor.forClass(String.class);
        when(userRepository.upsertByEmail(email.capture(), anyString(), any())).thenReturn(host);
        when(roomManager.createRoom(eq("q1"), eq("host"), any()))
                .thenReturn(new GameSession("gs", "q1", "123", "host", "LOBBY", 0, null, null, null, null));
        assertNotNull(controller.createGame(Map.of("quizId", "q1")));
        assertEquals("system@sprintjudge.local", email.getValue());
    }

    @Test
    void updateSettingsNullValueIs400() {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("theme", null);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateSettings(body));
    }

    @Test
    void updateSettingsEmptyIsNoop() {
        controller.updateSettings(Map.of());
        verify(settingsService, org.mockito.Mockito.never()).set(anyString(), anyString());
    }

    @Test
    void importBankMissingJsonIs400() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.importBank(Map.of()));
    }

    @Test
    void importBankNonStringJsonIs400() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.importBank(Map.of("json", (Object) 42)));
    }

    @Test
    void importBankBlankJsonIs400() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.importBank(Map.of("json", (Object) "  ")));
    }

    @Test
    void importBankReplaceStringTrue() {
        when(importExportService.importAll(eq("{}"), eq(true))).thenReturn(5);
        assertEquals(5, controller.importBank(Map.of("json", (Object) "{}", "replace", (Object) "true"))
                .get("importedQuestions"));
    }

    @Test
    void importBankReplaceStringCaseInsensitive() {
        when(importExportService.importAll(eq("{}"), eq(true))).thenReturn(5);
        assertEquals(5, controller.importBank(Map.of("json", (Object) "{}", "replace", (Object) "TRUE"))
                .get("importedQuestions"));
    }

    @Test
    void importBankReplaceStringFalse() {
        when(importExportService.importAll(eq("{}"), eq(false))).thenReturn(1);
        assertEquals(1, controller.importBank(Map.of("json", (Object) "{}", "replace", (Object) "false"))
                .get("importedQuestions"));
    }

    @Test
    void importBankNegativeClampedToZero() {
        when(importExportService.importAll(eq("{}"), eq(false))).thenReturn(-1);
        assertEquals(0, controller.importBank(Map.of("json", (Object) "{}")).get("importedQuestions"));
    }

    private Quiz mxExisting(String title, String desc) {
        return new Quiz("q1", title, desc, "u1", null, false);
    }

    private void mxStubQuiz(Quiz existing) {
        when(quizRepository.findById("q1")).thenReturn(Optional.of(existing));
        when(quizRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Question mxQuestion(String type, int timeLimit, int points) {
        return new Question(null, "q1", "Q", null, type, null, timeLimit, points, "{}", 0, null);
    }

    @Test
    void mxUpdateQuizTitle199Ok() {
        mxStubQuiz(mxExisting("Old", null));
        assertEquals(199, controller.updateQuiz("q1", Map.of("title", "t".repeat(199))).title().length());
    }

    @Test
    void mxUpdateQuizTitle201Is400() {
        when(quizRepository.findById("q1")).thenReturn(Optional.of(mxExisting("Old", null)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuiz("q1", Map.of("title", "t".repeat(201))));
    }

    @Test
    void mxUpdateQuizTitleSingleCharOk() {
        mxStubQuiz(mxExisting("Old", null));
        assertEquals("x", controller.updateQuiz("q1", Map.of("title", "x")).title());
    }

    @Test
    void mxUpdateQuizTitleTabOnlyIs400() {
        when(quizRepository.findById("q1")).thenReturn(Optional.of(mxExisting("Old", null)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuiz("q1", Map.of("title", "\t")));
    }

    @Test
    void mxUpdateQuizTitleNewlineOnlyIs400() {
        when(quizRepository.findById("q1")).thenReturn(Optional.of(mxExisting("Old", null)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuiz("q1", Map.of("title", "\n")));
    }

    @Test
    void mxUpdateQuizTitleMixedWhitespaceIs400() {
        when(quizRepository.findById("q1")).thenReturn(Optional.of(mxExisting("Old", null)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuiz("q1", Map.of("title", " \t \n ")));
    }

    @Test
    void mxUpdateQuizTitleEmptyStringIs400() {
        when(quizRepository.findById("q1")).thenReturn(Optional.of(mxExisting("Old", null)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuiz("q1", Map.of("title", "")));
    }

    @Test
    void mxUpdateQuizTitleUnicode199Ok() {
        mxStubQuiz(mxExisting("Old", null));
        assertEquals("h\u00e9llo", controller.updateQuiz("q1", Map.of("title", "h\u00e9llo")).title());
    }

    @Test
    void mxUpdateQuizDescription3999Ok() {
        mxStubQuiz(mxExisting("Old", null));
        assertEquals(3999,
                controller.updateQuiz("q1", Map.of("description", "d".repeat(3999))).description().length());
    }

    @Test
    void mxUpdateQuizDescription4000Ok() {
        mxStubQuiz(mxExisting("Old", null));
        assertEquals(4000,
                controller.updateQuiz("q1", Map.of("description", "d".repeat(4000))).description().length());
    }

    @Test
    void mxUpdateQuizDescriptionEmptyStringOk() {
        mxStubQuiz(mxExisting("Old", "keep"));
        assertEquals("", controller.updateQuiz("q1", Map.of("description", "")).description());
    }

    @Test
    void mxUpdateQuizNullDescriptionPassesThrough() {
        when(quizRepository.findById("q1")).thenReturn(Optional.of(mxExisting("Old", "keep")));
        when(quizRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("description", null);
        assertEquals(null, controller.updateQuiz("q1", body).description());
    }

    @Test
    void mxUpdateQuizKeepsIdAndAuthor() {
        mxStubQuiz(mxExisting("Old", "D"));
        Quiz got = controller.updateQuiz("q1", Map.of("title", "New"));
        assertEquals("q1", got.id());
        assertEquals("u1", got.createdBy());
    }

    @Test
    void mxAddQuestionTimeLimitZeroDelegates() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(0, controller.addQuestion("q1", mxQuestion("MCQ", 0, 10)).timeLimitSec());
    }

    @Test
    void mxAddQuestionTimeLimitOneDelegates() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(1, controller.addQuestion("q1", mxQuestion("MCQ", 1, 10)).timeLimitSec());
    }

    @Test
    void mxAddQuestionTimeLimitNegativeDelegates() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(-5, controller.addQuestion("q1", mxQuestion("MCQ", -5, 10)).timeLimitSec());
    }

    @Test
    void mxAddQuestionTimeLimitLargeDelegates() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(3600, controller.addQuestion("q1", mxQuestion("MCQ", 3600, 10)).timeLimitSec());
    }

    @Test
    void mxAddQuestionPointsBaseZeroDelegates() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(0, controller.addQuestion("q1", mxQuestion("MCQ", 30, 0)).pointsBase());
    }

    @Test
    void mxAddQuestionPointsBaseNegativeDelegates() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(-10, controller.addQuestion("q1", mxQuestion("MCQ", 30, -10)).pointsBase());
    }

    @Test
    void mxAddQuestionPointsBaseLargeDelegates() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(1000000, controller.addQuestion("q1", mxQuestion("MCQ", 30, 1000000)).pointsBase());
    }

    @Test
    void mxAddQuestionTypeMcq() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("MCQ", controller.addQuestion("q1", mxQuestion("MCQ", 30, 10)).questionType());
    }

    @Test
    void mxAddQuestionTypeTrueFalse() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("TRUE_FALSE",
                controller.addQuestion("q1", mxQuestion("TRUE_FALSE", 30, 10)).questionType());
    }

    @Test
    void mxAddQuestionTypeMultipleSelect() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("MULTIPLE_SELECT",
                controller.addQuestion("q1", mxQuestion("MULTIPLE_SELECT", 30, 10)).questionType());
    }

    @Test
    void mxAddQuestionTypeNumeric() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("NUMERIC",
                controller.addQuestion("q1", mxQuestion("NUMERIC", 30, 10)).questionType());
    }

    @Test
    void mxAddQuestionTypeOutputPred() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("OUTPUT_PRED",
                controller.addQuestion("q1", mxQuestion("OUTPUT_PRED", 30, 10)).questionType());
    }

    @Test
    void mxAddQuestionTypeFillBlank() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("FILL_BLANK",
                controller.addQuestion("q1", mxQuestion("FILL_BLANK", 30, 10)).questionType());
    }

    @Test
    void mxAddQuestionTypeDragSort() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("DRAG_SORT",
                controller.addQuestion("q1", mxQuestion("DRAG_SORT", 30, 10)).questionType());
    }

    @Test
    void mxAddQuestionTypeClickBug() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("CLICK_BUG",
                controller.addQuestion("q1", mxQuestion("CLICK_BUG", 30, 10)).questionType());
    }

    @Test
    void mxAddQuestionTypeCodeCompletion() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("CODE_COMPLETION",
                controller.addQuestion("q1", mxQuestion("CODE_COMPLETION", 30, 10)).questionType());
    }

    @Test
    void mxAddQuestionTypeComplexity() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("COMPLEXITY",
                controller.addQuestion("q1", mxQuestion("COMPLEXITY", 30, 10)).questionType());
    }

    @Test
    void mxAddQuestionTypeOjFull() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("OJ_FULL",
                controller.addQuestion("q1", mxQuestion("OJ_FULL", 30, 10)).questionType());
    }

    @Test
    void mxAddQuestionTypeOjPatch() {
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals("OJ_PATCH",
                controller.addQuestion("q1", mxQuestion("OJ_PATCH", 30, 10)).questionType());
    }

    @Test
    void mxAddQuestionNullTypeIs400() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.addQuestion("q1", mxQuestion(null, 30, 10)));
    }

    @Test
    void mxAddQuestionBlankTypeIs400() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.addQuestion("q1", mxQuestion("  ", 30, 10)));
    }

    @Test
    void mxUpdateQuestionTimeLimitEdgeDelegates() {
        Question stored = new Question("qid", "q1", "Q", null, "MCQ", null, 30, 10, "{}", 0, null);
        when(questionRepository.findById("qid")).thenReturn(Optional.of(stored));
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Question body = new Question("qid", "q1", "Q", null, "MCQ", null, 0, 0, "{}", 0, null);
        Question got = controller.updateQuestion("qid", body);
        assertEquals(0, got.timeLimitSec());
        assertEquals(0, got.pointsBase());
    }

    @Test
    void mxUpdateQuestionNullTypeIs400() {
        Question stored = new Question("qid", "q1", "Q", null, "MCQ", null, 30, 10, "{}", 0, null);
        when(questionRepository.findById("qid")).thenReturn(Optional.of(stored));
        Question body = new Question("qid", "q1", "Q", null, null, null, 30, 10, "{}", 0, null);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.updateQuestion("qid", body));
    }

    @Test
    void mxSettingsEmptyMapRoundTrip() {
        when(settingsService.asMap()).thenReturn(Map.of());
        assertTrue(controller.settings().isEmpty());
    }

    @Test
    void mxSettingsMultipleEntriesRoundTrip() {
        when(settingsService.asMap()).thenReturn(Map.of("a", "1", "b", "2", "c", "3"));
        assertEquals(3, controller.settings().size());
        assertEquals("2", controller.settings().get("b"));
    }

    @Test
    void mxUpdateSettingsSingleEntry() {
        controller.updateSettings(Map.of("only", "one"));
        verify(settingsService).set("only", "one");
    }

    @Test
    void mxUpdateSettingsBlankValueAccepted() {
        controller.updateSettings(Map.of("k", "  "));
        verify(settingsService).set("k", "  ");
    }

    @Test
    void mxExportBankEmptyString() {
        when(importExportService.exportAll()).thenReturn("");
        assertEquals("", controller.exportBank());
        verify(importExportService).exportAll();
    }

    @Test
    void mxExportBankDelegatesOnce() {
        when(importExportService.exportAll()).thenReturn("E");
        controller.exportBank();
        verify(importExportService, org.mockito.Mockito.times(1)).exportAll();
    }

    @Test
    void mxImportBankReplaceBooleanTrueObject() {
        when(importExportService.importAll(eq("{}"), eq(true))).thenReturn(4);
        assertEquals(4, controller.importBank(Map.of("json", (Object) "{}", "replace", (Object) Boolean.TRUE))
                .get("importedQuestions"));
    }

    @Test
    void mxImportBankReplaceBooleanFalseObject() {
        when(importExportService.importAll(eq("{}"), eq(false))).thenReturn(4);
        assertEquals(4, controller.importBank(Map.of("json", (Object) "{}", "replace", (Object) Boolean.FALSE))
                .get("importedQuestions"));
    }

    @Test
    void mxImportBankReplaceNumericOneIsFalse() {
        when(importExportService.importAll(eq("{}"), eq(false))).thenReturn(2);
        assertEquals(2, controller.importBank(Map.of("json", (Object) "{}", "replace", (Object) 1))
                .get("importedQuestions"));
    }

    @Test
    void mxImportBankReplaceMixedCaseTrue() {
        when(importExportService.importAll(eq("{}"), eq(true))).thenReturn(6);
        assertEquals(6, controller.importBank(Map.of("json", (Object) "{}", "replace", (Object) "TrUe"))
                .get("importedQuestions"));
    }

    @Test
    void mxImportBankTabOnlyJsonIs400() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.importBank(Map.of("json", (Object) "\t\n")));
    }

    @Test
    void mxImportBankZeroImportedShape() {
        when(importExportService.importAll(eq("{}"), eq(false))).thenReturn(0);
        assertEquals(0, controller.importBank(Map.of("json", (Object) "{}")).get("importedQuestions"));
    }

    private GameSession mxGameSession() {
        return new GameSession("gs", "q1", "123", "host", "LOBBY", 0, null, null, null, null);
    }

    private void mxStubGame(com.sprintjudge.service.GameRoom.GameMode mode) {
        User host = new User("host", "a@b.c", "Al", null, null, null);
        when(userRepository.upsertByEmail(anyString(), anyString(), any())).thenReturn(host);
        when(roomManager.createRoom(eq("q1"), eq("host"), eq(mode))).thenReturn(mxGameSession());
    }

    @Test
    void mxCreateGameModeStandard() {
        mxStubGame(com.sprintjudge.service.GameRoom.GameMode.STANDARD);
        assertNotNull(controller.createGame(Map.of("quizId", "q1", "gameMode", "STANDARD")));
        verify(roomManager).createRoom("q1", "host",
                com.sprintjudge.service.GameRoom.GameMode.STANDARD);
    }

    @Test
    void mxCreateGameModeAutoPilot() {
        mxStubGame(com.sprintjudge.service.GameRoom.GameMode.AUTO_PILOT);
        assertNotNull(controller.createGame(Map.of("quizId", "q1", "gameMode", "AUTO_PILOT")));
        verify(roomManager).createRoom("q1", "host",
                com.sprintjudge.service.GameRoom.GameMode.AUTO_PILOT);
    }

    @Test
    void mxCreateGameModePractice() {
        mxStubGame(com.sprintjudge.service.GameRoom.GameMode.PRACTICE);
        assertNotNull(controller.createGame(Map.of("quizId", "q1", "gameMode", "PRACTICE")));
        verify(roomManager).createRoom("q1", "host",
                com.sprintjudge.service.GameRoom.GameMode.PRACTICE);
    }

    @Test
    void mxCreateGameModeExam() {
        mxStubGame(com.sprintjudge.service.GameRoom.GameMode.EXAM);
        assertNotNull(controller.createGame(Map.of("quizId", "q1", "gameMode", "EXAM")));
        verify(roomManager).createRoom("q1", "host",
                com.sprintjudge.service.GameRoom.GameMode.EXAM);
    }

    @Test
    void mxCreateGameModeTeam() {
        mxStubGame(com.sprintjudge.service.GameRoom.GameMode.TEAM);
        assertNotNull(controller.createGame(Map.of("quizId", "q1", "gameMode", "TEAM")));
        verify(roomManager).createRoom("q1", "host",
                com.sprintjudge.service.GameRoom.GameMode.TEAM);
    }

    @Test
    void mxCreateGameModeBattle() {
        mxStubGame(com.sprintjudge.service.GameRoom.GameMode.BATTLE);
        assertNotNull(controller.createGame(Map.of("quizId", "q1", "gameMode", "BATTLE")));
        verify(roomManager).createRoom("q1", "host",
                com.sprintjudge.service.GameRoom.GameMode.BATTLE);
    }

    @Test
    void mxCreateGameDefaultsToStandard() {
        mxStubGame(com.sprintjudge.service.GameRoom.GameMode.STANDARD);
        assertNotNull(controller.createGame(Map.of("quizId", "q1")));
        verify(roomManager).createRoom("q1", "host",
                com.sprintjudge.service.GameRoom.GameMode.STANDARD);
    }

    @Test
    void mxCreateGameLowercaseStandard() {
        mxStubGame(com.sprintjudge.service.GameRoom.GameMode.STANDARD);
        assertNotNull(controller.createGame(Map.of("quizId", "q1", "gameMode", "standard")));
        verify(roomManager).createRoom("q1", "host",
                com.sprintjudge.service.GameRoom.GameMode.STANDARD);
    }

    @Test
    void mxCreateGameMixedCaseBattle() {
        mxStubGame(com.sprintjudge.service.GameRoom.GameMode.BATTLE);
        assertNotNull(controller.createGame(Map.of("quizId", "q1", "gameMode", "Battle")));
        verify(roomManager).createRoom("q1", "host",
                com.sprintjudge.service.GameRoom.GameMode.BATTLE);
    }

    @Test
    void mxMetricsEmptySnapshot() {
        when(metricsService.snapshot()).thenReturn(Map.of());
        assertTrue(controller.metrics().isEmpty());
    }

    @Test
    void mxMetricsDelegatesOnce() {
        when(metricsService.snapshot()).thenReturn(Map.of("a", 1));
        controller.metrics();
        verify(metricsService, org.mockito.Mockito.times(1)).snapshot();
    }

    @Test
    void mxQuestionsEmptyList() {
        when(questionRepository.findByQuiz("q9")).thenReturn(List.of());
        assertTrue(controller.questions("q9").isEmpty());
    }

    @Test
    void mxQuizzesEmptyList() {
        when(quizRepository.findAll()).thenReturn(List.of());
        assertTrue(controller.quizzes().isEmpty());
    }
}
