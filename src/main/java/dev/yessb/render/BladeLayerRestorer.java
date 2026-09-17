package dev.yessb.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yessb.FixConfig;
import dev.yessb.YesSlashBladeFix;
import dev.yessb.compat.ModPresence;
import dev.yessb.compat.SlashBladeBridge;
import dev.yessb.compat.YsmBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 补偿渲染的入口：在"原版渲染器被跳过"的实体渲染收尾处，把拔刀剑的腰刀层画回来。
 */
public final class BladeLayerRestorer {

    /** 一旦补偿渲染抛异常就整体停用，避免每帧刷错误、影响游戏手感。 */
    private static boolean disabled;

    /** 诊断日志节流。 */
    private static long lastNote;

    private BladeLayerRestorer() {
    }

    /**
     * 诊断用：原版渲染器这一帧确实跑了，而该实体手上又拿着拔刀剑。
     *
     * <p>此时本模组<b>不</b>介入（避免重影）。这条记录的意义在于回答一个关键问题：
     * 「YSM 到底有没有掐掉这个实体的原版渲染？」
     * —— 如果日志里一直出现它，说明原版路径其实是通的，问题就不在渲染层被掐。
     */
    public static void noteVanillaRan(Entity entity) {
        if (!FixConfig.debugLog || !(entity instanceof LivingEntity living)) {
            return;
        }
        // 只查 ModList，别去碰 SlashBladeBridge —— 它内部有拔刀剑的类型引用，
        // 拔刀剑缺席时加载它会抛 NoClassDefFoundError（与 1.0.13 那次崩溃同类）。
        if (!ModPresence.hasSlashBlade()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastNote < 5000L) {
            return;
        }
        lastNote = now;
        if (SlashBladeBridge.holdsBlade(living)) {
            YesSlashBladeFix.LOGGER.info(
                    "[YES-SB][诊断] {} 的原版渲染器仍在运行（未被接管），故本模组不介入。",
                    living.getName().getString());
        }
    }

    public static void restore(Entity entity, double x, double y, double z, float partialTicks,
                               PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (disabled || !FixConfig.enabled || !FixConfig.restoreThirdPerson) {
            return;
        }
        // 拔刀剑没装就没什么可补的 —— 而这一条必须在这里先拦住：
        // 下面的 handPathHandles / holdsBlade / renderWaistBlade 都在 SlashBladeBridge 里，
        // 那是个引用了拔刀剑类型的类，缺席时加载它会直接崩（见 ModPresence 类注释）。
        if (!ModPresence.hasSlashBlade()) {
            return;
        }
        // 判据只有一条：这一帧原版渲染器没跑（由调用方传入）。没装 YSM 时原版必然跑，本方法不会被走到。
        //
        // 这里刻意不做"世界坐标 vs 界面坐标"的区分：补偿用的是相对于
        // EntityRenderDispatcher 传进来的 x/y/z 的局部变换，因此在界面空间里同样成立。
        // YSM 左上角的纸娃娃正是复用这个入口、带着界面空间姿态栈与全亮光照来画的，
        // 它和世界里的角色一样"相当于第三人称"，也一样需要这把腰刀。
        if (!(entity instanceof LivingEntity living) || living.isSpectator()) {
            return;
        }
        if (!isTarget(living)) {
            return;
        }
        // 手上没有拔刀剑就没什么可画的，快速返回（保持每帧开销可忽略）
        if (!SlashBladeBridge.holdsBlade(living)) {
            return;
        }
        // 谁负责画这把刀？两条路只能走一条，否则就是两把刀。
        //
        // 拔刀剑 2.0.7 的 ClientHandler#addLayers 会给**所有** LivingEntityRenderer 挂上
        // LayerMainBlade。所以判据很干脆：渲染器是原版 LivingEntityRenderer ⇒ 刀归腰挂层管
        // （它自己会按主手/副手/连招状态画，也正是 1.20.1 的样子）；
        // 否则（YSM/女仆之类换掉了渲染器、腰挂层不存在）⇒ 刀归"手持补画"那条路管。
        if (handPathHandles(living)) {
            return;
        }

        try {
            // 关键一步：YSM 掐掉原版渲染时，连带 model.setupAnim(...) 也没跑，
            // 而 LayerMainBlade 的 MMD 刀挂点正是从那些部位算出来的。
            //
            // ⚠️ 注意这一步<b>并不能</b>让玩家刀跟上 YSM 的动画 —— 它补的是"原版模型
            // 有没有被更新过"，让刀跟的是<b>原版</b>挥砍动作，而屏幕上播的是 YSM 的动画。
            // 两套动画天然对不上，要彻底对齐必须绑 YSM 模型自身的定位骨骼（尚未实现）。
            if (FixConfig.refreshModelPose) {
                SlashBladeBridge.refreshVanillaModelPose(living, partialTicks);
            }
            SlashBladeBridge.renderWaistBlade(living, x, y, z, partialTicks, poseStack, buffer, packedLight);
            noteRestored(living);
        } catch (Throwable t) {
            disabled = true;
            YesSlashBladeFix.LOGGER.error("[YES-SB] 腰刀层补偿渲染失败，已停用该功能（游戏其余部分不受影响）", t);
        }
    }

    /** 补偿成功的诊断日志；节流到 5 秒一条（每帧一条会把日志刷爆）。 */
    private static void noteRestored(LivingEntity living) {
        if (!FixConfig.debugLog) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastNote < 5000L) {
            return;
        }
        lastNote = now;
        YesSlashBladeFix.LOGGER.info("[YES-SB] 已为 {} 补画腰刀层", living.getName().getString());
    }

    /** "手持补画"那条路是否会负责这个实体的刀；是的话腰挂层就不要重复画。 */
    public static boolean handPathHandles(LivingEntity living) {
        // 女仆的刀由 TLM 的渲染层负责（见 TlmMaidBridge）——那边不归这两条路管，
        // 这里必须让开，否则腰挂层会和它各画一把。
        if (FixConfig.maidSlashBlade && ModPresence.isMaid(living)) {
            return true;
        }
        if (!FixConfig.handBladeInThirdPerson || !YsmBridge.isLoaded()) {
            return false;
        }
        // 同样先查 ModList：下面那句在 SlashBladeBridge 里，拔刀剑缺席时不能碰它。
        if (!ModPresence.hasSlashBlade()) {
            return false;
        }
        if (!SlashBladeBridge.isBlade(living.getMainHandItem())) {
            // 主手没刀：腰挂层还要负责把**副手**那把画在腰侧，所以不能跳过
            return false;
        }
        // 默认规则：只有"本来就没有腰挂层"的实体才交给手持补画。
        // 玩家一定带腰挂层 —— 之前强行让手持那条路接手，结果第一人称纸娃娃同时出现了两把刀。
        return !FixConfig.handBladeRequiresNoWaistLayer || !hasWaistLayer(living);
    }

    /**
     * 这个实体的渲染器有没有拔刀剑的腰挂层。
     *
     * <p>拔刀剑 2.0.7 的 {@code ClientHandler#addLayers} 会遍历所有已注册实体类型，
     * 给<b>每一个</b> {@code LivingEntityRenderer} 都加上 {@code LayerMainBlade}。
     * 因此"这个实体的刀该由腰挂层画"等价于"它的渲染器是原版 LivingEntityRenderer" ——
     * 比硬编码实体名单可靠，也不受版本变化影响。
     *
     * <p>两处调用方（{@link MixinEntityRenderDispatcher} 的进场记录与这里的派发判定）
     * 共用这一个实现，避免两处判断不一致导致重影。
     */
    public static boolean hasWaistLayer(Entity entity) {
        if (entity == null) {
            return false;
        }
        try {
            EntityRenderer<?> renderer = Minecraft.getInstance()
                    .getEntityRenderDispatcher().getRenderer(entity);
            return renderer instanceof LivingEntityRenderer<?, ?>;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isTarget(LivingEntity entity) {
        if (entity instanceof Player) {
            return FixConfig.affectsPlayers;
        }
        return FixConfig.affectsOtherLivingEntities;
    }
}
