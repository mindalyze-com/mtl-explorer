package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Slf4j
@Tag(name = "health-controller")
public class HealthGroupingController {
    private final HealthGroupingService service;

    public HealthGroupingController(HealthGroupingService service) {
        this.service = service;
    }

    @GetMapping("/api/google-health/metrics/grouped")
    @Operation(operationId = "getGoogleHealthGroupedMetrics",
            description = "Grouped statistics using inclusive startDate, exclusive endDate and an IANA timezone. SECOND returns one-second averages and ranges for one selected day. All chart modes are returned together.")
    public GHGroupedMetrics grouped(@RequestParam String startDate, @RequestParam String endDate,
                                    @RequestParam(defaultValue = "AUTO") GHGrouping groupBy,
                                    @RequestParam String timeZone) {
        return service.grouped(startDate, endDate, groupBy, timeZone);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail invalidRequest(ResponseStatusException exception) {
        log.warn("Google Health grouped request rejected: status={}, errorType={}, reason={}",
                exception.getStatusCode().value(), exception.getClass().getSimpleName(), exception.getReason());
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ProblemDetail invalidParameter(Exception exception) {
        String message = exception instanceof MissingServletRequestParameterException missing
                ? missing.getParameterName() + " is required."
                : "groupBy must be AUTO, SECOND, DAY, WEEK, MONTH or YEAR.";
        log.warn("Google Health grouped request rejected: errorType={}, reason={}",
                exception.getClass().getSimpleName(), message);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    }

}
