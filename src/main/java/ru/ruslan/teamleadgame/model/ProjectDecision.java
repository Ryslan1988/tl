package ru.ruslan.teamleadgame.model;

import java.util.List;

public record ProjectDecision(
        String id,
        String title,
        String description,
        List<ProjectOption> options
) {
    public record ProjectOption(String id, String title, String consequence, int score) {
    }
}
