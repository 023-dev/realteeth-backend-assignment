package com.realteeh.api.job.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.realteeh.api.job.application.dto.JobDetailResult;
import com.realteeh.api.job.application.exception.JobNotFoundException;
import com.realteeh.api.job.application.port.JobReadRepository;
import com.realteeh.api.job.domain.Job;
import com.realteeh.api.job.domain.JobStatus;
import com.realteeh.api.job.domain.repository.JobRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobQueryServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobReadRepository jobReadRepository;

    private JobQueryService jobQueryService;

    @BeforeEach
    void setUp() {
        jobQueryService = new JobQueryService(jobRepository, jobReadRepository);
    }

    @Test
    void 단건_조회는_DB에서_작업을_읽어_응답_DTO로_반환한다() {
        final UUID jobId = UUID.fromString("00000000-0000-0000-0000-000000000660");
        final Job existing = completedJob(jobId, "https://example.com/completed.png");
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(existing));

        final JobDetailResult result = jobQueryService.getOne(jobId);

        assertThat(result.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(result.result()).isEqualTo("done");
        assertThat(result.errorMessage()).isNull();
        assertThat(result.retryCount()).isZero();
        assertThat(result.imageUrl()).isEqualTo("https://example.com/completed.png");
        assertThat(result.updatedAt()).isEqualTo(LocalDateTime.of(2026, 4, 8, 9, 2));
    }

    @Test
    void 존재하지_않는_작업_조회는_예외를_던진다() {
        final UUID jobId = UUID.fromString("00000000-0000-0000-0000-000000000661");
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobQueryService.getOne(jobId))
                .isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void 상태_필터가_없으면_전체_조회_쿼리를_사용한다() {
        final PageRequest expectedPageRequest = PageRequest.of(
                0,
                20,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
        when(jobReadRepository.findAll(expectedPageRequest)).thenReturn(new PageImpl<>(java.util.List.of()));

        final var page = jobQueryService.getList(0, 20, null);

        assertThat(page.getContent()).isEmpty();
        verify(jobReadRepository).findAll(expectedPageRequest);
    }

    @Test
    void 상태_필터가_있으면_해당_쿼리로_조회한다() {
        final PageRequest expectedPageRequest = PageRequest.of(
                0,
                20,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
        when(jobReadRepository.findAllByStatus(JobStatus.PENDING, expectedPageRequest))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        final var page = jobQueryService.getList(0, 20, JobStatus.PENDING);

        assertThat(page.getContent()).isEmpty();
        verify(jobReadRepository).findAllByStatus(JobStatus.PENDING, expectedPageRequest);
    }

    @Test
    void 페이지가_달라도_동일한_기본_정렬_규칙을_유지한다() {
        final PageRequest expectedPageRequest = PageRequest.of(
                1,
                2,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
        when(jobReadRepository.findAll(expectedPageRequest)).thenReturn(new PageImpl<>(java.util.List.of()));

        final var page = jobQueryService.getList(1, 2, null);

        assertThat(page.getContent()).isEmpty();
        verify(jobReadRepository).findAll(expectedPageRequest);
    }

    private Job completedJob(
            final UUID jobId,
            final String imageUrl
    ) {
        final Job job = Job.create(imageUrl, null);
        ReflectionTestUtils.setField(job, "id", jobId);
        ReflectionTestUtils.setField(job, "createdAt", LocalDateTime.of(2026, 4, 8, 9, 0));
        ReflectionTestUtils.setField(job, "updatedAt", LocalDateTime.of(2026, 4, 8, 9, 1));
        job.markProcessing("worker-completed");
        job.markCompleted("done");
        ReflectionTestUtils.setField(job, "updatedAt", LocalDateTime.of(2026, 4, 8, 9, 2));
        return job;
    }
}
