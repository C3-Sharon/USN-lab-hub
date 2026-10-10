package com.usn.labhub.user.learning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningRoadmapStateMachineTest {

    @Test
    void allowsOnlyForwardTransitions() {
        assertTrue(LearningRoadmapStateMachine.canTransition("DRAFT", "PUBLISHED"));
        assertTrue(LearningRoadmapStateMachine.canTransition("DRAFT", "ARCHIVED"));
        assertTrue(LearningRoadmapStateMachine.canTransition("PUBLISHED", "ARCHIVED"));

        assertFalse(LearningRoadmapStateMachine.canTransition("PUBLISHED", "DRAFT"));
        assertFalse(LearningRoadmapStateMachine.canTransition("ARCHIVED", "PUBLISHED"));
        assertFalse(LearningRoadmapStateMachine.canTransition("DRAFT", "DRAFT"));
        assertFalse(LearningRoadmapStateMachine.canTransition("UNKNOWN", "PUBLISHED"));
    }
}
