package ru.ruslan.teamleadgame.model;

import java.util.List;

public record InterviewQuestion(
        String id,
        String category,
        String text,
        String hint,
        List<CandidateAnswer> answers
) {
}
