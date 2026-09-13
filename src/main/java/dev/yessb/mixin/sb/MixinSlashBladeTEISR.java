package dev.yessb.mixin.sb;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yessb.FixConfig;
import dev.yessb.YesSlashBladeFix;
import dev.yessb.compat.BladeTransform;
import dev.yessb.compat.SlashBladeBridge;
import dev.yessb.compat.YsmBridge;
import dev.yessb.render.VanillaRenderTracker;
import mods.flammpfeil.slashblade.client.renderer.SlashBladeTEISR;
import mods.flammpfeil.slashblade.client.renderer.model.BladeFirstPersonRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让拔刀剑在"手持"这条路上把 3D 刀身画出来。
 *
 * <h2>三个上下文，三种原行为</h2>
 * <table>
 *   <tr><th>上下文</th><th>拔刀剑原行为</th></tr>
 *   <tr><td>{@code THIRD_PERSON_*}</td><td><b>什么都不画</b> —— 那两把刀本来该由腰挂层画</td></tr>
 *   <tr><td>{@code FIXED} / GROUND / GUI / HEAD</td><td>画<b>平面图标</b>（{@code item_blade} 部件）</td></tr>
 *   <tr><td>{@code FIRST_PERSON_*}</td><td>画整套 MMD 刀 + 鞘（腰挂式，不是手持式）</td></tr>
 * </table>
 *
 * <p>本类分别接管这三种情况，统一改画 3D 刀身：
 * <ul>
 *   <li><b>第一人称</b>：重定向掉那次 MMD 调用，改按手持物绘制；</li>
 *   <li><b>第三方称主手</b>：直接画刀身并提前返回。注意<b>只对"没有腰挂层"的实体生效</b> ——
 *       拔刀剑给每个 {@code LivingEntityRenderer} 都挂了腰挂层，有腰挂层的实体（含玩家）
 *       由 {@code BladeLayerRestorer} 那条路负责，否则会出现两把刀；</li>
 *   <li><b>{@code FIXED}</b>：替换掉平面图标（已经架在刀架/展示框上的不碰）。</li>
 * </ul>
 *
 * <p><b>注意：车万女仆的手持与装饰槽都不走这里</b> —— 那两处由
 * {@code dev.yessb.mixin.tlm} 下的混入按 TLM 原本的变换处理，见 {@code TlmMaidBridge}。
 * 本类的 {@code FIXED} 分支只服务于除此之外的平面上下文。
 *
 * <p>所有注入都是 {@code require = 0}：靶点找不到就静默跳过，回到拔刀剑原行为，绝不崩游戏。
 */
@Mixin(value = SlashBladeTEISR.class, remap = false)
public abstract class MixinSlashBladeTEISR {

    // ------------------------------------------------------------------ 第一人称

    @Redirect(
            method = "renderBlade",
            at = @At(
                    value = "INVOKE",
                    target = "Lmods/flammpfeil/slashblade/client/renderer/model/BladeFirstPersonRender;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            require = 0,
            remap = false)
    private void yes_sb$firstPersonAsHeldItem(BladeFirstPersonRender instance, PoseStack poseStack,
                                              MultiBufferSource buffer, int light,
                                              ItemStack stack, ItemDisplayContext context,
                                              PoseStack outerPose, MultiBufferSource outerBuffer,
                                              int outerLight, int overlay) {
        SlashBladeBridge.renderFirstPersonBlade(instance, this, stack, poseStack, buffer, light);
    }

    // ------------------------------------------------ 第三方称（主手）与平面上下文

    /**
     * 在主手 / 平面上下文下改画 3D 刀身。
     *
     * <p>放在 HEAD 并允许取消：画成功就直接返回，不再走拔刀剑原来的"不画"或"画平面图标"。
     */
    @Inject(method = "renderBlade", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void yes_sb$drawBladeModel(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                                       MultiBufferSource buffer, int light, int overlay,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!FixConfig.enabled || !YsmBridge.isLoaded()) {
            return;
        }
        if (!shouldDrawBladeModel(stack, context)) {
            return;
        }
        BladeTransform transform = context == ItemDisplayContext.FIXED
                ? BladeTransform.flat() : BladeTransform.hand();
        if (FixConfig.debugLog) {
            yes_sb$diag(context, transform);
        }
        if (SlashBladeBridge.renderHandBlade(stack, poseStack, buffer, light, transform)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    private static long yes_sb$lastDiag;

    /** 诊断：这一次是"手持补画"在处理，报告上下文与"当前实体有没有腰挂层"。节流 2 秒。 */
    private static void yes_sb$diag(ItemDisplayContext context, BladeTransform transform) {
        long now = System.currentTimeMillis();
        if (now - yes_sb$lastDiag < 2000L) {
            return;
        }
        yes_sb$lastDiag = now;
        YesSlashBladeFix.LOGGER.info(
                "[YES-SB] 手持补画接管：上下文={} 缩放={} 当前实体有腰挂层={} 平面上下文={}",
                context, transform.scale(),
                VanillaRenderTracker.currentHasWaistLayer(),
                transform.flatContext());
    }

    private static boolean shouldDrawBladeModel(ItemStack stack, ItemDisplayContext context) {
        // 第三方称：只接管主手的那个上下文（副手交给腰挂层画在腰侧，避免出现两把）
        if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            if (!FixConfig.handBladeInThirdPerson || !isMainHandContext(context)) {
                return false;
            }
            // 拔刀剑给每个 LivingEntityRenderer 都挂了腰挂层；那种实体（含玩家）的刀
            // 应当由腰挂层画 —— 正是 1.20.1 的样子，也是第一人称纸娃娃出现两把刀的根因。
            // 只有"没有腰挂层"的实体才交给这条手持路径。
            return !FixConfig.handBladeRequiresNoWaistLayer
                    || !VanillaRenderTracker.currentHasWaistLayer();
        }
        // 平面上下文：把平面图标换成 3D 刀身。
        // 已经架在刀架/展示框上的不碰 —— 拔刀剑对它有专门的摆放逻辑。
        // （车万女仆的装饰槽不经过这里，见类文档。）
        if (context == ItemDisplayContext.FIXED) {
            return FixConfig.handBladeInFlatContext && !stack.isFramed();
        }
        return false;
    }

    /** 该展示上下文是否对应"主手"（按玩家惯用手判断）。 */
    private static boolean isMainHandContext(ItemDisplayContext context) {
        boolean rightHanded;
        try {
            LocalPlayer player = Minecraft.getInstance().player;
            rightHanded = player == null || player.getMainArm() == HumanoidArm.RIGHT;
        } catch (Throwable t) {
            rightHanded = true;
        }
        return rightHanded
                ? context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                : context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }
}
