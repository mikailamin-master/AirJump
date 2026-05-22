package org.ma.airjump.mixin;

import net.minecraft.client.Minecraft;
import org.ma.airjump.AirJump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void airjump$tick(CallbackInfo ci) {
        AirJump.client_tick((Minecraft) (Object) this);
    }
}
