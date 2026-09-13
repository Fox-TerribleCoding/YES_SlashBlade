package dev.yessb.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.yessb.FixConfig;
import dev.yessb.compat.TlmMaidBridge;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把车万女仆 1.21.1 移植时丢掉的那个「拔刀剑分支」补回去。
 *
 * <h2>落点为什么选 {@code renderArmWithItem}</h2>
 * TLM 1.20/1.21 分支的 {@code GeckoLayerMaidHeld.render} 里，主手那一段本来是：
 * <pre>
 *   if (SlashBladeCompat.isSlashBladeItem(mainHandItem)) {
 *       SlashBladeRender.renderMaidMainhandSlashBlade(...);     // ← 1.21.1 发布版没有这句
 *   } else {
 *       renderArmWithItem(entity, mainHandItem, geoModel, THIRD_PERSON_RIGHT_HAND, RIGHT, ...);
 *   }
 * </pre>
 * 1.21.1 发布版只剩下 {@code else} 那一支（逐条比对过字节码，其余结构完全一致）。
 * 于是拔刀剑落进 {@code renderArmWithItem} → {@code ItemInHandRenderer} →
 * 拔刀剑的 BEWLR 在第三方称上下文什么都不画 ⇒ 女仆手里空着。
 *
 * <p>与其去改 {@code render} 的分支结构，这里直接注入 {@code renderArmWithItem} 的<b>开头</b>：
 * 是拔刀剑就由我们画完并取消，其余物品原样交给 TLM。这样
 * <ul>
 *   <li>调用次数完全一致 —— {@code render} 对每只手各调它一次，多余的手部定位组不会误触发；</li>
 *   <li>{@code rightHandBones}/{@code leftHandBones} 为空、CarryOn 之类的既有判定全部保留，
 *       因为注入点在它们<b>之后</b>；</li>
 *   <li>不改动 TLM 的任何其它行为。</li>
 * </ul>
 *
 * <p>{@code required=false} + {@code defaultRequire=0}：靶点找不到就静默不生效，绝不崩游戏。
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidHeld",
        remap = false)
public abstract class MixinTlmMaidHeldLayer {

    @Inject(
            method = "renderArmWithItem(Lnet/minecraft/world/entity/Mob;"
                    + "Lnet/minecraft/world/item/ItemStack;"
                    + "Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/geo/animated/ILocationModel;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;"
                    + "Lnet/minecraft/world/entity/HumanoidArm;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void yes_sb$maidSlashBlade(Mob maid, ItemStack stack, ILocationModel geoModel,
                                       ItemDisplayContext displayContext, HumanoidArm arm,
                                       PoseStack poseStack, MultiBufferSource buffer, int light,
                                       CallbackInfo ci) {
        if (!FixConfig.enabled || !FixConfig.maidSlashBlade) {
            return;
        }
        if (!TlmMaidBridge.handles(maid, stack, geoModel, arm)) {
            return;
        }
        // TLM 的主手分支用的是 partialTicks；这个类拿不到它，出鞘那一下用 0 代（过渡本来就极短）
        if (TlmMaidBridge.renderMaidBlade(maid, stack, geoModel, arm, poseStack, buffer, light, 0.0F)) {
            ci.cancel();
        }
    }
}
