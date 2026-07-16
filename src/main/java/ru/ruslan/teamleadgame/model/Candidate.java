package ru.ruslan.teamleadgame.model;

import java.util.List;

public record Candidate(
        String id,
        String name,
        String role,
        String avatar,
        String personality,
        int realSkill,
        int correctAnswerWeight,
        List<String> strengths,
        List<String> gaps,
        List<String> roadmap
) {
}
