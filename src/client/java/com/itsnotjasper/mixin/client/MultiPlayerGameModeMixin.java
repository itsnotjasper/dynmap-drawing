package com.itsnotjasper.mixin.client;

import com.itsnotjasper.dynmap.input.LineToolClickHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void dynmapDraw$blockStartDestroy(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (LineToolClickHandler.shouldHandleInteraction(Minecraft.getInstance())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void dynmapDraw$blockContinueDestroy(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (LineToolClickHandler.shouldHandleInteraction(Minecraft.getInstance())) {
            cir.setReturnValue(false);
        }
    }
}
