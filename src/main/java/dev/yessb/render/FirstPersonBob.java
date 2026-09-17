package dev.yessb.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yessb.FixConfig;
import dev.yessb.YesSlashBladeFix;
import dev.yessb.mixin.bob.MixinGameRendererBob;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/**
 * 第一人称的刀跟随「视角摇晃」。
 *
 * <h2>要解决什么</h2>
 * 原版第一人称的手（以及手里的物品）会随脚步摇晃；而拔刀剑的第一人称渲染会把姿态栈
 * <b>清零</b>，摇摇晃动是刻在被抹掉的那份矩阵里的 —— 于是<b>手在晃、刀不晃</b>，
 * 两者不协调。本类把原版那份晃动补回到刀上。
 *
 * <h2>为什么"借"而不是"抄"</h2>
 * 直接调原版 {@code GameRenderer#bobView}（经 {@link MixinGameRendererBob} 的 {@code @Invoker}）：
 * 与手部逐像素一致，且 Minecraft 日后改算法时我们自动跟随。
 *
 * <h2>插在哪一层</h2>
 * {@link FirstPersonPoseBase} 算出的姿态基准是 {@code ModelViewMat⁻¹ × 相机旋转}，
 * 本类把摇晃<b>乘在该基准之后</b>：
 * <pre>
 *     姿态栈 := ModelViewMat⁻¹ × 相机旋转 × 摇晃
 * </pre>
 * <ul>
 *   <li>无光影：{@code ModelViewMat == 相机旋转} ⇒ 括号里只剩摇晃，最终 = {@code 相机旋转 × 摇晃 × 刀变换}；</li>
 *   <li>开光影：{@code ModelViewMat} 是 Iris 那个摇晃矩阵 ⇒ 先被基准确抵消、再把我们这份乘上去，
 *       最终<b>同样是</b> {@code 相机旋转 × 摇晃 × 刀变换}。</li>
 * </ul>
 * ⇒ 有/无光影表现一致；而且摇晃落在"刀自身那串变换之前"，效果是<b>整把刀随视角摇</b>，
 * 而不是绕刀自身原点自转。
 *
 * <h2>与游戏内置设置的关系</h2>
 * 门控读的就是原版那个开关 {@code options.bobView()}：<b>玩家关掉「视角摇晃」，刀也不晃</b>。
 *
 * <p>注意：<b>1.20.1 的第一人称刀是不晃的</b>（它同样清零了姿态栈）。所以这是本模组
 * <b>有意添加的增强</b>，不是"还原 1.20.1"；不需要它就把 {@code firstPersonBladeBob} 设为 {@code false}。
 */
public final class FirstPersonBob {

    private FirstPersonBob() {
    }

    /** 一旦取不到原版的 {@code bobView}（版本差异）就永久停用，不再每帧尝试。 */
    private static boolean broken;

    /** 诊断：状态翻转时才打一条。 */
    private static Boolean lastState;

    /**
     * 当前应当施加的摇晃矩阵。
     *
     * @return {@code null} 表示"不加摇晃"（功能关闭、开关关掉、或借不到原版实现）
     */
    public static Matrix4f matrix() {
        if (broken || !FixConfig.enabled || !FixConfig.firstPersonBladeBob) {
            return null;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameRenderer == null || mc.options == null) {
                return null;
            }
            // 绑定游戏内置的「视角摇晃」：玩家关掉它，刀也不晃。
            if (!Boolean.TRUE.equals(mc.options.bobView().get())) {
                noteState(false);
                return null;
            }
            noteState(true);
            float partialTicks = mc.getTimer().getGameTimeDeltaPartialTick(false);
            // 借原版的手：它只往姿态栈里加平移与旋转，所以空栈跑一遍拿到的就是纯摇晃矩阵。
            PoseStack probe = new PoseStack();
            ((MixinGameRendererBob) (Object) mc.gameRenderer).yes_sb$bobView(probe, partialTicks);
            return new Matrix4f(probe.last().pose());
        } catch (Throwable t) {
            broken = true;
            YesSlashBladeFix.LOGGER.warn(
                    "[YES-SB] 第一人称刀的视角摇晃不可用（借不到原版 bobView），已停用该功能；"
                            + "其余功能与游戏不受影响。原因: {}", t.toString());
            return null;
        }
    }

    /**
     * 诊断：只在状态翻转时打一条，回答"这个功能到底有没有生效"。
     *
     * <p>与 {@code MixinBladeFirstPersonRender} 的自检行同一思路 ——
     * 本模组做的多是"看不见就等于没干活"的补漏，必须留一个能被外部验证的接口。
     */
    private static void noteState(boolean on) {
        if (lastState != null && lastState == on) {
            return;
        }
        lastState = on;
        YesSlashBladeFix.LOGGER.info(
                "[YES-SB] 第一人称刀的视角摇晃：{}（跟随游戏内置「视角摇晃」设置）",
                on ? "开 —— 走动时刀会与手同步摇晃" : "关 —— 刀保持静止");
    }
}
