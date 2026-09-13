package dev.yessb.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yessb.render.VanillaRenderTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "原版渲染路径跑过"的打卡点。
 *
 * <p>被 YSM 接管的实体会跳过这一步（YSM 把这次调用条件化了），
 * 于是"打没打卡"就成了判断是否需要补偿渲染的可靠依据 —— 不需要知道 YSM 的任何内部结构。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void yes_sb$markVanillaRendered(LivingEntity entity, float entityYaw, float partialTicks,
                                            PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                            CallbackInfo ci) {
        VanillaRenderTracker.markVanillaRan(entity);
    }
}
