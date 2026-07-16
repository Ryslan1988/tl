package ru.ruslan.teamleadgame.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GameSession {
    private final UUID id;
    private final Instant createdAt;
    private final List<InterviewQuestion> questions;
    private final Map<String, Integer> candidateSelections = new HashMap<>();
    private final List<RoundResult> roundResults = new ArrayList<>();
    private final List<WorkResult> workResults = new ArrayList<>();
    private int currentRound;
    private int currentWorkScenario;
    private int delivery = 50;
    private int reliability = 50;
    private int teamHealth = 50;
    private int leadership = 50;
    private GameStatus status = GameStatus.WAITING_FOR_QUESTION;
    private String hiredCandidateId;

    public GameSession(UUID id, List<InterviewQuestion> questions, List<Candidate> candidates) {
        this.id = id;
        this.createdAt = Instant.now();
        this.questions = List.copyOf(questions);
        candidates.forEach(candidate -> candidateSelections.put(candidate.id(), 0));
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public List<InterviewQuestion> getQuestions() { return questions; }
    public Map<String, Integer> getCandidateSelections() { return candidateSelections; }
    public List<RoundResult> getRoundResults() { return roundResults; }
    public List<WorkResult> getWorkResults() { return workResults; }
    public int getCurrentRound() { return currentRound; }
    public int getCurrentWorkScenario() { return currentWorkScenario; }
    public int getDelivery() { return delivery; }
    public int getReliability() { return reliability; }
    public int getTeamHealth() { return teamHealth; }
    public int getLeadership() { return leadership; }
    public GameStatus getStatus() { return status; }
    public String getHiredCandidateId() { return hiredCandidateId; }

    public InterviewQuestion currentQuestion() {
        return currentRound < questions.size() ? questions.get(currentRound) : null;
    }

    public void askQuestion() {
        if (status != GameStatus.WAITING_FOR_QUESTION) {
            throw new IllegalStateException("Question cannot be requested in state " + status);
        }
        status = GameStatus.INTERVIEW;
    }

    public void registerAnswer(String candidateId, boolean correct, String correctCandidateId) {
        candidateSelections.computeIfPresent(candidateId, (key, value) -> value + 1);
        roundResults.add(new RoundResult(currentRound + 1, candidateId, correctCandidateId, correct));
        currentRound++;
        status = currentRound >= questions.size()
                ? GameStatus.READY_TO_HIRE
                : GameStatus.WAITING_FOR_QUESTION;
    }

    public void hire(String candidateId) {
        this.hiredCandidateId = candidateId;
        this.status = GameStatus.EMPLOYEE_HIRED;
    }

    public void openWorkScenario() {
        if (status != GameStatus.EMPLOYEE_HIRED) {
            throw new IllegalStateException("Work scenario cannot be opened in state " + status);
        }
        status = GameStatus.WORK_SCENARIO;
    }

    public void applyWorkDecision(String scenarioId, WorkScenario.WorkOption option, int totalScenarios) {
        delivery = bounded(delivery + option.deliveryDelta());
        reliability = bounded(reliability + option.reliabilityDelta());
        teamHealth = bounded(teamHealth + option.teamDelta());
        leadership = bounded(leadership + option.leadershipDelta());
        workResults.add(new WorkResult(
                currentWorkScenario + 1,
                scenarioId,
                option.id(),
                option.totalScore(),
                option.consequence()
        ));
        currentWorkScenario++;
        status = currentWorkScenario >= totalScenarios
                ? GameStatus.FINISHED
                : GameStatus.EMPLOYEE_HIRED;
    }

    private int bounded(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public record RoundResult(int round, String selectedCandidateId, String correctCandidateId, boolean correct) {}

    public record WorkResult(int round, String scenarioId, String optionId, int score, String consequence) {}
}
