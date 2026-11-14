package com.corz.client.utils.prestige;

import net.minecraft.util.math.MathHelper;

public class VisualRotationManager implements MinecraftAccess {
   private boolean initialized;
   private float renderYaw;
   private float renderPitch;
   private float prevRenderYaw;
   private float prevRenderPitch;

   public void tick() {
      if (mc.player == null) {
         this.initialized = false;
         return;
      }

      if (!this.initialized) {
         this.renderYaw = mc.player.getYaw();
         this.renderPitch = mc.player.getPitch();
         this.prevRenderYaw = this.renderYaw;
         this.prevRenderPitch = this.renderPitch;
         this.initialized = true;
         return;
      }

      Rotation targetRotation = PrestigeSupport.silentRotationManager.getServerRotation();
      if (targetRotation == null) {
         this.renderYaw = mc.player.getYaw();
         this.renderPitch = mc.player.getPitch();
         this.prevRenderYaw = this.renderYaw;
         this.prevRenderPitch = this.renderPitch;
      } else {
         this.prevRenderYaw = this.renderYaw;
         this.prevRenderPitch = this.renderPitch;
         float yawDelta = MathHelper.wrapDegrees(targetRotation.getYaw() - this.renderYaw);
         float pitchDelta = targetRotation.getPitch() - this.renderPitch;
         this.renderYaw = MathHelper.wrapDegrees(this.renderYaw + yawDelta * 0.4F);
         this.renderPitch = MathHelper.clamp(this.renderPitch + pitchDelta * 0.4F, -90.0F, 90.0F);
      }
   }

   public boolean isActive() {
      return this.initialized && PrestigeSupport.silentRotationManager.getServerRotation() != null;
   }

   public float getRenderYaw() {
      return this.renderYaw;
   }

   public float getRenderPitch() {
      return this.renderPitch;
   }

   public float getPrevRenderYaw() {
      return this.prevRenderYaw;
   }

   public float getPrevRenderPitch() {
      return this.prevRenderPitch;
   }
}
