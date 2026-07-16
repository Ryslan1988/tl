# Team Lead Game Backend

Отдельный REST-бэкенд для Vue-игры по найму Java-разработчика.

## Реализованная механика

1. Создание игровой сессии.
2. Пользователь нажимает «Задать вопрос».
3. Сервер возвращает один вопрос и ответы трёх кандидатов.
4. В каждом раунде ровно один технически правильный ответ.
5. Алгоритм распределяет правильные ответы по профилям кандидатов: Алексей — 5, Мира — 3, Максим — 2 ответа за игру. Порядок каждый раз перемешивается.
6. Пользователь выбирает кандидата, чей ответ считает правильным.
7. После 10 раундов сервер возвращает статистику выбора.
8. Пользователь нанимает одного кандидата.
9. Сервер выдаёт итог найма: точность оценки, сильные стороны, риски и персональный roadmap развития кандидата.
10. События игры сохраняются в H2 (`game_audit`).

Сценарии управления проблемами, авторизация, роли, уровни сложности, пользовательские вопросы и несколько правильных ответов не включены.

## Стек

- Java 25
- Spring Boot 4.1
- Spring Web
- Spring JDBC
- H2
- Bean Validation
- Swagger/OpenAPI

## Запуск

```bash
mvn spring-boot:run
```

- API: `http://localhost:8080/api/v1/games`
- Swagger: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/team-lead-game`

## Основной flow

### 1. Начать игру

```http
POST /api/v1/games
```

### 2. Задать следующий вопрос

```http
POST /api/v1/games/{sessionId}/questions/next
```

### 3. Выбрать ответ кандидата

```http
POST /api/v1/games/{sessionId}/answers
Content-Type: application/json

{
  "candidateId": "mira"
}
```

Шаги 2–3 повторяются 10 раз.

### 4. Получить статистику

```http
GET /api/v1/games/{sessionId}/summary
```

### 5. Нанять кандидата

```http
POST /api/v1/games/{sessionId}/hire
Content-Type: application/json

{
  "candidateId": "alex"
}
```

### 6. Повторно получить финальный результат

```http
GET /api/v1/games/{sessionId}/result
```

## Рабочая глава после найма

После `POST /api/v1/games/{sessionId}/hire` статус становится `EMPLOYEE_HIRED`. Игра продолжается пятью рабочими сценариями:

1. production-инцидент;
2. риск срыва релиза;
3. конфликт на code review;
4. спор о выделении микросервиса;
5. разговор о повышении сотрудника.

Получить следующий сценарий:

```http
POST /api/v1/games/{sessionId}/work/scenarios/next
```

Принять решение:

```http
POST /api/v1/games/{sessionId}/work/decisions
Content-Type: application/json

{
  "optionId": "rollback"
}
```

Каждое решение изменяет четыре показателя: `delivery`, `reliability`, `teamHealth`, `leadership`. После пяти сценариев статус становится `FINISHED`.

Итог всей игры:

```http
GET /api/v1/games/{sessionId}/career-result
```

Ответ содержит результат найма, итоговые метрики, оценку руководителя `S/A/B/C` и историю последствий решений.

## Расширенная версия

- 15 вопросов на игру, случайно выбираемых из банка из 24 тем.
- 10 рабочих сценариев после найма.
- Персональный roadmap строится по кандидату и темам, в которых он не дал лучший ответ.
