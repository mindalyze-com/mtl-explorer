package com.x8ing.mtl.server.mtlserver.web.services.admin;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"items", "page", "size", "totalElements", "totalPages"})
public record SystemLogPage(List<SystemLogSummary> items, int page, int size, long totalElements, int totalPages) {
}
