package com.sprintjudge.controller;

import com.sprintjudge.domain.analytics.AdaptiveSetRequest;
import com.sprintjudge.domain.analytics.AdaptiveSetResult;
import com.sprintjudge.domain.analytics.ClassReport;
import com.sprintjudge.domain.analytics.ItemReport;
import com.sprintjudge.domain.analytics.StudentReport;
import com.sprintjudge.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("all")
class AnalyticsControllerTest {

  private final AnalyticsService service = mock(AnalyticsService.class);
  private final AnalyticsController controller = new AnalyticsController(service);

  @Test
  void studentsForwardsOptionalFilter() {
    when(service.studentReports(null)).thenReturn(List.of(new StudentReport()));
    assertEquals(1, controller.students(null).size());
    when(service.studentReports("s1")).thenReturn(List.of());
    assertTrue(controller.students("s1").isEmpty());
    verify(service).studentReports("s1");
  }

  @Test
  void classesForwardsOptionalFilter() {
    ClassReport report = new ClassReport();
    when(service.classReport("s1")).thenReturn(report);
    assertEquals(report, controller.classes("s1"));
    verify(service).classReport("s1");
    controller.classes(null);
    verify(service).classReport(null);
  }

  @Test
  void itemsRequiresSubjectAndTrims() {
    assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.items(null)).getStatusCode()
        .value());
    assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.items("  ")).getStatusCode()
        .value());
    when(service.itemReports("math")).thenReturn(List.of(new ItemReport()));
    assertEquals(1, controller.items(" math ").size());
    verify(service).itemReports("math");
  }

  @Test
  void adaptiveSetDelegatesAndMapsBadInput() {
    AdaptiveSetRequest request = new AdaptiveSetRequest();
    AdaptiveSetResult result = new AdaptiveSetResult();
    when(service.buildSet(request)).thenReturn(result);
    assertEquals(result, controller.adaptiveSet(request));
    when(service.buildSet(request)).thenThrow(new IllegalArgumentException("subject is required"));
    assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.adaptiveSet(request))
        .getStatusCode().value());
  }
}
