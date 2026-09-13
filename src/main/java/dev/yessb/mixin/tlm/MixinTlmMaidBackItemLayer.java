package dev.yessb.mixin.tlm;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yessb.FixConfig;
import dev.yessb.compat.TlmMaidBridge;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把车万女仆 1.21.1 移植时丢掉的「背槽拔刀剑分支」补回去。
 *
 * <p>TLM 1.21 分支的 {@code GeckoLayerMaidBackItem.render} 本来是：
 * <pre>
 *   if (SlashBladeCompat.isSlashBladeItem(stack)) {
 *       SlashBladeRender.renderGeckoMaidBackSlashBlade(matrixStack, buffer, packedLight, stack);
 *   } else {
 *       Minecraft.getInstance().getItemRenderer().renderStatic(entity, stack, FIXED, ...);
 *   }
 * </pre>
 * 发布版只剩 {@code else} 那支（jar 内 slashblade 引用为 0），于是装饰槽里的拔刀剑
 * 掉进 {@code FIXED} 上下文被画成平面图标 —— 也就是用户看到的"位置不对"。
 *
 * <p>这里重定向那一次 {@code renderStatic}：是拔刀剑就按 TLM 的背槽变换自己画完，
 * 其余物品原样交回。
 *
 * <p>注入点在 {@code backpackBones} 定位、{@code offsetBackpackItem} 等既有处理<b>之后</b>，
 * 因此那些行为全部保留。
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidBackItem",
        remap = false)
public abstract class MixinTlmMaidBackItemLayer {

    @Redirect(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "ILnet/minecraft/world/entity/Mob;FFFFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic("
                            + "Lnet/minecraft/world/entity/LivingEntity;"
                            + "Lnet/minecraft/world/item/ItemStack;"
                            + "Lnet/minecraft/world/item/ItemDisplayContext;"
                            + "Z"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                            + "Lnet/minecraft/world/level/Level;"
                            + "III)V"),
            require = 0,
            remap = false)
    private void yes_sb$maidBackSlashBlade(ItemRenderer instance, LivingEntity entity, ItemStack stack,
                                           ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
                                           MultiBufferSource buffer, Level level, int light, int overlay,
                                           int seed) {
        if (FixConfig.enabled && FixConfig.maidSlashBlade
                && TlmMaidBridge.renderMaidBackBlade(stack, poseStack, buffer, light)) {
            return;
        }
        instance.renderStatic(entity, stack, context, leftHand, poseStack, buffer, level, light, overlay, seed);
    }
}
