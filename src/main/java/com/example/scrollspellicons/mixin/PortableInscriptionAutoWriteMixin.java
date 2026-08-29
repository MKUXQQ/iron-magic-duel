package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side compatibility for the external G-key portable inscription
 * table.  It reuses Iron's own clickMenuButton(-1) path, so the original
 * InscribeSpellEvent, spell validation and item consumption remain the
 * authority.  The guard and re-entry flag make this edge-triggered instead
 * of a per-tick consumer.
 */
@Mixin(value = InscriptionTableMenu.class, remap = false)
public abstract class PortableInscriptionAutoWriteMixin {
    private static final String PORTABLE_MENU =
            "com.example.portableinscriptiontable.menu.PortableInscriptionTableMenu";

    @Shadow private Player player;
    @Shadow private int selectedSpellIndex;
    @Shadow public abstract void setSelectedSpell(int index);
    @Shadow public abstract boolean clickMenuButton(Player player, int id);
    @Shadow public abstract net.minecraft.world.inventory.Slot getSpellBookSlot();
    @Shadow public abstract net.minecraft.world.inventory.Slot getScrollSlot();

    private boolean ironMagicDuel$autoWriteInProgress;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("TAIL"), remap = false)
    private void ironMagicDuel$tryOnOpen(int containerId, Inventory inventory,
                                         ContainerLevelAccess access, CallbackInfo ci) {
        ironMagicDuel$tryAutoWrite();
    }

    @Inject(method = "slotsChanged", at = @At("TAIL"), remap = false)
    private void ironMagicDuel$tryWhenInputsChange(Container container, CallbackInfo ci) {
        ironMagicDuel$tryAutoWrite();
    }

    @Inject(method = "setSelectedSpell", at = @At("TAIL"), remap = false)
    private void ironMagicDuel$tryWhenSpellChanges(int index, CallbackInfo ci) {
        ironMagicDuel$tryAutoWrite();
    }

    private void ironMagicDuel$tryAutoWrite() {
        if (!PORTABLE_MENU.equals(this.getClass().getName())
                || ironMagicDuel$autoWriteInProgress || player == null
                || player.level().isClientSide) {
            return;
        }

        ItemStack book = getSpellBookSlot().getItem();
        ItemStack scroll = getScrollSlot().getItem();
        // No input means no attempt and, importantly, no consumption.
        if (book.isEmpty() || scroll.isEmpty()) return;

        ironMagicDuel$autoWriteInProgress = true;
        try {
            // The screen normally selects the scroll's first spell by clicking
            // a row.  Portable input has only one source spell, so select it
            // automatically before invoking the authoritative confirm path.
            if (selectedSpellIndex < 0) setSelectedSpell(0);
            if (selectedSpellIndex >= 0) clickMenuButton(player, -1);
        } finally {
            ironMagicDuel$autoWriteInProgress = false;
        }
    }
}
