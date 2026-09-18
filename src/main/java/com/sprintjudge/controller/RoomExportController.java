package com.sprintjudge.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sprintjudge.service.RoomExportService;

/**
 * Teacher export for one room session. Auth is inherited from the
 * {@code /api/admin/**} rule, so no security wiring lives here.
 */
@RestController
@RequestMapping("/api/admin/export")
public class RoomExportController {

    private final RoomExportService exporter;

    /**
     * Creates the controller over the room exporter.
     *
     * @param exporter room export service, never null
     */
    public RoomExportController(RoomExportService exporter) {
        this.exporter = exporter;
    }

    /**
     * Exports one session as CSV.
     *
     * @param sessionId game session id
     * @return CSV text with header plus one row per submission
     */
    @GetMapping(value = "/room/{sessionId}", params = "format=csv", produces = "text/csv")
    public String roomCsv(@PathVariable String sessionId) {
        return exporter.exportCsv(sessionId);
    }

    /**
     * Exports one session as JSON rows.
     *
     * @param sessionId game session id
     * @return rows ordered by question then player
     */
    @GetMapping(value = "/room/{sessionId}", params = "format=json")
    public List<Map<String, Object>> roomJson(@PathVariable String sessionId) {
        return exporter.exportJson(sessionId);
    }
}
