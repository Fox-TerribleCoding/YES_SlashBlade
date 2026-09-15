package dev.yessb.mixin.tlm;

import dev.yessb.compat.TlmMaidCombatBridge;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把车万女仆 1.21.1 移植时丢掉的「挥刀时出刀光 + 写时间戳」补回去。
 *
 * <h2>为什么打在 {@code LivingEntity} 而不是 {@code EntityMaid}</h2>
 * TLM 1.20.1 的做法是覆写 {@code EntityMaid#swing(InteractionHand)}，
 * 但 <b>1.21.1 的 {@code EntityMaid} 根本没有这个覆写</b>
 * （{@code javap -p} 全表里只有 {@code isSwingingArms}/{@code setSwingingArms}），
 * 打上去只会得到一个"找不到方法"的失败注入。
 *
 * <p>所以这里打原版 {@code LivingEntity#swing(InteractionHand)} 的 HEAD，
 * 再由 {@link TlmMaidCombatBridge} 自己做"是不是女仆 + 拿的是不是拔刀剑 + 是不是攻击任务"的判定。
 * 语义与上游的覆写<b>一一对应</b>：上游也是在这个位置、以同样的先后顺序
 * （先 compat、后 {@code super.swing}）调用的。
 *
 * <h2>为什么这个 1 参版本就够了</h2>
 * {@code LivingEntity#swing(InteractionHand)} 只是个一行转发（{@code swing(hand, false)}），
 * 而两侧的调用者走的都是它：
 * <ul>
 *   <li>服务端 —— TLM 的 {@code MaidMeleeAttack} 调 {@code maid.swing(MAIN_HAND)}；</li>
 *   <li>客户端 —— 收到动画包后 {@code ClientPacketListener#handleAnimate} 调
 *       {@code entity.swing(InteractionHand)}。</li>
 * </ul>
 * ⇒ <b>同一份注入在两侧各跑一次</b>，服务端出事刀光、客户端写渲染用的时间戳，
 * 正好凑齐上游那两个副作用（详见 {@link TlmMaidCombatBridge} 的表格）。
 *
 * <p>注入本身是 failure-soft 的（本组配置 {@code required=false} + {@code defaultRequire=0}），
 * 靶点不存在时只是这一项不生效。
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityMaidSwing {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"))
    private void yes_sb$maidSwing(InteractionHand hand, CallbackInfo ci) {
        TlmMaidCombatBridge.onSwing((LivingEntity) (Object) this, hand);
    }
}
