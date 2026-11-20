package com.chubb.claims.event;

import java.util.Collection;
import java.util.Map;

/** Kafka topic for each domain event. Unknown types (e.g. document upload) are timeline-only. */
public final class ClaimEventTopics {

    public static final String CLAIM_SUBMITTED = "claim.submitted";
    public static final String CLAIM_ASSIGNED = "claim.assigned";
    public static final String INFO_REQUESTED = "claim.info-requested";
    public static final String INFO_PROVIDED = "claim.info-provided";
    public static final String CLAIM_ASSESSED = "claim.assessed";
    public static final String CLAIM_DECIDED = "claim.decided";
    public static final String CLAIM_SETTLED = "claim.settled";

    private static final Map<String, String> TOPIC_BY_EVENT_TYPE = Map.of(
            ClaimEventTypes.CLAIM_SUBMITTED, CLAIM_SUBMITTED,
            ClaimEventTypes.CLAIM_ASSIGNED, CLAIM_ASSIGNED,
            ClaimEventTypes.INFO_REQUESTED, INFO_REQUESTED,
            ClaimEventTypes.INFO_PROVIDED, INFO_PROVIDED,
            ClaimEventTypes.CLAIM_ASSESSED, CLAIM_ASSESSED,
            ClaimEventTypes.CLAIM_DECIDED, CLAIM_DECIDED,
            ClaimEventTypes.CLAIM_SETTLED, CLAIM_SETTLED);

    private ClaimEventTopics() {
    }

    public static String topicFor(String eventType) {
        return TOPIC_BY_EVENT_TYPE.get(eventType);
    }

    public static Collection<String> all() {
        return TOPIC_BY_EVENT_TYPE.values();
    }
}
