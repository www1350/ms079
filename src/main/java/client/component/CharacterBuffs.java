package client.component;

import client.DirtyTracker;
import client.ISkill;
import client.MapleBuffStat;
import client.MapleBuffStatValueHolder;
import client.MapleCharacter;
import client.SkillFactory;
import com.github.mrzhqiang.maplestory.timer.Timer;
import handling.world.PlayerBuffValueHolder;
import server.MapleStatEffect;
import server.Randomizer;
import server.maps.MapleSummon;
import server.maps.MapleDoor;
import tools.ConcurrentEnumMap;
import tools.MaplePacketCreator;
import tools.Pair;
import tools.packet.PetPacket;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;

public final class CharacterBuffs {

    private final MapleCharacter owner;
    private final DirtyTracker dirtyTracker;
    private final Map<MapleBuffStat, MapleBuffStatValueHolder> effects = new ConcurrentEnumMap<>(MapleBuffStat.class);
    private ScheduledFuture<?> dragonBloodSchedule;
    private ScheduledFuture<?> beholderHealingSchedule;
    private ScheduledFuture<?> beholderBuffSchedule;
    private ScheduledFuture<?> BerserkSchedule;
    private long lastRecoveryTime;
    private int battleshipHP;
    private int combo;
    private long lastCombo;

    public CharacterBuffs(MapleCharacter owner, DirtyTracker dirtyTracker) {
        this.owner = owner;
        this.dirtyTracker = dirtyTracker;
    }

    private void onActor(Runnable task) {
        owner.getActor().execute(task);
    }

    public Map<MapleBuffStat, MapleBuffStatValueHolder> getEffects() {
        return Collections.unmodifiableMap(effects);
    }

    @Deprecated
    public Map<MapleBuffStat, MapleBuffStatValueHolder> getEffectsInternal() {
        return effects;
    }

    // --- Schedule getters/setters (used internally by MapleCharacter for save/load) ---

    public ScheduledFuture<?> getDragonBloodSchedule() {
        return dragonBloodSchedule;
    }

    public void setDragonBloodSchedule(ScheduledFuture<?> s) {
        this.dragonBloodSchedule = s;
    }

    public ScheduledFuture<?> getBeholderHealingSchedule() {
        return beholderHealingSchedule;
    }

    public void setBeholderHealingSchedule(ScheduledFuture<?> s) {
        this.beholderHealingSchedule = s;
    }

    public ScheduledFuture<?> getBeholderBuffSchedule() {
        return beholderBuffSchedule;
    }

    public void setBeholderBuffSchedule(ScheduledFuture<?> s) {
        this.beholderBuffSchedule = s;
    }

    public ScheduledFuture<?> getBerserkSchedule() {
        return BerserkSchedule;
    }

    public void setBerserkSchedule(ScheduledFuture<?> s) {
        this.BerserkSchedule = s;
    }

    public long getLastRecoveryTime() {
        return lastRecoveryTime;
    }

    public void setLastRecoveryTime(long t) {
        this.lastRecoveryTime = t;
    }

    // --- battleshipHP ---

    public int getBattleshipHP() {
        return battleshipHP;
    }

    public void setBattleshipHP(int hp) {
        this.battleshipHP = hp;
        dirtyTracker.mark(DirtyTracker.Category.CORE);
    }

    // --- combo ---

    public short getCombo() {
        return (short) combo;
    }

    public void setCombo(short combo) {
        this.combo = combo;
    }

    public long getLastCombo() {
        return lastCombo;
    }

    public void setLastCombo(long combo) {
        this.lastCombo = combo;
    }

    // --- Simple queries ---

    public boolean isActiveBuffedValue(int skillid) {
        LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());
        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skillid) {
                return true;
            }
        }
        return false;
    }

    public Integer getBuffedValue(MapleBuffStat effect) {
        final MapleBuffStatValueHolder mbsvh = effects.get(effect);
        return mbsvh == null ? null : mbsvh.value;
    }

    public Integer getBuffedSkill_X(final MapleBuffStat effect) {
        final MapleBuffStatValueHolder mbsvh = effects.get(effect);
        if (mbsvh == null) {
            return null;
        }
        return mbsvh.effect.getX();
    }

    public Integer getBuffedSkill_Y(final MapleBuffStat effect) {
        final MapleBuffStatValueHolder mbsvh = effects.get(effect);
        if (mbsvh == null) {
            return null;
        }
        return mbsvh.effect.getY();
    }

    public boolean isBuffFrom(MapleBuffStat stat, ISkill skill) {
        final MapleBuffStatValueHolder mbsvh = effects.get(stat);
        if (mbsvh == null) {
            return false;
        }
        return mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skill.getId();
    }

    public int getBuffSource(MapleBuffStat stat) {
        final MapleBuffStatValueHolder mbsvh = effects.get(stat);
        return mbsvh == null ? -1 : mbsvh.effect.getSourceId();
    }

    public void setBuffedValue(MapleBuffStat effect, int value) {
        onActor(() -> {
            final MapleBuffStatValueHolder mbsvh = effects.get(effect);
            if (mbsvh == null) {
                return;
            }
            mbsvh.value = value;
        });
    }

    public Long getBuffedStarttime(MapleBuffStat effect) {
        final MapleBuffStatValueHolder mbsvh = effects.get(effect);
        return mbsvh == null ? null : mbsvh.startTime;
    }

    public MapleStatEffect getStatForBuff(MapleBuffStat effect) {
        final MapleBuffStatValueHolder mbsvh = effects.get(effect);
        return mbsvh == null ? null : mbsvh.effect;
    }

    // --- Registration ---

    public void registerEffect(MapleStatEffect effect, long starttime, ScheduledFuture<?> schedule) {
        registerEffect(effect, starttime, schedule, effect.getStatups());
    }

    public void registerEffect(MapleStatEffect effect, long starttime, ScheduledFuture<?>
            schedule, List<Pair<MapleBuffStat, Integer>> statups) {
        owner.getActor().execute(() -> {
            if (effect.isHide()) {
                owner.setHidden(true);
                owner.getMap().broadcastMessage(owner, MaplePacketCreator.removePlayerFromMap(owner.getId()), false);
            } else if (effect.isDragonBlood()) {
                prepareDragonBlood(effect);
            } else if (effect.isBerserk()) {
                checkBerserk();
            } else if (effect.isMonsterRiding_()) {
                owner.getMount().startSchedule();
            } else if (effect.isBeholder()) {
                prepareBeholderEffect();
            } else if (effect.getSourceId() == 1001 || effect.getSourceId() == 10001001 || effect.getSourceId() == 1001) {
                prepareRecovery();
            }
            int clonez = 0;
            for (Pair<MapleBuffStat, Integer> statup : statups) {
                if (statup.getLeft() == MapleBuffStat.ILLUSION) {
                    clonez = statup.getRight();
                }
                int value = statup.getRight().intValue();
                if (statup.getLeft() == MapleBuffStat.骑兽技能 && effect.getSourceId() == 5221006) {
                    if (battleshipHP <= 0) {
                        battleshipHP = value;
                    }
                }
                effects.put(statup.getLeft(), new MapleBuffStatValueHolder(effect, starttime, schedule, value));
            }
            if (clonez > 0) {
                int cloneSize = Math.max(owner.getNumClones(), owner.getCloneSize());
                if (clonez > cloneSize) {
                    for (int i = 0; i < clonez - cloneSize; i++) {
                        owner.cloneLook();
                    }
                }
            }
            owner.getStat().recalcLocalStats();
        });
    }

    // --- getBuffStats ---

    public List<MapleBuffStat> getBuffStats(final MapleStatEffect effect, final long startTime) {
        final List<MapleBuffStat> bstats = new ArrayList<>();
        final Map<MapleBuffStat, MapleBuffStatValueHolder> allBuffs = new EnumMap<>(effects);
        for (Entry<MapleBuffStat, MapleBuffStatValueHolder> stateffect : allBuffs.entrySet()) {
            final MapleBuffStatValueHolder mbsvh = stateffect.getValue();
            if (mbsvh.effect.sameSource(effect) && (startTime == -1 || startTime == mbsvh.startTime)) {
                bstats.add(stateffect.getKey());
            }
        }
        return bstats;
    }

    // --- Deregistration ---

    private boolean deregisterBuffStats(List<MapleBuffStat> stats) {
        boolean clonez = false;
        List<MapleBuffStatValueHolder> effectsToCancel = new ArrayList<>(stats.size());
        for (MapleBuffStat stat : stats) {
            final MapleBuffStatValueHolder mbsvh = effects.remove(stat);
            if (mbsvh != null) {
                boolean addMbsvh = true;
                for (MapleBuffStatValueHolder contained : effectsToCancel) {
                    if (mbsvh.startTime == contained.startTime && contained.effect == mbsvh.effect) {
                        addMbsvh = false;
                    }
                }
                if (addMbsvh) {
                    effectsToCancel.add(mbsvh);
                }
                if (stat == MapleBuffStat.SUMMON || stat == MapleBuffStat.PUPPET || stat == MapleBuffStat.REAPER) {
                    final int summonId = mbsvh.effect.getSourceId();
                    final MapleSummon summon = owner.getSummons().get(summonId);
                    if (summon != null) {
                        owner.getMap().broadcastMessage(MaplePacketCreator.removeSummon(summon, true));
                        owner.getMap().removeMapObject(summon);
                        owner.removeVisibleMapObject(summon);
                        owner.getSummons().remove(summonId);
                        if (summon.getSkill() == 1321007) {
                            if (beholderHealingSchedule != null) {
                                beholderHealingSchedule.cancel(false);
                                beholderHealingSchedule = null;
                            }
                            if (beholderBuffSchedule != null) {
                                beholderBuffSchedule.cancel(false);
                                beholderBuffSchedule = null;
                            }
                        }
                    }
                } else if (stat == MapleBuffStat.DRAGONBLOOD) {
                    if (dragonBloodSchedule != null) {
                        dragonBloodSchedule.cancel(false);
                        dragonBloodSchedule = null;
                    }
                } else if (stat == MapleBuffStat.ILLUSION) {
                    owner.disposeClones();
                    clonez = true;
                }
            }
        }
        for (MapleBuffStatValueHolder cancelEffectCancelTasks : effectsToCancel) {
            if (getBuffStats(cancelEffectCancelTasks.effect, cancelEffectCancelTasks.startTime).size() == 0) {
                if (cancelEffectCancelTasks.schedule != null) {
                    cancelEffectCancelTasks.schedule.cancel(false);
                }
            }
        }
        return clonez;
    }

    // --- Cancel ---

    public void cancelEffect(final MapleStatEffect effect, final boolean overwrite, final long startTime) {
        cancelEffect(effect, overwrite, startTime, effect.getStatups());
    }

    public void cancelEffect(final MapleStatEffect effect, final boolean overwrite, final long startTime, List<
            Pair<MapleBuffStat, Integer>> statups) {
        owner.getActor().execute(() -> {
            List<MapleBuffStat> buffstats;
            if (!overwrite) {
                buffstats = getBuffStats(effect, startTime);
            } else {
                buffstats = new ArrayList<>(statups.size());
                for (Pair<MapleBuffStat, Integer> statup : statups) {
                    buffstats.add(statup.getLeft());
                }
            }
            if (buffstats.isEmpty()) {
                return;
            }
            final boolean clonez = deregisterBuffStats(buffstats);
            if (effect.isMagicDoor()) {
                if (!owner.getDoors().isEmpty()) {
                    MapleDoor door = owner.getDoors().iterator().next();
                    for (MapleCharacter chr : door.getTarget().getCharacters()) {
                        door.sendDestroyData(chr.getClient());
                    }
                    for (MapleCharacter chr : door.getTown().getCharacters()) {
                        door.sendDestroyData(chr.getClient());
                    }
                    for (MapleDoor destroyDoor : owner.getDoors()) {
                        door.getTarget().removeMapObject(destroyDoor);
                        door.getTown().removeMapObject(destroyDoor);
                    }
                    owner.removeDoor();
                    owner.silentPartyUpdate();
                }
            } else if (effect.isMonsterRiding_()) {
                owner.getMount().cancelSchedule();
            } else if (effect.isMonsterRiding()) {
                cancelEffectFromBuffStat(MapleBuffStat.MECH_CHANGE);
            } else if (effect.isAranCombo()) {
                combo = 0;
            }
            if (!overwrite) {
                if (effect.isMonsterS()) {
                    cancelPlayerBuffs(buffstats, effect);
                } else {
                    cancelPlayerBuffs(buffstats);
                }
                if (effect.isHide() && owner.getClient().getChannelServer().getPlayerStorage().getCharacterById(owner.getId()) != null) {
                    owner.setHidden(false);
                    owner.getMap().broadcastMessage(owner, MaplePacketCreator.spawnPlayerMapobject(owner), false);

                    for (final client.inventory.MaplePet pet : owner.getPets()) {
                        if (pet.getSummoned()) {
                            owner.getMap().broadcastMessage(owner, PetPacket.showPet(owner, pet, false, false), false);
                        }
                    }
                    for (final WeakReference<MapleCharacter> chr : owner.getClones()) {
                        if (chr.get() != null) {
                            owner.getMap().broadcastMessage(chr.get(), MaplePacketCreator.spawnPlayerMapobject(chr.get()), false);
                        }
                    }
                }
            }
            if (!clonez) {
                for (WeakReference<MapleCharacter> chr : owner.getClones()) {
                    if (chr.get() != null) {
                        chr.get().getBuffsComp().cancelEffect(effect, overwrite, startTime);
                    }
                }
            }
        });
    }

    public void cancelBuffStats(MapleBuffStat... stat) {
        onActor(() -> {
            List<MapleBuffStat> buffStatList = Arrays.asList(stat);
            deregisterBuffStats(buffStatList);
            cancelPlayerBuffs(buffStatList);
        });
    }

    public void cancelEffectFromBuffStat(MapleBuffStat stat) {
        onActor(() -> {
            if (effects.get(stat) != null) {
                cancelEffect(effects.get(stat).effect, false, -1);
            }
        });
    }

    // --- cancelPlayerBuffs ---

    private void cancelPlayerBuffs(List<MapleBuffStat> buffstats) {
        boolean write = owner.getClient().getChannelServer().getPlayerStorage().getCharacterById(owner.getId()) != null;
        if (buffstats.contains(MapleBuffStat.HOMING_BEACON)) {
            if (write) {
                owner.getClient().getSession().write(MaplePacketCreator.cancelHoming());
            }
        } else if (buffstats.contains(MapleBuffStat.骑兽技能)) {
            owner.getClient().getSession().write(MaplePacketCreator.cancelBuffMONSTER(buffstats));
            owner.getMap().broadcastMessage(owner, MaplePacketCreator.cancelForeignBuffMONSTER(owner.getId(), buffstats), false);
        } else {
            owner.getClient().getSession().write(MaplePacketCreator.cancelBuff(buffstats));
            owner.getMap().broadcastMessage(owner, MaplePacketCreator.cancelForeignBuff(owner.getId(), buffstats), false);
        }
    }

    private void cancelPlayerBuffs(List<MapleBuffStat> buffstats, final MapleStatEffect effect) {
        if (effect.isMonsterS()) {
            owner.getClient().getSession().write(MaplePacketCreator.cancelBuffMONSTERS(buffstats));
            owner.getMap().broadcastMessage(owner, MaplePacketCreator.cancelForeignBuffMONSTERS(owner.getId(), buffstats), false);
        }
    }

    // --- Batch operations ---

    public void dispel() {
        if (!owner.isHidden()) {
            final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());
            for (MapleBuffStatValueHolder mbsvh : allBuffs) {
                if (mbsvh.effect.isSkill() && mbsvh.schedule != null && !mbsvh.effect.isMorph()) {
                    cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                }
            }
        }
    }

    public void dispelSkill(int skillid) {
        final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());

        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (skillid == 0) {
                if (mbsvh.effect.isSkill() && (mbsvh.effect.getSourceId() == 4331003 || mbsvh.effect.getSourceId() == 4331002 || mbsvh.effect.getSourceId() == 4341002 || mbsvh.effect.getSourceId() == 22131001 || mbsvh.effect.getSourceId() == 1321007 || mbsvh.effect.getSourceId() == 2121005 || mbsvh.effect.getSourceId() == 2221005 || mbsvh.effect.getSourceId() == 2311006 || mbsvh.effect.getSourceId() == 2321003 || mbsvh.effect.getSourceId() == 3111002 || mbsvh.effect.getSourceId() == 3111005 || mbsvh.effect.getSourceId() == 3211002 || mbsvh.effect.getSourceId() == 3211005 || mbsvh.effect.getSourceId() == 4111002)) {
                    cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                    break;
                }
            } else if (mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skillid) {
                cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                break;
            }
        }
    }

    public void dispelBuff(int skillid) {
        final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());

        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (mbsvh.effect.getSourceId() == skillid) {
                cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                break;
            }
        }
    }

    public void cancelAllBuffs_() {
        effects.clear();
    }

    public void cancelAllBuffs() {
        final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());

        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            cancelEffect(mbsvh.effect, false, mbsvh.startTime);
        }
    }

    public void cancelMorphs() {
        final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());

        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            switch (mbsvh.effect.getSourceId()) {
                case 5111005:
                case 5121003:
                case 15111002:
                case 13111005:
                    return;
                default:
                    if (mbsvh.effect.isMorph()) {
                        cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                        continue;
                    }
            }
        }
    }

    public int getMorphState() {
        LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());
        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (mbsvh.effect.isMorph()) {
                return mbsvh.effect.getSourceId();
            }
        }
        return -1;
    }

    public void silentGiveBuffs(List<PlayerBuffValueHolder> buffs) {
        if (buffs == null) {
            return;
        }
        for (PlayerBuffValueHolder mbsvh : buffs) {
            mbsvh.effect.silentApplyBuff(owner, mbsvh.startTime);
        }
    }

    public List<PlayerBuffValueHolder> getAllBuffs() {
        List<PlayerBuffValueHolder> ret = new ArrayList<>();
        LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());
        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            ret.add(new PlayerBuffValueHolder(mbsvh.startTime, mbsvh.effect));
        }
        return ret;
    }

    public void cancelMagicDoor() {
        final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<>(effects.values());

        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (mbsvh.effect.isMagicDoor()) {
                cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                break;
            }
        }
    }

    // --- Skill effect handlers ---

    public void handleEnergyCharge(final int skillid, final int targets) {
        onActor(() -> {
            final ISkill echskill = SkillFactory.getSkill(skillid);
            final byte skilllevel = owner.getSkillLevel(echskill);
            if (skilllevel > 0) {
                final MapleStatEffect echeff = echskill.getEffect(skilllevel);
                if (targets > 0) {
                    if (getBuffedValue(MapleBuffStat.ENERGY_CHARGE) == null) {
                        echeff.applyEnergyBuff(owner, true);
                    } else {
                        Integer energyLevel = getBuffedValue(MapleBuffStat.ENERGY_CHARGE);
                        if (energyLevel <= 15000) {
                            energyLevel += (echeff.getX() * targets);

                            owner.getClient().getSession().write(MaplePacketCreator.showOwnBuffEffect(skillid, 2));
                            owner.getMap().broadcastMessage(owner, MaplePacketCreator.showBuffeffect(owner.getId(), skillid, 2), false);

                            if (energyLevel >= 15000) {
                                energyLevel = 15000;
                            }

                            List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.ENERGY_CHARGE, energyLevel));
                            owner.getClient().getSession().write(MaplePacketCreator.能量条(stat, energyLevel / 1000));
                            setBuffedValue(MapleBuffStat.ENERGY_CHARGE, Integer.valueOf(energyLevel));
                            Timer.BUFF.schedule(() -> {
                                Integer el = 0;
                                setBuffedValue(MapleBuffStat.ENERGY_CHARGE, Integer.valueOf(el));
                                List<Pair<MapleBuffStat, Integer>> s = Collections.singletonList(new Pair<>(MapleBuffStat.ENERGY_CHARGE, el));
                                owner.getClient().getSession().write(MaplePacketCreator.能量条(s, 0));
                            }, 3 * 60 * 1000);
                        }
                    }
                }
            }
        });
    }

    public void handleBattleshipHP(int damage) {
        onActor(() -> {
            if (isActiveBuffedValue(5221006)) {
                battleshipHP -= damage;
                dirtyTracker.mark(DirtyTracker.Category.CORE);
                if (battleshipHP <= 0) {
                    battleshipHP = 0;
                    final MapleStatEffect effect = getStatForBuff(MapleBuffStat.骑兽技能);
                    owner.getClient().getSession().write(MaplePacketCreator.skillCooldown(5221006, effect.getCooldown()));
                    owner.addCooldown(5221006, System.currentTimeMillis(), effect.getCooldown() * 1000);
                    dispelSkill(5221006);
                }
            }
        });
    }

    public void handleOrbgain() {
        onActor(() -> {
            int orbcount = getBuffedValue(MapleBuffStat.COMBO);
            ISkill comboSkill;
            ISkill advcombo;

            switch (owner.getJob()) {
                case 1110:
                case 1111:
                case 1112:
                    comboSkill = SkillFactory.getSkill(11111001);
                    advcombo = SkillFactory.getSkill(11110005);
                    break;
                default:
                    comboSkill = SkillFactory.getSkill(1111002);
                    advcombo = SkillFactory.getSkill(1120003);
                    break;
            }

            MapleStatEffect ceffect = null;
            int advComboSkillLevel = owner.getSkillLevel(advcombo);
            if (advComboSkillLevel > 0) {
                ceffect = advcombo.getEffect(advComboSkillLevel);
            } else if (owner.getSkillLevel(comboSkill) > 0) {
                ceffect = comboSkill.getEffect(owner.getSkillLevel(comboSkill));
            } else {
                return;
            }

            if (orbcount < ceffect.getX() + 1) {
                int neworbcount = orbcount + 1;
                if (advComboSkillLevel > 0 && ceffect.makeChanceResult()) {
                    if (neworbcount < ceffect.getX() + 1) {
                        neworbcount++;
                    }
                }
                List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.COMBO, neworbcount));
                setBuffedValue(MapleBuffStat.COMBO, neworbcount);
                int duration = ceffect.getDuration();
                duration += (int) ((getBuffedStarttime(MapleBuffStat.COMBO) - System.currentTimeMillis()));

                owner.getClient().getSession().write(MaplePacketCreator.giveBuff(comboSkill.getId(), duration, stat, ceffect));
                owner.getMap().broadcastMessage(owner, MaplePacketCreator.giveForeignBuff(owner, owner.getId(), stat, ceffect), false);
            }
        });
    }

    public void handleOrbconsume() {
        onActor(() -> {
            ISkill comboSkill;

            switch (owner.getJob()) {
                case 1110:
                case 1111:
                    comboSkill = SkillFactory.getSkill(11111001);
                    break;
                default:
                    comboSkill = SkillFactory.getSkill(1111002);
                    break;
            }
            if (owner.getSkillLevel(comboSkill) <= 0) {
                return;
            }
            MapleStatEffect ceffect = getStatForBuff(MapleBuffStat.COMBO);
            if (ceffect == null) {
                return;
            }
            List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.COMBO, 1));
            setBuffedValue(MapleBuffStat.COMBO, 1);
            int duration = ceffect.getDuration();
            duration += (int) ((getBuffedStarttime(MapleBuffStat.COMBO) - System.currentTimeMillis()));

            owner.getClient().getSession().write(MaplePacketCreator.giveBuff(comboSkill.getId(), duration, stat, ceffect));
            owner.getMap().broadcastMessage(owner, MaplePacketCreator.giveForeignBuff(owner, owner.getId(), stat, ceffect), false);
        });
    }

    // --- Private helpers ---

    private void prepareDragonBlood(final MapleStatEffect bloodEffect) {
        if (dragonBloodSchedule != null) {
            dragonBloodSchedule.cancel(false);
        }
        dragonBloodSchedule = Timer.BUFF.register(() -> {
            owner.getActor().execute(() -> {
                if (owner.getStat().getHp() - bloodEffect.getX() > 1) {
                    cancelBuffStats(MapleBuffStat.DRAGONBLOOD);
                } else {
                    owner.addHP(-bloodEffect.getX());
                    owner.getClient().getSession().write(MaplePacketCreator.showOwnBuffEffect(bloodEffect.getSourceId(), 5));
                    owner.getMap().broadcastMessage(owner, MaplePacketCreator.showBuffeffect(owner.getId(), bloodEffect.getSourceId(), 5), false);
                }
            });
        }, 4000, 4000);
    }

    public void checkBerserk() {
        if (BerserkSchedule != null) {
            BerserkSchedule.cancel(false);
            BerserkSchedule = null;
        }

        final ISkill BerserkX = SkillFactory.getSkill(1320006);
        final int skilllevel = owner.getSkillLevel(BerserkX);
        if (skilllevel >= 1) {
            final MapleStatEffect ampStat = BerserkX.getEffect(skilllevel);
            owner.getStat().Berserk = owner.getStat().getHp() * 100 / owner.getStat().getMaxHp() <= ampStat.getX();
            owner.getClient().getSession().write(MaplePacketCreator.showOwnBuffEffect(1320006, 1, (byte) (owner.getStat().Berserk ? 1 : 0)));
            owner.getMap().broadcastMessage(owner, MaplePacketCreator.showBuffeffect(owner.getId(), 1320006, 1, (byte) (owner.getStat().Berserk ? 1 : 0)), false);

            BerserkSchedule = Timer.BUFF.schedule(() -> {
                owner.getActor().execute(() -> checkBerserk());
            }, 10000);
        }
    }

    private void prepareBeholderEffect() {
        if (beholderHealingSchedule != null) {
            beholderHealingSchedule.cancel(false);
        }
        if (beholderBuffSchedule != null) {
            beholderBuffSchedule.cancel(false);
        }
        ISkill bHealing = SkillFactory.getSkill(1320008);
        final int bHealingLvl = owner.getSkillLevel(bHealing);
        final int berserkLvl = owner.getSkillLevel(SkillFactory.getSkill(1320006));

        if (bHealingLvl > 0) {
            final MapleStatEffect healEffect = bHealing.getEffect(bHealingLvl);
            int healInterval = healEffect.getX() * 1000;
            beholderHealingSchedule = Timer.BUFF.register(() -> {
                owner.getActor().execute(() -> {
                    int remhppercentage = (int) Math.ceil((owner.getStat().getHp() * 100.0) / owner.getStat().getMaxHp());
                    if (berserkLvl == 0 || remhppercentage >= berserkLvl + 10) {
                        owner.addHP(healEffect.getHp());
                    }
                    owner.getClient().getSession().write(MaplePacketCreator.showOwnBuffEffect(1321007, 2));
                    owner.getMap().broadcastMessage(MaplePacketCreator.summonSkill(owner.getId(), 1321007, 5));
                    owner.getMap().broadcastMessage(owner, MaplePacketCreator.showBuffeffect(owner.getId(), 1321007, 2), false);
                });
            }, healInterval, healInterval);
        }
        ISkill bBuff = SkillFactory.getSkill(1320009);
        final int bBuffLvl = owner.getSkillLevel(bBuff);
        if (bBuffLvl > 0) {
            final MapleStatEffect buffEffect = bBuff.getEffect(bBuffLvl);
            int buffInterval = buffEffect.getX() * 1000;
            beholderBuffSchedule = Timer.BUFF.register(() -> {
                owner.getActor().execute(() -> {
                    buffEffect.applyTo(owner);
                    owner.getClient().getSession().write(MaplePacketCreator.showOwnBuffEffect(1321007, 2));
                    owner.getMap().broadcastMessage(MaplePacketCreator.summonSkill(owner.getId(), 1321007, Randomizer.nextInt(3) + 6));
                    owner.getMap().broadcastMessage(owner, MaplePacketCreator.showBuffeffect(owner.getId(), 1321007, 2), false);
                });
            }, buffInterval, buffInterval);
        }
    }

    private void prepareRecovery() {
        lastRecoveryTime = System.currentTimeMillis();
    }

    public boolean canRecovery() {
        return lastRecoveryTime > 0 && lastRecoveryTime + 5000 < System.currentTimeMillis() + 5000;
    }

    public void doRecovery() {
        MapleStatEffect eff = getStatForBuff(MapleBuffStat.RECOVERY);
        if (eff != null) {
            prepareRecovery();
            if (owner.getStat().getHp() > owner.getStat().getCurrentMaxHp()) {
                cancelEffectFromBuffStat(MapleBuffStat.RECOVERY);
            } else {
                owner.healHP(eff.getX());
            }
        }
    }
}
