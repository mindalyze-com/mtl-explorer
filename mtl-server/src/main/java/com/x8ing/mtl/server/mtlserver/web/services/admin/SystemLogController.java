package com.x8ing.mtl.server.mtlserver.web.services.admin;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogsRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/system-logs")
@RequiredArgsConstructor
public class SystemLogController {
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;
    private final SystemLogsRepository repository;
    private final MtlAppProperties properties;

    @GetMapping
    public SystemLogPage listSystemLogs(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
                                        @RequestParam(required = false) SystemLog.Source source,
                                        @RequestParam(required = false) SystemLog.Severity severity,
                                        @RequestParam(required = false) String topic1, @RequestParam(required = false) String topic2,
                                        @RequestParam(required = false) String topic3,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        requireViewer();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (from != null && to != null && from.isAfter(to))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination or date range");
        }
        Specification<SystemLog> filters = Specification.unrestricted();
        if (source != null) filters = filters.and((root, query, cb) -> cb.equal(root.get("source"), source));
        if (severity != null) filters = filters.and((root, query, cb) -> cb.equal(root.get("severity"), severity));
        if (topic1 != null) filters = filters.and((root, query, cb) -> cb.equal(root.get("topic1"), topic1));
        if (topic2 != null) filters = filters.and((root, query, cb) -> cb.equal(root.get("topic2"), topic2));
        if (topic3 != null) filters = filters.and((root, query, cb) -> cb.equal(root.get("topic3"), topic3));
        if (from != null) filters = filters.and((root, query, cb) -> cb.greaterThanOrEqualTo(eventTime(root, cb), from));
        if (to != null) filters = filters.and((root, query, cb) -> cb.lessThanOrEqualTo(eventTime(root, cb), to));
        filters = filters.and((root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class) {
                query.orderBy(cb.desc(eventTime(root, cb)), cb.desc(root.get("id")));
            }
            return cb.conjunction();
        });
        var result = repository.findAll(filters, PageRequest.of(page, size));
        return new SystemLogPage(result.stream().map(SystemLogSummary::from).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public SystemLogDetail getSystemLog(@PathVariable long id) {
        requireViewer();
        return repository.findById(id).map(SystemLogDetail::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static Expression<Instant> eventTime(Root<SystemLog> root, CriteriaBuilder cb) {
        return cb.coalesce(root.<Instant>get("occurredAt"), root.get("createDate").as(Instant.class));
    }

    private void requireViewer() {
        if (!properties.isLogViewerEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Log viewer disabled");
    }
}
