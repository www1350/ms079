package client.component;

import client.MapleCharacter;
import client.MapleDisease;
import client.MapleDiseaseValueHolder;
import com.github.mrzhqiang.maplestory.timer.Timer;
import tools.ConcurrentEnumMap;
import tools.MaplePacketCreator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public final class CharacterDiseases {

    private final MapleCharacter owner;
    private final Map<MapleDisease, MapleDiseaseValueHolder> diseases = new ConcurrentEnumMap<>(MapleDisease.class);
    // diseases2 is unused dead code kept for compatibility

    public CharacterDiseases(MapleCharacter owner) {
        this.owner = owner;
    }

    public List<MapleDiseaseValueHolder> getAllDiseases() {
        return new ArrayList<>(diseases.values());
    }

    public boolean hasDisease(final MapleDisease dis) {
        return diseases.keySet().contains(dis);
    }

    public void giveDebuff(final MapleDisease disease, int x, long duration, int skillid, int level) {
        ReentrantLock playerLock = owner.getLock();
        playerLock.lock();
        try {
            final List<tools.Pair<MapleDisease, Integer>> debuff = Collections.singletonList(
                    new tools.Pair<>(disease, Integer.valueOf(x)));

            if (!hasDisease(disease) && diseases.size() < 2) {
                if (!(disease == MapleDisease.SEDUCE || disease == MapleDisease.STUN)) {
                    if (owner.isActiveBuffedValue(2321005)) {
                        return;
                    }
                }

                diseases.put(disease, new MapleDiseaseValueHolder(disease, System.currentTimeMillis(), duration));
                owner.getClient().getSession().write(MaplePacketCreator.giveDebuff(debuff, skillid, level, (int) duration));
                owner.getMap().broadcastMessage(owner, MaplePacketCreator.giveForeignDebuff(owner.getId(), debuff, skillid, level), false);

                if (duration > 0) {
                    Timer.BUFF.schedule(() -> {
                        playerLock.lock();
                        try {
                            if (hasDisease(disease)) {
                                dispelDebuff(disease);
                            }
                        } finally {
                            playerLock.unlock();
                        }
                    }, duration);
                }
            }
        } finally {
            playerLock.unlock();
        }
    }

    public void giveSilentDebuff(final List<MapleDiseaseValueHolder> ld) {
        if (ld != null) {
            for (final MapleDiseaseValueHolder disease : ld) {
                diseases.put(disease.disease, disease);
            }
        }
    }

    public void dispelDebuff(MapleDisease debuff) {
        ReentrantLock playerLock = owner.getLock();
        playerLock.lock();
        try {
            if (hasDisease(debuff)) {
                long mask = debuff.getValue();
                boolean first = debuff.isFirst();
                owner.getClient().getSession().write(MaplePacketCreator.cancelDebuff(mask, first));
                owner.getMap().broadcastMessage(owner, MaplePacketCreator.cancelForeignDebuff(owner.getId(), mask, first), false);

                diseases.remove(debuff);
            }
        } finally {
            playerLock.unlock();
        }
    }

    public void dispelDebuffs() {
        ReentrantLock playerLock = owner.getLock();
        playerLock.lock();
        try {
            dispelDebuff(MapleDisease.CURSE);
            dispelDebuff(MapleDisease.DARKNESS);
            dispelDebuff(MapleDisease.POISON);
            dispelDebuff(MapleDisease.SEAL);
            dispelDebuff(MapleDisease.WEAKEN);
        } finally {
            playerLock.unlock();
        }
    }

    public void cancelAllDebuffs() {
        ReentrantLock playerLock = owner.getLock();
        playerLock.lock();
        try {
            diseases.clear();
        } finally {
            playerLock.unlock();
        }
    }

    public int size() {
        return diseases.size();
    }
}
