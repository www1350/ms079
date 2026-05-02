package client.component;

import client.DirtyTracker;
import client.ISkill;
import client.MapleCharacter;
import client.MapleStat;
import client.SkillEntry;
import client.SkillFactory;
import client.SkillMacro;
import com.github.mrzhqiang.maplestory.util.Numbers;
import com.github.mrzhqiang.maplestory.wz.WzData;
import com.github.mrzhqiang.maplestory.wz.WzElement;
import com.github.mrzhqiang.maplestory.wz.WzFile;
import com.github.mrzhqiang.maplestory.wz.element.ImgdirElement;
import constants.GameConstants;
import tools.MaplePacketCreator;
import tools.packet.UIPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class CharacterSkills {

    private final MapleCharacter owner;
    private final DirtyTracker dirtyTracker;
    private final Map<ISkill, SkillEntry> skills = new ConcurrentHashMap<>();
    private final SkillMacro[] skillMacros = new SkillMacro[5];
    private final int[] remainingSp = new int[10];
    private List<Integer> pendingSkills = null;

    public CharacterSkills(MapleCharacter owner, DirtyTracker dirtyTracker) {
        this.owner = owner;
        this.dirtyTracker = dirtyTracker;
    }

    // --- SP management ---

    public int getRemainingSp() {
        return remainingSp[GameConstants.getSkillBook(owner.getJob())];
    }

    public int getRemainingSp(final int skillbook) {
        return remainingSp[skillbook];
    }

    public int[] getRemainingSps() {
        return remainingSp;
    }

    public int getRemainingSpSize() {
        int ret = 0;
        for (int i = 0; i < remainingSp.length; i++) {
            if (remainingSp[i] > 0) {
                ret++;
            }
        }
        return ret;
    }

    public void setRemainingSp(int remainingSp) {
        this.remainingSp[GameConstants.getSkillBook(owner.getJob())] = remainingSp;
    }

    public void setRemainingSp(int remainingSp, final int skillbook) {
        this.remainingSp[skillbook] = remainingSp;
    }

    public void gainSP(int sp) {
        owner.getActor().execute(() -> {
            this.remainingSp[GameConstants.getSkillBook(owner.getJob())] += sp;
            owner.getClient().getSession().write(MaplePacketCreator.updateSp(owner, false));
            owner.getClient().getSession().write(UIPacket.getSPMsg((byte) sp, (short) owner.getJob()));
        });
    }

    public void gainSP(int sp, final int skillbook) {
        owner.getActor().execute(() -> {
            this.remainingSp[skillbook] += sp;
            owner.getClient().getSession().write(MaplePacketCreator.updateSp(owner, false));
            owner.getClient().getSession().write(UIPacket.getSPMsg((byte) sp, (short) owner.getJob()));
        });
    }

    public void resetSP(int sp) {
        owner.getActor().execute(() -> {
            for (int i = 0; i < this.remainingSp.length; i++) {
                this.remainingSp[i] = sp;
            }
            owner.updateSingleStat(MapleStat.AVAILABLESP, getRemainingSp());
        });
    }

    public void resetAPSP() {
        owner.getActor().execute(() -> {
            for (int i = 0; i < remainingSp.length; i++) {
                this.remainingSp[i] = 0;
            }
            owner.getClient().getSession().write(MaplePacketCreator.updateSp(owner, false));
            owner.gainAp((short) -owner.getRemainingAp());
        });
    }

    // --- Skills CRUD ---

    public Map<ISkill, SkillEntry> getSkills() {
        return Collections.unmodifiableMap(skills);
    }

    public Map<ISkill, SkillEntry> getSkillsInternal() {
        return skills;
    }

    public byte getSkillLevel(final ISkill skill) {
        if (skill == null) {
            return 0;
        }
        final SkillEntry ret = skills.get(skill);
        if (ret == null || ret.skillevel <= 0) {
            return 0;
        }
        return (byte) Math.min(skill.getMaxLevel(), ret.skillevel + (skill.isBeginnerSkill() ? 0 : owner.getStat().incAllskill));
    }

    public int getMasterLevel(int skill) {
        return getMasterLevel(SkillFactory.getSkill(skill));
    }

    public int getMasterLevel(final ISkill skill) {
        if (skill == null) {
            return 0;
        }
        final SkillEntry ret = skills.get(skill);
        if (ret == null) {
            return 0;
        }
        return ret.masterlevel;
    }

    public int getAllSkillLevels() {
        int rett = 0;
        for (Entry<ISkill, SkillEntry> ret : skills.entrySet()) {
            if ((!((ISkill) ret.getKey()).isBeginnerSkill()) && ret.getValue().skillevel > 0) {
                rett += ret.getValue().skillevel;
            }
        }
        return rett;
    }

    public void changeSkillLevel(final ISkill skill, int newLevel, int newMasterlevel) {
        owner.getActor().execute(() -> {
            if (skill == null) {
                return;
            }
            changeSkillLevelImpl(skill, newLevel, newMasterlevel,
                    skill.isTimeLimited() ? (System.currentTimeMillis() + (long) (30L * 24L * 60L * 60L * 1000L)) : -1);
        });
    }

    public void changeSkillLevel(final ISkill skill, int newLevel, int newMasterlevel, long expiration) {
        owner.getActor().execute(() -> {
            if (skill == null || (!GameConstants.isApplicableSkill(skill.getId()) && !GameConstants.isApplicableSkill_(skill.getId()))) {
                return;
            }
            changeSkillLevelImpl(skill, newLevel, newMasterlevel, expiration);
        });
    }

    public void changeSkillLevel_Skip(final ISkill skill, int newLevel, int newMasterlevel) {
        owner.getActor().execute(() -> {
            if (skill == null) {
                return;
            }
            changeSkillLevelImpl(skill, newLevel, newMasterlevel, -1L);
        });
    }

    private void changeSkillLevelImpl(final ISkill skill, int newLevel, int newMasterlevel, long expiration) {
        owner.getClient().getSession().write(MaplePacketCreator.updateSkill(skill.getId(), newLevel, newMasterlevel, expiration));
        if (newLevel == 0 && newMasterlevel == 0) {
            if (skills.containsKey(skill)) {
                skills.remove(skill);
            } else {
                return;
            }
        } else {
            skills.put(skill, new SkillEntry(newLevel, newMasterlevel, expiration));
        }
        dirtyTracker.mark(DirtyTracker.Category.SKILLS);
        if (GameConstants.isRecoveryIncSkill(skill.getId())) {
            owner.getStat().relocHeal();
        } else if (GameConstants.isElementAmp_Skill(skill.getId())) {
            owner.getStat().recalcLocalStats();
        }
    }

    public void baseSkills() {
        if (GameConstants.getJobNumber(owner.getJob()) >= 3) {
            List<Integer> baseSkills = SkillFactory.getSkillsByJob(owner.getJob());
            if (baseSkills != null) {
                for (int i : baseSkills) {
                    final ISkill skil = SkillFactory.getSkill(i);
                    if (skil != null && !skil.isInvisible() && skil.isFourthJob()
                            && getSkillLevel(skil) <= 0 && getMasterLevel(skil) <= 0 && skil.getMasterLevel() > 0) {
                        changeSkillLevel(skil, (byte) 0, (byte) skil.getMasterLevel());
                    }
                }
            }
        }
    }

    public void maxAllSkills() {
        WzData.STRING.directory().findFile("Skill.img")
                .map(WzFile::content)
                .map(ImgdirElement::childrenStream)
                .ifPresent(stream -> stream.map(WzElement::name)
                        .map(Numbers::ofInt)
                        .map(SkillFactory::getSkill1)
                        .filter(skill -> owner.getLevel() > 0)
                        .forEach(skill -> changeSkillLevel(skill, skill.getMaxLevel(), skill.getMaxLevel())));
    }

    public void maxSkills() {
        for (ISkill sk : SkillFactory.getAllSkills()) {
            changeSkillLevel(sk, sk.getMaxLevel(), sk.getMaxLevel());
        }
    }

    // --- Skill expiration ---

    public void addPendingSkill(int skillId) {
        if (pendingSkills == null) {
            pendingSkills = new ArrayList<>();
        }
        pendingSkills.add(skillId);
    }

    public List<Integer> getPendingSkills() {
        return pendingSkills;
    }

    public void clearPendingSkills() {
        pendingSkills = null;
    }

    public void checkSkillExpirations(long currentTime) {
        final List<ISkill> toberem = new ArrayList<>();
        for (Entry<ISkill, SkillEntry> skil : skills.entrySet()) {
            if (skil.getValue().expiration != -1 && currentTime > skil.getValue().expiration) {
                toberem.add(skil.getKey());
            }
        }
        for (ISkill skil : toberem) {
            addPendingSkill(skil.getId());
            skills.remove(skil);
        }
    }

    // --- Macros ---

    public void sendMacros() {
        for (int i = 0; i < 5; i++) {
            if (skillMacros[i] != null) {
                owner.getClient().getSession().write(MaplePacketCreator.getMacros(skillMacros));
                break;
            }
        }
    }

    public void updateMacros(int position, SkillMacro updateMacro) {
        owner.getActor().execute(() -> {
            skillMacros[position] = updateMacro;
            dirtyTracker.mark(DirtyTracker.Category.SKILL_MACROS);
        });
    }

    public SkillMacro[] getMacros() {
        return skillMacros;
    }
}
