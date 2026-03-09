package com.crafting.controller;

import com.crafting.service.LogsService;
import com.crafting.service.LogsService.LogFileInfo;
import com.crafting.service.LogsService.LogViewResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles logs related operations.
 */
@RestController
@RequestMapping("/logs")
public class LogsController {

    private final static Logger log = LoggerFactory.getLogger(LogsController.class);
    private final LogsService logsService;

    public LogsController(LogsService logsService) {
        this.logsService = logsService;
    }

    /**
     * Archives the current log file and starts a new one.
     * @return ResponseEntity with status OK if successful, or INTERNAL_SERVER_ERROR if an error occurs.
     */
    @PostMapping("/archive")
    public ResponseEntity<String> archiveLogs() {
        log.debug("POST /logs/archive");
        return ResponseEntity.ok(logsService.archiveLogs());
    }

    /**
     * Deletes all archived log files.
     * @return ResponseEntity with status OK if successful, or INTERNAL_SERVER_ERROR if an error
     */
    @PostMapping("/clear")
    public ResponseEntity<String> clearLogs() {
        log.debug("POST /logs/clear");
        return ResponseEntity.ok(logsService.clearArchives());
    }

    @GetMapping("/files")
    public ResponseEntity<List<LogFileInfo>> listLogFiles() {
        log.debug("GET /logs/files");
        return ResponseEntity.ok(logsService.listLogFiles());
    }

    /**
     * Retrieves the current log file content.
     * @return ResponseEntity with the log file content if successful, or INTERNAL_SERVER_ERROR if
     */
    @GetMapping("/current")
    public ResponseEntity<LogViewResponse> getCurrentLogs(
            @RequestParam(required = false, defaultValue = "current") String file,
            @RequestParam(required = false) Integer lines,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search) {
        log.debug("GET /logs/current file={} lines={} level={} search='{}'", file, lines, level, search);
        return ResponseEntity.ok(logsService.getLogView(file, lines, level, search));
    }
}
