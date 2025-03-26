package net.jrdemiurge.simplyswordsoverhaul.mixin;

import net.jrdemiurge.simplyswordsoverhaul.scheduler.Scheduler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sweenus.simplyswords.api.SimplySwordsAPI;
import net.sweenus.simplyswords.item.custom.StarsEdgeSwordItem;
import net.sweenus.simplyswords.registry.SoundRegistry;
import net.sweenus.simplyswords.util.HelperMethods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Mixin(HelperMethods.class)
public abstract class MixinHelperMethods {


    @Inject(method = "incrementStatusEffect", at = @At("HEAD"), cancellable = true, remap = false)
    private static void modifyIncrementStatusEffect(LivingEntity livingEntity, MobEffect statusEffect, int duration, int amplifier, int amplifierMax, CallbackInfo ci) {
        if (livingEntity.hasEffect(statusEffect)) {
            int currentAmplifier = livingEntity.getEffect(statusEffect).getAmplifier();
            livingEntity.addEffect(new MobEffectInstance(statusEffect, duration, Math.min(amplifierMax, currentAmplifier + amplifier), false, false, true));
        }
        livingEntity.addEffect(new MobEffectInstance(statusEffect, duration, 0, false, false, true));

        ci.cancel();
    }

}
