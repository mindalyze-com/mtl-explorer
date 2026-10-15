package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.LocalDate;

@JsonPropertyOrder({"startDate", "endDate"})
public record GHReloadRequest(LocalDate startDate, LocalDate endDate) {
}
