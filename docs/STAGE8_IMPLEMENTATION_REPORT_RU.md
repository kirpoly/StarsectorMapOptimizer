# Stage 8 — Clean BaseIndustry path, validation и production-профиль

## Итог

Форк больше не использует и не предлагает замену `starfarer.api.jar`. Исходный Prepatcher устанавливает тонкую обёртку на чистый `BaseIndustry.getMaxDeficit()`, сохраняя оригинальный метод как raw fallback. AoTD регистрирует source-level resolver только через проверенный loader-safe ABI.

## Производственный профиль

AoTD требует полный capability mask `0xff`. Отсутствие javaagent, clean wrapper или любой другой обязательной возможности приводит к ранней понятной ошибке. Частичный или тихий fallback к vanilla deficit semantics запрещён.

## Проверено

- raw fallback `7` и resolver result `39`;
- idempotent transformation;
- реальный AoTD resolver и codex semantics;
- Stage 6/7 regressions;
- 17 deficit scenarios / 51 assertions;
- ASM verification;
- отсутствие reflection в интеграционном ABI;
- 86 runtime payload classes.

Полный запуск игры, `-Xverify:all` на реальном core JAR, save/load и JFR A/B требуют пользовательского окружения Starsector.
