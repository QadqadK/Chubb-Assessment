package com.chubb.claims;

import com.chubb.claims.domain.Claim;
import com.chubb.claims.domain.ClaimStatus;
import com.chubb.claims.domain.InfoRequest;
import com.chubb.claims.domain.OutboxEvent;
import com.chubb.claims.repository.ClaimRepository;
import com.chubb.claims.repository.InfoRequestRepository;
import com.chubb.claims.repository.OutboxEventRepository;
import com.chubb.claims.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.outbox.publisher-enabled=false",
        "app.kafka.topics-enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class ClaimantApiIntegrationTest {

    private static final String MY_MOTOR_POLICY = "c1000000-0000-0000-0000-000000000001";
    private static final String MY_PROPERTY_POLICY = "c1000000-0000-0000-0000-000000000002";
    private static final String SG_MOTOR_POLICY = "c1000000-0000-0000-0000-000000000003";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void uploads(DynamicPropertyRegistry registry) {
        registry.add("app.storage.upload-dir",
                () -> Path.of(System.getProperty("java.io.tmpdir"), "chubb-test-uploads").toString());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClaimRepository claims;
    @Autowired InfoRequestRepository infoRequests;
    @Autowired UserRepository users;
    @Autowired OutboxEventRepository outbox;

    @Test
    void loginRejectsWrongPassword() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "claimant.my@example.com",
                                "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void claimantCanSubmitListAndTrackOwnClaim() throws Exception {
        String token = login("claimant.my@example.com");

        MvcResult created = submit(token, MY_MOTOR_POLICY, "2026-11-01", "Rear-ended at traffic light");
        JsonNode body = json.readTree(created.getResponse().getContentAsString());
        String claimId = body.get("id").asText();

        assertThat(body.get("reference").asText()).startsWith("CLM-MY-");
        assertThat(body.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(body.get("currency").asText()).isEqualTo("MYR");

        mvc.perform(get("/claims").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + claimId + "')]", hasSize(1)));

        mvc.perform(get("/claims/{id}", claimId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.timeline[0].eventType").value("CLAIM_SUBMITTED"))
                .andExpect(jsonPath("$.timeline[0].toStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.infoRequests").isEmpty())
                .andExpect(jsonPath("$.documents").isEmpty());
    }

    @Test
    void submittingAClaimWritesAnUnpublishedOutboxEvent() throws Exception {
        String token = login("claimant.my@example.com");
        String claimId = json.readTree(submit(token, MY_MOTOR_POLICY, "2026-11-01", "Outbox test")
                .getResponse().getContentAsString()).get("id").asText();

        List<OutboxEvent> rows = outbox.findByAggregateIdOrderByCreatedAtAsc(UUID.fromString(claimId));

        assertThat(rows).hasSize(1);
        OutboxEvent row = rows.get(0);
        assertThat(row.getEventType()).isEqualTo("CLAIM_SUBMITTED");
        assertThat(row.getPublishedAt()).isNull();

        JsonNode payload = json.readTree(row.getPayload());
        assertThat(payload.get("eventId").asText()).isNotBlank();
        assertThat(payload.get("eventType").asText()).isEqualTo("CLAIM_SUBMITTED");
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(payload.get("claimId").asText()).isEqualTo(claimId);
        assertThat(payload.get("reference").asText()).startsWith("CLM-MY-");
        assertThat(payload.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(payload.get("data").get("policyId").asText()).isEqualTo(MY_MOTOR_POLICY);
    }

    @Test
    void claimDetailRequiresAuthentication() throws Exception {
        mvc.perform(get("/claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void claimantCannotReadAnotherClaimantsClaim() throws Exception {
        String sgToken = login("claimant.sg@example.com");
        String sgClaimId = json.readTree(submit(sgToken, SG_MOTOR_POLICY, "2026-11-01", "Stolen wing mirror")
                .getResponse().getContentAsString()).get("id").asText();

        String myToken = login("claimant.my@example.com");
        mvc.perform(get("/claims/{id}", sgClaimId).header(HttpHeaders.AUTHORIZATION, bearer(myToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimantCannotSubmitAgainstSomeoneElsesPolicy() throws Exception {
        String token = login("claimant.my@example.com");

        mvc.perform(post("/claims")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody(SG_MOTOR_POLICY, "2026-11-01", "Not my policy")))
                .andExpect(status().isForbidden());
    }

    @Test
    void incidentDateOutsidePolicyPeriodIsRejected() throws Exception {
        String token = login("claimant.my@example.com");

        mvc.perform(post("/claims")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody(MY_MOTOR_POLICY, "2025-06-01", "Too early")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void officerCannotUseClaimantEndpoints() throws Exception {
        String officerToken = login("officer.my1@example.com");

        mvc.perform(get("/claims").header(HttpHeaders.AUTHORIZATION, bearer(officerToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/claims")
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody(MY_MOTOR_POLICY, "2026-11-01", "Officer attempt")))
                .andExpect(status().isForbidden());
    }

    @Test
    void answeringTheLastInfoRequestReturnsClaimToReview() throws Exception {
        String token = login("claimant.my@example.com");
        String claimId = json.readTree(submit(token, MY_PROPERTY_POLICY, "2026-11-01", "Burst pipe")
                .getResponse().getContentAsString()).get("id").asText();

        Claim claim = claims.findById(java.util.UUID.fromString(claimId)).orElseThrow();
        claim.transitionTo(ClaimStatus.UNDER_REVIEW);
        claim.transitionTo(ClaimStatus.INFO_REQUESTED);
        claims.saveAndFlush(claim);

        InfoRequest request = new InfoRequest();
        request.setClaimId(claim.getId());
        request.setRequestedBy(users.findByEmail("officer.my1@example.com").orElseThrow().getId());
        request.setQuestion("Please provide the plumber's report");
        request = infoRequests.saveAndFlush(request);

        mvc.perform(post("/claims/{claimId}/info-requests/{requestId}/response", claimId, request.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("response", "Report attached"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.response").value("Report attached"));

        mvc.perform(get("/claims/{id}", claimId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.timeline[?(@.eventType == 'INFO_PROVIDED')]", hasSize(1)));
    }

    @Test
    void claimantCanUploadADocumentAndSeeItOnTheTimeline() throws Exception {
        String token = login("claimant.my@example.com");
        String claimId = json.readTree(submit(token, MY_MOTOR_POLICY, "2026-11-02", "Scratched bumper")
                .getResponse().getContentAsString()).get("id").asText();

        MockMultipartFile file = new MockMultipartFile(
                "file", "repair-quote.pdf", "application/pdf", "quote".getBytes());

        mvc.perform(multipart("/claims/{id}/documents", claimId)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("repair-quote.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(5));

        mvc.perform(get("/claims/{id}", claimId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].fileName").value("repair-quote.pdf"))
                .andExpect(jsonPath("$.timeline[?(@.eventType == 'DOCUMENT_UPLOADED')]", hasSize(1)));
    }

    private String login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password123!"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        return body.get("token").asText();
    }

    private MvcResult submit(String token, String policyId, String incidentDate, String description)
            throws Exception {
        return mvc.perform(post("/claims")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimBody(policyId, incidentDate, description)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String claimBody(String policyId, String incidentDate, String description) throws Exception {
        return json.writeValueAsString(Map.of(
                "policyId", policyId,
                "incidentDate", incidentDate,
                "description", description,
                "incidentLocation", "Kuala Lumpur",
                "claimedAmount", 4500.00,
                "incidentDetails", Map.of("vehiclePlate", "WXY1234")));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
