package dev.yessb.compat;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.ILocationBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yessb.FixConfig;
import dev.yessb.YesSlashBladeFix;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 车万女仆（TLM）身上那把拔刀剑 —— 与 TLM 之间的隔离层。
 *
 * <h2>为什么需要它：TLM 1.21.1 移植时丢了两个分支</h2>
 * TLM 的 {@code 1.20} 与 {@code 1.21} 分支里有两处拔刀剑专用渲染，而在
 * <b>1.21.1 的发布版 jar 里两处都不见了</b>（逐条比对过字节码，其余结构完全一致）：
 *
 * <ol>
 *   <li><b>手部</b>（{@code GeckoLayerMaidHeld.render}）：
 * <pre>
 *   if (SlashBladeCompat.isSlashBladeItem(mainHandItem)) {
 *       SlashBladeRender.renderMaidMainhandSlashBlade(...);   // ← 发布版没有
 *   } else {
 *       renderArmWithItem(entity, mainHandItem, geoModel, THIRD_PERSON_RIGHT_HAND, RIGHT, ...);
 *   }
 * </pre>
 *       于是拔刀剑掉进 {@code ItemInHandRenderer} → {@code ItemRenderer.renderStatic(..., THIRD_PERSON_*)}，
 *       而拔刀剑的 BEWLR 对第三方称上下文<b>什么都不画</b> ⇒ 女仆手里空着。</li>
 *
 *   <li><b>背槽</b>（{@code GeckoLayerMaidBackItem.render}）：
 * <pre>
 *   if (SlashBladeCompat.isSlashBladeItem(stack)) {
 *       SlashBladeRender.renderGeckoMaidBackSlashBlade(...);  // ← 发布版没有
 *   } else {
 *       Minecraft.getInstance().getItemRenderer().renderStatic(entity, stack, FIXED, ...);
 *   }
 * </pre>
 *       于是装饰槽里的拔刀剑掉进 {@code FIXED}，被画成一张<b>平面图标</b>。</li>
 * </ol>
 *
 * <h2>本类做的事</h2>
 * 把这两个分支按原样补回来。变换、缩放、旋转、以及"刀鞘常驻 + 动作后若干刻内才出鞘"的判定
 * 全部照抄 TLM 的实现，<b>数值一个都没改</b>（TLM 代码为 MIT 许可，见 NOTICE）。我们只做两件事不同：
 * <ul>
 *   <li>不引用 TLM 的 {@code SlashBladeCompat}/{@code SlashBladeRender}（那两个类在 1.21.1 里不存在），
 *       改为调用本模组的 {@link SlashBladeBridge}；</li>
 *   <li>所有路径都做了兜底，任何异常都安静地返回 false，让 TLM 走它平常的逻辑。</li>
 * </ul>
 *
 * <p>本类只在 TLM 存在时被加载（混入闸门保证），因此不会因为类缺失而崩溃。
 */
public final class TlmMaidBridge {

    private TlmMaidBridge() {
    }

    // ------------------------------------------------------------------ 手部

    /**
     * 女仆手里这把刀是否由本模组接管（是的话调用方应跳过 TLM 原本的物品渲染）。
     *
     * <p>对应 TLM 源码里的 {@code SlashBladeCompat.isSlashBladeItem(...)}。
     */
    public static boolean handles(LivingEntity maid, ItemStack stack, ILocationModel model, HumanoidArm arm) {
        if (!FixConfig.enabled || !FixConfig.maidSlashBlade || maid == null || model == null) {
            return false;
        }
        if (!SlashBladeBridge.isBlade(stack)) {
            return false;
        }
        // 与 TLM 一致：那只手没有定位组骨骼时，它压根不会走到这里，也就没有刀可画
        List<? extends ILocationBone> handBones =
                arm == HumanoidArm.LEFT ? model.leftHandBones() : model.rightHandBones();
        boolean ok = handBones != null && !handBones.isEmpty();
        if (!ok) {
            diag("女仆刀：{}手 没有定位组骨骼（leftHandBones/rightHandBones 为空）⇒ 不接管，退回 TLM 原本的逻辑",
                    arm == HumanoidArm.LEFT ? "副" : "主");
        }
        return ok;
    }

    /**
     * 画女仆手里那把拔刀剑。
     *
     * <p>变换与 TLM 的 {@code SlashBladeRender.renderMaidMainhandSlashBlade} /
     * {@code renderMaidOffhandSlashBlade} 一致：
     * <pre>
     *   移到腰侧定位组（主手用 LeftWaistLocator，副手用 RightWaistLocator）
     *   →（没有定位组时退到固定位移 + 俯仰）
     *   → translate(0, 0, -0.7)
     *   → scale(maidBladeScale)        // TLM Gecko 路径原值 0.01
     *   → rotY(-90) → rotZ(180)
     *   → 画鞘；若是刚出鞘，再转一个姿态；最后画刀身
     * </pre>
     *
     * <p>注意 TLM 把<b>主手</b>的刀画在<b>左侧腰位</b>（源码注释原文
     * {@code // 主手的刀渲染在左边}），并不是画在手上 —— 这是它的既有设计，我们原样保留。
     *
     * @param arm 该手（TLM 里主手对应 {@code LEFT} 的腰位、副手对应 {@code RIGHT}）
     * @return 是否画成功
     */
    public static boolean renderMaidBlade(LivingEntity maid, ItemStack stack, ILocationModel model,
                                          HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer,
                                          int light, float partialTicks) {
        try {
            boolean offhand = arm == HumanoidArm.LEFT;

            poseStack.pushPose();
            try {
                List<? extends ILocationBone> waistBones =
                        offhand ? model.rightWaistBones() : model.leftWaistBones();
                if (waistBones != null && !waistBones.isEmpty()) {
                    // 注意：这个返回值的含义是"定位组缩放为 0（即被隐藏）"，不是"没找到"。
                    // TLM 的腰位路径同样忽略它、无条件施加变换，这里保持一致。
                    RenderUtils.prepMatrixForLocator(poseStack, waistBones);
                } else {
                    // 模型没有腰位定位组时的退路，与 TLM 一致
                    poseStack.translate(offhand ? 0.25D : -0.25D, 1.25D, 0.0D);
                    poseStack.mulPose(Axis.XP.rotationDegrees(offhand ? 5.0F : 20.0F));
                }
                poseStack.translate(0.0D, 0.0D, -0.7D);

                float scale = (float) FixConfig.maidBladeScale;
                poseStack.scale(scale, scale, scale);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));

                applyMaidTweaks(poseStack, scale);

                // 刀鞘：不管出没出鞘都画在腰上
                SlashBladeBridge.renderSheath(stack, poseStack, buffer, light);

                if (!offhand) {
                    // 主手这条有"刚出鞘"的姿态；副手那条 TLM 是鞘 + 刀身一起画的，没有这一段
                    long elapsed = SlashBladeBridge.ticksSinceLastAction(maid, stack);
                    boolean drawn = elapsed >= 0L && elapsed < FixConfig.maidBladeDrawTicks;
                    if (drawn) {
                        float i = elapsed + partialTicks;
                        // 分母为什么是 0.007：TLM 原代码照抄自 Bedrock 那条（缩放 0.007），
                        // 而 Gecko 这条的缩放是 0.01。属于上游的既成行为，为对齐手感原样保留。
                        poseStack.translate(0.0D, 0.0D,
                                -0.5D / FixConfig.maidBladeDrawDistanceFactor);
                        poseStack.mulPose(Axis.YP.rotationDegrees(60.0F + i * 48.0F));
                        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                    }
                    // 只在"是否出鞘"翻转时打一条：这是唯一能证明
                    // 「客户端那份时间戳真的写进去了」的观测点（见 §12 的分析）。
                    noteDrawnState(maid, drawn, elapsed);
                }

                SlashBladeBridge.renderBladeBody(stack, poseStack, buffer, light);
                // 这里刻意<b>不</b>打"已画出"的日志：它每 2 秒就会刷一条，
                // 而它要证明的事（TLM 渲染路径接管成功）在 1.0.1 时代就已经验证完了 ——
                // 属于"当时为了证明某条路活着而加、现在只剩噪音"的那一类。
                // 真正有价值的是失败路径（见 handles() 里那条"没有定位组骨骼 ⇒ 不接管"）。
                return true;
            } finally {
                poseStack.popPose();
            }
        } catch (Throwable t) {
            // 任何意外都当作"我们不管"，交还 TLM 原本的物品渲染
            return false;
        }
    }

    // ------------------------------------------------------------------ 背槽（装饰槽）

    /**
     * 画女仆<b>背槽（装饰槽）</b>那把刀。
     *
     * <p>变换照抄 TLM 的 {@code renderGeckoMaidBackSlashBlade}：
     * {@code translate(1.25, -0.25, 0)} → {@code rotZ(-15)} → {@code scale}，
     * 也就是"斜挂在背上"。
     *
     * @return 是否画成功（false 时调用方应照常走 {@code renderStatic}）
     */
    public static boolean renderMaidBackBlade(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer,
                                              int light) {
        if (!FixConfig.enabled || !FixConfig.maidSlashBlade || !SlashBladeBridge.isBlade(stack)) {
            return false;
        }
        try {
            poseStack.pushPose();
            try {
                poseStack.translate(1.25D, -0.25D, 0.0D);
                poseStack.mulPose(Axis.ZN.rotationDegrees(15.0F));

                float scale = (float) FixConfig.maidBladeScale;
                poseStack.scale(scale, scale, scale);

                applyMaidTweaks(poseStack, scale);

                // TLM 背槽版本画的是完整的"刀 + 鞘"
                SlashBladeBridge.renderSheath(stack, poseStack, buffer, light);
                SlashBladeBridge.renderBladeBody(stack, poseStack, buffer, light);
                // 同手部那条：成功路径不打日志（理由见上方注释）
                return true;
            } finally {
                poseStack.popPose();
            }
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 攻击任务判定

    /**
     * 这只女仆是不是被指派了"攻击"任务。
     *
     * <p>上游的挥刀兼容里有一道前置判定：任务必须是攻击任务，否则砍树、钓鱼之类也会甩出刀光。
     * 任务 UID 走 TLM 自己的 {@code SynchedEntityData}（{@code DATA_TASK}，字符串），
     * <b>所以客户端同样拿得到</b> —— 这一条对"客户端那次 swing 也要能通过判定"是必需的。
     *
     * <p>本方法只在 TLM 存在时被调用（混入闸门保证），因此可以直接引用它的类型。
     */
    public static boolean isAttackTask(LivingEntity maid) {
        try {
            if (!(maid instanceof EntityMaid m)) {
                return false;
            }
            IMaidTask task = m.getTask();
            return task != null && TaskAttack.UID.equals(task.getUid());
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 可选的姿态微调（默认全 0 = 与 TLM 完全一致）。
     *
     * <p>位移除以 {@code scale}，使配置值就等于最终的<b>世界位移（方块）</b>，
     * 与 {@code waistOffset*} 同一约定。
     */
    private static void applyMaidTweaks(PoseStack poseStack, float scale) {
        if (FixConfig.maidBladeRotZ != 0.0D || FixConfig.maidBladeRotY != 0.0D
                || FixConfig.maidBladeRotX != 0.0D) {
            poseStack.mulPose(Axis.ZP.rotationDegrees((float) FixConfig.maidBladeRotZ));
            poseStack.mulPose(Axis.YP.rotationDegrees((float) FixConfig.maidBladeRotY));
            poseStack.mulPose(Axis.XP.rotationDegrees((float) FixConfig.maidBladeRotX));
        }
        if (scale != 0.0F
                && (FixConfig.maidBladeOffsetX != 0.0D || FixConfig.maidBladeOffsetY != 0.0D
                || FixConfig.maidBladeOffsetZ != 0.0D)) {
            poseStack.translate((float) (FixConfig.maidBladeOffsetX / scale),
                    (float) (FixConfig.maidBladeOffsetY / scale),
                    (float) (FixConfig.maidBladeOffsetZ / scale));
        }
    }

    /** 诊断日志节流（2 秒一条），避免刷屏。只在 {@code debugLog=true} 时输出。 */
    private static long lastDiag;

    private static void diag(String fmt, Object... args) {
        if (!FixConfig.debugLog) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDiag < 2000L) {
            return;
        }
        lastDiag = now;
        YesSlashBladeFix.LOGGER.info("[YES-SB] " + fmt, args);
    }

    /** 上一次观测到的出鞘状态（按实体记，避免多只女仆互相盖掉）。 */
    private static int lastDrawnEntity = Integer.MIN_VALUE;
    private static boolean lastDrawn;

    /**
     * 在"是否出鞘"翻转时打一条。
     *
     * <p><b>为什么值得单独一条</b>：出鞘动作完全由 {@code lastActionTime} 驱动，而
     * 渲染读的是<b>客户端自己那份</b>物品堆。所以这一行是"客户端那次写时间戳到底成没成"
     * 唯一的观测点 —— 光看服务端的 {@code 时间戳=已写} 是证明不了客户端那一半的。
     *
     * <p><b>只打"是"、不打"否"</b>（1.0.8 起）：每次攻击会翻转两次，两条都打就打了一倍噪音，
     * 而"收回鞘里"这件事本身没有信息量。
     * 但**每个实体的第一次观测仍然会打**（通常是"否"），那是基线 ——
     * 留着它是为了**不重犯 §12.9.2 的错**：万一"客户端时间戳链"整个断了，
     * "是"永远不会出现，这时日志里只剩基线那一条，
     * 配合两侧的 {@code 时间戳=已写/未写} 依然能定位，而不是"安静得像成功一样"。
     */
    private static void noteDrawnState(LivingEntity maid, boolean drawn, long elapsed) {
        if (!FixConfig.debugLog) {
            return;
        }
        int id = maid.getId();
        boolean sameEntity = id == lastDrawnEntity;
        if (sameEntity && drawn == lastDrawn) {
            return;
        }
        boolean firstForEntity = !sameEntity;
        lastDrawnEntity = id;
        lastDrawn = drawn;

        if (!drawn && !firstForEntity) {
            // 收回鞘里：不打（每次攻击两条太吵，且无信息量）
            return;
        }
        YesSlashBladeFix.LOGGER.info(
                "[YES-SB] 女仆刀：出鞘状态={}（距上次动作 {} 刻，判定窗口 {} 刻）",
                drawn ? "是 —— 客户端时间戳已生效"
                        : "否 —— 基线（此后只在出鞘时打一条；一直没有『是』就说明时间戳链断了）",
                elapsed, FixConfig.maidBladeDrawTicks);
    }
}
