package com.realteeh.api.job.presentation;

import com.realteeh.api.job.application.dto.JobCreateResult;
import com.realteeh.api.job.application.dto.JobDetailResult;
import com.realteeh.api.job.application.dto.JobSummaryResult;
import com.realteeh.api.job.application.service.JobCommandService;
import com.realteeh.api.job.application.service.JobQueryService;
import com.realteeh.api.job.domain.JobStatus;
import com.realteeh.api.job.presentation.dto.JobDetailResponse;
import com.realteeh.api.job.presentation.dto.JobSimpleResponse;
import com.realteeh.api.job.presentation.dto.JobPageResponse;
import com.realteeh.api.job.presentation.dto.JobRequest;
import com.realteeh.api.job.presentation.dto.JobResponseMapper;
import com.realteeh.api.job.presentation.spec.JobV1ApiSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@Validated
@RequiredArgsConstructor
public class JobV1ApiController implements JobV1ApiSpec {

    private final JobCommandService jobCommandService;
    private final JobQueryService jobQueryService;

    @Override
    @PostMapping
    public ResponseEntity<JobSimpleResponse> createJob(
            @Valid @RequestBody final JobRequest request,
            @Size(max = 128, message = "멱등키 형식이 올바르지 않습니다.")
            @RequestHeader(name = "X-Idempotency-Key", required = false) final String idempotencyKey
    ) {
        final JobCreateResult result = jobCommandService.create(request.imageUrl(), idempotencyKey);
        final HttpStatus responseStatus = result.createdNew() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(responseStatus).body(JobResponseMapper.toSimpleResponse(result));
    }

    @Override
    @GetMapping("/{jobId}")
    public ResponseEntity<JobDetailResponse> getJob(@PathVariable final UUID jobId) {
        final JobDetailResult found = jobQueryService.getOne(jobId);
        return ResponseEntity.ok(JobResponseMapper.toDetailResponse(found));
    }

    @Override
    @GetMapping
    public ResponseEntity<JobPageResponse> getJobs(
            @Min(value = 0, message = "page는 0 이상이어야 합니다.")
            @RequestParam(name = "page", defaultValue = "0") final int page,
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.")
            @RequestParam(name = "size", defaultValue = "20") final int size,
            @RequestParam(name = "status", required = false) final JobStatus status
    ) {
        final Page<JobSummaryResult> jobs = jobQueryService.getList(page, size, status);
        return ResponseEntity.ok(JobResponseMapper.toPageResponse(jobs));
    }
}
