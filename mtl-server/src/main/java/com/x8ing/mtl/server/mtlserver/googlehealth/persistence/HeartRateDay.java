package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import java.time.LocalDate;

public record HeartRateDay(LocalDate date, double average, double minimum, double maximum) {
}
