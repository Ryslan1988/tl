package ru.ruslan.teamleadgame.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.ruslan.teamleadgame.dto.GameDtos.*;
import ru.ruslan.teamleadgame.service.GameService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/games")
public class GameController {
    private final GameService gameService;

    public GameController(GameService gameService) { this.gameService = gameService; }

    @PostMapping public StartGameResponse startGame() { return gameService.start(); }
    @PostMapping("/{sessionId}/questions/next") public AskQuestionResponse askQuestion(@PathVariable UUID sessionId) { return gameService.askQuestion(sessionId); }
    @PostMapping("/{sessionId}/answers") public AnswerResult selectAnswer(@PathVariable UUID sessionId, @Valid @RequestBody SelectAnswerRequest request) { return gameService.selectAnswer(sessionId, request); }
    @GetMapping("/{sessionId}/summary") public InterviewSummary summary(@PathVariable UUID sessionId) { return gameService.summary(sessionId); }
    @PostMapping("/{sessionId}/hire") public HireResult hire(@PathVariable UUID sessionId, @Valid @RequestBody HireRequest request) { return gameService.hire(sessionId, request); }
    @PostMapping("/{sessionId}/work/scenarios/next") public WorkScenarioResponse nextWorkScenario(@PathVariable UUID sessionId) { return gameService.nextWorkScenario(sessionId); }
    @PostMapping("/{sessionId}/work/decisions") public WorkDecisionResult workDecision(@PathVariable UUID sessionId, @Valid @RequestBody WorkDecisionRequest request) { return gameService.makeWorkDecision(sessionId, request); }
    @GetMapping("/{sessionId}/result") public FinalResult result(@PathVariable UUID sessionId) { return gameService.finalResult(sessionId); }
    @GetMapping("/{sessionId}/career-result") public CareerResult careerResult(@PathVariable UUID sessionId) { return gameService.careerResult(sessionId); }
}
