package com.sprintjudge.controller;

import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.domain.models.User;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.UserRepository;
import com.sprintjudge.service.GameRoom;
import com.sprintjudge.service.GameRoomManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicControllerTest {

    @Mock QuizRepository quizRepository;
    @Mock GameSessionRepository sessionRepository;
    @Mock UserRepository userRepository;
    @Mock GameRoomManager roomManager;
    @Mock QuestionRepository questionRepository;
    @Mock HttpServletRequest http;

    @InjectMocks PublicController controller;

    private GameSession session(String pin, String status) {
        return new GameSession("s", "q1", pin, "host", status, "STANDARD", 0, null, null, null, Instant.now());
    }

    @Test
    void noPinListsNothing() {
        assertTrue(controller.listQuizzes(null).isEmpty());
        assertTrue(controller.listQuizzes("  ").isEmpty());
    }

    @Test
    void unknownPinListsNothing() {
        when(sessionRepository.findByPin("000000")).thenReturn(Optional.empty());
        assertTrue(controller.listQuizzes("000000").isEmpty());
    }

    @Test
    void endedPinListsNothing() {
        when(sessionRepository.findByPin("111111"))
                .thenReturn(Optional.of(session("111111", "ENDED")));
        assertTrue(controller.listQuizzes("111111").isEmpty());
    }

    @Test
    void livePinReturnsItsQuiz() {
        when(sessionRepository.findByPin("123456"))
                .thenReturn(Optional.of(session("123456", "LOBBY")));
        when(quizRepository.findById("q1"))
                .thenReturn(Optional.of(new Quiz("q1", "T", null, null, null, false)));
        assertEquals(1, controller.listQuizzes("123456").size());
    }

    @Test
    void livePinWithMissingQuizListsNothing() {
        when(sessionRepository.findByPin("222222"))
                .thenReturn(Optional.of(session("222222", "ACTIVE")));
        when(quizRepository.findById("q1")).thenReturn(Optional.empty());
        assertTrue(controller.listQuizzes(" 222222 ").isEmpty());
    }

    private Quiz quiz(String id, boolean template) {
        return new Quiz(id, "T", null, null, null, template);
    }

    private User practiceHost() {
        return new User("u", "practice@sprintjudge.local", "Practice", null, null, null);
    }

    private Question question(String id, String quizId) {
        return new Question(id, quizId, "T", "D", "MCQ", null, 30, 100, "{}", 0, null);
    }

    private void givenPracticeBacking() {
        when(http.getRemoteAddr()).thenReturn("9.9.9.9");
        when(quizRepository.findById("tpl")).thenReturn(Optional.of(quiz("tpl", true)));
        when(questionRepository.findByQuiz("tpl")).thenReturn(List.of(question("q1", "tpl")));
        when(userRepository.upsertByEmail("practice@sprintjudge.local", "Practice", null))
                .thenReturn(practiceHost());
        when(roomManager.createRoom(eq("tpl"), eq("u"), eq(GameRoom.GameMode.PRACTICE)))
                .thenReturn(session("123456", "LOBBY"));
    }

    @Test
    void practiceSpinsUpRoomOnTemplateQuiz() {
        when(quizRepository.findAll()).thenReturn(List.of(quiz("plain", false), quiz("tpl", true)));
        givenPracticeBacking();
        assertEquals("123456", controller.practice(null, http).pinCode());
    }

    @Test
    void practiceFallsBackToFirstQuiz() {
        when(quizRepository.findAll()).thenReturn(List.of(quiz("tpl", false)));
        givenPracticeBacking();
        assertEquals("123456", controller.practice(null, http).pinCode());
    }

    @Test
    void practiceUsesRequestedBank() {
        when(quizRepository.findById("tpl")).thenReturn(Optional.of(quiz("tpl", true)));
        when(questionRepository.findByQuiz("tpl")).thenReturn(List.of(question("q1", "tpl")));
        when(http.getRemoteAddr()).thenReturn("9.9.9.6");
        when(userRepository.upsertByEmail("practice@sprintjudge.local", "Practice", null))
                .thenReturn(practiceHost());
        when(roomManager.createRoom(eq("tpl"), eq("u"), eq(GameRoom.GameMode.PRACTICE)))
                .thenReturn(session("123456", "LOBBY"));
        assertEquals("123456",
                controller.practice(Map.of("quizId", "  tpl "), http).pinCode());
    }

    @Test
    void practiceUnknownBankIs404() {
        when(quizRepository.findById("ghost")).thenReturn(Optional.empty());
        when(http.getRemoteAddr()).thenReturn("9.9.9.5");
        var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.practice(Map.of("quizId", "ghost"), http));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void practiceEmptyBankIs404() {
        when(quizRepository.findById("tpl")).thenReturn(Optional.of(quiz("tpl", true)));
        when(questionRepository.findByQuiz("tpl")).thenReturn(List.of());
        when(http.getRemoteAddr()).thenReturn("9.9.9.4");
        var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.practice(Map.of("quizId", "tpl"), http));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void practiceWithoutQuizzesIs404() {
        when(quizRepository.findAll()).thenReturn(List.of());
        when(http.getRemoteAddr()).thenReturn("9.9.9.8");
        var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.practice(null, http));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void practiceRateLimitsPerIp() {
        when(quizRepository.findAll()).thenReturn(List.of(quiz("tpl", true)));
        when(http.getRemoteAddr()).thenReturn("9.9.9.7");
        when(quizRepository.findById("tpl")).thenReturn(Optional.of(quiz("tpl", true)));
        when(questionRepository.findByQuiz("tpl")).thenReturn(List.of(question("q1", "tpl")));
        when(userRepository.upsertByEmail("practice@sprintjudge.local", "Practice", null))
                .thenReturn(practiceHost());
        when(roomManager.createRoom(eq("tpl"), eq("u"), eq(GameRoom.GameMode.PRACTICE)))
                .thenReturn(session("123456", "LOBBY"));
        for (int i = 0; i < 10; i++) {
            controller.practice(null, http);
        }
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.practice(null, http));
    }

    @Test
    void banksListsHeadersWithoutAnswers() {
        when(quizRepository.findAll()).thenReturn(List.of(quiz("a", true), quiz("b", false)));
        var banks = controller.banks();
        assertEquals(2, banks.size());
        assertEquals("a", banks.get(0).id());
        assertEquals("T", banks.get(0).title());
    }

    @Test
    void banksEmptyWhenNoQuizzes() {
        when(quizRepository.findAll()).thenReturn(List.of());
        assertTrue(controller.banks().isEmpty());
    }

    @Test
    void netReportsTheFirstLanIpv4Address() throws Exception {
        NetworkInterface nic = mock(NetworkInterface.class);
        when(nic.isUp()).thenReturn(true);
        when(nic.isLoopback()).thenReturn(false);
        Inet4Address lan = mock(Inet4Address.class);
        when(lan.isLoopbackAddress()).thenReturn(false);
        when(lan.isLinkLocalAddress()).thenReturn(false);
        when(lan.getHostAddress()).thenReturn("192.168.1.20");
        Inet4Address loopback = mock(Inet4Address.class);
        when(loopback.isLoopbackAddress()).thenReturn(true);
        Inet4Address linkLocal = mock(Inet4Address.class);
        when(linkLocal.isLoopbackAddress()).thenReturn(false);
        when(linkLocal.isLinkLocalAddress()).thenReturn(true);
        Inet6Address v6 = mock(Inet6Address.class);
        when(nic.getInetAddresses())
                .thenReturn(Collections.enumeration(List.of(lan, loopback, linkLocal, v6)));
        NetworkInterface down = mock(NetworkInterface.class);
        when(down.isUp()).thenReturn(false);
        NetworkInterface loopNic = mock(NetworkInterface.class);
        when(loopNic.isUp()).thenReturn(true);
        when(loopNic.isLoopback()).thenReturn(true);
        try (var mocked = mockStatic(NetworkInterface.class)) {
            mocked.when(NetworkInterface::getNetworkInterfaces)
                    .thenReturn(Collections.enumeration(List.of(nic, down, loopNic)));
            when(http.isSecure()).thenReturn(false);
            when(http.getServerName()).thenReturn("quiz.local");
            when(http.getServerPort()).thenReturn(8080);
            var info = controller.net(http);
            assertEquals("quiz.local", info.host());
            assertEquals(8080, info.port());
            assertEquals(List.of("http://192.168.1.20:8080"), info.urls());
        }
    }

    @Test
    void netFailsClosedWhenInterfacesCannotBeRead() throws Exception {
        try (var mocked = mockStatic(NetworkInterface.class)) {
            mocked.when(NetworkInterface::getNetworkInterfaces).thenThrow(new SocketException("x"));
            when(http.getServerName()).thenReturn("quiz.local");
            when(http.getServerPort()).thenReturn(8080);
            var info = controller.net(http);
            assertTrue(info.urls().isEmpty());
        }
    }

    @Test
    void netUsesHttpsWhenTheRequestIsSecure() throws Exception {
        try (var mocked = mockStatic(NetworkInterface.class)) {
            mocked.when(NetworkInterface::getNetworkInterfaces)
                    .thenReturn(Collections.emptyEnumeration());
            when(http.isSecure()).thenReturn(true);
            when(http.getServerName()).thenReturn("quiz.local");
            when(http.getServerPort()).thenReturn(443);
            var info = controller.net(http);
            assertEquals("quiz.local", info.host());
            assertTrue(info.urls().isEmpty());
        }
    }
}
