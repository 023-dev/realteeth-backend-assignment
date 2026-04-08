package com.realteeh.api.job.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record JobRequest(
        @NotBlank(message = "imageUrl은 필수입니다.")
        @Size(max = 1024, message = "imageUrl 길이는 1024자를 초과할 수 없습니다.")
        @URL(message = "imageUrl 형식이 올바르지 않습니다.")
        String imageUrl
) {
}
