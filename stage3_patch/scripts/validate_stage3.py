#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
errors = []

required = [
    'src/main/java/com/theawakening/health/PermanentHealthRules.java',
    'src/main/java/com/theawakening/health/HealthManager.java',
    'src/main/java/com/theawakening/difficulty/DifficultyProfile.java',
    'src/main/java/com/theawakening/difficulty/DifficultyManager.java',
]
for rel in required:
    if not (root / rel).exists():
        errors.append(f'missing {rel}')

rules = (root / 'src/main/java/com/theawakening/health/PermanentHealthRules.java').read_text()
manager = (root / 'src/main/java/com/theawakening/health/HealthManager.java').read_text()
progression = (root / 'src/main/java/com/theawakening/progression/ProgressionManager.java').read_text()
events = (root / 'src/main/java/com/theawakening/progression/ProgressionEvents.java').read_text()
difficulty = (root / 'src/main/java/com/theawakening/difficulty/AwakeningDifficulty.java').read_text()
commands = (root / 'src/main/java/com/theawakening/command/AwakeningCommands.java').read_text()

checks = {
    'absolute heart cap': 'ABSOLUTE_MAX_USES = 10' in rules,
    'phase two cap': 'PHASE_TWO_MAX_USES = 5' in rules,
    '+2 HP per use': 'HEALTH_PER_USE = 2.0D' in rules,
    'fixed modifier uuid': 'PERMANENT_HEALTH_MODIFIER_ID' in manager and 'UUID.fromString' in manager,
    'transient health modifier': 'addTransientModifier' in manager,
    'duplicate prevention': 'removeModifier(PERMANENT_HEALTH_MODIFIER_ID)' in manager,
    'phase-aware gameplay grant': 'maxUsesForPhase(before.phase())' in progression,
    'runtime reapply': 'reapplyRuntimeState' in progression and events.count('reapplyRuntimeState') >= 2,
    'difficulty profiles': all(name + '(new DifficultyProfile' in difficulty for name in ['NORMAL','HARD','NIGHTMARE','AWAKENED']),
    'difficulty profile command': 'Commands.literal("profile")' in commands,
    'health debug commands': 'Commands.literal("health")' in commands and 'Commands.literal("grant")' in commands,
}
for name, ok in checks.items():
    if not ok:
        errors.append(f'failed check: {name}')

if errors:
    print('STAGE 3 VALIDATION FAILED')
    for error in errors:
        print(' -', error)
    sys.exit(1)

print('STAGE 3 STATIC VALIDATION PASSED')
print('Health caps: P1=20 HP, P2=30 HP, P3+=40 HP')
print('Java sources:', len(list((root / 'src/main/java').rglob('*.java'))))
