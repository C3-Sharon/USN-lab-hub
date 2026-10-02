package com.usn.labhub.user.project;

import java.util.Map;
import java.util.Set;

public final class TaskStateMachine {

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "TODO", Set.of("IN_PROGRESS", "CANCELED"),
            "IN_PROGRESS", Set.of("BLOCKED", "DONE", "CANCELED"),
            "BLOCKED", Set.of("IN_PROGRESS", "CANCELED"),
            "DONE", Set.of(),
            "CANCELED", Set.of());

    private TaskStateMachine() {
    }

    public static boolean canTransition(String currentStatus, String targetStatus) {
        return TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(targetStatus);
    }
}
