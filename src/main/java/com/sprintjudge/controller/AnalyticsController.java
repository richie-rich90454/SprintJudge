package com.sprintjudge.controller;

import com.sprintjudge.domain.analytics.AdaptiveSetRequest;
import com.sprintjudge.domain.analytics.AdaptiveSetResult;
import com.sprintjudge.domain.analytics.ClassReport;
import com.sprintjudge.domain.analytics.ItemReport;
import com.sprintjudge.domain.analytics.StudentReport;
import com.sprintjudge.service.AnalyticsService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin analytics: submission-derived reports and adaptive set building. Auth
 * is inherited from the {@code /api/admin/**} rule, as in the bank controller.
 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AnalyticsController {

	private final AnalyticsService service;

	public AnalyticsController(AnalyticsService service) {
		this.service = service;
	}

	/**
	 * Per-player accuracy and score summary.
	 *
	 * @param sessionId
	 *            optional session filter
	 * @return one row per player
	 */
	@GetMapping("/students")
	public List<StudentReport> students(@RequestParam(required = false) String sessionId) {
		return service.studentReports(sessionId);
	}

	/**
	 * Class-wide rollup with an optional per-session breakdown.
	 *
	 * @param sessionId
	 *            optional session filter
	 * @return class report
	 */
	@GetMapping("/classes")
	public ClassReport classes(@RequestParam(required = false) String sessionId) {
		return service.classReport(sessionId);
	}

	/**
	 * Per-item report for one bank subject.
	 *
	 * @param subject
	 *            required bank subject code
	 * @return one row per attempted bank item
	 */
	@GetMapping("/items")
	public List<ItemReport> items(@RequestParam String subject) {
		if (subject == null || subject.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subject is required");
		}
		return service.itemReports(subject.trim());
	}

	/**
	 * Builds a playable quiz from a difficulty-balanced or adaptive selection.
	 *
	 * @param request
	 *            subject, target size, and selection mode
	 * @return generated quiz id and selection summary
	 */
	@PostMapping("/adaptive-set")
	public AdaptiveSetResult adaptiveSet(@RequestBody AdaptiveSetRequest request) {
		try {
			return service.buildSet(request);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}
}
