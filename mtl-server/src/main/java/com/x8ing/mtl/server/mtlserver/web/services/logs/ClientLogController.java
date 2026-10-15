package com.x8ing.mtl.server.mtlserver.web.services.logs;

import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.WebClientLoginRepository;
import com.x8ing.mtl.server.mtlserver.web.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
public class ClientLogController {
    private static final int FILE_LOG_TOPIC_CHARS = 64;
    private static final int FILE_LOG_MESSAGE_CHARS = 256;
    private static final int FILE_LOG_EXCEPTION_CHARS = 64;
    private static final int FILE_LOG_DESCRIPTION_CHARS = 256;
    private final SystemLogService service;
    private final ObjectMapper objectMapper;
    private final WebClientLoginRepository logins;

    @PostMapping("/client")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitClientLog(@Valid @RequestBody ClientLogRequest body, HttpServletRequest request) {
        String loginRef = (String) request.getAttribute(JwtUtil.CLIENT_LOGIN_REF);
        if (loginRef == null || loginRef.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (body.lastOccurredAt() != null && (body.occurredAt() == null || body.lastOccurredAt().isBefore(body.occurredAt()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event time range");
        }
        String originalLoginRef = body.originalClientLoginRef();
        if (originalLoginRef != null && !originalLoginRef.equals(loginRef)) {
            var principal = request.getUserPrincipal();
            boolean sameAccount = principal != null && logins.findByClientLoginRef(originalLoginRef)
                    .map(login -> principal.getName().equalsIgnoreCase(login.getUserName())).orElse(false);
            if (!sameAccount) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Original session belongs to another account");
        }
        if (body.context() != null && objectMapper.writeValueAsBytes(body.context()).length > ClientDiagnosticLimits.CONTEXT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Diagnostic context is too large");
        }
        SystemLog event = new SystemLog();
        event.setSource(SystemLog.Source.CLIENT);
        event.setClientEventId(body.clientEventId());
        event.setOccurredAt(body.occurredAt());
        event.setLastOccurredAt(body.lastOccurredAt());
        event.setOccurrenceCount(body.occurrenceCount());
        event.setOriginalClientLoginRef(originalLoginRef);
        event.setSeverity(body.severity() == null ? null : SystemLog.Severity.valueOf(body.severity().name()));
        event.setTopic1(sanitizedField(body.topic1(), ClientDiagnosticLimits.TOPIC_CHARS));
        event.setTopic2(sanitizedField(body.topic2(), ClientDiagnosticLimits.TOPIC_CHARS));
        event.setTopic3(sanitizedField(body.topic3(), ClientDiagnosticLimits.TOPIC_CHARS));
        event.setMessage(DiagnosticSanitizer.text(body.message()));
        event.setDetail(DiagnosticSanitizer.text(body.detail()));
        event.setStackTrace(DiagnosticSanitizer.text(body.stackTrace()));
        event.setStackFingerprint(StackFingerprint.of(event.getStackTrace()));
        event.setContext(DiagnosticSanitizer.context(body.context()));
        event.setBuildId(sanitizedField(body.buildId(), ClientDiagnosticLimits.BUILD_CHARS));
        event.setClientLoginRef(loginRef);
        try {
            if (!service.saveClientLog(event)) return;
        } catch (RuntimeException ex) {
            log.error("Client diagnostic insert failed: type={}", ex.getClass().getSimpleName(), ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Diagnostic storage unavailable");
        }
        Object errorType = event.getContext() == null ? null : event.getContext().get("errorType");
        log.info("Client problem: logId={}, clientSeverity={}, topics={}/{}/{}, message={}, exception={}, description={}",
                event.getId(), event.getSeverity(), fileLogText(event.getTopic1(), FILE_LOG_TOPIC_CHARS),
                fileLogText(event.getTopic2(), FILE_LOG_TOPIC_CHARS), fileLogText(event.getTopic3(), FILE_LOG_TOPIC_CHARS),
                fileLogText(event.getMessage(), FILE_LOG_MESSAGE_CHARS),
                fileLogFirstLine(errorType, FILE_LOG_EXCEPTION_CHARS),
                fileLogFirstLine(event.getDetail(), FILE_LOG_DESCRIPTION_CHARS));
    }

    private static String fileLogFirstLine(Object value, int maxLength) {
        if (!(value instanceof String text) || text.isBlank()) return "unspecified";
        return fileLogText(text.strip().split("\\R", 2)[0], maxLength);
    }

    private static String fileLogText(String value, int maxLength) {
        if (value == null) return "unspecified";
        String line = value.replaceAll("\\R|\\p{Cntrl}", " ");
        return line.length() <= maxLength ? line : line.substring(0, maxLength) + "…";
    }

    private static String sanitizedField(String value, int maxLength) {
        String clean = DiagnosticSanitizer.text(value);
        if (clean != null && clean.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sanitized diagnostic field is too long");
        }
        return clean;
    }
}
