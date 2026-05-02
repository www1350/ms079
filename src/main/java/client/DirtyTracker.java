package client;

import java.util.EnumSet;

/**
 * Tracks which data categories have changed since the last save, so saveToDB
 * can skip expensive delete-then-insert cycles for unchanged tables.
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

    private final EnumSet<Category> dirty = EnumSet.noneOf(Category.class);

    public void mark(Category category) {
        dirty.add(category);
    }

    public boolean isDirty(Category category) {
        return dirty.contains(category);
    }

    public boolean isClean(Category category) {
        return !dirty.contains(category);
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
