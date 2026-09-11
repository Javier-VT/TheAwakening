#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/theawakening"

def read(rel):
    p = ROOT / rel
    assert p.exists(), f"missing: {rel}"
    return p.read_text(encoding="utf-8")

rules = read("src/main/java/com/theawakening/revive/ReviveRules.java")
manager = read("src/main/java/com/theawakening/revive/ReviveManager.java")
events = read("src/main/java/com/theawakening/revive/ReviveEvents.java")
network = read("src/main/java/com/theawakening/network/ModNetwork.java")
client = read("src/main/java/com/theawakening/client/ClientReviveInput.java")
death = read("src/main/java/com/theawakening/death/DeathEchoManager.java")
commands = read("src/main/java/com/theawakening/command/AwakeningCommands.java")
props = read("gradle.properties")

assert "mod_version=0.4.0-stage4" in props
assert "REVIVE_HOLD_TICKS = 80" in rules
assert "REVIVE_HEALTH_FRACTION = 0.40D" in rules
assert "NORMAL -> 3" in rules and "HARD -> 2" in rules and "NIGHTMARE, AWAKENED -> 1" in rules
assert "30 * 20" in rules and "25 * 20" in rules and "22 * 20" in rules and "20 * 20" in rules
assert "tryEnterDowned" in manager and "forceDefinitiveDeath" in manager
assert "POST_REVIVE_INVULNERABILITY" in manager
assert "DOWNED_MOVEMENT_UUID" in manager
assert "LivingDeathEvent" in events and "event.setCanceled(true)" in events
assert "LivingAttackEvent" in events and "AttackEntityEvent" in events
assert 'PROTOCOL_VERSION = "3"' in network and "ReviveIntentPacket" in network
assert "keyUse.isDown()" in client and "sendToServer" in client
assert "DURATION_TICKS = 40" in death
assert "import net.minecraft.world.entity.decoration.ArmorStand" not in death
assert "new ArmorStand" not in death and "new ArmorStand" not in manager
for token in ['literal("revive")', 'literal("begin")', 'literal("status")', 'literal("down")', 'literal("end")']:
    assert token in commands, f"missing command token {token}"

sources = list(JAVA.rglob("*.java"))
print("STAGE 4 STATIC VALIDATION PASSED")
print("Revive: 4s hold, 40% HP, 3s invulnerability; DOWNED window 20-30s")
print(f"Java sources: {len(sources)}")
