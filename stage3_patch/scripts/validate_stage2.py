#!/usr/bin/env python3
from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]
errors=[]
required=[
 'src/main/java/com/theawakening/progression/ProgressionManager.java',
 'src/main/java/com/theawakening/progression/ProgressionEvents.java',
 'src/main/java/com/theawakening/network/packet/PlayerProgressSyncPacket.java',
 'src/main/java/com/theawakening/client/ClientProgressionState.java',
]
for rel in required:
    if not (root/rel).exists(): errors.append(f'missing {rel}')
world=(root/'src/main/java/com/theawakening/progression/AwakeningWorldProgress.java').read_text()
player=(root/'src/main/java/com/theawakening/player/PlayerData.java').read_text()
network=(root/'src/main/java/com/theawakening/network/ModNetwork.java').read_text()
events=(root/'src/main/java/com/theawakening/progression/ProgressionEvents.java').read_text()
checks={
 'world data version':'CURRENT_DATA_VERSION' in world,
 'world player safe mutation':'updatePlayer(UUID playerId' in world and 'setDirty();' in world,
 'global challenges':'completedChallenges' in world,
 'boss records':'recordBossTime' in world,
 'player data migration':'migrateAndSanitize' in player,
 'player snapshot sync':'applySnapshot' in player,
 'network packet registration':'PlayerProgressSyncPacket.class' in network,
 'Forge 1.20 main-thread consumer':'consumerMainThread' in network,
 'login reconciliation':'reconcileOnLogin' in events,
}
for name,ok in checks.items():
    if not ok: errors.append(f'failed check: {name}')
old=(root/'src/main/java/com/theawakening/player/PlayerDataEvents.java').read_text()
if '@Mod.EventBusSubscriber' in old or '@SubscribeEvent' in old:
    errors.append('duplicate player lifecycle subscriber remains in PlayerDataEvents')
for rel in ['src/main/java/com/theawakening/progression/ProgressionManager.java','src/main/java/com/theawakening/progression/AwakeningWorldProgress.java']:
    txt=(root/rel).read_text()
    if 'net.minecraft.client' in txt:
        errors.append(f'client class leaked into server progression: {rel}')
if errors:
    print('STAGE 2 VALIDATION FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('STAGE 2 STATIC VALIDATION PASSED')
print('Java sources:', len(list((root/'src/main/java').rglob('*.java'))))
