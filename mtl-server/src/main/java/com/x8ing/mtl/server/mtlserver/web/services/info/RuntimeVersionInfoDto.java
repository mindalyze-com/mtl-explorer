package com.x8ing.mtl.server.mtlserver.web.services.info;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"java", "postgresql", "postgis"})
public record RuntimeVersionInfoDto(String java, String postgresql, String postgis) {
}
