package com.usn.labhub.user.project;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskStateMachineTest {

    @Test
    void implementsFrozenTransitionMatrixAndTerminalStates() {
        Set<String> statuses = Set.of("TODO", "IN_PROGRESS", "BLOCKED", "DONE", "CANCELED");
        Set<String> allowed = Set.of(
                "TODO->IN_PROGRESS", "TODO->CANCELED",
                "IN_PROGRESS->BLOCKED", "IN_PROGRESS->DONE", "IN_PROGRESS->CANCELED",
                "BLOCKED->IN_PROGRESS", "BLOCKED->CANCELED");

        for (String current : statuses) {
            for (String target : statuses) {
                assertEquals(allowed.contains(current + "->" + target),
                        TaskStateMachine.canTransition(current, target), current + " -> " + target);
            }
        }
    }
}
