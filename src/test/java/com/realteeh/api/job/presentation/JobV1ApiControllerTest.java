package com.realteeh.api.job.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realteeh.api.job.infrastructure.persistence.JobJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class JobV1ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobJpaRepository jobJpaRepository;

    @AfterEach
    void tearDown() {
        jobJpaRepository.deleteAll();
    }

    @Test
    void 동일한_멱등성_키_요청은_기존_작업을_반환한다() throws Exception {
        final String requestBody = """
                {
                  "imageUrl": "https://example.com/image.png"
                }
                """;

        final MvcResult createdResult = mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "same-key")
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn();

        final JsonNode createdBody = objectMapper.readTree(createdResult.getResponse().getContentAsString());
        final String firstJobId = createdBody.get("jobId").asText();

        final MvcResult duplicateResult = mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "same-key")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        final JsonNode duplicateBody = objectMapper.readTree(duplicateResult.getResponse().getContentAsString());
        assertThat(duplicateBody.get("jobId").asText()).isEqualTo(firstJobId);
    }

    @Test
    void 같은_멱등키에_다른_payload면_409를_반환한다() throws Exception {
        final String firstRequestBody = """
                {
                  "imageUrl": "https://example.com/image.png"
                }
                """;
        final String secondRequestBody = """
                {
                  "imageUrl": "https://example.com/other-image.png"
                }
                """;

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "conflict-key")
                        .content(firstRequestBody))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "conflict-key")
                        .content(secondRequestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void 생성_후_단건_조회와_목록_조회가_가능하다() throws Exception {
        final String requestBody = """
                {
                  "imageUrl": "https://example.com/image2.png"
                }
                """;

        final MvcResult createdResult = mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn();

        final JsonNode createdBody = objectMapper.readTree(createdResult.getResponse().getContentAsString());
        final String jobId = createdBody.get("jobId").asText();

        final MvcResult getOneResult = mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andReturn();
        final JsonNode oneBody = objectMapper.readTree(getOneResult.getResponse().getContentAsString());
        assertThat(oneBody.get("jobId").asText()).isEqualTo(jobId);
        assertThat(oneBody.get("status").asText()).isEqualTo("PENDING");
        assertThat(oneBody.has("result")).isTrue();
        assertThat(oneBody.has("errorMessage")).isTrue();
        assertThat(oneBody.has("retryCount")).isTrue();
        assertThat(oneBody.has("updatedAt")).isTrue();
        assertThat(oneBody.get("retryCount").asInt()).isEqualTo(0);

        final MvcResult listResult = mockMvc.perform(get("/api/v1/jobs")
                        .param("page", "0")
                        .param("size", "20")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andReturn();
        final JsonNode listBody = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(listBody.get("content").isArray()).isTrue();
        assertThat(listBody.get("totalElements").asLong()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void 잘못된_URL_요청은_검증_실패를_반환한다() throws Exception {
        final String invalidBody = """
                {
                  "imageUrl": "not-url"
                }
                """;

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 잘못된_JSON_본문은_검증_실패를_반환한다() throws Exception {
        final String malformedBody = """
                {
                  "imageUrl": "https://example.com/image5.png"
                """;

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("요청 본문 형식이 올바르지 않습니다."));
    }

    @Test
    void 잘못된_content_type_요청은_검증_실패를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain-text-body"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("요청 Content-Type이 올바르지 않습니다."));
    }

    @Test
    void 존재하지_않는_jobId_조회는_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/{jobId}", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void 공백_멱등키는_무시되어_새로운_작업을_생성한다() throws Exception {
        final String requestBody = """
                {
                  "imageUrl": "https://example.com/image3.png"
                }
                """;

        final MvcResult firstResult = mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "   ")
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn();

        final MvcResult secondResult = mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "   ")
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andReturn();

        final String firstJobId = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("jobId").asText();
        final String secondJobId = objectMapper.readTree(secondResult.getResponse().getContentAsString()).get("jobId").asText();

        assertThat(firstJobId).isNotEqualTo(secondJobId);
    }

    @Test
    void 잘못된_status_필터는_검증_실패를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 멱등키가_너무_길면_검증_실패를_반환한다() throws Exception {
        final String requestBody = """
                {
                  "imageUrl": "https://example.com/image4.png"
                }
                """;
        final String longKey = "k".repeat(129);
        assertThatCode(() -> mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", longKey)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")))
                .doesNotThrowAnyException();
    }

    @Test
    void size가_최대_범위를_초과하면_검증_실패를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("size는 100 이하여야 합니다."));
    }
}
