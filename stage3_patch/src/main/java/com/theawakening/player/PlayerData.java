package com.theawakening.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import com.theawakening.health.PermanentHealthRules;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Persistent per-player progression mirror.
 *
 * <p>The server remains authoritative through {@code ProgressionManager}. This data is stored on the
 * player as a resilient mirror (survives clone/respawn) and is also used as the client-side snapshot
 * after a progression sync packet.</p>
 */
public final class PlayerData {
    public static final int CURRENT_DATA_VERSION = 3;

    private static final String ROOT_KEY = "theawakening_player";
    private static final String VERSION_KEY = "dataVersion";
    private static final String PHASE_KEY = "personalPhase";
    private static final String HEARTS_KEY = "permanentHeartUses";
    private static final String FATE_WHEELS_KEY = "completedFateWheels";
    private static final String CHALLENGES_KEY = "completedChallenges";

    private final CompoundTag tag;

    private PlayerData(CompoundTag tag) {
        this.tag = tag;
        migrateAndSanitize();
    }

    public static PlayerData get(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            root.put(ROOT_KEY, new CompoundTag());
        }
        return new PlayerData(root.getCompound(ROOT_KEY));
    }

    public static CompoundTag rawCopy(Player player) {
        return get(player).saveSnapshot();
    }

    public int personalPhase() {
        return Mth.clamp(tag.getInt(PHASE_KEY), 1, 8);
    }

    public void setPersonalPhase(int phase) {
        tag.putInt(PHASE_KEY, Mth.clamp(phase, 1, 8));
    }

    public int permanentHeartUses() {
        return PermanentHealthRules.sanitizeStoredUses(tag.getInt(HEARTS_KEY));
    }

    public void setPermanentHeartUses(int uses) {
        tag.putInt(HEARTS_KEY, PermanentHealthRules.sanitizeStoredUses(uses));
    }

    public int completedFateWheels() {
        return Math.max(0, tag.getInt(FATE_WHEELS_KEY));
    }

    public void setCompletedFateWheels(int count) {
        tag.putInt(FATE_WHEELS_KEY, Math.max(0, count));
    }

    public Set<String> completedChallenges() {
        return Collections.unmodifiableSet(readStringSet(tag.getList(CHALLENGES_KEY, Tag.TAG_STRING)));
    }

    public boolean hasCompletedChallenge(String id) {
        return id != null && completedChallenges().contains(id);
    }

    public void completeChallenge(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        Set<String> values = new HashSet<>(completedChallenges());
        if (values.add(id)) {
            tag.put(CHALLENGES_KEY, writeStringSet(values));
        }
    }

    public CompoundTag saveSnapshot() {
        CompoundTag snapshot = new CompoundTag();
        snapshot.putInt(VERSION_KEY, CURRENT_DATA_VERSION);
        snapshot.putInt(PHASE_KEY, personalPhase());
        snapshot.putInt(HEARTS_KEY, permanentHeartUses());
        snapshot.putInt(FATE_WHEELS_KEY, completedFateWheels());
        snapshot.put(CHALLENGES_KEY, writeStringSet(completedChallenges()));
        return snapshot;
    }

    /** Applies an authoritative server snapshot to this player's local mirror. */
    public void applySnapshot(CompoundTag snapshot) {
        if (snapshot == null) {
            return;
        }
        setPersonalPhase(snapshot.getInt(PHASE_KEY));
        setPermanentHeartUses(snapshot.getInt(HEARTS_KEY));
        setCompletedFateWheels(snapshot.getInt(FATE_WHEELS_KEY));
        tag.put(CHALLENGES_KEY, writeStringSet(readStringSet(snapshot.getList(CHALLENGES_KEY, Tag.TAG_STRING))));
        tag.putInt(VERSION_KEY, CURRENT_DATA_VERSION);
    }

    public static void copyForClone(Player original, Player clone) {
        CompoundTag originalRoot = original.getPersistentData();
        if (originalRoot.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            clone.getPersistentData().put(ROOT_KEY, originalRoot.getCompound(ROOT_KEY).copy());
        }
    }

    private void migrateAndSanitize() {
        int version = Math.max(0, tag.getInt(VERSION_KEY));

        if (!tag.contains(PHASE_KEY, Tag.TAG_INT)) {
            tag.putInt(PHASE_KEY, 1);
        }
        if (!tag.contains(HEARTS_KEY, Tag.TAG_INT)) {
            tag.putInt(HEARTS_KEY, 0);
        }
        if (!tag.contains(FATE_WHEELS_KEY, Tag.TAG_INT)) {
            tag.putInt(FATE_WHEELS_KEY, 0);
        }
        if (!tag.contains(CHALLENGES_KEY, Tag.TAG_LIST)) {
            tag.put(CHALLENGES_KEY, new ListTag());
        }

        // Version 1 contained only personalPhase and permanentHeartUses.
        if (version < 2) {
            tag.putInt(FATE_WHEELS_KEY, Math.max(0, tag.getInt(FATE_WHEELS_KEY)));
        }
        // Version 3 establishes the absolute 40 HP progression ceiling (10 uses).
        if (version < 3) {
            tag.putInt(HEARTS_KEY, PermanentHealthRules.sanitizeStoredUses(tag.getInt(HEARTS_KEY)));
        }

        tag.putInt(PHASE_KEY, Mth.clamp(tag.getInt(PHASE_KEY), 1, 8));
        tag.putInt(HEARTS_KEY, PermanentHealthRules.sanitizeStoredUses(tag.getInt(HEARTS_KEY)));
        tag.putInt(FATE_WHEELS_KEY, Math.max(0, tag.getInt(FATE_WHEELS_KEY)));
        tag.put(CHALLENGES_KEY, writeStringSet(readStringSet(tag.getList(CHALLENGES_KEY, Tag.TAG_STRING))));
        tag.putInt(VERSION_KEY, CURRENT_DATA_VERSION);
    }

    private static ListTag writeStringSet(Set<String> values) {
        ListTag list = new ListTag();
        values.stream().filter(value -> value != null && !value.isBlank()).sorted()
            .map(StringTag::valueOf).forEach(list::add);
        return list;
    }

    private static Set<String> readStringSet(ListTag list) {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String value = list.getString(i);
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }
}
