package dev.yessb.mixin.sb;

import dev.yessb.render.FirstPersonPoseBase;
import mods.flammpfeil.slashblade.client.renderer.model.BladeFirstPersonRender;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 停用拔刀剑自带的「Iris 近似补偿」。
 *
 * <h2>它是什么</h2>
 * 2.0.7 的 {@code BladeFirstPersonRender} 有一个 {@code isIrisLoaded} 字段，构造时取自
 * {@code ModList.get().isLoaded("iris")}；{@code render} 里据此走一段特判：
 * <pre>
 *     if (isIrisLoaded && Iris.isPackInUseQuick()) {
 *         poseStack.mulPose(XP, +player.getXRot());     // 与后面那句 -clamp(xRot) 配对
 *         poseStack.mulPose(YP, yaw + 180.0f);          // 与下面那句 (180 - yaw) 抵消成 360°
 *     }
 *     poseStack.mulPose(YP, 180.0f - yaw);
 * </pre>
 * 它想做的，是用 {@code yaw / pitch} 去<b>近似</b> {@code ModelViewMat⁻¹} —— 因为 Iris 会把
 * 自己的「视角摇晃矩阵」塞进 {@code ModelViewMat}（见 {@link FirstPersonPoseBase} 的类注释）。
 *
 * <h2>为什么现在必须停掉它</h2>
 * 自 1.0.12 起，本模组的姿态基准已经<b>精确</b>地算出 {@code ModelViewMat⁻¹ × 相机旋转}，
 * 于是这段近似不再是"补偿"，而是<b>纯粹的错误</b>：
 * <ul>
 *   <li>{@code R_y(yaw+180) · R_y(180−yaw) = R_y(360) = 单位阵}
 *       ⇒ <b>偏航被整个抵消</b> —— 开光影时第一人称的刀"水平方向被锁定、不随视角转"；</li>
 *   <li>前面多出的 {@code R_x(+xRot)} 与后面的 {@code R_x(−clamp(xRot))} 并不抵消
 *       ⇒ 多出一层俯仰 —— 表现是"竖直方向随视角转向而动，方向还相反"。</li>
 * </ul>
 *
 * <h2>为什么用"改字段来源"而不是去拦那段分支</h2>
 * 1.0.10 曾试过 {@code @Redirect} 掉 {@code Iris.isPackInUseQuick()} —— 那条注入在 Mixin
 * <b>校验阶段</b>就失败，并<b>连累整份混入类作废</b>（详见 {@code 进展与结论.md} §13.7）。
 * 这里改为拦<b>构造器里那次 {@code ModList.isLoaded("iris")}</b>：
 * <ul>
 *   <li>靶点是 NeoForge 自己的类（{@code ModList}），必定可加载、校验必过；</li>
 *   <li>与"那段分支长什么样"无关 —— 字段一旦为 false，整段特判自然永不执行；</li>
 *   <li>只影响这一个类的这一个字段，不碰 Iris，不碰光影包，不碰任何第三方文件。</li>
 * </ul>
 *
 * <p>{@code require = 0}：靶点匹配不到就静默跳过（回到现状，不会更糟）。
 * 开关见 {@code firstPersonIrisHack}。⚠️ 该字段在 {@code BladeFirstPersonRender} <b>第一次被构造</b>
 * 时读取（单例）⇒ <b>改这个开关需要重启游戏</b>，与本模组其它热重载开关不同。
 */
@Mixin(value = BladeFirstPersonRender.class, remap = false)
public class MixinBladeFirstPersonRenderIrisFlag {

    /**
     * 把"iris 装没装"这个查询对拔刀剑隐瞒掉（仅当本模组决定接管姿态基准时）。
     *
     * <p>返回 false ⇒ {@code isIrisLoaded = false} ⇒ 那段近似补偿永不执行。
     */
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/fml/ModList;isLoaded(Ljava/lang/String;)Z"),
            require = 0,
            remap = false)
    private boolean yes_sb$hideIrisFromSlashBlade(ModList list, String modId) {
        boolean loaded = list.isLoaded(modId);
        if (loaded && FirstPersonPoseBase.shouldIgnoreSlashBladeIrisHack()) {
            FirstPersonPoseBase.noteIrisHackIgnored(modId);
            // 只对这一个是"谎报"；别的 modid 原样返回
            return false;
        }
        return loaded;
    }
}
