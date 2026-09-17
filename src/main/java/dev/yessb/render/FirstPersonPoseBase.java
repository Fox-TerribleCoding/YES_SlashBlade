package dev.yessb.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.yessb.FixConfig;
import dev.yessb.YesSlashBladeFix;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 第一人称刀的姿态<b>基准</b> —— 修"开光影后第一人称的刀跑到别处、随移动漂移"。
 *
 * <h2>机制（全部由字节码确证）</h2>
 * 一个顶点最终的变换是：
 * <pre>
 *     最终 = ModelViewMat × 姿态栈
 * </pre>
 * 而拔刀剑 {@code BladeFirstPersonRender#render} 会把姿态栈<b>清零</b>：
 * <pre>
 *     PoseStack.Pose me = matrixStack.last();
 *     me.pose().identity();      // ← 抹掉整个累积矩阵
 * </pre>
 *
 * <p><b>无光影时</b>（{@code GameRenderer#renderItemInHand} 的开头）：
 * <pre>
 *     quaternionf = camera.rotation().conjugate();
 *     matrix4f    = new Matrix4f().rotation(quaternionf);   // = 世界→视图旋转 viewRot
 *     poseStack.mulPose(matrix4f.invert());                 // 姿态栈 = viewRot⁻¹
 *     modelViewStack.pushMatrix(); modelViewStack.mul(matrix4f);  // ModelViewMat = viewRot
 * </pre>
 * ⇒ 两者互补。清零之后最终变成 {@code viewRot × B}，**这恰好是拔刀剑一直以来的"正常样子"**
 * ——刀跟着视角转，正是靠这一层 {@code viewRot}。
 *
 * <p><b>开光影时</b>，Iris 的 {@code pathways.HandRenderer} 把 {@code ModelViewMat} 换成了
 * 它自己那个「姿态栈 = 单位矩阵 + bobHurt + bobView」的矩阵（<b>视角摇晃矩阵，不含 viewRot</b>），
 * 而姿态栈从单位矩阵开始。于是清零之后：
 * <pre>
 *     最终 = 摇晃矩阵 × B      // 少了 viewRot、多了一层摇晃 ⇒ 位置不对 + 随移动漂移
 * </pre>
 *
 * <h2>修法</h2>
 * 让最终结果恒等于无光影下的那个：
 * <pre>
 *     姿态栈 := ModelViewMat⁻¹ × viewRot
 * </pre>
 * <ul>
 *   <li>无光影：{@code ModelViewMat == viewRot} ⇒ 该式 = 单位矩阵 ⇒ <b>与"清零"逐位相同，零改动</b>；</li>
 *   <li>开光影：{@code ModelViewMat == 摇晃矩阵} ⇒ 该式 = 摇晃矩阵⁻¹ × viewRot
 *       ⇒ 最终 = 摇晃矩阵 × 摇晃矩阵⁻¹ × viewRot × B = viewRot × B ⇒ <b>与无光影一致</b>。</li>
 * </ul>
 * 代码里还加了一道保险：<b>先比较 {@code ModelViewMat} 与 {@code viewRot}，相等就直接清零</b>，
 * 这样"无光影下零改动"是<b>结构性保证</b>，不依赖等式成立。
 *
 * <p>本修正<b>不检测光影包、不判断光影种类</b>，对任何光影同样成立。
 */
public final class FirstPersonPoseBase {

    private FirstPersonPoseBase() {
    }

    /** 诊断节流。 */
    private static long lastDiag;

    /** 自检日志只报一次。 */
    private static volatile boolean aliveNoted;

    /** 混入"还活着"的自检 —— 用来回答"这次实测跑的到底是不是修好的那份"。 */
    public static void noteAlive() {
        if (aliveNoted) {
            return;
        }
        aliveNoted = true;
        YesSlashBladeFix.LOGGER.info(
                "[YES-SB] 第一人称姿态基准修正已注入（MixinBladeFirstPersonRender 生效）；基准模式={}",
                FixConfig.firstPersonPoseBase);
    }

    /**
     * 计算应当写进姿态栈的基准。
     *
     * @return {@code null} 表示"按原样清零"（不干预）
     */
    private static Matrix4f base(boolean withDiag) {
        if (!FixConfig.enabled) {
            return null;
        }
        String mode = FixConfig.firstPersonPoseBase;
        if ("identity".equalsIgnoreCase(mode)) {
            if (withDiag) {
                diag(null, null, null, "identity（拔刀剑原行为）");
            }
            return null;
        }

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f viewRot = cameraViewRotation();

        if ("modelview".equalsIgnoreCase(mode)) {
            // 仅供对照：直接把 ModelView 抵掉。已实测会在<b>无光影</b>下把 viewRot 也抵掉，
            // 表现是"刀不再跟着视角" —— 保留它是为了留一条可复现的对照路径。
            if (withDiag) {
                diag(null, modelView, viewRot, "ModelView⁻¹（对照用）");
            }
            return modelView.invert();
        }

        // 默认候选：ModelView⁻¹ × viewRot
        if (viewRot == null) {
            if (withDiag) {
                diag(null, modelView, null, "ModelView⁻¹（取不到相机旋转时的退路）");
            }
            return modelView.invert();
        }
        if (near(modelView, viewRot)) {
            // 无光影：ModelViewMat 本来就等于 viewRot ⇒ 清零就是正确答案，一个字都不改。
            if (withDiag) {
                diag(null, modelView, viewRot, "identity（ModelView 已等于相机旋转，无需修正）");
            }
            return null;
        }
        Matrix4f result = modelView.invert().mul(viewRot);
        if (withDiag) {
            diag(null, modelView, viewRot, "ModelView⁻¹ × viewRot");
        }
        return result;
    }

    /** 替换 {@code me.pose().identity()}。 */
    public static Matrix4f apply(Matrix4f self) {
        try {
            if (FixConfig.debugLog) {
                // 先留一份"清零之前"的姿态，诊断里要用（节流只影响打印，不影响这里）
                pendingEntry = new Matrix4f(self);
            }
            Matrix4f base = base(true);
            if (base == null) {
                return self.identity();
            }
            self.set(base);
            return self;
        } catch (Throwable t) {
            // 任何意外都退回拔刀剑原行为，绝不让第一人称没有刀
            return self.identity();
        }
    }

    /** 替换 {@code me.normal().identity()} —— 法线基准跟着姿态基准走。 */
    public static Matrix3f applyNormal(Matrix3f self) {
        try {
            Matrix4f base = base(false);
            if (base == null) {
                return self.identity();
            }
            Matrix3f normal = new Matrix3f(base);
            normal.invert().transpose();
            self.set(normal);
            return self;
        } catch (Throwable t) {
            return self.identity();
        }
    }

    /**
     * 相机朝向的「世界→视图旋转」，与 {@code GameRenderer#render} 里构造
     * {@code frustumMatrix} 的方式逐字节一致：
     * <pre>
     *     new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()))
     * </pre>
     */
    private static Matrix4f cameraViewRotation() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameRenderer == null) {
                return null;
            }
            Camera camera = mc.gameRenderer.getMainCamera();
            if (camera == null) {
                return null;
            }
            return new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 两个矩阵是否（近似）相等。 */
    private static boolean near(Matrix4f a, Matrix4f b) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                if (Math.abs(a.get(c, r) - b.get(c, r)) > 0.001f) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 诊断

    private static Matrix4f pendingEntry;

    /**
     * 诊断：把"光影基准 ModelView / 相机旋转 / 入口姿态"打出来。
     *
     * <p>这是判断该问题的<b>唯一</b>客观接口 —— 开/关光影各看一次，
     * 就能直接看出 ModelView 是不是被光影换掉了、换成了什么。节流 2 秒。
     */
    private static void diag(Matrix4f entry, Matrix4f modelView, Matrix4f viewRot, String chosen) {
        Matrix4f e = entry != null ? entry : pendingEntry;
        if (!FixConfig.debugLog) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDiag < 2000L) {
            return;
        }
        lastDiag = now;
        YesSlashBladeFix.LOGGER.info(
                "[YES-SB] 第一人称[基准]：采用={} ModelView={} 像相机旋转={} 入口姿态={}",
                chosen,
                modelView == null ? "未读取" : compact(modelView),
                modelView == null ? "?" : (viewRot == null ? "?" : (near(modelView, viewRot) ? "是" : "★否")),
                e == null ? "未记录" : compact(e));
    }

    /** 把 4x4 压成一行：平移量 + 左上 3x3（各保留 2 位小数）。 */
    private static String compact(Matrix4f m) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("T(")
                .append(round(m.m30())).append(',')
                .append(round(m.m31())).append(',')
                .append(round(m.m32())).append(") R[");
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                if (c > 0 || r > 0) {
                    sb.append(' ');
                }
                sb.append(round(m.get(c, r)));
            }
        }
        sb.append(']');
        return sb.toString();
    }

    private static String round(float v) {
        return String.valueOf(Math.round(v * 100.0f) / 100.0f);
    }
}
