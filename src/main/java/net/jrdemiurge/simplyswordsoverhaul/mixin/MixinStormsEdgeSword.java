package net.jrdemiurge.simplyswordsoverhaul.mixin;

import net.jrdemiurge.simplyswordsoverhaul.Config;
import net.jrdemiurge.simplyswordsoverhaul.scheduler.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.sweenus.simplyswords.api.SimplySwordsAPI;
import net.sweenus.simplyswords.item.custom.StormsEdgeSwordItem;
import net.sweenus.simplyswords.registry.SoundRegistry;
import net.sweenus.simplyswords.util.HelperMethods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(StormsEdgeSwordItem.class)
public abstract class MixinStormsEdgeSword {

    @Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
    public void modifyHurtEnemyMethod(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        if (!Config.enableStormsEdgeChanges){
            return;
        }
        if (!attacker.level().isClientSide()) {
            int cooldownReduction = Config.stormsEdgeHitCooldownReduction;

            Player player = (Player) attacker;

            reduceCooldown(player, (StormsEdgeSwordItem) (Object) this, cooldownReduction);
        }
        cir.setReturnValue(hurtEnemyUniqueSword(stack, target, attacker));
    }

    private void reduceCooldown(Player player, Item item, int reductionTicks) {
        CompoundTag playerData = player.getPersistentData();
        String cooldownKey = "storms_edge_last_cooldown";
        int lastCooldown = playerData.getInt(cooldownKey);

        float cooldownPercent = player.getCooldowns().getCooldownPercent(item, 0);
        int remainingTicks = Math.round(lastCooldown * cooldownPercent);
        int newCooldown = Math.max(0, remainingTicks - reductionTicks);

        playerData.putInt(cooldownKey, newCooldown);

        player.getCooldowns().addCooldown(item, newCooldown);

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
        if (!Config.enableStormsEdgeChanges){
            return;
        }
        if (!user.level().isClientSide()) {
            ItemStack offHandItem = user.getItemInHand(InteractionHand.OFF_HAND);
            boolean isMainHandUse = hand == InteractionHand.MAIN_HAND;
            boolean isOffHandItemNotOnCooldown = !user.getCooldowns().isOnCooldown(offHandItem.getItem());
            if (isMainHandUse && isOffHandItemNotOnCooldown) {
                user.getCooldowns().addCooldown(offHandItem.getItem(), 4);
            }

            int cooldownReduction = Config.stormsEdgeHitCooldownReduction;
            int cooldown = Config.stormsEdgeCooldownTicks;
            double scale = Config.stormsEdgeDashDistance / 6D;
            int effectDuration = Config.stormsEdgeEffectDuration;
            int maxHasteLevel = Config.stormsEdgeMaxHasteLevel;

            CompoundTag playerData = user.getPersistentData();
            String cooldownKey = "storms_edge_last_cooldown";
            playerData.putInt(cooldownKey, cooldown);

            world.playSound(null, user, SoundRegistry.MAGIC_BOW_CHARGE_SHORT_VERSION.get(), user.getSoundSource(), 0.4F, 1.2F);

            Vec3 look = user.getLookAngle().normalize();
            if (user.isShiftKeyDown()) {
                look = look.scale(-1);
            }

            double angleY = Math.toDegrees(Math.asin(look.y));
            if (Math.abs(angleY) <= 10 || (user.onGround() & angleY < 0)) { // пусть пока что || будет тут но потом я это условие вынесу в отдельный конфиг
                look = new Vec3(look.x, 0, look.z).normalize();
            }

            Vec3 dashVelocity = look.scale(scale);

            user.invulnerableTime = 15;
            user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 15, 4, false, false));

            int dashDuration = 4;

            for (int i = 0; i < dashDuration; i++) {
                int delay = i * 2;

                Scheduler.schedule(() -> {
                    user.setDeltaMovement(dashVelocity);
                    ((ServerPlayer) user).connection.send(new ClientboundSetEntityMotionPacket(user));

                    AABB area = user.getBoundingBox().inflate(1.5);
                    List<LivingEntity> nearbyMobs = world.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != user);

                    for (LivingEntity mob : nearbyMobs) {
                        if (HelperMethods.checkFriendlyFire(mob, user)) {

                            if (mob.invulnerableTime == 0) {
                                reduceCooldown(user, (StormsEdgeSwordItem) (Object) this, cooldownReduction);
                            }
                            mob.hurt(user.damageSources().playerAttack(user), calculateDamage(user, mob));
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

            boolean[] shouldDisableGravity = {false};

            Scheduler.schedule(() -> {
                if (!user.onGround()) {
                    user.setNoGravity(true);
                    shouldDisableGravity[0] = true;
                    user.hurtMarked = true;
                }
            }, dashDuration * 2, 0);

            Scheduler.schedule(() -> {
                if (shouldDisableGravity[0]) {
                    user.setNoGravity(false);
                    user.hurtMarked = true;
                }
            }, dashDuration * 2 + 6, 0);

            if (user.hasEffect(MobEffects.DIG_SPEED)) {
                int effectLevel = user.getEffect(MobEffects.DIG_SPEED).getAmplifier();
                if (effectLevel > maxHasteLevel - 2) effectLevel = maxHasteLevel - 2;
                user.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20 * effectDuration, effectLevel + 1, false, true));
            } else {
                user.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20 * effectDuration, 0, false, true));
            }
            user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * effectDuration, 0, false, true));

            user.getCooldowns().addCooldown((StormsEdgeSwordItem) (Object) this, cooldown);
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

        return baseDamage + enchantmentBonus;
    }

    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    private void modifyOnUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseTicks, CallbackInfo ci) {
        if (!Config.enableStormsEdgeChanges){
            return;
        }
        ci.cancel();
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void modifyReleaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        if (!Config.enableStormsEdgeChanges){
            return;
        }
        ci.cancel();
    }

    @OnlyIn(Dist.CLIENT)
    @Inject(method = "appendHoverText", at = @At("HEAD"), cancellable = true)
    private void modifyTooltip(ItemStack itemStack, Level world, List<Component> tooltip, TooltipFlag tooltipContext, CallbackInfo ci) {
        if (!Config.enableStormsEdgeChanges){
            return;
        }
        ci.cancel();

        float cooldownReduction = Config.stormsEdgeHitCooldownReduction / 20F;
        int cooldown = Config.stormsEdgeCooldownTicks;
        double dashDistance = Config.stormsEdgeDashDistance;
        int maxHasteLevel = Config.stormsEdgeMaxHasteLevel;
        float floatCooldown = (float) cooldown / 20;

        if (Screen.hasAltDown()){
            String translatedText = Component.translatable("tooltip.simply_swords_overhaul.stormsedgeitem_1", cooldownReduction).getString();
            for (String line : translatedText.split("\n")) tooltip.add(Component.literal(line));

            translatedText = Component.translatable("tooltip.simply_swords_overhaul.stormsedgeitem_2", dashDistance).getString();
            for (String line : translatedText.split("\n")) tooltip.add(Component.literal(line));

            tooltip.add(Component.literal(" "));
            tooltip.add(Component.translatable("tooltip.simply_swords_overhaul.cooldown", floatCooldown)
                    .withStyle(ChatFormatting.BLUE));

            SimplySwordsAPI.appendTooltipGemSocketLogic(itemStack, tooltip);

        } else if (Screen.hasShiftDown()) {
            String translatedText = Component.translatable("tooltip.simply_swords_overhaul.stormsedgeitem_1", cooldownReduction).getString();
            for (String line : translatedText.split("\n")) tooltip.add(Component.literal(line));

            translatedText = Component.translatable("tooltip.simply_swords_overhaul.stormsedgeitem_shift_1").getString();
            for (String line : translatedText.split("\n")) tooltip.add(Component.literal(line)
                    .withStyle(ChatFormatting.GRAY));


            translatedText = Component.translatable("tooltip.simply_swords_overhaul.stormsedgeitem_2", dashDistance).getString();
            for (String line : translatedText.split("\n")) tooltip.add(Component.literal(line));

            translatedText = Component.translatable("tooltip.simply_swords_overhaul.stormsedgeitem_shift_2", maxHasteLevel).getString();
            for (String line : translatedText.split("\n")) tooltip.add(Component.literal(line)
                        .withStyle(ChatFormatting.GRAY));


            tooltip.add(Component.literal(" "));
            tooltip.add(Component.translatable("tooltip.simply_swords_overhaul.cooldown", floatCooldown)
                    .withStyle(ChatFormatting.BLUE));

        } else {
            String translatedText = Component.translatable("tooltip.simply_swords_overhaul.stormsedgeitem_1", cooldownReduction).getString();
            for (String line : translatedText.split("\n")) tooltip.add(Component.literal(line));

            translatedText = Component.translatable("tooltip.simply_swords_overhaul.stormsedgeitem_2", dashDistance).getString();
            for (String line : translatedText.split("\n")) tooltip.add(Component.literal(line));


            tooltip.add(Component.literal(" "));
            tooltip.add(Component.translatable("tooltip.simply_swords_overhaul.cooldown", floatCooldown)
                    .withStyle(ChatFormatting.BLUE));

            SimplySwordsAPI.appendTooltipGemSocketLogic(itemStack, tooltip);

            if (!tooltip.get(tooltip.size() - 1)
                    .getString().equals(Component.translatable("item.simplyswords.common.showtooltip").getString())) {
                tooltip.add(Component.literal(""));
            }
            tooltip.add(Component.translatable("tooltip.simply_swords_overhaul.shift"));
        }
    }
}
