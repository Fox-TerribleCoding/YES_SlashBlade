package dev.yessb.mixin.ysm;

import com.elfmcys.yesstevemodel.O0o0ooO0oOoOoo0O000O0000;
import com.elfmcys.yesstevemodel.Oo0Oo0O0OoOoO0oooO00O0o0;
import com.elfmcys.yesstevemodel.ooOoOOooOoooOoooo0oO0Oo0;
import dev.yessb.FixConfig;
import dev.yessb.compat.SlashBladeBridge;
import dev.yessb.compat.YsmSlashBladeModule;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 YSM 2.6.5 里被掏空的「拔刀剑联动模块」补回来。
 *
 * <p>目标类 {@code com.elfmcys.yesstevemodel.o0000o000ooO00O00o0O0ooO} 在 1.21.1 是空壳
 * （逐条核对过字节码）：
 * <ul>
 *   <li>{@code isSlashBlade(ItemStack)} 恒 {@code false}</li>
 *   <li>{@code getAnimationName(ctx)} 恒 {@code ""}</li>
 *   <li>{@code playMainAnimation(entity, ctx, name, arg)} 恒 {@code null}</li>
 *   <li>molang {@code slashblade_animation} 恒 {@code ""}</li>
 * </ul>
 * 这些返回值让 YSM 自己的两处调用点成了死代码。本混入<b>只在方法开头补返回值</b>，
 * 不替换、不复制 YSM 的任何实现，一共补三处：
 * <ol>
 *   <li>{@code isSlashBlade} —— 让解析器那段「手持拔刀剑」的分支能进去；</li>
 *   <li>{@code getAnimationName} —— 补出当前该播的剑技动画名（{@code slashblade:combo_a1} 等）；</li>
 *   <li>{@code playMainAnimation} —— 补出主动画的拔刀剑变体（{@code slashblade:idle/walk/...}）。</li>
 * </ol>
 *
 * <p><b>⚠️ 返回值有个必须遵守的约束</b>：剑技那条在"模型没有这条动画"时<b>必须返回空串</b>。
 * YSM 解析器拿到非空名字后会 {@code if (hasAnimation) play; else return CONTINUE;}，
 * 那个 {@code CONTINUE} 会<b>跳过</b>普通挥动分支 —— 模型就卡在上一帧姿态。
 * 详见 {@link YsmSlashBladeModule} 的类文档。早期版本误判为"不覆盖"，代价是一个卡死 bug。
 *
 * <p>靶向注入是版本敏感的，由 {@code YsmMixinPlugin} 按版本前缀白名单放行；
 * 配置里 {@code required=false} + {@code defaultRequire=0}，靶点不存在时只是不生效。
 */
@Mixin(targets = "com.elfmcys.yesstevemodel.o0000o000ooO00O00o0O0ooO", remap = false)
public abstract class MixinYsmSlashBladeModule {

    /** 这是不是拔刀剑（本体，或登记进 YSM 标签的附属）。 */
    @Inject(
            method = "oOo0OO0O0o000OO0O000oo0o(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void yes_sb$isSlashBlade(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (FixConfig.enabled && FixConfig.slashbladeComboAnimations && SlashBladeBridge.isBlade(stack)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }

    /** 当前该播哪条剑技动画（形如 {@code slashblade:combo_a1}）。 */
    @Inject(
            method = "oOo0OO0O0o000OO0O000oo0o(Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void yes_sb$slashBladeAnimationName(Oo0Oo0O0OoOoO0oooO00O0o0<?> ctx,
                                                       CallbackInfoReturnable<String> cir) {
        if (!FixConfig.enabled || !FixConfig.slashbladeComboAnimations) {
            return;
        }
        String name = YsmSlashBladeModule.animationNameFor(ctx);
        if (name != null && !name.isEmpty()) {
            cir.setReturnValue(name);
        }
    }

    /**
     * 手持拔刀剑时，把主动画换成 {@code slashblade:idle} / {@code slashblade:walk} 之类。
     *
     * <p>这是 1.20.1 那份兼容模块的另外一半（{@code playMainAnimation}）。空桩恒返回 null，
     * 于是玩家主动画派发器每次都跳过它走通用分支 —— 持刀时的待机/走/跑/跳/潜行专属动作全没了。
     * 这里只在"主手是拔刀剑"时接管；其余情况原样返回 null。
     *
     * <p>第一个参数 {@code entity} 本方法用不到（实体是从 {@code ctx} 里取的），
     * 但必须保留以匹配靶点签名。
     */
    @Inject(
            method = "oOo0OO0O0o000OO0O000oo0o(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;"
                    + "Ljava/lang/String;"
                    + "Lcom/elfmcys/yesstevemodel/ooOoOOooOoooOoooo0oO0Oo0;)"
                    + "Lcom/elfmcys/yesstevemodel/O0o0ooO0oOoOoo0O000O0000;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void yes_sb$mainStateAnimation(LivingEntity entity,
                                                  Oo0Oo0O0OoOoO0oooO00O0o0<?> ctx,
                                                  String animationName,
                                                  ooOoOOooOoooOoooo0oO0Oo0 loopType,
                                                  CallbackInfoReturnable<O0o0ooO0oOoOoo0O000O0000> cir) {
        if (!FixConfig.enabled || !FixConfig.slashbladeMainStateAnimations) {
            return;
        }
        O0o0ooO0oOoOoo0O000O0000 result = YsmSlashBladeModule.mainStateAnimation(ctx, animationName, loopType);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
