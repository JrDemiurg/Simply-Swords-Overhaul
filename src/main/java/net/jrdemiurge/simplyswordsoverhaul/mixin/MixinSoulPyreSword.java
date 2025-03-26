package net.jrdemiurge.simplyswordsoverhaul.mixin;

import net.jrdemiurge.simplyswordsoverhaul.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.sweenus.simplyswords.api.SimplySwordsAPI;
import net.sweenus.simplyswords.item.custom.SoulPyreSwordItem;
import net.sweenus.simplyswords.util.HelperMethods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(SoulPyreSwordItem.class)
public abstract class MixinSoulPyreSword {

    private static int stepMod = 0;

    @Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
    public void modifyHurtEnemyMethod(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        if (!attacker.level().isClientSide()) {
            int witherDuration = Config.soulPyreWitherDuration;
            int witherLevel = Config.soulPyreWitherLevel;

            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 20*witherDuration, witherLevel - 1));
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

    @Inject(method = "inventoryTick", at = @At("HEAD"), cancellable = true)
    private void modifyInventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected, CallbackInfo ci) {

        if (stepMod > 0) {
            --stepMod;
        }

        if (stepMod <= 0) {
            stepMod = 7;
        }

        HelperMethods.createFootfalls(entity, stack, world, stepMod, ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.MYCELIUM, true);
        HelperMethods.createFootfalls(entity, stack, world, stepMod, ParticleTypes.SMALL_FLAME, ParticleTypes.SMALL_FLAME, ParticleTypes.MYCELIUM, false);
        SimplySwordsAPI.inventoryTickGemSocketLogic(stack, world, entity, 50, 50);
        ci.cancel();
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    public void modifyUseMethod(Level world, Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!user.level().isClientSide()) {
            ItemStack offHandItem = user.getItemInHand(InteractionHand.OFF_HAND);
            boolean isMainHandUse = hand == InteractionHand.MAIN_HAND;
            boolean isOffHandItemNotOnCooldown = !user.getCooldowns().isOnCooldown(offHandItem.getItem());
            if (isMainHandUse && isOffHandItemNotOnCooldown) {
                user.getCooldowns().addCooldown(offHandItem.getItem(), 4);
            }

            double maxAbilityDistance = Config.soulPyreMaxAbilityDistance;
            double teleportDistance = Config.soulPyreTeleportDistance;
            int cooldown = Config.soulPyreCooldownTicks;
            Vec3 lookVec = user.getLookAngle().normalize();
            Vec3 start = user.position().add(0, user.getEyeHeight(), 0);
            Vec3 end = start.add(lookVec.normalize().scale(maxAbilityDistance));

            EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(world, user, start, end, user.getBoundingBox().inflate(32), e -> e instanceof LivingEntity && e != user);

            if (hitResult != null) {
                Entity target = hitResult.getEntity();
                Vec3 horizontalLookVec = new Vec3(lookVec.x, 0, lookVec.z).normalize().scale(teleportDistance);
                Vec3 teleportPos = user.position().add(horizontalLookVec);
                if (world.getBlockState(BlockPos.containing(teleportPos)).getCollisionShape(world, BlockPos.containing(teleportPos)).isEmpty()) {
                    target.teleportTo(teleportPos.x, teleportPos.y, teleportPos.z);
                    user.getCooldowns().addCooldown((SoulPyreSwordItem) (Object) this, cooldown);
                } else {
                    user.getCooldowns().addCooldown((SoulPyreSwordItem) (Object) this, 5);
                }
            } else {
                user.getCooldowns().addCooldown((SoulPyreSwordItem) (Object) this, 5);
            }
        }
        cir.setReturnValue(InteractionResultHolder.success(user.getItemInHand(hand)));
    }

    @Inject(method = "appendHoverText", at = @At("HEAD"), cancellable = true)
    private void modifyTooltip(ItemStack itemStack, Level world, List<Component> tooltip, TooltipFlag tooltipContext, CallbackInfo ci) {
        ci.cancel();

        int witherDuration = Config.soulPyreWitherDuration;
        int witherLevel = Config.soulPyreWitherLevel;
        double maxAbilityDistance = Config.soulPyreMaxAbilityDistance;
        int cooldown = Config.soulPyreCooldownTicks;
        float floatCooldown = (float) cooldown / 20;

        String translatedText = Component.translatable("tooltip.simply_swords_overhaul.solupyreitem", witherLevel, witherDuration, maxAbilityDistance).getString();

        for (String line : translatedText.split("\n")) {
            tooltip.add(Component.literal(line));
        }

        tooltip.add(Component.literal(" "));
        tooltip.add(Component.translatable("tooltip.simply_swords_overhaul.cooldown", floatCooldown)
                .withStyle(ChatFormatting.BLUE));

        SimplySwordsAPI.appendTooltipGemSocketLogic(itemStack, tooltip);
    }
}
