package com.sprintjudge.controller;

import com.sprintjudge.domain.profile.ProfileReport;
import com.sprintjudge.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("all")
class ProfileControllerTest {

  private final ProfileService service = mock(ProfileService.class);
  private final ProfileController controller = new ProfileController(service);

  @Test
  void createHandlesMissingAndPresentBody() {
    ProfileReport report = new ProfileReport(null, null, null, null, null);
    when(service.create(null, null, null)).thenReturn(report);
    assertEquals(report, controller.create(null));
    when(service.create("Alice", "u1", "rt")).thenReturn(report);
    assertEquals(report,
        controller.create(Map.of("nickname", "Alice", "playerUuid", "u1", "rejoinToken", "rt")));
    verify(service).create("Alice", "u1", "rt");
  }

  @Test
  void getRequiresBearerToken() {
    assertEquals(401, assertThrows(ResponseStatusException.class, () -> controller.get("p1", null)).getStatusCode()
        .value());
    assertEquals(401, assertThrows(ResponseStatusException.class, () -> controller.get("p1", "Bearer ")).getStatusCode()
        .value());
    assertEquals(401, assertThrows(ResponseStatusException.class, () -> controller.get("p1", "Basic x")).getStatusCode()
        .value());
    ProfileReport report = new ProfileReport(null, null, null, null, null);
    when(service.get("p1", "tok")).thenReturn(report);
    assertEquals(report, controller.get("p1", "Bearer tok"));
    assertEquals(report, controller.get("p1", "Bearer   tok  "));
  }

  @Test
  void updateUsesBearerAndOptionalBody() {
    ProfileReport report = new ProfileReport(null, null, null, null, null);
    when(service.update("p1", "tok", null, null, null)).thenReturn(report);
    assertEquals(report, controller.update("p1", "Bearer tok", null));
    when(service.update("p1", "tok", "Bob", "u1", "rt")).thenReturn(report);
    assertNotNull(controller.update("p1", "Bearer tok",
        Map.of("nickname", "Bob", "playerUuid", "u1", "rejoinToken", "rt")));
    verify(service).update("p1", "tok", "Bob", "u1", "rt");
    assertEquals(401, assertThrows(ResponseStatusException.class,
        () -> controller.update("p1", "Bearer ", Map.of())).getStatusCode().value());
  }
}
