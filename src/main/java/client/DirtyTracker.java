package client;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which data categories have changed since the last save, so saveToDB
 * can skip expensive delete-then-insert cycles for unchanged tables.
 *
 * Thread-safe: uses ConcurrentHashMap so mark() from any thread (IO, Timer,
 * Actor) and isDirty()+clear() from saveToDB thread are safe.
 */
public final class DirtyTracker {

    public enum Category {
        CORE,           // character core row (stats, level, job, meso, exp, etc.)
        SKILLS,         // skills table
        COOLDOWNS,      // skill_cooldowns table
        SKILL_MACROS,   // skill_macros table
        INVENTORY_SLOTS,// inventory_slots table
        INVENTORY,      // inventory_items table
        QUEST_INFO,     // quest_info table
        QUEST_STATUS,   // quest_status table (+ mob kills)
        BUDDIES,        // buddies table
        ACHIEVEMENTS,   // achievements table
        SAVED_LOCATIONS,// saved_locations table
        TROCK_LOCATIONS,// trock_locations + regrock_locations
        WISHLIST,       // wishlist table
    }

    private final ConcurrentHashMap<Category, Boolean> dirty = new ConcurrentHashMap<>();

    public void mark(Category category) {
        dirty.put(category, Boolean.TRUE);
    }

    public boolean isDirty(Category category) {
        return dirty.containsKey(category);
    }

    public boolean isClean(Category category) {
        return !dirty.containsKey(category);
    }

    public void clear() {
        dirty.clear();
    }

    public boolean hasAny() {
        return !dirty.isEmpty();
    }

    public int dirtyCount() {
        return dirty.size();
    }
}
