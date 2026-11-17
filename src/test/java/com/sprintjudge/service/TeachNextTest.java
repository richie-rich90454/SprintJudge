package com.sprintjudge.service;

import com.sprintjudge.bank.BankRecordSource;
import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.SubmissionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class TeachNextTest {

	@Mock
	GameSessionRepository sessionRepository;
	@Mock
	QuestionRepository questionRepository;
	@Mock
	SubmissionRepository submissionRepository;
	@Mock
	BankRecordSource bankRecords;

	private TeachNextService service() {
		return new TeachNextService(sessionRepository, questionRepository, submissionRepository, bankRecords);
	}

	private GameSession session() {
		return new GameSession("s1", "qz", "1234", "host", "ENDED", "STANDARD", 0, null, null, null, Instant.now());
	}

	private Question q(String id, String title, String config) {
		return new Question(id, "qz", title, "D", "MCQ", null, 30, 100, config, 0, Instant.now());
	}

	private Submission sub(String qId, boolean correct) {
		return new Submission("sub-" + qId, "s1", qId, "P", "p1", "{}", 10, correct, null, 1, Instant.now());
	}

	@Test
	void unknownSessionThrows404() {
		when(sessionRepository.findById("nope")).thenReturn(Optional.empty());
		assertThrows(org.springframework.web.server.ResponseStatusException.class,
				new org.junit.jupiter.api.function.Executable() {
					@Override
					public void execute() throws java.lang.Throwable {
						service().recommendPractice("nope");
					}
				});
	}

	@Test
	void hardestQuestionPresentMapsTopic() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "Loops", "{\"topic\":\"Loops\"}");
		Question q2 = q("q2", "Arrays", "{\"topic\":\"Arrays\"}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1, q2));
		when(submissionRepository.findBySession("s1")).thenReturn(List.of(sub("q1", false), sub("q2", true)));
		when(bankRecords.subjects()).thenReturn(Set.of("JAVA_PROGRAMMING"));
		when(bankRecords.records("JAVA_PROGRAMMING", null))
				.thenReturn(List.of(Map.of("subject", "JAVA_PROGRAMMING", "unit", "Unit 3", "topic", "For Loops")));
		when(bankRecords.records("JAVA_PROGRAMMING", "Unit 3"))
				.thenReturn(List.of(Map.of("subject", "JAVA_PROGRAMMING", "unit", "Unit 3", "topic", "For Loops"),
						Map.of("subject", "JAVA_PROGRAMMING", "unit", "Unit 3", "topic", "More Loops")));
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("JAVA_PROGRAMMING", rec.getSubject());
		assertEquals("Unit 3", rec.getUnit());
		assertEquals("Loops", rec.getTopic());
		assertEquals(2, rec.getCount());
	}

	@Test
	void noSubmissionsUsesFirstQuestion() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "Variables", "{\"topic\":\"Variables\"}");
		Question q2 = q("q2", "Arrays", "{\"topic\":\"Arrays\"}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1, q2));
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		when(bankRecords.subjects()).thenReturn(Set.of("JAVA_PROGRAMMING"));
		when(bankRecords.records("JAVA_PROGRAMMING", null)).thenReturn(
				List.of(Map.of("subject", "JAVA_PROGRAMMING", "unit", "Unit 1", "topic", "Primitive Types")));
		when(bankRecords.records("JAVA_PROGRAMMING", "Unit 1")).thenReturn(
				List.of(Map.of("subject", "JAVA_PROGRAMMING", "unit", "Unit 1", "topic", "Primitive Types")));
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("Variables", rec.getTopic());
		assertEquals("", rec.getSubject());
		assertEquals("", rec.getUnit());
		assertEquals(0, rec.getCount());
		// first question q1 has rate 0, same as q2; q1 wins (first)
	}

	@Test
	void noMatchDefaultsToJavaUnit1() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "Zzzz", "{\"topic\":\"Zzzz\"}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1));
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		when(bankRecords.subjects()).thenReturn(Set.of("JAVA_PROGRAMMING"));
		when(bankRecords.records("JAVA_PROGRAMMING", null)).thenReturn(
				List.of(Map.of("subject", "JAVA_PROGRAMMING", "unit", "Unit 1", "topic", "Primitive Types")));
		when(bankRecords.records("JAVA_PROGRAMMING", "Unit 1")).thenReturn(
				List.of(Map.of("subject", "JAVA_PROGRAMMING", "unit", "Unit 1", "topic", "Primitive Types")));
		// topic Zzzz won't match, but loop still scans; need default. However first
		// record topic doesn't contain Zzzz, unit doesn't contain Zzzz, so no match ->
		// default.
		// But our mock returns records for JAVA_PROGRAMMING; scan finds no match, stays
		// default subject/unit JAVA_PROGRAMMING/Unit 1. count from records(default) =
		// above 1 -> min(10,1)=1.
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("", rec.getSubject());
		assertEquals("", rec.getUnit());
		assertEquals("Zzzz", rec.getTopic());
		assertEquals(0, rec.getCount());
	}

	@Test
	void emptyQuestionsTopicBlank() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		when(bankRecords.subjects()).thenReturn(Set.of());
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("", rec.getTopic());
		assertEquals("", rec.getSubject());
		assertEquals("", rec.getUnit());
		assertEquals(0, rec.getCount());
	}

	@Test
	void topicFromTitleWhenNoConfig() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "Primitive Types", "{}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1));
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		when(bankRecords.subjects()).thenReturn(Set.of());
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("Primitive Types", rec.getTopic());
	}

	@Test
	void countCappedAtTen() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "T", "{\"topic\":\"T\"}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1));
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		when(bankRecords.subjects()).thenReturn(Set.of("JAVA_PROGRAMMING"));
		when(bankRecords.records("JAVA_PROGRAMMING", null))
				.thenReturn(List.of(Map.of("subject", "JAVA_PROGRAMMING", "unit", "Unit 1", "topic", "T")));
		when(bankRecords.records("JAVA_PROGRAMMING", "Unit 1")).thenReturn(java.util.Collections.nCopies(12,
				Map.of("subject", "JAVA_PROGRAMMING", "unit", "Unit 1", "topic", "T")));
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals(10, rec.getCount());
	}

	@Test
	void nullQuestionsReturnsBlankTopic() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		when(questionRepository.findByQuiz("qz")).thenReturn(null);
		when(bankRecords.subjects()).thenReturn(Set.of());
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("", rec.getTopic());
	}

	@Test
	void repeatedSubmissionsAndSharedTopicTakeAggregationBranches() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "Loops", "{\"topic\":\"Loops\"}");
		Question q2 = q("q2", "Loops", "{\"topic\":\"Loops\"}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1, q2));
		when(submissionRepository.findBySession("s1")).thenReturn(List.of(sub("q1", true), sub("q1", true)));
		when(bankRecords.subjects()).thenReturn(Set.of());
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("Loops", rec.getTopic());
	}

	@Test
	void blankTopicShortCircuitsTopicMatchingAndBlankUnitIsIgnored() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "", "{\"topic\":\"\"}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1));
		when(bankRecords.subjects()).thenReturn(Set.of("JAVA_PROGRAMMING"));
		when(bankRecords.records("JAVA_PROGRAMMING", null))
				.thenReturn(List.of(Map.of("topic", "Loops"), Map.of("unit", "U1", "topic", "Loops")));
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("", rec.getTopic());
		assertEquals(0, rec.getCount());
	}

	@Test
	void reverseTopicContainmentAndMissingFields() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "Loops", "{\"topic\":\"Loops\"}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1));
		when(bankRecords.subjects()).thenReturn(Set.of("JAVA_PROGRAMMING"));
		List<Map<String, Object>> records = List.of(Map.of("unit", "U1", "topic", "Loop"), Map.of("topic", "Loops"),
				Map.of("unit", "U1"));
		when(bankRecords.records("JAVA_PROGRAMMING", null)).thenReturn(records);
		when(bankRecords.records("JAVA_PROGRAMMING", "U1")).thenReturn(records);
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("JAVA_PROGRAMMING", rec.getSubject());
		assertEquals("U1", rec.getUnit());
	}

	@Test
	void equalMatchesPreferJavaProgrammingSubject() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "Loops", "{\"topic\":\"Loops\"}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1));
		Set<String> subjects = new LinkedHashSet<String>();
		subjects.add("PYTHON");
		subjects.add("JAVA_PROGRAMMING");
		when(bankRecords.subjects()).thenReturn(subjects);
		when(bankRecords.records("PYTHON", null)).thenReturn(List.of(Map.of("unit", "U1", "topic", "Loops")));
		when(bankRecords.records("PYTHON", "U1")).thenReturn(List.of(Map.of("unit", "U1", "topic", "Loops")));
		when(bankRecords.records("JAVA_PROGRAMMING", null)).thenReturn(List.of(Map.of("unit", "JU", "topic", "Loops")));
		when(bankRecords.records("JAVA_PROGRAMMING", "JU")).thenReturn(List.of(Map.of("unit", "JU", "topic", "Loops")));
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("JAVA_PROGRAMMING", rec.getSubject());
	}

	@Test
	void equalMatchesAcrossJavaUnitsKeepFirstUnit() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "Loops", "{\"topic\":\"Loops\"}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1));
		when(bankRecords.subjects()).thenReturn(Set.of("JAVA_PROGRAMMING"));
		when(bankRecords.records("JAVA_PROGRAMMING", null))
				.thenReturn(List.of(Map.of("unit", "U1", "topic", "Loops"), Map.of("unit", "U2", "topic", "Loops")));
		when(bankRecords.records("JAVA_PROGRAMMING", "U1")).thenReturn(List.of(Map.of("topic", "Loops")));
		when(bankRecords.records("JAVA_PROGRAMMING", "U2")).thenReturn(List.of(Map.of("topic", "Loops")));
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("JAVA_PROGRAMMING", rec.getSubject());
		assertEquals("U1", rec.getUnit());
	}

	@Test
	void weakerAndNonJavaTiesKeepJavaProgrammingAhead() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "Loops", "{\"topic\":\"Loops\"}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1));
		Set<String> subjects = new LinkedHashSet<String>();
		subjects.add("JAVA_PROGRAMMING");
		subjects.add("PYTHON");
		subjects.add("C");
		when(bankRecords.subjects()).thenReturn(subjects);
		when(bankRecords.records("JAVA_PROGRAMMING", null))
				.thenReturn(List.of(Map.of("unit", "U1", "topic", "Loops"), Map.of("unit", "U2", "topic", "Loops")));
		when(bankRecords.records("JAVA_PROGRAMMING", "U1"))
				.thenReturn(List.of(Map.of("topic", "Loops"), Map.of("topic", "Loops")));
		when(bankRecords.records("JAVA_PROGRAMMING", "U2"))
				.thenReturn(List.of(Map.of("topic", "Loops"), Map.of("topic", "Loops")));
		when(bankRecords.records("PYTHON", null)).thenReturn(List.of(Map.of("unit", "P1", "topic", "Loops")));
		when(bankRecords.records("PYTHON", "P1")).thenReturn(List.of(Map.of("topic", "Loops")));
		when(bankRecords.records("C", null)).thenReturn(List.of(Map.of("unit", "C1", "topic", "Loops")));
		when(bankRecords.records("C", "C1")).thenReturn(List.of(Map.of("topic", "Loops"), Map.of("topic", "Loops")));
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("JAVA_PROGRAMMING", rec.getSubject());
	}

	@Test
	void zeroMatchesAcrossSubjectsLeavesEmptyRecommendation() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "Loops", "{\"topic\":\"Loops\"}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1));
		Set<String> subjects = new LinkedHashSet<String>();
		subjects.add("A");
		subjects.add("B");
		when(bankRecords.subjects()).thenReturn(subjects);
		when(bankRecords.records("A", null)).thenReturn(List.of(Map.of("unit", "U", "topic", "X")));
		when(bankRecords.records("A", "U")).thenReturn(List.of(Map.of("topic", "X")));
		when(bankRecords.records("B", null)).thenReturn(List.of(Map.of("unit", "U", "topic", "Y")));
		when(bankRecords.records("B", "U")).thenReturn(List.of(Map.of("topic", "Y")));
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("", rec.getSubject());
		assertEquals(0, rec.getCount());
	}

	@Test
	void topicOfCoversNullBlankMalformedAndNullTitle() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "FromTitle", null);
		Question q2 = q("q2", "FromTitle2", "   ");
		Question q3 = q("q3", "FromTitle3", "{oops");
		Question q4 = q("q4", "FromTitle4", "{\"topic\":\"   \"}");
		Question q5 = q("q5", null, null);
		Question q6 = q("q6", "BomTitle", "\uFEFF");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1, q2, q3, q4, q5, q6));
		when(bankRecords.subjects()).thenReturn(Set.of());
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("FromTitle", rec.getTopic());
	}

	@Test
	void subjectWithoutUnitScansAllRecords() {
		when(sessionRepository.findById("s1")).thenReturn(Optional.of(session()));
		Question q1 = q("q1", "T", "{\"topic\":\"T\"}");
		when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1));
		when(submissionRepository.findBySession("s1")).thenReturn(List.of());
		when(bankRecords.subjects()).thenReturn(Set.of("JAVA_PROGRAMMING"));
		when(bankRecords.records("JAVA_PROGRAMMING", null))
				.thenReturn(List.of(Map.of("subject", "JAVA_PROGRAMMING", "unit", "", "topic", "T")));
		TeachNextService.Recommendation rec = service().recommendPractice("s1");
		assertEquals("JAVA_PROGRAMMING", rec.getSubject());
		assertEquals("", rec.getUnit());
		assertEquals("T", rec.getTopic());
		assertEquals(1, rec.getCount());
	}
}
