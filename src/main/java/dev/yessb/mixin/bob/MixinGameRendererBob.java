package dev.yessb.mixin.bob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 把原版的「视角摇晃」数学借出来，供第一人称的刀使用。
 *
 * <p>{@code GameRenderer#bobView(PoseStack, float)} 是 <b>private</b> 的。本模组不重写那几行数学，
 * 而是用 {@code @Invoker} 直接调它 —— 这样：
 * <ul>
 *   <li>刀的摇晃与手部<b>逐像素一致</b>（同一份实现、同一个 partialTicks）；</li>
 *   <li>Minecraft 日后改动摇晃算法时，我们<b>自动跟随</b>，不会悄悄漂掉。</li>
 * </ul>
 *
 * <p><b>为什么单独一个混入类 + 单独一个混入配置</b>（{@code yessb.bob.mixins.json}，
 * {@code required=false} + {@code defaultRequire=0}）：
 * {@code bobView} 是私有方法，名字/签名跨版本可能变。按本项目踩过的坑
 * （见 {@code 进展与结论.md} §13.7：一条注入校验失败会让<b>整份混入类</b>作废），
 * 这类靶点必须<b>隔离</b>——它失败时只会让"借不到摇晃"这一件事失效，
 * 调用方 {@code FirstPersonBob} 会捕获并永久停用该功能，其余功能与游戏都不受影响。
 *
 * <p>靶点是 <b>Minecraft 自己的类</b>（不是第三方模组的内部结构），因此本身也属于
 * 「能过校验的安全靶点」那一类。
 */
@Mixin(GameRenderer.class)
public interface MixinGameRendererBob {

    /** 转调 {@code GameRenderer#bobView}。 */
    @Invoker("bobView")
    void yes_sb$bobView(PoseStack poseStack, float partialTicks);
}
