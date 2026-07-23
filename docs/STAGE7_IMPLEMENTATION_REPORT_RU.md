# Stage 7: глобальные фазы и связь двух планировщиков

## Выполнено

1. Удалён полный legacy price pipeline и настройка его включения.
2. Удалены автоматические переходы к live-object price calculation при capture/model failure.
3. Сохранены ограниченные recovery-механизмы registry/generations: один холодный rebuild с negative cache, resync только после жёстких глобальных барьеров, stale requeue и quarantine повторяющейся ошибки.
4. Internal trade переведён на immutable DTO и dynamic workers; live `MarketAPI` из worker path удалён.
5. Введены локальные публикации и неизменяемый committed cut. Новые market snapshots во время cut откладываются до следующей ревизии.
6. Month end переведён на двухфазную последовательность: доставка pending time → локальный refresh → settlement cut.
7. Перед save workers останавливаются, после чего Prepatcher доставляет pending market time; сериализуется последняя committed AoTD revision.
8. Prepatcher ABI расширен global-boundary capability, schema 4, mask `0xef`.

## Ограничения

Полный игровой запуск, межмодовая проверка и JFR A/B не выполнялись из-за отсутствия установленного Starsector и полного набора зависимостей. `BaseIndustry` clean-core path остаётся этапом 8.
