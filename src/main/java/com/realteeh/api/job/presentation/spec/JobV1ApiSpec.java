package com.realteeh.api.job.presentation.spec;

import com.realteeh.api.job.domain.JobStatus;
import com.realteeh.api.job.presentation.dto.JobDetailResponse;
import com.realteeh.api.job.presentation.dto.JobSimpleResponse;
import com.realteeh.api.job.presentation.dto.JobPageResponse;
import com.realteeh.api.job.presentation.dto.JobRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "이미지 처리 Job 관리를 위한 API")
public interface JobV1ApiSpec {

    @Operation(summary = "이미지 처리 Job 생성 API", description = "이미지 처리 Job을 생성하는 API입니다. 요청이 성공하면 생성된 Job의 정보를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Job 생성 성공"),
            @ApiResponse(responseCode = "200", description = "이미 존재하는 Job이 있어 해당 Job 정보 반환"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터", content = @Content(examples = {
                    @ExampleObject(name = "잘못된 요청 예시")
            })),
    })
    ResponseEntity<JobSimpleResponse> createJob(
            @Valid @RequestBody JobRequest request,
            @Size(max = 128, message = "멱등키 형식이 올바르지 않습니다.")
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKey
    );

    @Operation(summary = "Job 단건 조회 API", description = "Job ID로 상태와 결과를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 Job"),
    })
    ResponseEntity<JobDetailResponse> getJob(
            @PathVariable("jobId") UUID jobId
    );

    @Operation(summary = "Job 목록 조회 API", description = "페이지/상태 필터 조건으로 작업 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
    })
    ResponseEntity<JobPageResponse> getJobs(
            @Min(value = 0, message = "page는 0 이상이어야 합니다.")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.")
            @RequestParam(name = "size", defaultValue = "20") int size,
            @Parameter(description = "상태 필터")
            @RequestParam(name = "status", required = false) JobStatus status
    );
}
