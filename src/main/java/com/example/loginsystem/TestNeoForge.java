package com.example.loginsystem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
public class TestNeoForge {
    public void test() {
        HolderLookup.Provider p = null;
        CompoundTag t = null;
        ItemStack s = ItemStack.EMPTY;
        s.save(p);
        ItemStack.parse(p, t);
        ItemStack.parseOptional(p, t);
        net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre e = null;
        e.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
    }
}
