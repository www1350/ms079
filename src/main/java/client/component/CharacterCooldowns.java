package client.component;

import client.DirtyTracker;
import client.MapleCharacter;
import client.MapleCoolDownValueHolder;
import com.github.mrzhqiang.maplestory.domain.DSkillCooldown;
import com.github.mrzhqiang.maplestory.domain.query.QDSkillCooldown;
import com.github.mrzhqiang.maplestory.timer.Timer;
import tools.MaplePacketCreator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class CharacterCooldowns {

    private final MapleCharacter owner;
    private final DirtyTracker dirtyTracker;
    private final Map<Integer, MapleCoolDownValueHolder> coolDowns = new ConcurrentHashMap<>();

    public CharacterCooldowns(MapleCharacter owner, DirtyTracker dirtyTracker) {
        this.owner = owner;
        this.dirtyTracker = dirtyTracker;
    }

    public void addCooldown(int skillId, long startTime, long length) {
        ReentrantLock playerLock = owner.getLock();
        playerLock.lock();
        try {
            coolDowns.put(skillId, new MapleCoolDownValueHolder(skillId, startTime, length));
            dirtyTracker.mark(DirtyTracker.Category.COOLDOWNS);

            long delay = startTime + length - System.currentTimeMillis();
            if (delay > 0) {
                final long scheduledStart = startTime;
                Timer.BUFF.schedule(() -> {
                    playerLock.lock();
                    try {
                        MapleCoolDownValueHolder current = coolDowns.get(skillId);
                        if (current != null && current.startTime == scheduledStart) {
                            coolDowns.remove(skillId);
                            if (owner.getClient() != null && owner.getClient().getSession() != null) {
                                owner.getClient().getSession().write(MaplePacketCreator.skillCooldown(skillId, 0));
                            }
                        }
                    } finally {
                        playerLock.unlock();
                    }
                }, delay);
            }
        } finally {
            playerLock.unlock();
        }
    }

    public void removeCooldown(int skillId) {
        ReentrantLock playerLock = owner.getLock();
        playerLock.lock();
        try {
            if (coolDowns.containsKey(skillId)) {
                coolDowns.remove(skillId);
            }
            dirtyTracker.mark(DirtyTracker.Category.COOLDOWNS);
        } finally {
            playerLock.unlock();
        }
    }

    public boolean skillisCooling(int skillId) {
        return coolDowns.containsKey(skillId);
    }

    public void giveCoolDowns(final int skillid, long starttime, long length) {
        addCooldown(skillid, starttime, length);
    }

    public void giveCoolDowns(final List<MapleCoolDownValueHolder> cooldowns) {
        ReentrantLock playerLock = owner.getLock();
        playerLock.lock();
        try {
            if (cooldowns != null) {
                for (MapleCoolDownValueHolder cooldown : cooldowns) {
                    coolDowns.put(cooldown.skillId, cooldown);
                }
            } else {
                new QDSkillCooldown().character.eq(owner.character).findEach(it -> {
                    if (it.getLength() + it.getStartTime() - System.currentTimeMillis() > 0) {
                        giveCoolDowns(it.getSkillId(), it.getStartTime(), it.getLength());
                    }
                });
                new QDSkillCooldown().character.eq(owner.character).delete();
            }
        } finally {
            playerLock.unlock();
        }
    }

    public List<MapleCoolDownValueHolder> getCooldowns() {
        return new ArrayList<>(coolDowns.values());
    }

    public Map<Integer, MapleCoolDownValueHolder> getCoolDownsMap() {
        return coolDowns;
    }
}
