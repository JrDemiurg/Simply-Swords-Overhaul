package net.jrdemiurge.simplyswordsoverhaul.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.sweenus.simplyswords.api.SimplySwordsAPI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SimplySwordsAPI.class)
public abstract class MixinSimplySwordsAPI {

    @OnlyIn(Dist.CLIENT)
    @Inject(method = "appendTooltipGemSocketLogic", at = @At("TAIL"), remap = false)
    private static void modifyAppendTooltipGemSocketLogic(ItemStack itemStack, List<Component> tooltip, CallbackInfo ci) {
        CompoundTag nbt = itemStack.getOrCreateTag();
        if (Screen.hasAltDown()) {
            if (nbt.getString("runic_power").isEmpty() && nbt.getString("nether_power").isEmpty()) {
                tooltip.remove(tooltip.size() - 1);
                tooltip.remove(tooltip.size() - 1);
            }

            if (!nbt.getString("runic_power").equals("no_socket") && !nbt.getString("nether_power").equals("no_socket")
                    && !nbt.getString("runic_power").isEmpty() && !nbt.getString("nether_power").isEmpty()) {

                for (int i = tooltip.size() - 1; i >= 0; i--) {
                    if (tooltip.get(i).getString().isEmpty()) {
                        tooltip.remove(i);
                        break;
                    }
                }
            }
        } else {
            if (nbt.getString("runic_power").isEmpty() && nbt.getString("nether_power").isEmpty()) {
                tooltip.remove(tooltip.size() - 1);
                tooltip.remove(tooltip.size() - 1);
            }
        }
    }
}
