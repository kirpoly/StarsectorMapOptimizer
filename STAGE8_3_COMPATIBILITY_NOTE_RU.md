# Совместимость со Stage 8.3 AoTD Scheduler Fork

Изменения Stage 8.3 находятся только в AoTD Scheduler Fork: добавлена post-immigration фаза торговых снимков и диагностические fingerprints.

Код Prepatcher, schema bridge, capability mask `0xff`, clean `BaseIndustry` wrapper и bootstrap JAR не изменялись. Эта сборка побитово совместима с AoTD Stage 8.3 по существующему Stage 8 production ABI.
