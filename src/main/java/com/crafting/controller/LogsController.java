package com.crafting.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;



/**
 * Handles logs related operations.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/logs")
public class LogsController {

    private final static Logger log = LoggerFactory.getLogger(LogsController.class);
    private static final String LOG_FILE_PATH = "logs/crafting.log";
    private static final String ARCHIVE_DIR = "logs/archive/";

    /**
     * Archives the current log file and starts a new one.
     * @return ResponseEntity with status OK if successful, or INTERNAL_SERVER_ERROR if an error occurs.
     */
    @PostMapping("/archive")
    public ResponseEntity<String> archiveLogs() {
        try {
            log.info("Archiving current log file");
            Files.createDirectories(Paths.get(ARCHIVE_DIR));
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path source = Paths.get(LOG_FILE_PATH);
            Path target = Paths.get(ARCHIVE_DIR + "crafting_" + timestamp + ".log");
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            Files.createFile(source);
            log.info("Previous log file archived successfully as {}", target.getFileName());
            return ResponseEntity.ok("Logs archived successfully");
        } catch (IOException e) {
            log.error("Error archiving log file: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error archiving logs");
        } finally {
            log.info("Log archiving process over");
        }
    }

    /**
     * Deletes all archived log files.
     * @return ResponseEntity with status OK if successful, or INTERNAL_SERVER_ERROR if an error
     */
    @PostMapping("/clear")
    public ResponseEntity<String> clearLogs() {
        AtomicInteger deletedCount = new AtomicInteger(0);
        try {
            log.info("Clearing archived log files");
            Files.walk(Paths.get(ARCHIVE_DIR))
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        deletedCount.incrementAndGet();
                        log.info("Deleted archived log file: {}", path.getFileName());
                    } catch (IOException e) {
                        log.error("Error deleting archived log file {}: {}", path.getFileName(), e.getMessage());
                    }
                });
            log.info("Archived log files cleared successfully");
            return ResponseEntity.ok("Cleared " + deletedCount.get() + " archived log files");
        } catch (IOException e) {
            log.error("Error clearing archived log files: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error clearing archived logs");
        } finally {
            log.info("Log clearing process over");
        }
    }

    /**
     * Retrieves the current log file content.
     * @return ResponseEntity with the log file content if successful, or INTERNAL_SERVER_ERROR if
     */
    @PostMapping("/current")
    public ResponseEntity<String> getCurrentLogs() {
        try {
            log.info("Retrieving current log file content");
            String logs = new String(Files.readAllBytes(Paths.get(LOG_FILE_PATH)));
            log.info("Current log file content retrieved successfully");
            return ResponseEntity.ok(logs);
        } catch (IOException e) {
            log.error("Error retrieving current log file content: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving current logs");
        } finally {
            log.info("Log retrieval process over");
        }
    }
}
