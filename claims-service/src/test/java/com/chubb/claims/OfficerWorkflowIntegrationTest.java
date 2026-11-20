package com.chubb.claims;

import com.chubb.claims.domain.OutboxEvent;
import com.chubb.claims.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.outbox.publisher-enabled=false",
        "app.kafka.topics-enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class OfficerWorkflowIntegrationTest {

    private static final String MY_MOTOR_POLICY = "c1000000-0000-0000-0000-000000000001";

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
    @Autowired OutboxEventRepository outbox;

    @Test
    void queueShowsIncomingClaimAndOnlyOneOfficerCanPickItUp() throws Exception {
        String claimantToken = login("claimant.my@example.com").get("token").asText();
        String claimId = submitClaim(claimantToken, "Queue pickup test");

        JsonNode officer1 = login("officer.my1@example.com");
        JsonNode officer2 = login("officer.my2@example.com");
        String officer1Token = officer1.get("token").asText();
        String officer1Id = officer1.get("user").get("id").asText();

        mvc.perform(get("/officer/queue")
                        .param("status", "SUBMITTED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(officer1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + claimId + "')]", hasSize(1)));

        mvc.perform(post("/claims/{id}/assign", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officer1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.assignedOfficerId").value(officer1Id));

        mvc.perform(post("/claims/{id}/assign", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officer2.get("token").asText())))
                .andExpect(status().isConflict());

        mvc.perform(get("/officer/queue")
                        .param("status", "SUBMITTED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(officer1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + claimId + "')]", hasSize(0)));

        mvc.perform(get("/officer/me/workload")
                        .header(HttpHeaders.AUTHORIZATION, bearer(officer1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOpen", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.byStatus.UNDER_REVIEW", greaterThanOrEqualTo(1)));
    }

    @Test
    void officerCanRequestInfoAssessApproveAndSettle() throws Exception {
        String claimantToken = login("claimant.my@example.com").get("token").asText();
        String officerToken = login("officer.my1@example.com").get("token").asText();
        String claimId = submitClaim(claimantToken, "Full workflow test");

        mvc.perform(post("/claims/{id}/assign", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken)))
                .andExpect(status().isOk());

        MvcResult infoRequest = mvc.perform(post("/claims/{id}/info-requests", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "question", "Please upload the police report"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.claimId").value(claimId))
                .andReturn();
        String infoRequestId = json.readTree(infoRequest.getResponse().getContentAsString())
                .get("id").asText();

        mvc.perform(get("/claims/{id}", claimId).header(HttpHeaders.AUTHORIZATION, bearer(claimantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INFO_REQUESTED"));

        mvc.perform(post("/claims/{id}/info-requests/{requestId}/response", claimId, infoRequestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(claimantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("response", "Report attached"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"));

        mvc.perform(post("/claims/{id}/assessment", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "notes", "Damage is consistent with the incident",
                                "assessedAmount", 4200.00))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessedAmount").value(4200.00));

        mvc.perform(get("/officer/claims/{id}", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSESSED"))
                .andExpect(jsonPath("$.reserveAmount").value(4200.00))
                .andExpect(jsonPath("$.claimantName").value("Aisha Claimant"))
                .andExpect(jsonPath("$.latestAssessment.assessedAmount").value(4200.00));

        mvc.perform(post("/claims/{id}/decision", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "outcome", "APPROVED",
                                "reason", "Covered by the motor policy"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.settlementAmount").value(4200.00));

        mvc.perform(post("/claims/{id}/settle", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        mvc.perform(get("/claims/{id}", claimId).header(HttpHeaders.AUTHORIZATION, bearer(claimantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        List<OutboxEvent> events = outbox.findByAggregateIdOrderByCreatedAtAsc(UUID.fromString(claimId));
        assertThat(events).extracting(OutboxEvent::getEventType).containsExactly(
                "CLAIM_SUBMITTED",
                "CLAIM_ASSIGNED",
                "INFO_REQUESTED",
                "INFO_PROVIDED",
                "CLAIM_ASSESSED",
                "CLAIM_DECIDED",
                "CLAIM_SETTLED");
        events.forEach(event -> assertThat(event.getPublishedAt()).isNull());

        JsonNode decided = json.readTree(events.stream()
                .filter(event -> event.getEventType().equals("CLAIM_DECIDED"))
                .findFirst()
                .orElseThrow()
                .getPayload());
        assertThat(decided.get("data").get("outcome").asText()).isEqualTo("APPROVED");
        assertThat(decided.get("data").get("settlementAmount").decimalValue())
                .isEqualByComparingTo("4200.00");
    }

    @Test
    void officerCanRejectAnAssessedClaim() throws Exception {
        String claimantToken = login("claimant.my@example.com").get("token").asText();
        String officerToken = login("officer.my1@example.com").get("token").asText();
        String claimId = submitClaim(claimantToken, "Rejection test");

        mvc.perform(post("/claims/{id}/assign", claimId)
                .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))).andExpect(status().isOk());
        mvc.perform(post("/claims/{id}/assessment", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "notes", "No cover for this peril",
                                "assessedAmount", 0))))
                .andExpect(status().isOk());

        mvc.perform(post("/claims/{id}/decision", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "outcome", "REJECTED",
                                "reason", "Excluded peril"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.settlementAmount").value(nullValue()));

        mvc.perform(get("/claims/{id}", claimId).header(HttpHeaders.AUTHORIZATION, bearer(claimantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
    }

    @Test
    void officerCannotActOnUnassignedOrOtherOfficersClaims() throws Exception {
        String claimantToken = login("claimant.my@example.com").get("token").asText();
        String officer1Token = login("officer.my1@example.com").get("token").asText();
        String officer2Token = login("officer.my2@example.com").get("token").asText();
        String claimId = submitClaim(claimantToken, "Assignment guard test");

        mvc.perform(post("/claims/{id}/assessment", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officer2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "notes", "Trying before pickup",
                                "assessedAmount", 100))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/claims/{id}/assign", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officer1Token)))
                .andExpect(status().isOk());

        mvc.perform(post("/claims/{id}/info-requests", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officer2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("question", "Not my claim"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleBoundariesBetweenClaimantAndOfficerAreEnforced() throws Exception {
        String claimantToken = login("claimant.my@example.com").get("token").asText();
        String officerToken = login("officer.my1@example.com").get("token").asText();
        String claimId = submitClaim(claimantToken, "Role boundary test");

        mvc.perform(get("/officer/queue").header(HttpHeaders.AUTHORIZATION, bearer(claimantToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/claims/{id}/assign", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(claimantToken)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/officer/queue")
                        .param("market", "SG")
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/claims/{id}", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/officer/claims/{id}", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void assessmentBeforeReviewIsRejected() throws Exception {
        String claimantToken = login("claimant.my@example.com").get("token").asText();
        String officerToken = login("officer.my1@example.com").get("token").asText();
        String claimId = submitClaim(claimantToken, "Info guard test");

        mvc.perform(post("/claims/{id}/assign", claimId)
                .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))).andExpect(status().isOk());
        mvc.perform(post("/claims/{id}/info-requests", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("question", "Need more detail"))))
                .andExpect(status().isOk());

        mvc.perform(post("/claims/{id}/assessment", claimId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(officerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "notes", "Should not work yet",
                                "assessedAmount", 100))))
                .andExpect(status().isConflict());
    }

    private JsonNode login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password123!"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("token").asText()).isNotBlank();
        return body;
    }

    private String submitClaim(String token, String description) throws Exception {
        MvcResult result = mvc.perform(post("/claims")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "policyId", MY_MOTOR_POLICY,
                                "incidentDate", "2026-11-01",
                                "description", description,
                                "incidentLocation", "Kuala Lumpur",
                                "claimedAmount", 4500.00,
                                "incidentDetails", Map.of("vehiclePlate", "WXY1234")))))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
