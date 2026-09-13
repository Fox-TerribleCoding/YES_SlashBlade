package dev.yessb.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yessb.render.BladeLayerRestorer;
import dev.yessb.render.VanillaRenderTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 实体渲染的进出场记录 + 收尾补偿渲染。
 *
 * <p>选择 {@code EntityRenderDispatcher#render} 作为落点，是因为：
 * <ul>
 *   <li>它同时是原版渲染路径和 YSM 渲染路径的公共入口，
 *       因此无论中间被谁接管，收尾都在同一个位置；</li>
 *   <li>它把实体世界坐标 x/y/z 直接交给我们，不必再去别处反推；</li>
 *   <li>它是原版public API，不受任何模组影响，比注入某个模组的内部实现稳得多。</li>
 * </ul>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void yes_sb$onRenderHead(Entity entity, double x, double y, double z, float rotationYaw,
                                     float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
                                     int packedLight, CallbackInfo ci) {
        // 顺带记下"这个实体带不带腰挂层"，供收尾补偿与物品渲染两侧共用
        // （两处判断必须一致，否则会出现重影）。
        VanillaRenderTracker.enter(entity, BladeLayerRestorer.hasWaistLayer(entity));
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void yes_sb$onRenderReturn(Entity entity, double x, double y, double z, float rotationYaw,
                                       float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight, CallbackInfo ci) {
        boolean shouldRestore = VanillaRenderTracker.shouldRestore();
        if (!shouldRestore) {
            // 原版渲染器照常跑了（拔刀剑自己那层已经画过），或者是嵌套重入，都不该重复画
            BladeLayerRestorer.noteVanillaRan(entity);
            return;
        }
        BladeLayerRestorer.restore(entity, x, y, z, partialTicks, poseStack, buffer, packedLight);
    }
}
