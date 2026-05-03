package client.inventory;

import client.MapleCharacter;
import com.google.common.collect.Maps;
import constants.GameConstants;
import server.MapleItemInformationProvider;
import tools.MaplePacketCreator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MapleInventory implements Iterable<IItem>, Serializable {

    private final AtomicInteger slotLimit;
    private final MapleInventoryType type;

    private final Map<Integer, IItem> inventory = Maps.newLinkedHashMap();
    private final List<IItem> removeInventory = new ArrayList<>();
    private final BitSet freeSlots = new BitSet(97);

    public MapleInventory(MapleInventoryType type, int slotLimit) {
        this.slotLimit = new AtomicInteger(slotLimit);
        this.type = type;
        freeSlots.set(1, slotLimit + 1);
    }

    public void addSlot(byte slot) {
        int oldLimit = slotLimit.get();
        this.slotLimit.getAndAdd(slot);
        int newLimit = slotLimit.get();
        if (newLimit > 96) {
            slotLimit.set(96);
            newLimit = 96;
        }
        if (newLimit > oldLimit) {
            freeSlots.set(oldLimit + 1, newLimit + 1);
        }
    }

    public int getSlotLimit() {
        return slotLimit.intValue();
    }

    public void setSlotLimit(int slot) {
        if (slot > 96) {
            slot = 96;
        }
        slotLimit.set(slot);
    }

    /**
     * Returns the item with its slot id if it exists within the inventory,
     * otherwise null is returned
     */
    public IItem findById(int itemId) {
        for (IItem item : inventory.values()) {
            if (item.getItemId() == itemId) {
                return item;
            }
        }
        return null;
    }

    public IItem findByUniqueId(int itemId) {
        for (IItem item : inventory.values()) {
            if (item.getUniqueId() == itemId) {
                return item;
            }
        }
        return null;
    }

    public int countById(int itemId) {
        int possesed = 0;
        for (IItem item : inventory.values()) {
            if (item.getItemId() == itemId) {
                possesed += item.getQuantity();
            }
        }
        return possesed;
    }

    public List<IItem> listById(int itemId) {
        List<IItem> ret = new ArrayList<>();
        for (IItem item : inventory.values()) {
            if (item.getItemId() == itemId) {
                ret.add(item);
            }
        }
        // the linkedhashmap does impose insert order as returned order but we can not guarantee that this is still the
        // correct order - blargh, we could empty the map and reinsert in the correct order after each inventory
        // addition, or we could use an array/list, it's only 255 entries anyway...
        if (ret.size() > 1) {
            Collections.sort(ret);
        }
        return ret;
    }

    public Collection<IItem> list() {
        return inventory.values();
    }

    public Collection<IItem> waitDeleteList() {
        return removeInventory;
    }

    private void removeFromWaitDelete(int itemId) {
        removeInventory.removeIf(it -> it.getItemId() == itemId);
    }

    /**
     * Adds the item to the inventory and returns the assigned slot id
     */
    public int addItem(IItem item) {
        int slotId = getNextFreeSlot();
        if (slotId < 0) {
            return -1;
        }
        inventory.put(slotId, item);
        freeSlots.clear(slotId);
        item.setPosition(slotId);
        removeFromWaitDelete(item.getItemId());
        return slotId;
    }

    public void addFromDB(IItem item) {
        if (item.getPosition() < 0 && !type.equals(MapleInventoryType.EQUIPPED)) {
            return;
        }
        inventory.put(item.getPosition(), item);
        if (item.getPosition() > 0) {
            freeSlots.clear(item.getPosition());
        }
        removeFromWaitDelete(item.getItemId());
    }

    public boolean move2(int sSlot, int dSlot, int slotMax) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        IItem source = this.inventory.get(sSlot);
        IItem target = this.inventory.get(dSlot);
        if (source == null) {
            throw new InventoryException("Trying to move empty slot");
        }
        if (target == null) {
            source.setPosition(dSlot);
            this.inventory.put(dSlot, source);
            this.inventory.remove(sSlot);
            freeSlots.set(sSlot);
            freeSlots.clear(dSlot);
        } else if ((target.getItemId() == source.getItemId()) && (!GameConstants.isThrowingStar(source.getItemId())) && (!GameConstants.isBullet(source.getItemId()))) {
            if (this.type.getType() == MapleInventoryType.EQUIP.getType()) {
                swap(target, source);
            }
            if (source.getQuantity() + target.getQuantity() > slotMax) {
                short rest = (short) (source.getQuantity() + target.getQuantity() - slotMax);
                if (rest + slotMax != source.getQuantity() + target.getQuantity()) {
                    return false;
                }
                source.setQuantity(rest);
                target.setQuantity(slotMax);
            } else {
                target.setQuantity((short) (source.getQuantity() + target.getQuantity()));
                this.inventory.remove(sSlot);
                freeSlots.set(sSlot);
            }
        } else {
            swap(target, source);
        }
        return true;
    }

    public void move(int sSlot, int dSlot, int slotMax) {
        if (dSlot > slotLimit.get()) {
            return;
        }
        Item source = (Item) inventory.get(sSlot);
        Item target = (Item) inventory.get(dSlot);
        if (source == null) {
            throw new InventoryException("Trying to move empty slot");
        }
        if (target == null) {
            source.setPosition(dSlot);
            inventory.put(dSlot, source);
            inventory.remove(sSlot);
            freeSlots.set(sSlot);
            freeSlots.clear(dSlot);
        } else if (target.getItemId() == source.getItemId() && !GameConstants.isThrowingStar(source.getItemId()) && !GameConstants.isBullet(source.getItemId()) && target.getOwner().equals(source.getOwner()) && target.getExpiration() == source.getExpiration()) {
            if (type.getType() == MapleInventoryType.EQUIP.getType() || type.getType() == MapleInventoryType.CASH.getType()) {
                swap(target, source);
            } else if (source.getQuantity() + target.getQuantity() > slotMax) {
                source.setQuantity((short) ((source.getQuantity() + target.getQuantity()) - slotMax));
                target.setQuantity(slotMax);
            } else {
                target.setQuantity((short) (source.getQuantity() + target.getQuantity()));
                inventory.remove(sSlot);
                freeSlots.set(sSlot);
            }
        } else {
            swap(target, source);
        }
    }

    private void swap(IItem source, IItem target) {
        inventory.remove(source.getPosition());
        inventory.remove(target.getPosition());
        int swapPos = source.getPosition();
        source.setPosition(target.getPosition());
        target.setPosition(swapPos);
        inventory.put(source.getPosition(), source);
        inventory.put(target.getPosition(), target);
    }

    public IItem getItem(int slot) {
        return inventory.get(slot);
    }

    public void removeItem(int slot) {
        removeItem(slot, 1, false);
    }

    public void removeItem(int slot, int quantity, boolean allowZero) {
        removeItem(slot, quantity, allowZero, null);
    }

    public void removeItem(int slot, int quantity, boolean allowZero, MapleCharacter chr) {
        IItem item = inventory.get(slot);
        if (item == null) { // TODO is it ok not to throw an exception here?
            return;
        }
        item.setQuantity((short) (item.getQuantity() - quantity));
        if (item.getQuantity() < 0) {
            item.setQuantity((short) 0);
        }
        if (item.getQuantity() == 0 && !allowZero) {
            removeSlot(slot);
        }
        if (chr != null) {
            chr.getClient().sendPacket(MaplePacketCreator.modifyInventory(false, new ModifyInventory(ModifyInventory.Types.REMOVE, item)));
            chr.dropMessage(5, "期限道具[" + MapleItemInformationProvider.getInstance().getName(item.getItemId()) + "]已经过期");
        }
    }

    public void removeSlot(int slot) {
        IItem item = inventory.remove(slot);
        if (item != null) {
            removeInventory.add(item);
            if (slot > 0) {
                freeSlots.set(slot);
            }
        }
    }

    public void moveSlot(int slot) {
        inventory.remove(slot);
        if (slot > 0) {
            freeSlots.set(slot);
        }
    }

    public void dropSlot(int slot) {
        IItem rmItem = inventory.remove(slot);
        removeInventory.add(rmItem);
        if (slot > 0) {
            freeSlots.set(slot);
        }
    }

    public boolean isFull() {
        return inventory.size() >= slotLimit.get();
    }

    public boolean isFull(int margin) {
        return inventory.size() + margin >= slotLimit.get();
    }

    public int getNextFreeSlot() {
        return freeSlots.nextSetBit(1);
    }

    public int getNumFreeSlot() {
        return freeSlots.cardinality();
    }

    public MapleInventoryType getType() {
        return type;
    }

    @Override
    public Iterator<IItem> iterator() {
        return Collections.unmodifiableCollection(inventory.values()).iterator();
    }
    
}
