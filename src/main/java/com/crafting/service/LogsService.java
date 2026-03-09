package com.crafting.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LogsService {

    private static final Logger log = LoggerFactory.getLogger(LogsService.class);
    private static final DateTimeFormatter ARCHIVE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})(?: \\[(?<thread>[^\\]]+)])? (?<level>TRACE|DEBUG|INFO|WARN|ERROR) (?<logger>[^-]+?) - (?<message>.*)$");
    private static final int DEFAULT_LINES = 400;
    private static final int MAX_LINES = 5000;

    private final Path currentLogPath = Paths.get("logs", "crafting.log");
    private final Path archiveDirPath = Paths.get("logs", "archive");

    public String archiveLogs() {
        try {
            Files.createDirectories(archiveDirPath);
            ensureCurrentLogExists();
            String timestamp = LocalDateTime.now().format(ARCHIVE_TIMESTAMP);
            Path target = archiveDirPath.resolve("crafting_" + timestamp + ".log");
            log.debug("Archiving log file from {} to {}", currentLogPath, target);
            Files.move(currentLogPath, target, StandardCopyOption.REPLACE_EXISTING);
            Files.createFile(currentLogPath);
            log.info("Log file archived as {}", target.getFileName());
            return "Logs archived successfully";
        } catch (IOException e) {
            log.error("Error archiving log file", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error archiving logs");
        }
    }

    public String clearArchives() {
        int deletedCount = 0;
        try {
            if (!Files.exists(archiveDirPath)) {
                return "Cleared 0 archived log files";
            }

            try (Stream<Path> paths = Files.list(archiveDirPath)) {
                List<Path> files = paths.filter(Files::isRegularFile).toList();
                for (Path file : files) {
                    Files.deleteIfExists(file);
                    deletedCount++;
                    log.debug("Deleted archived log file {}", file.getFileName());
                }
            }
            log.info("Cleared {} archived log files", deletedCount);
            return "Cleared " + deletedCount + " archived log files";
        } catch (IOException e) {
            log.error("Error clearing archived logs", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error clearing archived logs");
        }
    }

    public List<LogFileInfo> listLogFiles() {
        try {
            ensureCurrentLogExists();
            LogFileInfo current = toLogFileInfo(currentLogPath, true);
            List<LogFileInfo> archives;
            if (Files.exists(archiveDirPath)) {
                try (Stream<Path> paths = Files.list(archiveDirPath)) {
                    archives = paths
                            .filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(this::safeLastModified).reversed())
                            .map(path -> toLogFileInfo(path, false))
                            .toList();
                }
            } else {
                archives = List.of();
            }
            return Stream.concat(Stream.of(current), archives.stream()).toList();
        } catch (IOException e) {
            log.error("Error listing log files", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error listing log files");
        }
    }

    public LogViewResponse getLogView(String fileKey, Integer lines, String level, String search) {
        try {
            Path file = resolveFile(fileKey);
            ensureFileExists(file);

            List<String> rawLines = Files.readAllLines(file, StandardCharsets.UTF_8);
            Map<String, Long> levelCounts = new LinkedHashMap<>();
            levelCounts.put("TRACE", 0L);
            levelCounts.put("DEBUG", 0L);
            levelCounts.put("INFO", 0L);
            levelCounts.put("WARN", 0L);
            levelCounts.put("ERROR", 0L);

            List<LogEntry> parsed = new java.util.ArrayList<>(rawLines.size());
            String normalizedLevel = normalizeLevel(level);
            String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

            for (int i = 0; i < rawLines.size(); i++) {
                LogEntry entry = parseLogLine(i + 1L, rawLines.get(i));
                if (entry.level() != null && levelCounts.containsKey(entry.level())) {
                    levelCounts.put(entry.level(), levelCounts.get(entry.level()) + 1L);
                }
                boolean matchesLevel = normalizedLevel == null || normalizedLevel.equals(entry.level());
                boolean matchesSearch = normalizedSearch.isEmpty()
                        || entry.raw().toLowerCase(Locale.ROOT).contains(normalizedSearch)
                        || (entry.message() != null && entry.message().toLowerCase(Locale.ROOT).contains(normalizedSearch))
                        || (entry.logger() != null && entry.logger().toLowerCase(Locale.ROOT).contains(normalizedSearch));
                if (matchesLevel && matchesSearch) {
                    parsed.add(entry);
                }
            }

            int requestedLines = lines == null ? DEFAULT_LINES : Math.max(1, Math.min(lines, MAX_LINES));
            int fromIndex = Math.max(parsed.size() - requestedLines, 0);
            List<LogEntry> visible = parsed.subList(fromIndex, parsed.size());
            boolean truncated = parsed.size() > requestedLines;

            return new LogViewResponse(
                    toFileKey(file),
                    file.getFileName().toString(),
                    rawLines.size(),
                    parsed.size(),
                    visible.size(),
                    truncated,
                    Files.size(file),
                    toOffsetDateTime(Files.getLastModifiedTime(file)),
                    levelCounts,
                    visible
            );
        } catch (IOException e) {
            log.error("Error reading log view for fileKey={}", fileKey, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving logs");
        }
    }

    private void ensureCurrentLogExists() throws IOException {
        Files.createDirectories(currentLogPath.getParent());
        if (!Files.exists(currentLogPath)) {
            Files.createFile(currentLogPath);
        }
    }

    private void ensureFileExists(Path file) {
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Log file not found");
        }
    }

    private Path resolveFile(String fileKey) {
        if (fileKey == null || fileKey.isBlank() || "current".equalsIgnoreCase(fileKey)) {
            return currentLogPath;
        }

        Path candidate = archiveDirPath.resolve(fileKey).normalize();
        if (!candidate.startsWith(archiveDirPath.normalize())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid log file");
        }
        return candidate;
    }

    private String toFileKey(Path file) {
        return file.equals(currentLogPath) ? "current" : file.getFileName().toString();
    }

    private LogFileInfo toLogFileInfo(Path path, boolean current) {
        try {
            ensureFileExists(path);
            return new LogFileInfo(
                    toFileKey(path),
                    path.getFileName().toString(),
                    current,
                    Files.size(path),
                    toOffsetDateTime(Files.getLastModifiedTime(path))
            );
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading log file metadata");
        }
    }

    private FileTime safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.fromMillis(0L);
        }
    }

    private OffsetDateTime toOffsetDateTime(FileTime time) {
        return OffsetDateTime.ofInstant(time.toInstant(), ZoneId.systemDefault());
    }

    private String normalizeLevel(String level) {
        if (level == null || level.isBlank() || "ALL".equalsIgnoreCase(level)) {
            return null;
        }
        return switch (level.trim().toUpperCase(Locale.ROOT)) {
            case "TRACE", "DEBUG", "INFO", "WARN", "ERROR" -> level.trim().toUpperCase(Locale.ROOT);
            case "WARNING" -> "WARN";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid log level");
        };
    }

    private LogEntry parseLogLine(long lineNumber, String rawLine) {
        Matcher matcher = LOG_LINE_PATTERN.matcher(rawLine);
        if (!matcher.matches()) {
            return new LogEntry(lineNumber, null, null, null, null, rawLine, rawLine);
        }
        return new LogEntry(
                lineNumber,
                matcher.group("timestamp"),
                matcher.group("level"),
                matcher.group("logger") != null ? matcher.group("logger").trim() : null,
                matcher.group("thread"),
                matcher.group("message"),
                rawLine
        );
    }

    public record LogFileInfo(
            String key,
            String fileName,
            boolean current,
            long sizeBytes,
            OffsetDateTime lastModified
    ) {
    }

    public record LogEntry(
            long lineNumber,
            String timestamp,
            String level,
            String logger,
            String thread,
            String message,
            String raw
    ) {
    }

    public record LogViewResponse(
            String fileKey,
            String fileName,
            long totalLines,
            long matchedLines,
            int returnedLines,
            boolean truncated,
            long sizeBytes,
            OffsetDateTime lastModified,
            Map<String, Long> levelCounts,
            List<LogEntry> entries
    ) {
    }
}
