package dev.yessb.mixin.sb;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yessb.render.FirstPersonPoseBase;
import mods.flammpfeil.slashblade.client.renderer.model.BladeFirstPersonRender;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修掉"开光影后第一人称的刀跑到别处、并随移动漂移"。
 *
 * <p>{@code BladeFirstPersonRender#render} 用
 * <pre>
 *     me.pose().identity();
 *     me.normal().identity();
 * </pre>
 * 把姿态矩阵清零，假定了"清零 = 相机空间"。该假定只在 {@code ModelViewMat} 是单位矩阵时成立；
 * Iris 会把「视角摇晃矩阵」塞进 {@code ModelViewMat}（见 {@code FirstPersonPoseBase} 类注释），
 * 于是清零多留下一层摇晃矩阵 ⇒ 刀被摆到别处、并随移动漂移。
 *
 * <p>本混入把这两次 {@code identity()} 重定向到 {@link FirstPersonPoseBase}：
 * 基准改用 {@code ModelViewMat⁻¹}，结果恒等于"相机空间下的那套变换"，与光影无关；
 * 无光影时（ModelView≈I）是恒等替换。
 *
 * <h2>⚠️ 教训：这里刻意<b>只放能过校验的靶点</b></h2>
 * 曾经还在这里加过第三条 —— 重定向 {@code Iris.isPackInUseQuick()}
 * （用来停用拔刀剑自己那段"Iris 近似补偿"）。但那条在 <b>Mixin 校验阶段</b>就报
 * {@code InvalidInjectionException: Failed validating @At("INVOKE").target}，
 * 而 {@code require = 0} <b>挡不住这种失败</b> —— 它会让<b>整份混入类</b>作废，
 * 于是连上面两条也一起没注入（1.0.9 实测"与 1.0.8 完全一致"就是这个原因）。
 *
 * <p>所以现在：靶点只用 Minecraft / JOML 自己的类（必定可加载、校验必过）；
 * 那段"Iris 近似补偿"的处理**另案**，做的时候必须放在<b>独立的混入类</b>里，
 * 失败时只连累它自己。
 *
 * <p>全部 {@code require = 0}，配置里 {@code defaultRequire=0}：靶点找不到就静默跳过，回到原行为。
 */
@Mixin(value = BladeFirstPersonRender.class, remap = false)
public class MixinBladeFirstPersonRender {

    /**
     * 自检：本混入是否真的注入成功了。
     *
     * <p>只报一次。它回答一个很实际的问题 —— "这次测试跑的到底是不是修好的那份"。
     * 之前就是缺了这么一条，才让"靶点校验失败 ⇒ 整份混入作废"白白吃掉一轮实测。
     */
    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            require = 0,
            remap = false)
    private void yes_sb$alive(PoseStack poseStack, MultiBufferSource buffer, int light, CallbackInfo ci) {
        FirstPersonPoseBase.noteAlive();
    }

    /** 姿态基准：{@code me.pose().identity()}。 */
    @Redirect(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;identity()Lorg/joml/Matrix4f;"),
            require = 0,
            remap = false)
    private Matrix4f yes_sb$firstPersonPoseBase(Matrix4f self) {
        return FirstPersonPoseBase.apply(self);
    }

    /** 法线基准：{@code me.normal().identity()}。 */
    @Redirect(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3f;identity()Lorg/joml/Matrix3f;"),
            require = 0,
            remap = false)
    private Matrix3f yes_sb$firstPersonNormalBase(Matrix3f self) {
        return FirstPersonPoseBase.applyNormal(self);
    }
}
