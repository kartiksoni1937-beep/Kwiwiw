package com.corz.client.mixins;

import ai.auth.Module;
import com.corz.client.utils.prestige.PrestigeSupport;
import com.corz.client.utils.prestige.VisualRotationManager;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntityRenderer.class})
public class MixinLivingEntityRenderer {
   @Inject(
      method = {"method_62355(Lnet/minecraft/class_1309;Lnet/minecraft/class_10042;F)V"},
      at = {@At("TAIL")}
   )
   private void corzSilentAimPose(LivingEntity renderedEntity, LivingEntityRenderState livingEntityRenderState, float tickDelta, CallbackInfo callbackInfo) {
      if (renderedEntity == Module.mc.player) {
         VisualRotationManager visual = PrestigeSupport.visualRotationManager;
         if (visual.isActive()) {
            livingEntityRenderState.bodyYaw = visual.getRenderYaw();
            livingEntityRenderState.relativeHeadYaw = 0.0F;
            livingEntityRenderState.pitch = visual.getRenderPitch();
         }
      }
   }
}
