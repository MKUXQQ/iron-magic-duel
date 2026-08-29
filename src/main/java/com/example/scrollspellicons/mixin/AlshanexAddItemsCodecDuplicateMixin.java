package com.example.scrollspellicons.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Alshanex 4.0.2 registers the same AddItemsModifier.CODEC through two
 * DeferredRegisters. MappedRegistry rejects the second registration by value.
 * This guard only returns the existing holder for that exact, known case.
 */
@Mixin(MappedRegistry.class)
public abstract class AlshanexAddItemsCodecDuplicateMixin<T> {
    @Inject(method = "register(Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;Lnet/minecraft/core/RegistrationInfo;)Lnet/minecraft/core/Holder$Reference;",
            at = @At("HEAD"), cancellable = true)
    private void ironMagicDuel$skipExactAlshanexDuplicate(
            ResourceKey<T> key, T value, RegistrationInfo registrationInfo,
            CallbackInfoReturnable<Holder.Reference<T>> callback) {
        MappedRegistry<T> registry = (MappedRegistry<T>) (Object) this;
        if (!isAlshanexLootCodecRegistry(registry)) {
            return;
        }
        ResourceLocation incoming = key.location();
        if (!isAllowedAlshanexId(incoming)) {
            return;
        }
        // MappedRegistry's identity map makes this an exact object-identity
        // check, never a Codec string or exception-text match.
        java.util.Optional<ResourceKey<T>> existingKey = registry.getResourceKey(value);
        if (existingKey.isEmpty() || !isAllowedAlshanexId(existingKey.get().location())) {
            // First registration (or another namespace) follows vanilla.
            return;
        }
        registry.getHolder(existingKey.get()).ifPresent(callback::setReturnValue);
    }

    private static <T> boolean isAlshanexLootCodecRegistry(MappedRegistry<T> registry) {
        return registry.key().equals(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS);
    }

    private static boolean isAllowedAlshanexId(ResourceLocation id) {
        return id != null
                && id.getNamespace().equals("alshanex_familiars")
                && (id.getPath().equals("add_items")
                || id.getPath().equals("add_items_modifier"));
    }
}
