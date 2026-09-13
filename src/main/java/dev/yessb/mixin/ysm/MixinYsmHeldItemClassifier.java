package dev.yessb.mixin.ysm;

import dev.yessb.FixConfig;
import dev.yessb.compat.SlashBladeBridge;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 YSM 漏掉的那个"拔刀剑"分类补回去。
 *
 * <p>背景：YSM 的手持物分类器把手持物品归类为一个"类型串"，
 * 这个串会被用来拼出条件动画名，例如 {@code hold_mainhand:sword}、{@code swing:axe}。
 * 官方文档明确列出了 {@code hold_mainhand:slashblade} / {@code swing:slashblade} /
 * {@code use_mainhand:slashblade} 三个内置分类，YSM 自己的默认模型里也带着这三个动画，
 * 而该分类器读了 13 个内置标签中的 12 个 —— <b>唯独漏掉拔刀剑</b>。
 *
 * <p>后果不是"归类为空"，而是<b>沾了别的分类的光</b>：拔刀剑的
 * {@code ItemSlashBlade extends SwordItem}（已核实），
 * 所以它会落到第一个判断上、被归成 {@code sword} ⇒ 播的是普通剑的持握/挥动动画，
 * 而不是官方为它准备的那三套。
 *
 * <p>做法：目标方法开头判断"这是不是拔刀剑"，是就提前返回官方约定的分类串。
 * 这里只有我们自己的一行判断，不含 YSM 的任何代码。
 *
 * <p>注意本项<b>默认关闭</b>（{@code slashbladeAnimations}）：它会把分类从 {@code sword}
 * 改成 {@code slashblade}，属于行为变更；模型包若没带 {@code swing:slashblade}，
 * 原本能用的 {@code swing:sword} 也会一起失效。剑技动画靠
 * {@code slashbladeComboAnimations} 就够了，通常不需要动这里。
 */
@Mixin(targets = "com.elfmcys.yesstevemodel.Oooo0o00000O00o0o0oo0Ooo", remap = false)
public abstract class MixinYsmHeldItemClassifier {

    private static final String YES_SB$SLASHBLADE = "slashblade";

    @Inject(
            method = "oOo0OO0O0o000OO0O000oo0o(Lnet/minecraft/world/item/ItemStack;)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void yes_sb$classifySlashBlade(ItemStack stack, CallbackInfoReturnable<String> cir) {
        if (FixConfig.enabled && FixConfig.slashbladeAnimations
                && SlashBladeBridge.isBlade(stack)) {
            cir.setReturnValue(YES_SB$SLASHBLADE);
        }
    }
}
