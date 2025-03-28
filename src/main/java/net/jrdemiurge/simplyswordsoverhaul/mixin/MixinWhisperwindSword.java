package net.jrdemiurge.simplyswordsoverhaul.mixin;

import net.jrdemiurge.simplyswordsoverhaul.Config;
import net.jrdemiurge.simplyswordsoverhaul.scheduler.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sweenus.simplyswords.api.SimplySwordsAPI;
import net.sweenus.simplyswords.item.custom.WhisperwindSwordItem;
import net.sweenus.simplyswords.registry.SoundRegistry;
import net.sweenus.simplyswords.util.HelperMethods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(WhisperwindSwordItem.class)
public abstract class MixinWhisperwindSword {

    @Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
    public void modifyHurtEnemyMethod(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        if (!Config.enableWhisperwindChanges){
            return;
        }
        if (!attacker.level().isClientSide()) {
            if (target.getHealth() == 0.0F) {
                attacker.level().playSound(null, attacker, SoundRegistry.MAGIC_SWORD_SPELL_02.get(), attacker.getSoundSource(), 0.3F, 1.8F);

                Player player = (Player) attacker;
                player.getCooldowns().addCooldown((WhisperwindSwordItem) (Object) this, 0);
            }
        }
        cir.setReturnValue(hurtEnemyUniqueSword(stack, target, attacker));
    }

    public boolean hurtEnemyUniqueSword(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide()) {
            HelperMethods.playHitSounds(attacker, target);
            SimplySwordsAPI.postHitGemSocketLogic(stack, target, attacker);
        }

        return hurtEnemySword(stack, target, attacker);
    }

    public boolean hurtEnemySword(ItemStack pStack, LivingEntity pTarget, LivingEntity pAttacker) {
        pStack.hurtAndBreak(1, pAttacker, (p_43296_) -> {
            p_43296_.broadcastBreakEvent(EquipmentSlot.MAINHAND);
        });
        return true;
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    public void modifyUseMethod(Level world, Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!Config.enableWhisperwindChanges){
            return;
        }
        if (!user.level().isClientSide()) {
            ItemStack offHandItem = user.getItemInHand(InteractionHand.OFF_HAND);

            boolean isMainHandUse = hand == InteractionHand.MAIN_HAND;
            boolean isOffHandItemNotOnCooldown = !user.getCooldowns().isOnCooldown(offHandItem.getItem());
            if (isMainHandUse && isOffHandItemNotOnCooldown) {
                user.getCooldowns().addCooldown(offHandItem.getItem(), 4);
            }

            int cooldown = Config.whisperwindCooldownTicks;
            double scale = Config.whisperwindDashDistance / 11D;
            world.playSound(null, user, SoundRegistry.ELEMENTAL_BOW_SCIFI_SHOOT_IMPACT_01.get(), user.getSoundSource(), 0.6F, 1.0F);

            Vec3 look = user.getLookAngle().normalize();
            look = new Vec3(look.x, 0, look.z).normalize().scale(scale);
            Vec3 finalLook = look;

            user.invulnerableTime = 20;
            user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 4, false, false));

            int dashDuration = 7;

            for (int i = 0; i < dashDuration; i++) {
                int delay = i * 2;

                Scheduler.schedule(() -> {
                    user.setDeltaMovement(finalLook);
                    ((ServerPlayer) user).connection.send(new ClientboundSetEntityMotionPacket(user));

                    AABB area = user.getBoundingBox().inflate(1.5);
                    List<LivingEntity> nearbyMobs = world.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != user);

                    for (LivingEntity mob : nearbyMobs) {
                        if (HelperMethods.checkFriendlyFire(mob, user)) {

                            mob.hurt(user.damageSources().playerAttack(user), calculateDamage(user, mob));

                            if (mob.getHealth() == 0.0F) {
                                user.level().playSound(null, user, SoundRegistry.MAGIC_SWORD_SPELL_02.get(), user.getSoundSource(), 0.3F, 1.8F);
                                user.getCooldowns().addCooldown((WhisperwindSwordItem) (Object) this, 0);
                            }
                        }
                    }

                    int particleRadius = (int)(1.5);
                    double xpos = user.getX() - (double)(particleRadius + 1);
                    double ypos = user.getY();
                    double zpos = user.getZ() - (double)(particleRadius + 1);

                    for(int b = particleRadius * 2; b > 0; --b) {
                        for(int j = particleRadius * 2; j > 0; --j) {
                            float choose = (float)(Math.random() * 1.0);
                            HelperMethods.spawnParticle(world, ParticleTypes.ELECTRIC_SPARK, xpos + (double)b + (double)choose, ypos + 0.4, zpos + (double)j + (double)choose, 0.0, 0.1, 0.0);
                            HelperMethods.spawnParticle(world, ParticleTypes.CLOUD, xpos + (double)b + (double)choose, ypos + 0.1, zpos + (double)j + (double)choose, 0.0, 0.0, 0.0);
                            HelperMethods.spawnParticle(world, ParticleTypes.WARPED_SPORE, xpos + (double)b + (double)choose, ypos, zpos + (double)j + (double)choose, 0.0, 0.1, 0.0);
                        }
                    }
                }, delay, 0);
            }

            Scheduler.schedule(() -> {
                user.setDeltaMovement(Vec3.ZERO);
                ((ServerPlayer) user).connection.send(new ClientboundSetEntityMotionPacket(user));
            }, dashDuration * 2, 0);

            user.getCooldowns().addCooldown((WhisperwindSwordItem) (Object) this, cooldown);
        }
        cir.setReturnValue(InteractionResultHolder.success(user.getItemInHand(hand)));
    }

    private static float calculateDamage(Player player, Entity target) {
        float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float enchantmentBonus;

        if (target instanceof LivingEntity livingTarget) {
            enchantmentBonus = EnchantmentHelper.getDamageBonus(player.getMainHandItem(), livingTarget.getMobType());
        } else {
            enchantmentBonus = EnchantmentHelper.getDamageBonus(player.getMainHandItem(), MobType.UNDEFINED);
        }

        net.minecraftforge.event.entity.player.CriticalHitEvent hitResult = net.minecraftforge.common.ForgeHooks.getCriticalHit(player, target, true, true ? 1.5F : 1.0F);
        boolean flag2 = hitResult != null;
        if (flag2) {
            baseDamage *= hitResult.getDamageModifier();
        }

        return baseDamage + enchantmentBonus;
    }

    @Inject(method = "appendHoverText", at = @At("HEAD"), cancellable = true)
    private void modifyTooltip(ItemStack itemStack, Level world, List<Component> tooltip, TooltipFlag tooltipContext, CallbackInfo ci) {
        if (!Config.enableWhisperwindChanges){
            return;
        }
        ci.cancel();

        int dashDistance = Config.whisperwindDashDistance;
        String translatedText = Component.translatable("tooltip.simply_swords_overhaul.whisperwinditem", dashDistance).getString();

        for (String line : translatedText.split("\n")) {
            tooltip.add(Component.literal(line));
        }

        int cooldown = Config.whisperwindCooldownTicks;
        float floatCooldown = (float) cooldown / 20;
        tooltip.add(Component.literal(" "));
        tooltip.add(Component.translatable("tooltip.simply_swords_overhaul.cooldown", floatCooldown)
                .withStyle(ChatFormatting.BLUE));

        SimplySwordsAPI.appendTooltipGemSocketLogic(itemStack, tooltip);
    }
}
