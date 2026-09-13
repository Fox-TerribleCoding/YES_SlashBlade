package dev.yessb.compat;

import com.elfmcys.yesstevemodel.O0o0ooO0oOoOoo0O000O0000;
import com.elfmcys.yesstevemodel.Oo0Oo0O0OoOoO0oooO00O0o0;
import com.elfmcys.yesstevemodel.OoO0oo0o0o0oOoo0oOOO0Ooo;
import com.elfmcys.yesstevemodel.o0OOoOOoOO0Oo00OOoOOO0oo;
import com.elfmcys.yesstevemodel.ooOoOOooOoooOoooo0oO0Oo0;
import dev.yessb.FixConfig;
import dev.yessb.YesSlashBladeFix;
import net.minecraft.world.entity.LivingEntity;

/**
 * 与 YSM「拔刀剑联动模块」之间的隔离层。
 *
 * <p>本模组只在这里接触 YSM 的类型；YSM 缺席或版本不匹配时下面的方法不会被调用，
 * 因此不会因为类不存在而崩溃（混入闸门会先拒掉注入，这个类就永远不会被加载）。
 *
 * <h2>背景：这个模块在 1.21.1 被整个掏空了</h2>
 * YSM 2.6.5 的 {@code com.elfmcys.yesstevemodel.o0000o000ooO00O00o0O0ooO} 是空壳
 * （逐条核对过字节码）：
 * <pre>
 *   isSlashBlade(ItemStack)                  -&gt; 恒 false
 *   getAnimationName(ctx)                    -&gt; 恒 ""
 *   playMainAnimation(entity, ctx, name, arg) -&gt; 恒 null
 *   molang slashblade_animation              -&gt; 恒 ""
 * </pre>
 * 而用它的地方全都完好：YSM 的解析器里那段 {@code if (isSlashBlade(...))} 从来没进过，
 * 玩家主动画派发器也因为拿到 {@code null} 而每次都走通用分支。
 * 本模组把这两个返回值补回去 —— <b>只补返回值，不含 YSM 的任何实现</b>。
 *
 * <p>补充语义全部取自 YSM 1.20.1 的公开源码（Apache-2.0，见
 * {@code YesSteveModel/YesSteveModel@dev/1.20} 的 {@code client/compat/slashblade}），
 * 并逐条与本机 2.0.7 拔刀剑的公开 API 对照过。
 *
 * <h2>⚠️ 一个必须记住的坑：名字非空但模型没这条动画</h2>
 * YSM 解析器拿到非空名字后的写法是：
 * <pre>
 *   if (model.hasAnimation(name)) return play(ctx, name, ...);
 *   return CONTINUE;                 // ← 名字非空但动画不存在 ⇒ 到此为止
 * </pre>
 * 后半句会让它<b>跳过</b>后面那段普通的持握/挥动逻辑，模型于是卡在上一帧的姿态里出不来。
 * 实测症状：空中连招（{@code aerial_rave_a2_end2} 这类模型没带的收招）停下后，
 * 模型姿态一直不变，落地了还是那个动作。
 *
 * <p>所以本类的 {@link #animationNameFor} <b>必须</b>在"模型没有这条动画"时返回空串，
 * 让解析器继续往下走。早期版本误以为 {@code CONTINUE} 是"不覆盖"、返回什么名字都不会
 * 压平姿态，那个判断是错的 —— 代价就是上面那个卡死 bug。
 */
public final class YsmSlashBladeModule {

    /** 模型包里拔刀剑动画的统一前缀；主动画会被问成 {@code slashblade:idle} 之类。 */
    private static final String PREFIX = "slashblade:";

    private YsmSlashBladeModule() {
    }

    /**
     * 从 YSM 的动画上下文里取出实体。
     *
     * <p>调用链来自对解析器字节码的观察：{@code ctx.getHolder()} 拿到持有者，
     * 再 {@code holder.getEntity()} 得到实体。持有者可能是载具/护甲之类的非生物持有者，
     * 因此一律用 instanceof 兜底。
     */
    public static LivingEntity livingEntityOf(Oo0Oo0O0OoOoO0oooO00O0o0<?> ctx) {
        if (ctx == null) {
            return null;
        }
        try {
            return holderEntity(ctx.oOoo00O0o0oO0o0oO00OO0O0());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static LivingEntity holderEntity(Object holder) {
        if (holder instanceof OoO0oo0o0o0oOoo0oOOO0Ooo<?> model
                && model.ooo00OoO00OOOO0oOooOo0Oo() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    /** 模型包自己有没有登记这条动画（YSM 播之前也会做同样的检查）。 */
    private static boolean modelHasAnimation(Oo0Oo0O0OoOoO0oooO00O0o0<?> ctx, String name) {
        try {
            Object holder = ctx.oOoo00O0o0oO0o0oO00OO0O0();
            if (holder instanceof OoO0oo0o0o0oOoo0oOOO0Ooo<?> model) {
                return model.Oo0O0OoOo0O0oOoo0000O0oO(name) != null;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ------------------------------------------------------------------ ① 剑技动画

    /**
     * 此刻该播的拔刀剑剑技动画名（形如 {@code slashblade:combo_a1}）；空串表示"照常走通用逻辑"。
     *
     * <p>对应 1.20.1 的 {@code SlashBladeAnimation.getAnimationName(event)}
     * （旧版拔刀剑同时还有 {@code getAnimationName(IContext)} 这个重载，2.6.5 只留了一个）。
     *
     * <p>{@code debugLog=true} 时会顺带报告模型包有没有这条动画 —— 一次进游戏就能分清
     * 是"我们没触发"还是"模型包没带资源"。模型包必须在 {@code ysm.json} 里声明
     * {@code "slashblade": "animations/....json"} 才会有这些动画（官方文档原话：
     * 「如果你修改的拔刀剑动画不起作用，那么先查看这一处是否声明了文件」）。
     */
    public static String animationNameFor(Oo0Oo0O0OoOoO0oooO00O0o0<?> ctx) {
        try {
            LivingEntity entity = livingEntityOf(ctx);
            if (entity == null) {
                return "";
            }
            String name = SlashBladeBridge.comboAnimationName(entity);
            if (name.isEmpty()) {
                return "";
            }
            boolean modelHas = modelHasAnimation(ctx, name);
            diag(name, modelHas);

            // ★ 关键：模型没有这条动画时必须返回空串（理由见类文档顶部）。
            // 返回非空名字会让解析器直接 return CONTINUE，从而跳过普通的持握/挥动分支，
            // 模型就卡在上一帧的姿态里出不来。
            return modelHas ? name : "";
        } catch (Throwable t) {
            return "";
        }
    }

    // ------------------------------------------------------------------ 诊断

    private static long lastDiag;
    private static String lastDiagName = "";

    /** 只在"名字变化"或每隔 2 秒时打一条，避免刷屏（早前每帧一条，日志涨到 2MB）。 */
    private static void diag(String name, boolean modelHas) {
        if (!FixConfig.debugLog) {
            return;
        }
        long now = System.currentTimeMillis();
        if (name.equals(lastDiagName) && now - lastDiag < 2000L) {
            return;
        }
        lastDiag = now;
        lastDiagName = name;
        YesSlashBladeFix.LOGGER.info("[YES-SB] 剑技动画 {}：模型包{}这条动画{}", name,
                modelHas ? "有" : "缺", modelHas ? "" : "（返回空串，交还通用逻辑）");
    }

    private static long lastMainDiag;
    private static String lastMainDiagName = "";

    /** 主动画同理：只在名字变化或每 2 秒打一条。 */
    private static void diagMain(String animationName, boolean modelHas) {
        if (!FixConfig.debugLog) {
            return;
        }
        long now = System.currentTimeMillis();
        if (animationName.equals(lastMainDiagName) && now - lastMainDiag < 2000L) {
            return;
        }
        lastMainDiag = now;
        lastMainDiagName = animationName;
        YesSlashBladeFix.LOGGER.info("[YES-SB] 主动画 {}：{}", animationName,
                modelHas ? "用模型包里的 slashblade 专属版" : "模型包没有专属版，退回原名");
    }

    // ------------------------------------------------------------- ② 主动画的拔刀剑变体

    /**
     * 手持拔刀剑时，把主动画换成 {@code slashblade:<idle/walk/run/jump/sneak/fly/...>}。
     *
     * <p>对应 1.20.1 的
     * {@code SlashBladeCompat.playMainAnimation(entity, event, animationName, loopType)}：
     * <pre>
     *   if (已装拔刀剑 &amp;&amp; 主手是拔刀剑) {
     *       String name = "slashblade:" + animationName;
     *       if (模型里有 name) 播 name; else 播 animationName;   // ← 这里就是所谓的"内置回退"
     *   }
     *   否则返回 null，让 YSM 走通用逻辑
     * </pre>
     * 第二轮把这条回退误判成了"借用默认模型的拔刀剑动画"；实际它只是"没有专属动画就退回原名"。
     *
     * <p>播放本身委托给 YSM 自己的公开入口
     * （{@code o0OOoOOoOO0Oo00OOoOOO0oo} 的静态 resolve），我们只决定用哪个名字。
     *
     * @return 该让解析器采用的返回值；{@code null} 表示"我们不管，交还原逻辑"
     */
    public static O0o0ooO0oOoOoo0O000O0000 mainStateAnimation(Oo0Oo0O0OoOoO0oooO00O0o0<?> ctx,
                                                              String animationName,
                                                              ooOoOOooOoooOoooo0oO0Oo0 loopType) {
        try {
            if (animationName == null || animationName.isEmpty()) {
                return null;
            }
            LivingEntity entity = livingEntityOf(ctx);
            if (entity == null || !SlashBladeBridge.holdsBladeInMainHand(entity)) {
                return null;
            }
            String prefixed = PREFIX + animationName;
            boolean modelHas = modelHasAnimation(ctx, prefixed);
            String chosen = modelHas ? prefixed : animationName;
            diagMain(animationName, modelHas);
            return o0OOoOOoOO0Oo00OOoOOO0oo.oOo0OO0O0o000OO0O000oo0o(ctx, chosen, loopType);
        } catch (Throwable t) {
            // 任何意外都当作"不管"，交还 YSM 原逻辑
            return null;
        }
    }
}
