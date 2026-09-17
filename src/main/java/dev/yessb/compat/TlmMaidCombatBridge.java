package dev.yessb.compat;

import dev.yessb.FixConfig;
import dev.yessb.YesSlashBladeFix;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 车万女仆"挥刀"这件事本身 —— 与 TLM 之间的第二个隔离层（另一个见 {@link TlmMaidBridge}）。
 *
 * <h2>背景：TLM 1.21.1 丢掉的第三处</h2>
 * TLM 的 {@code 1.20} 与 {@code 1.21} 分支里，{@code EntityMaid} 覆写了 {@code swing}：
 * <pre>
 *   public void swing(InteractionHand pHand) {
 *       SlashBladeCompat.swingSlashBlade(this, getItemInHand(pHand));
 *       super.swing(pHand);
 *   }
 * </pre>
 * 而在 <b>1.21.1 的发布版 jar 里，这个覆写连同整个 {@code compat/slashblade} 包一起不见了</b>
 * （{@code javap -p} 全表里只剩 {@code isSwingingArms}/{@code setSwingingArms}；
 * 全 jar 对 {@code slashblade} 的引用数为 0）。于是女仆照常挥刀、照常造成伤害，
 * 但<b>既没有刀光，刀也不会出鞘</b>。
 *
 * <h2>为什么信号点是 swing()</h2>
 * 客户端并不知道"女仆挥刀了"，它只知道收到了一个动画包：
 * <pre>
 *   服务端 MaidMeleeAttack → maid.swing(MAIN_HAND) → LivingEntity.swing(hand,false)
 *        → new ClientboundAnimatePacket(this, 0) → ServerChunkCache#broadcast
 *   客户端 ClientPacketListener#handleAnimate → LivingEntity.swing(MAIN_HAND)
 * </pre>
 * 也就是说 <b>{@code swing()} 是同一份代码会在两侧各跑一次的方法</b>，
 * 而那两个副作用正好一个属于服务端、一个属于客户端：
 *
 * <table border="1">
 *   <tr><th>副作用</th><th>生效的一侧</th><th>机制</th></tr>
 *   <tr><td>刀光（斩击特效）</td><td><b>服务端</b></td>
 *       <td>生成 {@code EntitySlashEffect} 实体，由原版实体同步发给所有客户端</td></tr>
 *   <tr><td>刀模出鞘动作</td><td><b>客户端</b></td>
 *       <td>写客户端自己那份 ItemStack 上的 {@code lastActionTime}，渲染层读的就是它</td></tr>
 * </table>
 *
 * <p>⚠️ 注意 {@code AttackManager.doSlash(...)} 在客户端<b>第一句就 {@code return null}</b>
 * （1.9.65 与 2.0.7 都是），所以客户端那次调用打不出刀光 —— 但它<b>不影响</b>紧随其后的
 * 写时间戳那一句。这不是巧合，而是上游那五行代码的设计意图。
 *
 * <p>本类按<b>行为语义</b>重新实现，不复制上游代码文件；数值（摆动角范围、特效参数、
 * 时间戳）与上游一致，因为那些就是"观感"本身。归属声明见 NOTICE。
 */
public final class TlmMaidCombatBridge {

    private TlmMaidCombatBridge() {
    }

    // ---------------------------------------------------------------- 行为常量

    /** 斩击特效的摆动角在上游是 {@code [±30)} 度。 */
    private static final int ROLL_SPAN = 60;
    private static final int ROLL_MIN = -30;

    /** 特效强度参数，与上游一致（1.0 是标准伤害倍率；真实伤害由 {@code doHurtTarget} 负责）。 */
    private static final double SLASH_DAMAGE = 1.0D;

    // ---------------------------------------------------------------- 入口

    /**
     * 挥刀信号的统一入口。
     *
     * <p>由 {@code dev.yessb.mixin.tlm.MixinLivingEntityMaidSwing} 在
     * {@code LivingEntity#swing(InteractionHand)} 的 HEAD 调用 —— <b>两侧都会调到</b>，
     * 这正是我们要的（服务端出刀光、客户端写时间戳）。任何异常都安静地吞掉：
     * 这条路径太靠近每帧逻辑，绝不能因为本模组抛异常而影响游戏。
     */
    public static void onSwing(LivingEntity entity, InteractionHand hand) {
        try {
            if (!FixConfig.enabled || !FixConfig.maidSlashBladeAttack) {
                return;
            }
            // ⚠️ 第一道闸必须是"有没有装 TLM"，且只能用 ModList 查 ——
            // 本类**一行都不能引用 TLM 的类型**，否则没装 TLM 的整合包里，
            // 本类在链接/校验阶段就会抛 NoClassDefFoundError（try 都进不去）。
            // 1.0.13 在 ATM10 上的崩溃就是这个原因，详见 ModPresence 的类注释。
            if (!ModPresence.hasTlm()) {
                return;
            }
            LivingEntity maid = entity;
            if (!ModPresence.isMaid(maid)) {
                // 打的是原版 LivingEntity，所以每只生物挥刀都会经过这里；先做最便宜的判定。
                // 这一条刻意完全静默：它会被每只怪、每次左键打到，打日志没有意义。
                return;
            }
            if (upstreamOwnsSwing(maid.getClass())) {
                // TLM 哪天自己把覆写加回来，我们就自动让路（见 NOTICE 的退役承诺），
                // 免得同一帧跑两遍、变成两道刀光。
                report(maid, hand, "让路（上游已自己声明 swing 覆写）", false, false);
                return;
            }
            // 同样只查 ModList：拔刀剑缺席时不能去碰 SlashBladeBridge（那会加载它，
            // 而它的字段/局部变量里有拔刀剑的类型，属于同一类隐患）。
            if (!ModPresence.hasSlashBlade()) {
                report(maid, hand, "跳过（拔刀剑未安装）", false, false);
                return;
            }

            ItemStack stack = maid.getItemInHand(hand);
            if (!SlashBladeBridge.isBlade(stack)) {
                report(maid, hand, "跳过（该手不是拔刀剑）", false, false);
                return;
            }
            if (!TlmMaidBridge.isAttackTask(maid)) {
                // 上游的判定是"任务必须是攻击任务"，否则砍树挖矿也会甩刀光。
                // 这里把读到的任务名一并打出来：客户端任务若读不到，会显示成 ? 或 idle。
                report(maid, hand, "跳过（任务不是攻击任务）", false, false);
                return;
            }

            int roll = maid.getRandom().nextInt(ROLL_SPAN) + ROLL_MIN;

            // 服务端这次真的会生成斩击特效；客户端这次是空转，无害。
            boolean arc = SlashBladeBridge.showSlashArc(maid, roll);
            // 客户端这次写的才是关键：渲染层靠它判断"刚出鞘"。
            boolean marked = SlashBladeBridge.markLastAction(maid, stack);

            report(maid, hand, "生效", arc, marked);
        } catch (Throwable t) {
            // 失败即静默：本模组的任何一环出问题都不该影响游戏。
            // 但要留痕 —— 否则"没生效"和"抛异常"在日志里长得一模一样（第十一轮的教训）。
            reportQuietly(entity, hand, "★异常 " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    // ---------------------------------------------------------------- 让路闸

    /**
     * 该实体类是否<b>自己声明了</b> {@code swing(InteractionHand)}。
     *
     * <p>我们注入的是原版 {@code LivingEntity#swing}（因为 1.21.1 的 {@code EntityMaid}
     * 已经没有这个覆写了）。一旦上游把它加回来，虚分派会先走它的覆写、再走
     * {@code super.swing}，于是我们的注入仍然会触发 ⇒ 同一帧两遍。
     * 这里用"方法声明在哪个类上"来判定，结果按类缓存（一个实体类只查一次反射）。
     *
     * <p>查不出来时返回 {@code true}（让路）—— 宁可不动，也不要重复。
     */
    private static final ClassValue<Boolean> OWNS_SWING = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("swing", InteractionHand.class).getDeclaringClass() != LivingEntity.class;
            } catch (Throwable t) {
                return Boolean.TRUE;
            }
        }
    };

    private static boolean upstreamOwnsSwing(Class<?> type) {
        try {
            return OWNS_SWING.get(type);
        } catch (Throwable t) {
            return true;
        }
    }

    // ---------------------------------------------------------------- 诊断

    /**
     * 每一侧最先无条件打印的条数。
     *
     * <p>这是<b>被实测打脸之后加的</b>：原来只有一个共享的 2 秒节流，
     * 而服务端那次 {@code swing()} 与客户端那次（动画包回来触发）<b>只差约 1 刻</b>，
     * 于是一旦服务端先打了，客户端那条<b>必定</b>被节流吃掉 ——
     * 结果就是"客户端到底跑没跑"这件最该看清的事，恰好被日志藏了起来
     * （1.0.5 实测日志里 6 条服务端、0 条客户端，就是这么来的）。
     *
     * <p>现在改成：<b>按侧各自计数与节流</b>，每侧前几条必打，
     * 另外只要"结果变了"也必打。这样两侧跑没跑一眼可辨。
     */
    private static final int FIRST_PER_SIDE = 3;

    /** 下标 0 = 客户端，1 = 服务端。 */
    private static final AtomicInteger[] SEEN = {new AtomicInteger(), new AtomicInteger()};
    private static final long[] LAST_AT = new long[2];
    private static final String[] LAST_KEY = {"", ""};

    private static void report(LivingEntity maid, InteractionHand hand, String what, boolean arc, boolean marked) {
        if (!FixConfig.debugLog || maid == null) {
            return;
        }
        try {
            int side = maid.level().isClientSide() ? 0 : 1;
            String key = what + '|' + arc + '|' + marked + '|' + hand;
            int n = SEEN[side].incrementAndGet();
            long now = System.currentTimeMillis();
            boolean first = n <= FIRST_PER_SIDE;
            boolean cooled = now - LAST_AT[side] >= 2000L;
            boolean changed = !key.equals(LAST_KEY[side]);
            if (!first && !cooled && !changed) {
                return;
            }
            LAST_AT[side] = now;
            LAST_KEY[side] = key;
            YesSlashBladeFix.LOGGER.info(
                    "[YES-SB] 女仆挥砍：侧={} 手={} 结果={} 刀光={} 时间戳={} 累计={} 任务={}",
                    side == 0 ? "客户端" : "服务端",
                    hand == InteractionHand.MAIN_HAND ? "主手" : "副手",
                    what,
                    arc ? "已生成" : "未生成",
                    marked ? "已写" : "未写",
                    n,
                    taskName(maid));
        } catch (Throwable ignored) {
            // 诊断本身绝不能成为新的故障点
        }
    }

    /** 连实体都还不是女仆时的兜底留痕（异常路径用），不依赖任何 TLM 类型。 */
    private static void reportQuietly(LivingEntity entity, InteractionHand hand, String what) {
        if (!FixConfig.debugLog) {
            return;
        }
        try {
            if (ModPresence.isMaid(entity)) {
                report(entity, hand, what, false, false);
            } else {
                YesSlashBladeFix.LOGGER.info("[YES-SB] 女仆挥砍：侧={} 结果={}（非女仆）",
                        entity != null && entity.level().isClientSide() ? "客户端" : "服务端", what);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 把当前任务名读出来给日志用。
     *
     * <p>真正的读取放在 {@link TlmMaidBridge} 里 —— 那里可以正大光明地引用 TLM 的类型，
     * 因为**只有确认是女仆之后**才会走到这里（而女仆存在就说明 TLM 装了）。
     */
    private static String taskName(LivingEntity maid) {
        return TlmMaidBridge.describeTask(maid);
    }
}
