
Провести аудит секретов и конфигураций: найти API ключи/токены в репозитории и в истории, удалить `.env` из репо, подготовить перенос секретов в Secret Manager (см. [`src/main/resources/application-prod.yaml:1`](src/main/resources/application-prod.yaml:1) и [`src/main/java/net/protsenko/fundy/app/utils/FeedbackTelegramSender.java:19`](src/main/java/net/protsenko/fundy/app/utils/FeedbackTelegramSender.java:19))

-------------------------------------------------------------
×
Ограничить доступ к actuator и prometheus: добавить IP-белый список или basic auth для `/actuator` и метрик (см. [`src/main/resources/application-prod.yaml:23`](src/main/resources/application-prod.yaml:23))

-------------------------------------------------------------
×
Ввести ingress rate limiting и IP throttling (NGINX/Cloudflare) для всех публичных путей; настроить лимит на  requests per second для UI и API (управлять на уровне [`compose.prod.yaml:1`](compose.prod.yaml:1) / Nginx)

-------------------------------------------------------------
×
Реализовать серверный rate limiting и per-user/ per-IP quotas в приложении (bucket4j или Spring RateLimiter) для критичных эндпойнтов (например `/api/market/*`, контроллеры в [`src/main/java/net/protsenko/fundy/app/controller`](src/main/java/net/protsenko/fundy/app/controller:1))

-------------------------------------------------------------
×
Добавить per-exchange throttling, retry с backoff и circuit breaker (Resilience4j), чтобы не превышать лимиты бирж; покрыть все exchange-клиенты

-------------------------------------------------------------
×
Перейти от локального Caffeine к согласованному shared cache (Redis) для уменьшения дублирующих вызовов между экземплярами; обновить TTL для `tickers` (текущие `tickers-ttl: 2s` в [`src/main/resources/application-prod.yaml:42`](src/main/resources/application-prod.yaml:42))

-------------------------------------------------------------
×
Аудит и оптимизация HTTP-клиентов: проверить, что exchange-клиенты используют неблокирующие вызовы (`HttpClient.sendAsync` или reactive `WebClient`), и настроить таймауты и ограничения параллелизма (см. [`HttpBeansConfig` HttpClient](src/main/java/net/protsenko/fundy/app/config/HttpBeansConfig.java:11) и [`AsyncConfig` exchangeExecutor](src/main/java/net/protsenko/fundy/app/config/AsyncConfig.java:14))

-------------------------------------------------------------
×
Пересмотреть размеры и типы пулов потоков: заменить фиксированный пул на управляемый пул с очередью или использовать виртуальные потоки там, где это безопасно (см. [`Thread.ofVirtual()` в AsyncConfig](src/main/java/net/protsenko/fundy/app/config/AsyncConfig.java:20))

-------------------------------------------------------------
×
Ограничить отправку в Telegram: добавить валидацию/санитизацию входа, rate-limit по IP/юзеру и опцию "dry-run" для массового релиза (см. [`FeedbackTelegramSender.send`](src/main/java/net/protsenko/fundy/app/utils/FeedbackTelegramSender.java:29))

-------------------------------------------------------------
×
Настроить мониторинг и alerting: Prometheus + Grafana + Alertmanager; минимум alert'ы на error-rate > X%, latency, CPU, OOM, disk

-------------------------------------------------------------
×
Внедрить централизованное логирование (Loki/ELK), формат JSON логов и retention policy для расследований

-------------------------------------------------------------
×
Провести нагрузочное тестирование (k6/Gatling): смоделировать пики с запасом (целевые RPS и concurrency); подготовить acceptance-criteria для стабильности

-------------------------------------------------------------
×
Добавить resource limits и readiness/liveness для контейнеров; подготовить манифесты для k8s (deployments, hpa) или plan для горизонтального масштабирования при Docker Compose

-------------------------------------------------------------
×
Усилить безопасность образов и CI: запускать сканирование образов (Trivy), зависимостей (Snyk/Dependabot), собрать CI pipeline для автоматического билда/деплоя и сканов (см. сборку образа в [`compose.prod.yaml:3`](compose.prod.yaml:3))

-------------------------------------------------------------
×
Запускать контейнеры от непривилегированного пользователя и минимизировать базовые образы (поменять runtime image в [`Dockerfile:15`](Dockerfile:15) чтобы добавить USER)

-------------------------------------------------------------
×
Добавить фронтенд-оптимизации и CDN: раздать статический фронтенд через CDN, включить caching headers, debouncing запросов на клиенте и throttle UI-запросов

-------------------------------------------------------------
×
Подготовить юридический текст и UI: Disclaimer, Terms of Use и Privacy Policy; показывать предупреждение про финансовые риски

-------------------------------------------------------------
×
Подготовить operational runbook: шаги на случай инцидента, контактные данные, инструкции по ротации ключей и откату релиза

-------------------------------------------------------------
×
Провести staged rollout: canary (5-10% аудитории) с мониторингом, затем постепенное расширение до 100%

-------------------------------------------------------------