package ru.ruslan.teamleadgame.dto;

import jakarta.validation.constraints.NotBlank;
import ru.ruslan.teamleadgame.model.Candidate;
import ru.ruslan.teamleadgame.model.GameStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GameDtos {
    private GameDtos() {}

    public record StartGameResponse(UUID sessionId, GameStatus status, int totalRounds,
                                    List<CandidateView> candidates, String actionText) {}
    public record CandidateView(String id, String name, String role, String avatar, String personality) {
        public static CandidateView from(Candidate c) { return new CandidateView(c.id(), c.name(), c.role(), c.avatar(), c.personality()); }
    }
    public record AskQuestionResponse(GameStatus status, QuestionView question) {}
    public record QuestionView(int round, int totalRounds, String id, String category, String text, String hint, List<AnswerView> answers) {}
    public record AnswerView(String candidateId, String text) {}
    public record SelectAnswerRequest(@NotBlank String candidateId) {}
    public record AnswerResult(boolean correct, String selectedCandidateId, String correctCandidateId, String reaction,
                               GameStatus status, boolean canAskNextQuestion, Map<String, Integer> selectionStats) {}
    public record InterviewSummary(UUID sessionId, GameStatus status, int correctAnswers, int totalRounds,
                                   Map<String, Integer> selectionStats, List<CandidateResult> candidates) {}
    public record CandidateResult(String id, String name, int realSkill, int selectedTimes, int correctAnswersGiven, String verdict) {}
    public record HireRequest(@NotBlank String candidateId) {}
    public record HireResult(String hiredCandidateId, String hiredCandidateName, String message, GameStatus status,
                             FinalResult interviewResult, boolean workChapterAvailable, int workScenarioCount) {}

    public record WorkScenarioResponse(GameStatus status, int scenarioNumber, int totalScenarios,
                                       WorkScenarioView scenario, WorkMetrics metrics) {}
    public record WorkScenarioView(String id, String type, String title, String description,
                                   String employeeMessage, List<WorkOptionView> options) {}
    public record WorkOptionView(String id, String title, String description) {}
    public record WorkDecisionRequest(@NotBlank String optionId) {}
    public record WorkDecisionResult(boolean positive, String selectedOptionId, String consequence,
                                     String employeeReaction, ScoreDelta delta, WorkMetrics metrics,
                                     GameStatus status, boolean finished, boolean hasNextScenario) {}
    public record ScoreDelta(int delivery, int reliability, int teamHealth, int leadership, int total) {}
    public record WorkMetrics(int delivery, int reliability, int teamHealth, int leadership, int overall) {}

    public record FinalResult(String hiredCandidateId, String hiredCandidateName, int hiringAccuracy,
                              int selectedTimes, int correctAnswersGiven, String hiringVerdict, String analysis,
                              List<String> strengths, List<String> risks, List<String> learningRoadmap) {}
    public record CareerResult(FinalResult interview, WorkMetrics metrics, String leadershipGrade,
                               String leadershipVerdict, List<WorkHistoryItem> history) {}
    public record WorkHistoryItem(int round, String scenarioId, String optionId, int score, String consequence) {}
}
