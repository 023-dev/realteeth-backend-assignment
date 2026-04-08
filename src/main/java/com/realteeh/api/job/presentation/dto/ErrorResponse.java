package com.realteeh.api.job.presentation.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record ErrorResponse(
        String code,
        String message,
        String timestamp
) {
    public static ErrorResponse of(
            final String code,
            final String message
    ) {
        return new ErrorResponse(
                code,
                message,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
}
