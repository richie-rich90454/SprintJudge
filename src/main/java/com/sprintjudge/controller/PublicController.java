package com.sprintjudge.controller;

import org.eclipse.jdt.annotation.Nullable;
import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.domain.models.User;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.UserRepository;
import com.sprintjudge.service.GameRoom;
import com.sprintjudge.service.GameRoomManager;
import com.sprintjudge.service.executor.CodeExecutor;
import com.sprintjudge.service.executor.RunRequest;
import com.sprintjudge.service.executor.RunResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deliberately minimal public surface. Question payloads (which embed answer
 * keys in their config) are NEVER exposed here - they are admin-only, so a
 * player cannot fetch correct answers before a round.
 */
@RestController
@RequestMapping("/api/public")
@EnableScheduling
public class PublicController {

	private static final Logger log = LoggerFactory.getLogger(PublicController.class);

	private final QuizRepository quizRepository;
	private final GameSessionRepository sessionRepository;
	private final CodeExecutor executor;
	private final UserRepository userRepository;
	private final GameRoomManager roomManager;
	private final QuestionRepository questionRepository;

	/** Fixed-window per-IP rate limit for the live runner (abuse guard). */
	private final ConcurrentHashMap<String, long[]> runWindow = new ConcurrentHashMap<>();
	private static final int RUN_LIMIT_PER_MIN = 30;
	private final ConcurrentHashMap<String, long[]> practiceWindow = new ConcurrentHashMap<>();
	private static final int PRACTICE_LIMIT_PER_MIN = 10;
	private static final long WINDOW_MS = 60_000;
	private static final long STALE_MS = 120_000;

	@org.springframework.beans.factory.annotation.Value("${sprintjudge.executor.mode:native}")
	private String executorMode = "native";

	/**
	 * Production policy: the public runner is unsandboxed under the native
	 * executor, so it is refused in prod unless nsjail is the active mode.
	 */
	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.core.env.Environment environment;

	boolean publicRunAllowed() {
		// Sandboxed execution is always acceptable. Unsanboxed native execution
		// is fine in development, but in production it must be an explicit,
		// deliberate opt-in: otherwise an anonymous caller could run arbitrary
		// code as the service user and read its secrets.
		if (!"native".equalsIgnoreCase(executorMode))
			return true;
		// Unsandboxed execution is anonymous RCE, so it always requires an
		// explicit opt-in -- in dev just as much as in prod.
		return Boolean.parseBoolean(environment.getProperty("sprintjudge.executor.public-run", "false"));
	}

	public PublicController(QuizRepository quizRepository, GameSessionRepository sessionRepository,
			CodeExecutor executor, UserRepository userRepository, GameRoomManager roomManager,
			QuestionRepository questionRepository) {
		this.quizRepository = quizRepository;
		this.sessionRepository = sessionRepository;
		this.executor = executor;
		this.userRepository = userRepository;
		this.roomManager = roomManager;
		this.questionRepository = questionRepository;
	}

	/** Shared fixed-window limiter; true when the caller is over budget. */
	private static boolean overLimit(ConcurrentHashMap<String, long[]> window, String ip, int limit) {
		long now = System.currentTimeMillis();
		long[] slot = window.compute(ip, new java.util.function.BiFunction<String, long[], long[]>() {
			@Override
			public long[] apply(String k, long[] v) {
				// Age from the LAST request, not the first: idling must not hand
				// the caller a fresh budget.
				if (v == null || now - v[0] > WINDOW_MS) {
					return new long[]{now, 1};
				}
				v[0] = now;
				v[1]++;
				return v;
			}
		});
		return slot[1] > limit;
	}

	/**
	 * Resolve the real client IP so every caller behind one proxy does not share a
	 * single rate-limit bucket. Proxy headers are trusted only from loopback peers,
	 * matching the WebSocket and login limiters.
	 */
	private static String clientIp(jakarta.servlet.http.HttpServletRequest http) {
		return com.sprintjudge.util.ClientIpResolver.resolve(http.getRemoteAddr(), http.getHeader("X-Forwarded-For"),
				http.getHeader("X-Real-IP"));
	}

	/**
	 * PIN-scoped quiz preview for the join flow. The full bank is never listed
	 * anonymously - without a live PIN there is nothing to see.
	 */
	@GetMapping("/quizzes")
	public List<Quiz> listQuizzes(@RequestParam(value = "pin", required = false) String pin, HttpServletRequest http) {
		if (pin == null || pin.isBlank())
			return List.of();
		// Same per-IP budget as /practice: without it this endpoint is a free
		// PIN oracle for brute-forcing live rooms before spending JOIN attempts.
		if (overLimit(practiceWindow, clientIp(http), PRACTICE_LIMIT_PER_MIN))
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "rate_limited");
		java.util.Optional<GameSession> sessionOpt = sessionRepository.findByPin(pin.trim());
		if (!sessionOpt.isPresent())
			return List.of();
		GameSession s = sessionOpt.get();
		if ("ENDED".equals(s.getStatus()))
			return List.of();
		java.util.Optional<Quiz> quizOpt = quizRepository.findById(s.getQuizId());
		if (!quizOpt.isPresent())
			return List.of();
		return List.of(quizOpt.get());
	}

	/**
	 * Public question-bank headers for the library and the practice picker.
	 * Answer-free by construction: correct answers live in question configs, which
	 * are never exposed here - only id/title/description cross the wire.
	 */
	@GetMapping("/banks")
	public List<BankDto> banks() {
		List<BankDto> result = new ArrayList<>();
		for (Quiz q : quizRepository.findAll()) {
			result.add(new BankDto(q.getId(), q.getTitle(), q.getDescription()));
		}
		return List.copyOf(result);
	}
	public static final class BankDto {
		private String id;
		private String title;
		private String description;

		@SuppressWarnings("all")
		public BankDto() {
		}

		public BankDto(String id, String title, String description) {
			this.id = id;
			this.title = title;
			this.description = description;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			BankDto that = (BankDto) o;
			return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(title, that.title)
					&& java.util.Objects.equals(description, that.description);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(id, title, description);
		}

		@Override
		public String toString() {
			return "BankDto[id=" + id + ", title=" + title + ", description=" + description + "]";
		}
	}

	/**
	 * LAN join addresses. A host opened on localhost would otherwise render a QR
	 * code that points every phone back at itself.
	 */
	@GetMapping("/net")
	public NetInfo net(HttpServletRequest request) {
		String scheme = request.isSecure() ? "https" : "http";
		List<String> urls = new ArrayList<>();
		try {
			for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!nic.isUp() || nic.isLoopback())
					continue;
				for (InetAddress address : Collections.list(nic.getInetAddresses())) {
					if (address instanceof Inet4Address && !address.isLoopbackAddress()
							&& !address.isLinkLocalAddress()) {
						urls.add(scheme + "://" + address.getHostAddress() + ":" + request.getServerPort());
					}
				}
			}
		} catch (SocketException e) {
			log.warn("Could not enumerate network interfaces for LAN join URLs", e);
		}
		return new NetInfo(request.getServerName(), request.getServerPort(), List.copyOf(urls));
	}

	public static final class NetInfo {
		private String host;
		private int port;
		private List<String> urls;

		@SuppressWarnings("all")
		public NetInfo() {
		}

		public NetInfo(String host, int port, List<String> urls) {
			this.host = host;
			this.port = port;
			this.urls = urls;
		}

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public int getPort() {
			return port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public List<String> getUrls() {
			return urls;
		}

		public void setUrls(List<String> urls) {
			this.urls = urls;
		}

		@Override
		public boolean equals(@Nullable Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			NetInfo that = (NetInfo) o;
			return port == that.port && java.util.Objects.equals(host, that.host)
					&& java.util.Objects.equals(urls, that.urls);
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(host, port, urls);
		}

		@Override
		public String toString() {
			return "NetInfo[host=" + host + ", port=" + port + ", urls=" + urls + "]";
		}
	}

	/**
	 * Live code execution for the interactive console. Compiles + runs with the
	 * supplied stdin and returns combined output. Rate-limited per IP.
	 *
	 * This endpoint is deliberately UNAUTHENTICATED, and only when the explicit
	 * opt-in {@code sprintjudge.executor.public-run=true} is set (required for
	 * native mode, which E2E enables). With the flag off, every caller gets 403.
	 */
	@PostMapping("/run")
	public RunResult run(@Valid @RequestBody RunRequest request, HttpServletRequest http) {
		if (!publicRunAllowed()) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.FORBIDDEN, "public_runner_disabled");
		}
		String ip = clientIp(http);
		if (overLimit(runWindow, ip, RUN_LIMIT_PER_MIN)) {
			log.warn("Rate limit exceeded for IP: {}", ip);
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "rate_limited");
		}
		return executor.run(request);
	}

	/**
	 * One-click solo practice. Spins up a PRACTICE-mode room on the chosen bank (or
	 * the practice set: first template quiz, else first quiz) with a server-side
	 * practice host - no admin session involved, so players never touch /admin. The
	 * first join auto-starts Q1; empty banks 404.
	 */
	@PostMapping("/practice")
	public com.sprintjudge.domain.models.GameSession practice(@RequestBody(required = false) Map<String, String> body,
			HttpServletRequest http) {
		String ip = clientIp(http);
		if (overLimit(practiceWindow, ip, PRACTICE_LIMIT_PER_MIN)) {
			log.warn("Practice rate limit exceeded for IP: {}", ip);
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "rate_limited");
		}
		String quizId = body != null ? body.getOrDefault("quizId", "").trim() : "";
		if (quizId.isEmpty()) {
			List<Quiz> quizzes = quizRepository.findAll();
			for (Quiz q : quizzes) {
				if (q.isTemplate()) {
					quizId = q.getId();
					break;
				}
			}
			if (quizId.isEmpty() && !quizzes.isEmpty()) {
				quizId = quizzes.get(0).getId();
			}
		}
		if (quizId.isEmpty() || quizRepository.findById(quizId).isEmpty()
				|| questionRepository.findByQuiz(quizId).isEmpty()) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.NOT_FOUND, "no practice set yet");
		}
		User host = userRepository.upsertByEmail("practice@sprintjudge.local", "Practice", null);
		return roomManager.createRoom(quizId, host.getId(), GameRoom.GameMode.PRACTICE);
	}

	@Scheduled(fixedRate = 60_000)
	public void evictStaleRateLimits() {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<String, long[]>> runIt = runWindow.entrySet().iterator();
		while (runIt.hasNext()) {
			Map.Entry<String, long[]> e = runIt.next();
			if (now - e.getValue()[0] > STALE_MS)
				runIt.remove();
		}
		Iterator<Map.Entry<String, long[]>> practiceIt = practiceWindow.entrySet().iterator();
		while (practiceIt.hasNext()) {
			Map.Entry<String, long[]> e = practiceIt.next();
			if (now - e.getValue()[0] > STALE_MS)
				practiceIt.remove();
		}
	}
}
