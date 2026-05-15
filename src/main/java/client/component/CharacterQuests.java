package client.component;

import client.DirtyTracker;
import client.MapleCharacter;
import client.MapleQuestStatus;
import server.quest.MapleQuest;
import tools.MaplePacketCreator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class CharacterQuests {

    private final MapleCharacter owner;
    private final DirtyTracker dirtyTracker;
    private final Map<MapleQuest, MapleQuestStatus> quests = new LinkedHashMap<>();
    private final Map<Integer, String> questinfo = new LinkedHashMap<>();

    public CharacterQuests(MapleCharacter owner, DirtyTracker dirtyTracker) {
        this.owner = owner;
        this.dirtyTracker = dirtyTracker;
    }

    // --- Quest info ---

    @Deprecated
    public Map<Integer, String> getInfoQuestMapInternal() {
        return questinfo;
    }

    public Map<Integer, String> getInfoQuestMap() {
        return Collections.unmodifiableMap(questinfo);
    }

    public void questInfoPacket(final tools.data.output.MaplePacketLittleEndianWriter mplew) {
        mplew.writeShort(questinfo.size());
        for (final Entry<Integer, String> q : questinfo.entrySet()) {
            mplew.writeShort(q.getKey());
            mplew.writeMapleAsciiString(q.getValue() == null ? "" : q.getValue());
        }
    }

    public void updateInfoQuest(final int questid, final String data) {
        owner.getActor().execute(() -> {
            questinfo.put(questid, data);
            owner.getClient().getSession().write(MaplePacketCreator.updateInfoQuest(questid, data));
            dirtyTracker.mark(DirtyTracker.Category.QUEST_INFO);
        });
    }

    public String getInfoQuest(final int questid) {
        if (questinfo.containsKey(questid)) {
            return questinfo.get(questid);
        }
        return "";
    }

    // --- Quest status ---

    @Deprecated
    public Map<MapleQuest, MapleQuestStatus> getQuestMapInternal() {
        return quests;
    }

    public Map<MapleQuest, MapleQuestStatus> getQuestMap() {
        return Collections.unmodifiableMap(quests);
    }

    public int getQuestStatus(int quest) {
        return getQuest(MapleQuest.getInstance(quest)).getStatus();
    }

    public MapleQuestStatus getQuest(final MapleQuest quest) {
        if (!quests.containsKey(quest)) {
            return new MapleQuestStatus(quest, (byte) 0);
        }
        return quests.get(quest);
    }

    public void setQuestAdd(int quest) {
        setQuestAddZ(MapleQuest.getInstance(quest), (byte) 2, null);
    }

    public void setQuestAddZ(final MapleQuest quest, final byte status, final String customData) {
        owner.getActor().execute(() -> {
            final MapleQuestStatus stat = new MapleQuestStatus(quest, status);
            stat.setCustomData(customData);
            quests.put(quest, stat);
            dirtyTracker.mark(DirtyTracker.Category.QUEST_STATUS);
        });
    }

    public void setQuestAdd(final MapleQuest quest, final byte status, final String customData) {
        owner.getActor().execute(() -> {
            if (!quests.containsKey(quest)) {
                final MapleQuestStatus stat = new MapleQuestStatus(quest, status);
                stat.setCustomData(customData);
                quests.put(quest, stat);
                dirtyTracker.mark(DirtyTracker.Category.QUEST_STATUS);
            }
        });
    }

    public MapleQuestStatus getQuestNAdd(final MapleQuest quest) {
        if (!quests.containsKey(quest)) {
            final MapleQuestStatus status = new MapleQuestStatus(quest, (byte) 0);
            quests.put(quest, status);
            dirtyTracker.mark(DirtyTracker.Category.QUEST_STATUS);
            return status;
        }
        return quests.get(quest);
    }

    public MapleQuestStatus getQuestRemove(MapleQuest quest) {
        dirtyTracker.mark(DirtyTracker.Category.QUEST_STATUS);
        return quests.remove(quest);
    }

    public MapleQuestStatus getQuestNoAdd(final MapleQuest quest) {
        return quests.get(quest);
    }

    public void updateQuest(final MapleQuestStatus quest) {
        updateQuest(quest, false);
    }

    public void updateQuest(final MapleQuestStatus quest, final boolean update) {
        owner.getActor().execute(() -> {
            quests.put(quest.getQuest(), quest);
            dirtyTracker.mark(DirtyTracker.Category.QUEST_STATUS);
            if (!(quest.isCustom())) {
                owner.getClient().getSession().write(MaplePacketCreator.updateQuest(quest));
                if (quest.getStatus() == 1 && !update) {
                    owner.getClient().getSession().write(MaplePacketCreator.updateQuestInfo(owner, quest.getQuest().getId(), quest.getNpc(), (byte) 8));
                }
            }
        });
    }

    // --- Queries ---

    public int getNumQuest() {
        int i = 0;
        for (final MapleQuestStatus q : quests.values()) {
            if (q.getStatus() == 2 && !(q.isCustom())) {
                i++;
            }
        }
        return i;
    }

    public void mobKilled(final int id, final int skillID) {
        for (MapleQuestStatus q : quests.values()) {
            if (q.getStatus() != 1 || !q.hasMobKills()) {
                continue;
            }
            if (q.mobKilled(id, skillID)) {
                owner.getClient().getSession().write(MaplePacketCreator.updateQuestMobKills(q));
                if (q.getQuest().canComplete(owner, null)) {
                    owner.getClient().getSession().write(MaplePacketCreator.getShowQuestCompletion(q.getQuest().getId()));
                }
            }
        }
    }

    public List<MapleQuestStatus> getStartedQuests() {
        List<MapleQuestStatus> ret = new LinkedList<>();
        for (MapleQuestStatus q : quests.values()) {
            if (q.getStatus() == 1 && !(q.isCustom())) {
                ret.add(q);
            }
        }
        return ret;
    }

    public List<MapleQuestStatus> getCompletedQuests() {
        List<MapleQuestStatus> ret = new LinkedList<>();
        for (MapleQuestStatus q : quests.values()) {
            if (q.getStatus() == 2 && !(q.isCustom())) {
                ret.add(q);
            }
        }
        return ret;
    }
}
