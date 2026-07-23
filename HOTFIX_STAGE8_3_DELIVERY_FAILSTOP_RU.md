# Stage 8.3 runtime hotfix: delivery listener fail-stop

## Исправлено

`StarsectorPrepatcherRuntimeBridge.publishAoTDMarketTimeDelivered()` теперь отдельно обрабатывает `LinkageError`:

- повреждённый listener отключается после первой ошибки;
- capability `AOTD_CAPABILITY_NATIVE_DELIVERY_EVENTS` снимается на текущую сессию;
- системные properties обновляются;
- в лог один раз записывается полный stack trace и требование пересобрать всё семейство `SchedulerBridge`.

Обычные исключения listener по-прежнему используют ограниченное fail-open логирование.

## Очистка runtime-пакета

Удалены `.git`, `.build`, старые agent baseline JAR, сохранённые runtime-сессии, `logs.zip`, media и validation dumps. Исходники и целевой regression test сохранены для последующего переноса исправления в актуальную ветку.
