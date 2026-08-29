package com.example.scrollspellicons.mixin;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Connector can expose Flashback's classes while omitting its namespace from
 * Minecraft's ResourceManager.  The official font is still present on the
 * mapped mod class path, so only the missing-resource branch is bridged here.
 * The normal ResourceManager path remains authoritative whenever it succeeds.
 */
@Mixin(targets = "com.moulberry.flashback.editor.ui.ReplayUI", remap = false)
public abstract class FlashbackFontResourceMixin {
    private static final String FLASHBACK_NAMESPACE = "flashback";
    private static final String FONT_ROOT = "assets/flashback/";

    @Inject(method = "loadFont", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void ironMagicDuel$loadMappedFont(String fileName,
                                                       CallbackInfoReturnable<byte[]> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(FLASHBACK_NAMESPACE, fileName);
        if (minecraft.getResourceManager().getResource(id).isPresent()) {
            return;
        }

        String resourcePath = FONT_ROOT + fileName;
        try (InputStream stream = openOnMappedModClasspath(resourcePath)) {
            if (stream == null) {
                return;
            }
            cir.setReturnValue(stream.readAllBytes());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read official Flashback font " + resourcePath,
                    exception);
        }
    }

    private static InputStream openOnMappedModClasspath(String resourcePath) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        InputStream stream = contextLoader == null ? null : contextLoader.getResourceAsStream(resourcePath);
        if (stream != null) {
            return stream;
        }

        ClassLoader ownLoader = FlashbackFontResourceMixin.class.getClassLoader();
        return ownLoader == contextLoader ? null : ownLoader.getResourceAsStream(resourcePath);
    }
}
