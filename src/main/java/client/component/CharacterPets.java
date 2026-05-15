package client.component;

import client.MapleCharacter;
import client.SkillFactory;
import client.inventory.IItem;
import client.inventory.MapleInventoryIdentifier;
import client.inventory.MapleInventoryType;
import client.inventory.MaplePet;
import client.inventory.PetDataFactory;
import com.github.mrzhqiang.maplestory.timer.Timer;
import com.github.mrzhqiang.maplestory.wz.element.data.Vector;
import constants.EquipSlot;
import constants.GameConstants;
import server.MapleInventoryManipulator;
import tools.MaplePacketCreator;
import tools.packet.PetPacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

public final class CharacterPets {

    private final MapleCharacter owner;
    private final List<MaplePet> pets;
    private final byte[] petStore;
    private ScheduledFuture<?> petHungerTask;

    public CharacterPets(MapleCharacter owner) {
        this.owner = owner;
        this.pets = new ArrayList<>();
        this.petStore = new byte[3];
        Arrays.fill(petStore, EquipSlot.SENTINEL);
    }

    // --- Pet store (saved summon slots) ---

    public byte[] getPetStores() {
        return petStore;
    }

    public void spawnSavedPets() {
        owner.getActor().execute(() -> {
            for (int i = 0; i < petStore.length; i++) {
                if (petStore[i] > -1) {
                    spawnPet(petStore[i], false, true);
                }
            }
            owner.getClient().getSession().write(PetPacket.petStatUpdate(owner));
        });
    }

    // --- Pet list accessors ---

    /** Returns the pet at the given position in the pets list (list-index). */
    public MaplePet getPet(final int index) {
        return index >= 0 && index < pets.size() ? pets.get(index) : null;
    }

    /** Returns the summoned pet at the given summoned-only index (0=first summoned, 1=second, etc.). */
    public MaplePet getSummonedPet(final int index) {
        byte count = 0;
        for (final MaplePet pet : pets) {
            if (pet.getSummoned()) {
                if (count == index) {
                    return pet;
                }
                count++;
            }
        }
        return null;
    }

    @Deprecated
    public List<MaplePet> getPets() {
        return pets;
    }

    public List<MaplePet> getPetsUnmodifiable() {
        return Collections.unmodifiableList(pets);
    }

    public int getNoPets() {
        return pets.size();
    }

    public byte getPetIndex(final MaplePet petz) {
        byte count = 0;
        for (final MaplePet pet : pets) {
            if (pet.getSummoned()) {
                if (pet == petz) {
                    return count;
                }
                count++;
            }
        }
        return -1;
    }

    public byte getPetIndex(final int petId) {
        byte count = 0;
        for (final MaplePet pet : pets) {
            if (pet.getSummoned()) {
                if (pet.getUniqueId() == petId) {
                    return count;
                }
                count++;
            }
        }
        return -1;
    }

    public byte getPetById(final int petId) {
        byte count = 0;
        for (final MaplePet pet : pets) {
            if (pet.getSummoned()) {
                if (pet.getPetItemId() == petId) {
                    return count;
                }
                count++;
            }
        }
        return -1;
    }

    public int getPetSlot(MaplePet pet) {
        if (pets.size() > 0) {
            for (int i = 0; i < pets.size(); i++) {
                if (pets.get(i) != null && pets.get(i).getUniqueId() == pet.getUniqueId()) {
                    return i;
                }
            }
        }
        return -1;
    }

    // --- Pet mutations ---

    public void addPet(final MaplePet pet) {
        if (pets.contains(pet)) {
            pets.remove(pet);
        }
        pets.add(pet);
    }

    public void removePet(MaplePet pet, boolean shiftLeft) {
        pet.setSummoned(0);
    }

    public void removePetCS(MaplePet pet) {
        pets.remove(pet);
    }

    public void unequipAllPets() {
        for (final MaplePet pet : pets) {
            if (pet != null) {
                unequipPet(pet, true, false);
            }
        }
    }

    public void unequipPet(MaplePet pet, boolean shiftLeft, boolean hunger) {
        owner.getActor().execute(() -> {
            if (pet.getSummoned()) {
                pet.saveToDb();
                owner.getClient().getSession().write(PetPacket.updatePet(pet,
                        owner.getInventory(MapleInventoryType.CASH).getItem(pet.getInventoryPosition()), false));
                if (owner.getMap() != null) {
                    owner.getMap().broadcastMessage(owner, PetPacket.showPet(owner, pet, true, hunger), true);
                }
                removePet(pet, shiftLeft);
                owner.getClient().getSession().write(PetPacket.petStatUpdate(owner));
                owner.getClient().getSession().write(MaplePacketCreator.enableActions());

                boolean anySummoned = false;
                for (MaplePet p : getPets()) {
                    if (p.getSummoned()) {
                        anySummoned = true;
                        break;
                    }
                }
                if (!anySummoned) {
                    cancelPetHungerTask();
                }
            }
        });
    }

    // --- Spawn pet ---

    public void spawnPet(byte slot) {
        spawnPet(slot, false, true);
    }

    public void spawnPet(byte slot, boolean lead) {
        spawnPet(slot, lead, true);
    }

    public void spawnPet(byte slot, boolean lead, boolean broadcast) {
        owner.getActor().execute(() -> {
            final IItem item = owner.getInventory(MapleInventoryType.CASH).getItem(slot);
            if (item == null || item.getItemId() > GameConstants.PET_ID_MAX || item.getItemId() < GameConstants.PET_ID_MIN) {
                return;
            }
            switch (item.getItemId()) {
                case GameConstants.EVO_PET_47:
                case GameConstants.EVO_PET_28: {
                    final MaplePet pet = MaplePet.createPet(item.getItemId() + 1, MapleInventoryIdentifier.getInstance());
                    if (pet != null) {
                        MapleInventoryManipulator.addById(owner.getClient(), item.getItemId() + 1, (short) 1, item.getOwner(), pet, GameConstants.EVO_PET_DEFAULT_DAYS, (byte) 0);
                        MapleInventoryManipulator.removeFromSlot(owner.getClient(), MapleInventoryType.CASH, slot, (short) 1, false);
                    }
                    break;
                }
                default: {
                    final MaplePet pet = item.getPet();
                    if (pet != null && (item.getItemId() != GameConstants.DRAGON_PET_ID || pet.getSecondsLeft() > 0)
                            && (item.getExpiration() == -1 || item.getExpiration() > System.currentTimeMillis())) {
                        if (pet.getSummoned()) {
                            unequipPet(pet, true, false);
                        } else {
                            int leadid = GameConstants.getPetLeadSkill(owner.getJob());
                            if (owner.getSkillLevel(SkillFactory.getSkill(leadid)) == 0 && getSummonedPet(0) != null) {
                                unequipPet(getSummonedPet(0), false, false);
                            } else if (lead || owner.getSkillLevel(SkillFactory.getSkill(leadid)) <= 0) {
                                // shiftPetsRight();
                            }
                            final Vector pos = owner.getPosition();
                            pet.setPos(pos);
                            try {
                                pet.setFh(owner.getMap().getFootholds().findBelow(pos).getId());
                            } catch (NullPointerException e) {
                                pet.setFh(0);
                            }
                            pet.setStance(0);
                            pet.setSummoned(slot);

                            addPet(pet);
                            startPetHungerTask();
                            if (broadcast) {
                                owner.getMap().broadcastMessage(owner, PetPacket.showPet(owner, pet, false, false), true);
                                owner.getClient().getSession().write(PetPacket.updatePet(pet,
                                        owner.getInventory(MapleInventoryType.CASH).getItem(pet.getInventoryPosition()), true));
                                owner.getClient().getSession().write(PetPacket.petStatUpdate(owner));
                            }
                        }
                    }
                    break;
                }
            }
            owner.getClient().getSession().write(PetPacket.emptyStatUpdate());
        });
    }

    // --- Pet hunger task ---

    private void startPetHungerTask() {
        if (petHungerTask != null) {
            return;
        }
        petHungerTask = Timer.BUFF.register(() -> {
            if (owner.getClient() == null || !owner.getClient().isLoggedIn()) {
                cancelPetHungerTask();
                return;
            }
            owner.getActor().submit(() -> {
                boolean anySummoned = false;
                for (MaplePet pet : getPets()) {
                    if (pet.getSummoned()) {
                        anySummoned = true;
                        if (pet.getPetItemId() == GameConstants.DRAGON_PET_ID && pet.getSecondsLeft() > 0) {
                            pet.setSecondsLeft(pet.getSecondsLeft() - 1);
                            if (pet.getSecondsLeft() <= 0) {
                                unequipPet(pet, true, true);
                                continue;
                            }
                        }
                        int newFullness = pet.getFullness() - PetDataFactory.getHunger(pet.getPetItemId());
                        if (newFullness <= GameConstants.PET_HUNGER_THRESHOLD) {
                            pet.setFullness(GameConstants.PET_HUNGER_RESET);
                            unequipPet(pet, true, true);
                        } else {
                            pet.setFullness(newFullness);
                            if (owner.getClient() != null && owner.getClient().getSession() != null) {
                                owner.getClient().getSession().write(PetPacket.updatePet(pet,
                                        owner.getInventory(MapleInventoryType.CASH).getItem(pet.getInventoryPosition()), true));
                            }
                        }
                    }
                }
                if (!anySummoned) {
                    cancelPetHungerTask();
                }
            });
        }, GameConstants.PET_HUNGER_INTERVAL_MS, GameConstants.PET_HUNGER_INTERVAL_MS);
    }

    private void cancelPetHungerTask() {
        if (petHungerTask != null) {
            petHungerTask.cancel(false);
            petHungerTask = null;
        }
    }
}
