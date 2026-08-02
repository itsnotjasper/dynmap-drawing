package com.itsnotjasper.mixin.client;

import com.itsnotjasper.dynmap.input.LineToolClickHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void dynmapDraw$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (LineToolClickHandler.onAttackClick()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void dynmapDraw$onStartUseItem(CallbackInfo ci) {
        if (LineToolClickHandler.onUseClick()) {
            ci.cancel();
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void dynmapDraw$onContinueAttack(CallbackInfo ci) {
        if (LineToolClickHandler.shouldHandleInteraction((Minecraft) (Object) this)) {
            ci.cancel();
        }
    }
}
