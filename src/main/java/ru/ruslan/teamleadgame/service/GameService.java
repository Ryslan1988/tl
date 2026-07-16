package ru.ruslan.teamleadgame.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.ruslan.teamleadgame.dto.GameDtos.*;
import ru.ruslan.teamleadgame.exception.GameException;
import ru.ruslan.teamleadgame.model.*;
import ru.ruslan.teamleadgame.repository.GameAuditRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {
    private final GameContent content;
    private final GameAuditRepository auditRepository;
    private final Map<UUID, GameSession> sessions = new ConcurrentHashMap<>();

    public GameService(GameContent content, GameAuditRepository auditRepository) {
        this.content = content;
        this.auditRepository = auditRepository;
    }

    public StartGameResponse start() {
        GameSession session = new GameSession(UUID.randomUUID(), content.questions(), content.candidates());
        sessions.put(session.getId(), session);
        auditRepository.save(session.getId(), "GAME_STARTED", "rounds=" + session.getQuestions().size());
        return new StartGameResponse(
                session.getId(),
                session.getStatus(),
                session.getQuestions().size(),
                content.candidates().stream().map(CandidateView::from).toList(),
                "Задать вопрос"
        );
    }

    public AskQuestionResponse askQuestion(UUID sessionId) {
        GameSession session = session(sessionId);
        requireStatus(session, GameStatus.WAITING_FOR_QUESTION);
        session.askQuestion();
        auditRepository.save(session.getId(), "QUESTION_ASKED", "round=" + (session.getCurrentRound() + 1));
        return new AskQuestionResponse(session.getStatus(), questionView(session));
    }

    public AnswerResult selectAnswer(UUID sessionId, SelectAnswerRequest request) {
        GameSession session = session(sessionId);
        requireStatus(session, GameStatus.INTERVIEW);
        InterviewQuestion question = session.currentQuestion();
        CandidateAnswer selected = question.answers().stream()
                .filter(answer -> answer.candidateId().equals(request.candidateId()))
                .findFirst()
                .orElseThrow(() -> new GameException(HttpStatus.BAD_REQUEST, "Candidate has no answer in this round"));
        String correctId = question.answers().stream()
                .filter(CandidateAnswer::correct)
                .findFirst()
                .orElseThrow()
                .candidateId();

        session.registerAnswer(request.candidateId(), selected.correct(), correctId);
        auditRepository.save(session.getId(), "ANSWER_SELECTED",
                "candidate=" + request.candidateId() + ";correct=" + selected.correct());
        String reaction = selected.correct()
                ? "Верно. Вы выбрали технически обоснованный ответ."
                : "Неверно. Оценивайте аргументы, а не уверенность подачи.";

        return new AnswerResult(
                selected.correct(),
                request.candidateId(),
                correctId,
                reaction,
                session.getStatus(),
                session.getStatus() == GameStatus.WAITING_FOR_QUESTION,
                Map.copyOf(session.getCandidateSelections())
        );
    }

    public InterviewSummary summary(UUID sessionId) {
        GameSession session = session(sessionId);
        if (session.getStatus() == GameStatus.INTERVIEW || session.getStatus() == GameStatus.WAITING_FOR_QUESTION) {
            throw new GameException(HttpStatus.CONFLICT, "Interview is not finished");
        }
        int correct = (int) session.getRoundResults().stream().filter(GameSession.RoundResult::correct).count();
        List<CandidateResult> results = content.candidates().stream()
                .map(candidate -> new CandidateResult(
                        candidate.id(),
                        candidate.name(),
                        candidate.realSkill(),
                        session.getCandidateSelections().getOrDefault(candidate.id(), 0),
                        correctAnswersGiven(session, candidate.id()),
                        verdict(candidate.realSkill())))
                .toList();
        return new InterviewSummary(
                session.getId(),
                session.getStatus(),
                correct,
                session.getQuestions().size(),
                Map.copyOf(session.getCandidateSelections()),
                results
        );
    }

    public HireResult hire(UUID sessionId, HireRequest request) {
        GameSession session = session(sessionId);
        requireStatus(session, GameStatus.READY_TO_HIRE);
        Candidate candidate = candidate(request.candidateId());
        session.hire(candidate.id());
        auditRepository.save(session.getId(), "CANDIDATE_HIRED", "candidate=" + candidate.id());
        FinalResult result = interviewResult(session);
        return new HireResult(
                candidate.id(), candidate.name(), result.hiringVerdict(), session.getStatus(),
                result, true, content.workScenarios(candidate.id()).size()
        );
    }

    public WorkScenarioResponse nextWorkScenario(UUID sessionId) {
        GameSession session = session(sessionId);
        requireStatus(session, GameStatus.EMPLOYEE_HIRED);
        List<WorkScenario> scenarios = content.workScenarios(session.getHiredCandidateId());
        if (session.getCurrentWorkScenario() >= scenarios.size()) {
            throw new GameException(HttpStatus.CONFLICT, "All work scenarios are completed");
        }
        session.openWorkScenario();
        WorkScenario scenario = scenarios.get(session.getCurrentWorkScenario());
        auditRepository.save(session.getId(), "WORK_SCENARIO_OPENED", "scenario=" + scenario.id());
        return new WorkScenarioResponse(session.getStatus(), session.getCurrentWorkScenario() + 1, scenarios.size(),
                new WorkScenarioView(scenario.id(), scenario.type(), scenario.title(), scenario.description(),
                        scenario.employeeMessage(), shuffledOptions(scenario).stream()
                                .map(o -> new WorkOptionView(o.id(), o.title(), o.description())).toList()),
                metrics(session));
    }

    public WorkDecisionResult makeWorkDecision(UUID sessionId, WorkDecisionRequest request) {
        GameSession session = session(sessionId);
        requireStatus(session, GameStatus.WORK_SCENARIO);
        List<WorkScenario> scenarios = content.workScenarios(session.getHiredCandidateId());
        WorkScenario scenario = scenarios.get(session.getCurrentWorkScenario());
        WorkScenario.WorkOption option = scenario.options().stream()
                .filter(o -> o.id().equals(request.optionId()))
                .findFirst()
                .orElseThrow(() -> new GameException(HttpStatus.BAD_REQUEST, "Unknown scenario option"));
        session.applyWorkDecision(scenario.id(), option, scenarios.size());
        auditRepository.save(session.getId(), "WORK_DECISION_SELECTED",
                "scenario=" + scenario.id() + ";option=" + option.id() + ";score=" + option.totalScore());
        ScoreDelta delta = new ScoreDelta(option.deliveryDelta(), option.reliabilityDelta(),
                option.teamDelta(), option.leadershipDelta(), option.totalScore());
        return new WorkDecisionResult(option.totalScore() > 0, option.id(), option.consequence(),
                option.employeeReaction(), delta, metrics(session), session.getStatus(),
                session.getStatus() == GameStatus.FINISHED,
                session.getStatus() == GameStatus.EMPLOYEE_HIRED);
    }

    public CareerResult careerResult(UUID sessionId) {
        GameSession session = session(sessionId);
        if (session.getStatus() != GameStatus.FINISHED) {
            throw new GameException(HttpStatus.CONFLICT, "Work chapter is not finished");
        }
        WorkMetrics metrics = metrics(session);
        String grade = metrics.overall() >= 80 ? "S" : metrics.overall() >= 65 ? "A" : metrics.overall() >= 50 ? "B" : "C";
        String verdict = switch (grade) {
            case "S" -> "Вы создали устойчивую команду и принимаете зрелые управленческие решения.";
            case "A" -> "Сильное руководство: результат достигнут, отдельные решения можно сделать более системными.";
            case "B" -> "Команда справилась, но баланс сроков, качества и людей периодически терялся.";
            default -> "Управленческие решения создали высокий риск для продукта и удержания команды.";
        };
        List<WorkHistoryItem> history = session.getWorkResults().stream()
                .map(r -> new WorkHistoryItem(r.round(), r.scenarioId(), r.optionId(), r.score(), r.consequence()))
                .toList();
        return new CareerResult(interviewResult(session), metrics, grade, verdict, history);
    }

    public FinalResult finalResult(UUID sessionId) {
        GameSession session = session(sessionId);
        if (session.getHiredCandidateId() == null) {
            throw new GameException(HttpStatus.CONFLICT, "Candidate is not hired yet");
        }
        return interviewResult(session);
    }

    private FinalResult interviewResult(GameSession session) {
        Candidate hired = candidate(session.getHiredCandidateId());
        int selectedTimes = session.getCandidateSelections().getOrDefault(hired.id(), 0);
        int correctAnswersGiven = correctAnswersGiven(session, hired.id());
        int hiringAccuracy = correctAnswersGiven == 0
                ? 0
                : Math.min(100, (int) Math.round(selectedTimes * 100.0 / correctAnswersGiven));

        String hiringVerdict;
        String analysis;
        if (hired.realSkill() >= 90) {
            hiringVerdict = "Сильный найм";
            analysis = "Вы выбрали кандидата с наиболее сильной технической базой. Следите, чтобы архитектурная уверенность не превращалась в избыточную сложность.";
        } else if (hired.realSkill() >= 80) {
            hiringVerdict = "Перспективный найм";
            analysis = "Кандидат хорошо работает с качеством и деталями, но ему потребуется развитие системного дизайна и лидерской уверенности.";
        } else {
            hiringVerdict = "Рискованный найм";
            analysis = "Кандидат убедительно коммуницирует, но результаты интервью показывают технические пробелы. Нужен конкретный план развития и наставник.";
        }

        return new FinalResult(
                hired.id(),
                hired.name(),
                hiringAccuracy,
                selectedTimes,
                correctAnswersGiven,
                hiringVerdict,
                analysis,
                hired.strengths(),
                hired.gaps(),
                personalizedRoadmap(session, hired)
        );
    }

    private List<String> personalizedRoadmap(GameSession session, Candidate hired) {
        LinkedHashSet<String> roadmap = new LinkedHashSet<>(hired.roadmap());
        Map<String, String> topicSteps = Map.ofEntries(
                Map.entry("Java", "Закрепить Java Core: коллекции, контракты объектов, records и проектирование моделей"),
                Map.entry("Concurrency", "Разобрать Java Memory Model, happens-before и практику безопасной многопоточности"),
                Map.entry("Spring", "Углубить Spring AOP, транзакции, конфигурацию и жизненный цикл бинов"),
                Map.entry("Kafka", "Практиковать Kafka delivery semantics, Outbox, идемпотентность и обработку повторов"),
                Map.entry("PostgreSQL", "Поработать с execution plan, индексами, блокировками и уровнями изоляции PostgreSQL"),
                Map.entry("JPA", "Разобрать fetch strategies, N+1, batching и границы транзакций JPA"),
                Map.entry("Architecture", "Регулярно решать system design кейсы и фиксировать решения через ADR"),
                Map.entry("System Design", "Практиковать выбор синхронных и асинхронных взаимодействий с анализом компромиссов"),
                Map.entry("Kubernetes", "Отработать probes, resources, graceful shutdown и диагностику приложений в Kubernetes"),
                Map.entry("Observability", "Собрать учебный стенд с метриками, логами и distributed tracing"),
                Map.entry("Security", "Повторить secure coding, управление секретами и процесс реагирования на инциденты"),
                Map.entry("Testing", "Расширить практику интеграционных тестов с Testcontainers и контрактных тестов"),
                Map.entry("Resilience", "Изучить timeout, retry, circuit breaker, bulkhead и управляемую деградацию"),
                Map.entry("DDD", "Потренироваться выделять bounded context и моделировать доменные границы"),
                Map.entry("Performance", "Освоить профилирование JVM и подход measure-first к оптимизации"),
                Map.entry("Caching", "Разобрать cache-aside, TTL, stampede и стратегии инвалидации"),
                Map.entry("CI/CD", "Собрать pipeline с quality gates, canary release и автоматическим rollback"),
                Map.entry("Code Review", "Практиковать аргументированное code review с обозначением критичности замечаний"),
                Map.entry("Transactions", "Разобрать аномалии транзакций и подобрать isolation level для реальных кейсов")
        );

        for (int i = 0; i < session.getRoundResults().size(); i++) {
            GameSession.RoundResult round = session.getRoundResults().get(i);
            if (!round.correctCandidateId().equals(hired.id()) && i < session.getQuestions().size()) {
                String category = session.getQuestions().get(i).category();
                String step = topicSteps.get(category);
                if (step != null) roadmap.add(step);
            }
        }
        roadmap.add("Составить 90-дневный план: теория, практический мини-проект и контрольная архитектурная сессия");
        return roadmap.stream().limit(8).toList();
    }

    private WorkMetrics metrics(GameSession session) {
        int overall = (session.getDelivery() + session.getReliability() + session.getTeamHealth() + session.getLeadership()) / 4;
        return new WorkMetrics(session.getDelivery(), session.getReliability(), session.getTeamHealth(), session.getLeadership(), overall);
    }

    private int correctAnswersGiven(GameSession session, String candidateId) {
        return (int) session.getRoundResults().stream()
                .filter(result -> result.correctCandidateId().equals(candidateId))
                .count();
    }

    private QuestionView questionView(GameSession session) {
        InterviewQuestion question = session.currentQuestion();
        if (question == null) {
            return null;
        }
        return new QuestionView(
                session.getCurrentRound() + 1,
                session.getQuestions().size(),
                question.id(),
                question.category(),
                question.text(),
                question.hint(),
                question.answers().stream()
                        .map(answer -> new AnswerView(answer.candidateId(), answer.text()))
                        .toList()
        );
    }

    private List<WorkScenario.WorkOption> shuffledOptions(WorkScenario scenario) {
        List<WorkScenario.WorkOption> options = new ArrayList<>(scenario.options());
        Collections.shuffle(options);
        return options;
    }

    private GameSession session(UUID id) {
        GameSession session = sessions.get(id);
        if (session == null) {
            throw new GameException(HttpStatus.NOT_FOUND, "Game session not found");
        }
        return session;
    }

    private Candidate candidate(String id) {
        return content.candidates().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new GameException(HttpStatus.BAD_REQUEST, "Unknown candidate"));
    }

    private void requireStatus(GameSession session, GameStatus expected) {
        if (session.getStatus() != expected) {
            throw new GameException(
                    HttpStatus.CONFLICT,
                    "Invalid game state: expected " + expected + ", actual " + session.getStatus()
            );
        }
    }

    private String verdict(int skill) {
        return skill >= 90
                ? "Сильный senior"
                : skill >= 80
                ? "Уверенный middle+ с потенциалом роста"
                : "Сильная подача, но технические пробелы";
    }
}
