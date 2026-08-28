package com.tinyurl.dto;

import java.time.LocalDate;

public record DailyRedirectCount(LocalDate date, long count) {
}
