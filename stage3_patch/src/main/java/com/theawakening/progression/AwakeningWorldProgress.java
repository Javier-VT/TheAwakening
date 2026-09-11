package com.theawakening.progression;

import com.theawakening.difficulty.AwakeningDifficulty;
import com.theawakening.health.PermanentHealthRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-authoritative world progression persisted in the Overworld data storage. */
public final class AwakeningWorldProgress extends SavedData {
    public static final int CURRENT_DATA_VERSION = 3;
    private static final String DATA_NAME = "theawakening_world_progress";

    private int globalPhase = 1;
    private AwakeningDifficulty difficulty = AwakeningDifficulty.NORMAL;
    private final Set<String> defeatedBosses = new HashSet<>();
    private final Set<String> unlockedDimensions = new HashSet<>();
    private final Set<String> unlockedRecipes = new HashSet<>();
    private final Set<String> activeEvents = new HashSet<>();
    private final Set<String> completedChallenges = new HashSet<>();
    private final Map<String, Long> bossRecordsTicks = new HashMap<>();
    private final Map<UUID, PlayerProgress> playerProgress = new HashMap<>();
    private int completedFateWheels;

    public static AwakeningWorldProgress get(MinecraftServer server) {
        return get(server.overworld());
    }

    public static AwakeningWorldProgress get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(AwakeningWorldProgress::load, AwakeningWorldProgress::new, DATA_NAME);
    }

    public static AwakeningWorldProgress load(CompoundTag tag) {
        AwakeningWorldProgress data = new AwakeningWorldProgress();
        data.globalPhase = Mth.clamp(tag.getInt("globalPhase"), 1, 8);
        if (tag.contains("difficulty", Tag.TAG_STRING)) {
            try {
                data.difficulty = AwakeningDifficulty.valueOf(tag.getString("difficulty"));
            } catch (IllegalArgumentException ignored) {
                data.difficulty = AwakeningDifficulty.NORMAL;
            }
        }

        readStringSet(tag.getList("defeatedBosses", Tag.TAG_STRING), data.defeatedBosses);
        readStringSet(tag.getList("unlockedDimensions", Tag.TAG_STRING), data.unlockedDimensions);
        readStringSet(tag.getList("unlockedRecipes", Tag.TAG_STRING), data.unlockedRecipes);
        readStringSet(tag.getList("activeEvents", Tag.TAG_STRING), data.activeEvents);
        readStringSet(tag.getList("completedChallenges", Tag.TAG_STRING), data.completedChallenges);
        data.completedFateWheels = Math.max(0, tag.getInt("completedFateWheels"));

        CompoundTag records = tag.getCompound("bossRecordsTicks");
        for (String key : records.getAllKeys()) {
            long value = records.getLong(key);
            if (value >= 0L) {
                data.bossRecordsTicks.put(key, value);
            }
        }

        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                data.playerProgress.put(uuid, PlayerProgress.load(players.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed external save data instead of crashing world load.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("dataVersion", CURRENT_DATA_VERSION);
        tag.putInt("globalPhase", globalPhase);
        tag.putString("difficulty", difficulty.name());
        tag.put("defeatedBosses", writeStringSet(defeatedBosses));
        tag.put("unlockedDimensions", writeStringSet(unlockedDimensions));
        tag.put("unlockedRecipes", writeStringSet(unlockedRecipes));
        tag.put("activeEvents", writeStringSet(activeEvents));
        tag.put("completedChallenges", writeStringSet(completedChallenges));
        tag.putInt("completedFateWheels", completedFateWheels);

        CompoundTag records = new CompoundTag();
        bossRecordsTicks.forEach(records::putLong);
        tag.put("bossRecordsTicks", records);

        CompoundTag players = new CompoundTag();
        playerProgress.forEach((uuid, progress) -> players.put(uuid.toString(), progress.save()));
        tag.put("players", players);
        return tag;
    }

    public int globalPhase() {
        return globalPhase;
    }

    public boolean setGlobalPhase(int phase) {
        int next = Mth.clamp(phase, 1, 8);
        if (globalPhase == next) {
            return false;
        }
        globalPhase = next;
        setDirty();
        return true;
    }

    /** Gameplay advancement is monotonic; admin/debug commands may still use setGlobalPhase. */
    public boolean advanceGlobalPhase(int requestedPhase) {
        int next = Mth.clamp(requestedPhase, 1, 8);
        if (next <= globalPhase) {
            return false;
        }
        globalPhase = next;
        setDirty();
        return true;
    }

    public AwakeningDifficulty difficulty() {
        return difficulty;
    }

    public boolean setDifficulty(AwakeningDifficulty difficulty) {
        if (difficulty == null || this.difficulty == difficulty) {
            return false;
        }
        this.difficulty = difficulty;
        setDirty();
        return true;
    }

    public Set<String> defeatedBosses() {
        return Set.copyOf(defeatedBosses);
    }

    public boolean markBossDefeated(String id) {
        boolean changed = addValid(defeatedBosses, id);
        if (changed) setDirty();
        return changed;
    }

    public boolean isBossDefeated(String id) {
        return id != null && defeatedBosses.contains(id);
    }

    public Set<String> unlockedDimensions() {
        return Set.copyOf(unlockedDimensions);
    }

    public boolean unlockDimension(String id) {
        boolean changed = addValid(unlockedDimensions, id);
        if (changed) setDirty();
        return changed;
    }

    public boolean isDimensionUnlocked(String id) {
        return id != null && unlockedDimensions.contains(id);
    }

    public Set<String> unlockedRecipes() {
        return Set.copyOf(unlockedRecipes);
    }

    public boolean unlockRecipe(String id) {
        boolean changed = addValid(unlockedRecipes, id);
        if (changed) setDirty();
        return changed;
    }

    public boolean isRecipeUnlocked(String id) {
        return id != null && unlockedRecipes.contains(id);
    }

    public Set<String> activeEvents() {
        return Set.copyOf(activeEvents);
    }

    public boolean setEventActive(String id, boolean active) {
        if (id == null || id.isBlank()) {
            return false;
        }
        boolean changed = active ? activeEvents.add(id) : activeEvents.remove(id);
        if (changed) setDirty();
        return changed;
    }

    public int completedFateWheels() {
        return completedFateWheels;
    }

    public void setCompletedFateWheels(int count) {
        int next = Math.max(0, count);
        if (completedFateWheels != next) {
            completedFateWheels = next;
            setDirty();
        }
    }

    public int incrementCompletedFateWheels() {
        completedFateWheels++;
        setDirty();
        return completedFateWheels;
    }

    public Set<String> completedChallenges() {
        return Set.copyOf(completedChallenges);
    }

    public boolean completeChallenge(String id) {
        boolean changed = addValid(completedChallenges, id);
        if (changed) setDirty();
        return changed;
    }

    public long bossRecordTicks(String bossId) {
        return bossRecordsTicks.getOrDefault(bossId, -1L);
    }

    /** Stores a record only when the supplied clear time is valid and better than the previous record. */
    public boolean recordBossTime(String bossId, long ticks) {
        if (bossId == null || bossId.isBlank() || ticks < 0L) {
            return false;
        }
        long previous = bossRecordsTicks.getOrDefault(bossId, Long.MAX_VALUE);
        if (ticks >= previous) {
            return false;
        }
        bossRecordsTicks.put(bossId, ticks);
        setDirty();
        return true;
    }

    /** Returns a defensive copy; callers cannot mutate SavedData without going through updatePlayer. */
    public PlayerProgress playerSnapshot(UUID playerId) {
        PlayerProgress progress = playerProgress.get(playerId);
        return progress == null ? new PlayerProgress() : progress.copy();
    }

    /** Creates/updates one player record and always marks SavedData dirty after the mutation. */
    public PlayerProgress updatePlayer(UUID playerId, Consumer<PlayerProgress> mutator) {
        if (playerId == null || mutator == null) {
            return new PlayerProgress();
        }
        PlayerProgress progress = playerProgress.computeIfAbsent(playerId, id -> new PlayerProgress());
        mutator.accept(progress);
        progress.sanitize();
        setDirty();
        return progress.copy();
    }

    public void replacePlayer(UUID playerId, PlayerProgress progress) {
        if (playerId == null || progress == null) {
            return;
        }
        playerProgress.put(playerId, progress.copy());
        setDirty();
    }

    private static boolean addValid(Set<String> set, String id) {
        return id != null && !id.isBlank() && set.add(id);
    }

    private static ListTag writeStringSet(Set<String> values) {
        ListTag list = new ListTag();
        values.stream().filter(value -> value != null && !value.isBlank()).sorted()
            .map(StringTag::valueOf).forEach(list::add);
        return list;
    }

    private static void readStringSet(ListTag list, Set<String> output) {
        for (int i = 0; i < list.size(); i++) {
            String value = list.getString(i);
            if (!value.isBlank()) {
                output.add(value);
            }
        }
    }

    public static final class PlayerProgress {
        private int phase = 1;
        private int permanentHeartsUsed;
        private int completedFateWheels;
        private final Set<String> challenges = new HashSet<>();

        public int phase() {
            return phase;
        }

        public void setPhase(int phase) {
            this.phase = Mth.clamp(phase, 1, 8);
        }

        public int permanentHeartsUsed() {
            return permanentHeartsUsed;
        }

        public void setPermanentHeartsUsed(int uses) {
            permanentHeartsUsed = PermanentHealthRules.sanitizeStoredUses(uses);
        }

        public int completedFateWheels() {
            return completedFateWheels;
        }

        public void setCompletedFateWheels(int count) {
            completedFateWheels = Math.max(0, count);
        }

        public int incrementCompletedFateWheels() {
            return ++completedFateWheels;
        }

        public Set<String> challenges() {
            return Set.copyOf(challenges);
        }

        public boolean completeChallenge(String id) {
            return addValid(challenges, id);
        }

        public PlayerProgress copy() {
            PlayerProgress copy = new PlayerProgress();
            copy.phase = phase;
            copy.permanentHeartsUsed = permanentHeartsUsed;
            copy.completedFateWheels = completedFateWheels;
            copy.challenges.addAll(challenges);
            return copy;
        }

        public void mergeMonotonic(PlayerProgress other) {
            if (other == null) return;
            phase = Math.max(phase, other.phase);
            permanentHeartsUsed = Math.max(permanentHeartsUsed, other.permanentHeartsUsed);
            completedFateWheels = Math.max(completedFateWheels, other.completedFateWheels);
            challenges.addAll(other.challenges);
            sanitize();
        }

        private void sanitize() {
            phase = Mth.clamp(phase, 1, 8);
            permanentHeartsUsed = PermanentHealthRules.sanitizeStoredUses(permanentHeartsUsed);
            completedFateWheels = Math.max(0, completedFateWheels);
            challenges.removeIf(value -> value == null || value.isBlank());
        }

        private CompoundTag save() {
            sanitize();
            CompoundTag tag = new CompoundTag();
            tag.putInt("phase", phase);
            tag.putInt("permanentHeartsUsed", permanentHeartsUsed);
            tag.putInt("completedFateWheels", completedFateWheels);
            tag.put("challenges", writeStringSet(challenges));
            return tag;
        }

        private static PlayerProgress load(CompoundTag tag) {
            PlayerProgress progress = new PlayerProgress();
            progress.phase = Mth.clamp(tag.getInt("phase"), 1, 8);
            progress.permanentHeartsUsed = PermanentHealthRules.sanitizeStoredUses(tag.getInt("permanentHeartsUsed"));
            progress.completedFateWheels = Math.max(0, tag.getInt("completedFateWheels"));
            readStringSet(tag.getList("challenges", Tag.TAG_STRING), progress.challenges);
            progress.sanitize();
            return progress;
        }
    }
}
