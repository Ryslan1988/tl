package ru.ruslan.teamleadgame.model;

import java.util.List;

public record WorkScenario(
        String id,
        String type,
        String title,
        String description,
        String employeeMessage,
        List<WorkOption> options
) {
    public record WorkOption(
            String id,
            String title,
            String description,
            String consequence,
            int deliveryDelta,
            int reliabilityDelta,
            int teamDelta,
            int leadershipDelta,
            String employeeReaction
    ) {
        public int totalScore() {
            return deliveryDelta + reliabilityDelta + teamDelta + leadershipDelta;
        }
    }
}
