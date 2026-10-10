package com.usn.labhub.user.learning;

import java.util.Map;
import java.util.Set;

public final class LearningRoadmapStateMachine {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "DRAFT", Set.of("PUBLISHED", "ARCHIVED"),
            "PUBLISHED", Set.of("ARCHIVED"),
            "ARCHIVED", Set.of());

    private LearningRoadmapStateMachine() {
    }

    public static boolean canTransition(String currentStatus, String targetStatus) {
        return TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(targetStatus);
    }
}
