package client.component;

import client.MapleCharacter;
import client.MapleDisease;
import client.MapleDiseaseValueHolder;
import com.github.mrzhqiang.maplestory.timer.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.ConcurrentEnumMap;
import tools.MaplePacketCreator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CharacterDiseases {

    private static final Logger LOGGER = LoggerFactory.getLogger(CharacterDiseases.class);

    private final MapleCharacter owner;
    private final Map<MapleDisease, MapleDiseaseValueHolder> diseases = new ConcurrentEnumMap<>(MapleDisease.class);

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
        owner.getActor().execute(() -> {
            final List<tools.Pair<MapleDisease, Integer>> debuff = Collections.singletonList(
                    new tools.Pair<>(disease, Integer.valueOf(x)));

            if (!hasDisease(disease) && diseases.size() < 2) {
                if (!(disease == MapleDisease.SEDUCE || disease == MapleDisease.STUN)) {
                    if (owner.isActiveBuffedValue(2321005)) {
                        return;
                    }
                }

                LOGGER.info("[CharacterDiseases] giveDebuff char=" + owner.getName() + " disease=" + disease + " mask=" + disease.getValue() + " x=" + x + " skillid=" + skillid + " level=" + level + " duration=" + duration);
                diseases.put(disease, new MapleDiseaseValueHolder(disease, System.currentTimeMillis(), duration));
                owner.getClient().getSession().write(MaplePacketCreator.giveDebuff(debuff, skillid, level, (int) duration));
                owner.getMap().broadcastMessage(owner, MaplePacketCreator.giveForeignDebuff(owner.getId(), debuff, skillid, level), false);

                if (duration > 0) {
                    Timer.BUFF.schedule(() -> {
                        owner.getActor().submit(() -> {
                            if (hasDisease(disease)) {
                                dispelDebuff(disease);
                            }
                        });
                    }, duration);
                }
            }
        });
    }

    public void giveSilentDebuff(final List<MapleDiseaseValueHolder> ld) {
        if (ld != null) {
            for (final MapleDiseaseValueHolder disease : ld) {
                diseases.put(disease.disease, disease);
            }
        }
    }

    public void dispelDebuff(MapleDisease debuff) {
        owner.getActor().execute(() -> {
            if (hasDisease(debuff)) {
                long mask = debuff.getValue();
                boolean first = debuff.isFirst();
                owner.getClient().getSession().write(MaplePacketCreator.cancelDebuff(mask, first));
                owner.getMap().broadcastMessage(owner, MaplePacketCreator.cancelForeignDebuff(owner.getId(), mask, first), false);

                diseases.remove(debuff);
            }
        });
    }

    public void dispelDebuffs() {
        owner.getActor().execute(() -> {
            dispelDebuff(MapleDisease.CURSE);
            dispelDebuff(MapleDisease.DARKNESS);
            dispelDebuff(MapleDisease.POISON);
            dispelDebuff(MapleDisease.SEAL);
            dispelDebuff(MapleDisease.WEAKEN);
        });
    }

    public void cancelAllDebuffs() {
        owner.getActor().execute(() -> {
            diseases.clear();
        });
    }

    public int size() {
        return diseases.size();
    }
}
