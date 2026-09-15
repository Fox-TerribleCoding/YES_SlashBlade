package dev.yessb.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yessb.FixConfig;
import dev.yessb.YesSlashBladeFix;
import mods.flammpfeil.slashblade.client.renderer.SlashBladeTEISR;
import mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade;
import mods.flammpfeil.slashblade.client.renderer.model.BladeFirstPersonRender;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;

/**
 * 与拔刀剑之间的唯一隔离层。
 *
 * <p>本模组只在这里、且只在运行时接触拔刀剑的类型；拔刀剑缺席时下面的方法不会被调用，
 * 因此不会因为类不存在而崩溃。
 *
 * <p>这里渲染用的是拔刀剑自己公开的 {@code LayerMainBlade}（MIT 许可的代码）。
 * 我们只是"把原本该被调用的那一层调用回来"，没有复制它的任何实现。
 */
public final class SlashBladeBridge {

    private static final String MOD_ID = "slashblade";

    /** YSM 为拔刀剑预留的物品标签；1.20.1 的实现也用它来识别附属模组。 */
    private static final TagKey<Item> SLASHBLADE_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("yes_steve_model", "slashblade"));

    private static Boolean available;

    private static LayerMainBlade<LivingEntity, EntityModel<LivingEntity>> layer;

    private SlashBladeBridge() {
    }

    public static boolean isAvailable() {
        if (available != null) {
            return available;
        }
        try {
            ModList list = ModList.get();
            if (list == null) {
                // 过早查询：不缓存，避免把"还没就绪"误当成"没装"
                return false;
            }
            boolean loaded = list.isLoaded(MOD_ID);
            if (loaded) {
                available = Boolean.TRUE;
            }
            return loaded;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 该物品堆是否是拔刀剑（本体，或登记进 YSM 标签的附属）。 */
    public static boolean isBlade(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            if (stack.getItem() instanceof ItemSlashBlade) {
                return true;
            }
            return stack.is(SLASHBLADE_TAG);
        } catch (Throwable t) {
            // 拔刀剑被卸载 / 类加载异常：静默当作"不是"
            return false;
        }
    }

    public static boolean holdsBladeInMainHand(LivingEntity entity) {
        return isBlade(entity.getMainHandItem());
    }

    // ------------------------------------------------------------------ 剑技动画名

    /** 拔刀剑把"待机"状态登记成这个名字；它对超时判定有个特例处理。 */
    private static final String STANDBY_STATE = "slashblade:standby";

    /** 待机状态的超时被额外削掉这么多毫秒 —— 这是 1.20.1 那份兼容模块的调校值。 */
    private static final int STANDBY_TIMEOUT_TRIM_MS = 553;

    /** 注册名与模型包动画名唯一不一致的一处：注册是 {@code combo_a4_ex}，动画是 {@code combo_a4ex}。 */
    private static final String COMBO_A4_EX_STATE = "slashblade:combo_a4_ex";
    private static final String COMBO_A4_EX_ANIMATION = "slashblade:combo_a4ex";

    /** 居合斩在空中时换用另一套动画，注册名与动画名各差一个后缀。 */
    private static final String JUDGEMENT_CUT = "slashblade:judgement_cut";
    private static final String JUDGEMENT_CUT_AIR = "slashblade:judgement_cut_slash_air";
    private static final String JUDGEMENT_CUT_JUST2 = "slashblade:judgement_cut_slash_just2";
    private static final String JUDGEMENT_CUT_AIR_JUST2 = "slashblade:judgement_cut_slash_air_just2";

    /** 拔刀剑的计时以"游戏刻"为单位，而连招超时是毫秒。 */
    private static final long MS_PER_TICK = 50L;

    /**
     * 取"此刻该播哪条拔刀剑动画" —— 也就是模型包 {@code slashblade.animation.json} 里的动画名。
     *
     * <p>这不是我们的发明，而是把 YSM 1.20.1 那份兼容模块的语义原样补回来（1.21.1 的原生重写
     * 把整个模块掏空了）。规则只有四条，全部来自对 1.20.1 的观察：
     * <ol>
     *   <li>取主手拔刀剑的连招状态 {@code getComboSeq()}，去连招状态注册表里查它；</li>
     *   <li>过了这条连招自己的 {@code timeoutMS} 就不再算数 —— 于是自然回落到普通持握/挥动动画
     *       （这就是"连招触发"而不是"一直播"的关键）；</li>
     *   <li>{@code combo_a4_ex} 这条注册名与动画名对不上，单独改名；</li>
     *   <li>居合斩在空中另有两条动画。</li>
     * </ol>
     *
     * <p>返回空串表示"此刻没有剑技动画"，调用方应当原样走它平常的逻辑。
     *
     * @return 动画名（形如 {@code slashblade:combo_a1}），或空串
     */
    public static String comboAnimationName(LivingEntity entity) {
        try {
            if (entity == null) {
                return bail("entity 为 null");
            }
            ItemStack stack = entity.getMainHandItem();
            if (!isBlade(stack)) {
                return bail("主手不是拔刀剑：" + stack);
            }
            ISlashBladeState state = BladeStateAccess.of(stack).orElse(null);
            if (state == null) {
                return bail("BladeStateAccess.of(stack) 为空");
            }
            ResourceLocation comboSeq = state.getComboSeq();
            if (comboSeq == null) {
                return bail("getComboSeq() 为 null");
            }
            ComboState combo = ComboStateRegistry.REGISTRY.get(comboSeq);
            if (combo == null) {
                return bail("连招注册表里没有 " + comboSeq + "（可能是别的模组注册的状态名）");
            }

            String stateName = comboSeq.toString();
            int timeoutMs = combo.getTimeoutMS();
            if (STANDBY_STATE.equals(stateName)) {
                timeoutMs -= STANDBY_TIMEOUT_TRIM_MS;
            }
            // getElapsedTime 是拔刀剑自己的公开助手：max(0, 游戏时间 - 上次动作时间)，单位是刻
            long elapsedTicks = state.getElapsedTime(entity);
            long elapsedMs = elapsedTicks * MS_PER_TICK;
            if (elapsedMs > timeoutMs) {
                // ★ 这一条在 1.0.1 里是完全静默的，导致「装了别的模组后剑技动画整条消失」
                //   只能看到"什么都没发生"，分不清是没被调用、超时、还是别的分支。
                //   把实际数值打出来，一次进游戏就能定论。
                return bail("超时：seq=" + stateName
                        + " elapsed=" + elapsedTicks + "刻/" + elapsedMs + "ms"
                        + " timeout=" + timeoutMs + "ms"
                        + " lastActionTime=" + state.getLastActionTime()
                        + " gameTime=" + entity.level().getGameTime()
                        + " 该状态帧区间=" + combo.getStartFrame() + ".." + combo.getEndFrame());
            }

            String animation = stateName;
            if (COMBO_A4_EX_STATE.equals(animation)) {
                animation = COMBO_A4_EX_ANIMATION;
            }
            if (!entity.onGround()) {
                if (JUDGEMENT_CUT.equals(animation)) {
                    animation = JUDGEMENT_CUT_AIR;
                } else if (JUDGEMENT_CUT_JUST2.equals(animation)) {
                    animation = JUDGEMENT_CUT_AIR_JUST2;
                }
            }
            return animation;
        } catch (Throwable t) {
            // 版本差异导致 API 对不上时安静地当作"没有剑技动画"。
            // 但**必须留下痕迹**：早前这里是完全静默的，
            // 一旦第三方模组让某个 API 抛异常，剑技动画会整条消失且毫无线索。
            return bail("异常 " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    // ---------------------------------------------------------------- 诊断

    private static long lastBailAt;
    private static String lastBailReason = "";

    /**
     * 报告"为什么这一步没给出剑技动画名"，然后返回空串。
     *
     * <p>节流 2 秒，并且**按原因去重** —— 与 {@code diag} 的全局节流不同，
     * 不同的失败原因都会被单独记一条，否则第一条会把后面全挡掉。
     * 仅在 {@code debugLog=true} 时输出。
     */
    private static String bail(String reason) {
        if (!FixConfig.debugLog) {
            return "";
        }
        long now = System.currentTimeMillis();
        if (reason.equals(lastBailReason) && now - lastBailAt < 2000L) {
            return "";
        }
        lastBailAt = now;
        lastBailReason = reason;
        YesSlashBladeFix.LOGGER.info("[YES-SB] 剑技动画为空 ⇒ {}", reason);
        return "";
    }

    /**
     * 任一只手拿着拔刀剑。
     *
     * <p>拔刀剑的腰刀层会分别处理主手与副手（例如副手那把也要挂在腰上），
     * 因此这里要放宽到两只手，让层自己去决定画什么 —— 保持与原版一致的行为。
     */
    public static boolean holdsBlade(LivingEntity entity) {
        return isBlade(entity.getMainHandItem()) || isBlade(entity.getOffhandItem());
    }

    /**
     * 把原版模型的姿态补算一遍 —— <b>「玩家的刀太低、没接到手上」的正解</b>。
     *
     * <h2>为什么必须补</h2>
     * YSM 掐掉的是整个原版 {@code LivingEntityRenderer.render}，而那个方法里除了画模型，
     * 还负责把模型各部位的姿态算出来：
     * <pre>
     *   model.attackTime = this.getAttackAnim(entity, partialTicks);
     *   model.riding = entity.isPassenger();
     *   model.young  = entity.isBaby();
     *   model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
     *   model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
     * </pre>
     * 而拔刀剑的 {@code LayerMainBlade} 构造 MMD 刀挂点（硬点A=刀、硬点B=鞘）时，
     * <b>正是读这些部位</b>。原版渲染被跳过后这些部位停在静止姿态，
     * 于是刀永远落在"静止时该在的位置"—— 表现就是偏低、而且不跟着手走。
     *
     * <p>这与本模组"把被掐掉的渲染层调用回来"是同一件事的另一半：
     * 不只是要调 {@code LayerMainBlade.render}，还得先让它读到的数据是对的。
     *
     * <p>参数完全照抄原版 {@code LivingEntityRenderer#render} 的算法。
     * 整段失败不影响游戏（拿不到模型就什么都不做）。
     */
    public static void refreshVanillaModelPose(LivingEntity entity, float partialTicks) {
        try {
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (!(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)) {
                return;
            }
            EntityModel<?> model = livingRenderer.getModel();
            if (model == null) {
                return;
            }

            float limbSwing = entity.walkAnimation.position(partialTicks);
            float limbSwingAmount = Math.min(1.0F, entity.walkAnimation.speed(partialTicks));
            float ageInTicks = entity.tickCount + partialTicks;
            float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
            float netHeadYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot) - bodyYaw;
            float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());

            model.attackTime = entity.getAttackAnim(partialTicks);
            model.riding = entity.isPassenger();
            model.young = entity.isBaby();

            @SuppressWarnings("unchecked")
            EntityModel<LivingEntity> typed = (EntityModel<LivingEntity>) model;
            typed.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
            typed.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        } catch (Throwable t) {
            if (FixConfig.debugLog) {
                YesSlashBladeFix.LOGGER.warn("[YES-SB] 补算原版模型姿态失败（不影响其它功能）: {}", t.toString());
            }
        }
    }

    /**
     * 画出"腰挂刀 + 连招出鞘"。
     *
     * <p>变换刻意复刻 {@code LivingEntityRenderer#render} 在遍历 RenderLayer 之前建立的环境：
     * <pre>
     *   translate(实体位置)
     *   scale(getScale)
     *   mulPose(Y, 180 - 身体朝向)     ← setupRotations 的常态分支
     *   scale(-1, -1, 1)
     *   translate(0, -1.501, 0)
     * </pre>
     * 拔刀剑的层本身就是按这个环境写的，因此只要把它摆回去，观感就与原版一致。
     */
    public static void renderWaistBlade(LivingEntity entity, double x, double y, double z, float partialTicks,
                                        PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (layer == null) {
            // 该层不使用父渲染器，因此传 null 是安全的（LayerMainBlade#render 内部不读父模型）
            layer = new LayerMainBlade<>(null);
        }

        Vec3 offset = renderOffset(entity, partialTicks);

        poseStack.pushPose();
        try {
            // 多乘一个对齐系数：拔刀剑的腰刀按原版玩家比例做的，而 YSM 模型通常整体缩小
            float scale = entity.getScale() * (float) FixConfig.thirdPersonScale;

            // 位移微调口子。
            //
            // 注意位置：**必须放在下面 scale(-1,-1,1) 之前**。那一步会把 Y 轴翻转，
            // 若把位移放在它之后，用户填正数会让刀往下走 —— 曾经就是这么写的，根本调不出来。
            // 这里再除以 scale，使配置值就是"最终世界位移（方块）"，正数=向上/向前，
            // 与人的直觉一致（后面 scale 会把它乘回来）。
            if (scale != 0.0F) {
                poseStack.translate((float) FixConfig.waistOffsetX / scale,
                        (float) FixConfig.waistOffsetY / scale,
                        (float) FixConfig.waistOffsetZ / scale);
            }

            poseStack.translate(x + offset.x, y + offset.y, z + offset.z);
            poseStack.scale(scale, scale, scale);

            float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));

            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, -1.501F, 0.0F);

            layer.render(poseStack, buffer, packedLight, entity,
                    0.0F, 0.0F, partialTicks, entity.tickCount + partialTicks, 0.0F, 0.0F);

            diag("腰挂层补偿：实体={} thirdPersonScale={} 位移=({},{},{})",
                    entity.getName().getString(), FixConfig.thirdPersonScale,
                    FixConfig.waistOffsetX, FixConfig.waistOffsetY, FixConfig.waistOffsetZ);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 第三方称：主手手持时，把手里的刀画出来。
     *
     * <p>这是"手持"这条路的补全 —— 拔刀剑的 BEWLR 对第三方称上下文故意不画，
     * 于是主手手持时刀只能靠腰挂层画在腰上；而"没有腰挂层"的实体就彻底看不见刀。
     * 这里直接按手持物画刀身。
     *
     * @param transform 该上下文的完整变换（缩放 / 朝向 / 位移 / 是否平面上下文）
     * @return 是否画成功（失败时调用方应让拔刀剑照常走原逻辑）
     */
    public static boolean renderHandBlade(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer,
                                          int light, BladeTransform transform) {
        try {
            return renderBladeModel(stack, poseStack, buffer, light, transform);
        } catch (Throwable t) {
            if (FixConfig.debugLog) {
                YesSlashBladeFix.LOGGER.warn("[YES-SB] 手持刀渲染失败: {}", t.toString());
            }
            return false;
        }
    }

    /** 第一人称手持图标绘制用的缩放，与拔刀剑自己画 GUI/地面图标时保持一致。 */
    private static final float FIRST_PERSON_ICON_SCALE = 0.0095F;

    /**
     * 本地玩家**当前**两只手里有没有拔刀剑。
     *
     * <p>用来把「玩家正握着的刀」与「切换物品装备动画里正在离场的那把刀」区分开。
     * 后者由 {@code ItemInHandRenderer} 回放旧物品造成，不该走本模组的"手持化"路径 ——
     * 否则会出现"切物品后冒出一把横向的刀并跟着装备动画下落"。
     *
     * <p>刻意用 {@code mc.player} 而不是传进来的 {@code stack}：
     * 那个 stack 正是"被渲染的物品"，在装备动画里它就是那把离场的刀，用它判断等于没判断。
     */
    private static boolean yes_sb$playerHoldsBlade() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) {
                return false;
            }
            return isBlade(mc.player.getMainHandItem()) || isBlade(mc.player.getOffhandItem());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 第一人称：把拔刀剑当作普通手持物来画。
     *
     * <p>拔刀剑原本在第一人称会调用 {@code BladeFirstPersonRender}，它会重置姿态矩阵、
     * 然后绘制"挂在身体上的整套 MMD 刀 + 鞘" —— 结果就是低头能看见一整把刀悬在腰上。
     * 这里改为按手持物绘制，并且<b>不</b>重置姿态矩阵，于是它会像手模一样固定朝向、随视角移动。
     *
     * <p>三种模式：
     * <ul>
     *   <li>{@code model} —— 画 3D 刀身（用拔刀剑自己的波前模型与渲染状态；位置朝向可配）</li>
     *   <li>{@code icon} —— 画平面图标（拔刀剑 GUI/地面图标用的那个部件）</li>
     *   <li>{@code off} —— 不干预，回到拔刀剑原本的行为</li>
     * </ul>
     *
     * @param original 原本的那次调用，用于回退
     * @param owner    发起调用的 {@code SlashBladeTEISR} 实例（以 Object 传入以保持隔离）
     */
    public static void renderFirstPersonBlade(Object original, Object owner, ItemStack stack,
                                              PoseStack poseStack, MultiBufferSource buffer, int light) {
        // ★ 只有"玩家此刻确实握着拔刀剑"时才接管第一人称。
        //
        // 漏掉这一条的后果（实测）：切换物品时 ItemInHandRenderer 会带着装备动画把**旧物品**
        // 再渲染一次，那一刻玩家手里已经是别的东西了 —— 于是 yes_sb$originalWillRender()
        // 里最后那句「主手是不是拔刀剑」判 false，auto 模式就走进"原版画不出来 ⇒ 本模组兜底"
        // 的分支，把**正在离场的那把刀**按手持物画了出来。
        // 表现就是「切物品后冒出一把横向的刀并跟着装备动画下落」。
        //
        // 兜底分支的本意是"原版真画不出来时别让第一人称空着"，而不是"玩家手里没刀了也去补一把"。
        if (FixConfig.enabled && FixConfig.firstPersonAsHeldItem && owner instanceof SlashBladeTEISR teisr
                && yes_sb$playerHoldsBlade()) {
            try {
                String mode = FixConfig.firstPersonMode;
                if ("model".equals(mode)) {
                    if (renderBladeModel(stack, poseStack, buffer, light, BladeTransform.firstPerson())) {
                        return;
                    }
                    // 取不到模型（异常刀）时退回图标
                    teisr.renderIcon(stack, poseStack, buffer, light, FIRST_PERSON_ICON_SCALE);
                    return;
                }
                if ("icon".equals(mode)) {
                    teisr.renderIcon(stack, poseStack, buffer, light, FIRST_PERSON_ICON_SCALE);
                    return;
                }
                if ("auto".equals(mode) && !yes_sb$originalWillRender()) {
                    // 原版画不出来（见下面的前置条件），此时若也放手，第一人称就彻底没有刀了。
                    // 兜底交给我们自己 —— 至少还能看见一把刀。
                    diag("第一人称[auto]：原版画不出来，改由本模组兜底画 3D 刀身");
                    if (renderBladeModel(stack, poseStack, buffer, light, BladeTransform.firstPerson())) {
                        return;
                    }
                    teisr.renderIcon(stack, poseStack, buffer, light, FIRST_PERSON_ICON_SCALE);
                    return;
                }
                // off / auto-且原版可用：什么都不做，落到下面的原行为
                if (!yes_sb$checkOriginalFirstPerson()) {
                    diag("第一人称：不干预，交回拔刀剑原生的 BladeFirstPersonRender"
                            + "（那正是 1.20.1 的样子：一整把刀斜跨画面）");
                }
            } catch (Throwable t) {
                // 版本差异导致不可用时，安静地退回拔刀剑原本的行为
                if (FixConfig.debugLog) {
                    YesSlashBladeFix.LOGGER.warn("[YES-SB] 第一人称手持化失败，回退到原行为: {}", t.toString());
                }
            }
        }
        if (original instanceof BladeFirstPersonRender firstPersonRender) {
            firstPersonRender.render(poseStack, buffer, light);
        }
    }

    /**
     * 原版 {@code BladeFirstPersonRender} 此刻会不会真的画东西 —— 条件逐条照抄它的字节码。
     *
     * <p>原版遇到任何一条不满足就<b>静默 return</b>，日志里看不出原因，表现就是
     * 「第一人称什么都没有」。所以本模组在 {@code firstPersonMode=auto} 时先问这一句，
     * 判断为"画不出来"就自己兜底。
     *
     * <p>其中 {@code 可建腰挂层} 最关键 —— 原版的腰挂层是靠
     * {@code new LayerMainBlade(渲染器)} 建的，而 YSM 把玩家的渲染器整个换掉了；
     * 只要它仍是 {@code LivingEntityRenderer}（⇒ 实现 {@code RenderLayerParent}）这一层就能建起来。
     *
     * @return 原版是否会真的画（同时把检查结果写进日志）
     */
    private static boolean yes_sb$checkOriginalFirstPerson() {
        boolean willRender = yes_sb$originalWillRender();
        if (FixConfig.debugLog) {
            try {
                Minecraft mc = Minecraft.getInstance();
                LocalPlayer player = mc.player;
                if (player == null) {
                    diag("第一人称[原版]：mc.player 为 null ⇒ 原版直接返回，画不出刀");
                } else {
                    Object renderer = mc.getEntityRenderDispatcher().getRenderer(player);
                    ItemStack main = player.getMainHandItem();
                    diag("第一人称[原版]前置条件：相机={} 睡觉={} hideGui={} 常飞={} 主手空={} 渲染器={} 可建腰挂层={} ⇒ {}",
                            mc.options.getCameraType(), player.isSleeping(), mc.options.hideGui,
                            mc.gameMode != null && mc.gameMode.isAlwaysFlying(), main.isEmpty(),
                            renderer == null ? "null" : renderer.getClass().getSimpleName(),
                            renderer instanceof RenderLayerParent,
                            willRender ? "原版会画" : "★原版画不出来");
                }
            } catch (Throwable t) {
                diag("第一人称[原版]前置条件检查失败: {}", t.toString());
            }
        }
        return willRender;
    }

    /** 只做判断、不打日志的那一半（{@code auto} 模式每帧都要问一次）。 */
    private static boolean yes_sb$originalWillRender() {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || mc.gameMode == null) {
                return false;
            }
            // 原版的 layer 是 new LayerMainBlade(渲染器) 建起来的：
            // 渲染器只要还实现 RenderLayerParent，这一层就建得起来。
            if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof RenderLayerParent)) {
                return false;
            }
            if (mc.options.getCameraType() != CameraType.FIRST_PERSON
                    || player.isSleeping()
                    || mc.options.hideGui
                    || mc.gameMode.isAlwaysFlying()) {
                return false;
            }
            ItemStack main = player.getMainHandItem();
            return !main.isEmpty() && BladeStateAccess.of(main).isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 用拔刀剑自己的波前模型画出 3D 刀身。
     *
     * <h2>缩放取值的来历（别再照搬挂台那个数）</h2>
     * 拔刀剑自己画这个模型时用 {@code scale(0.003125)}，但那是 {@code renderModel}——
     * <b>挂在刀架/展示框里</b>的尺寸。它另外画"图标"时用的是 <b>0.0095</b>
     * （`renderBlade` 里 GROUND=0.005 / GUI=0.008 / FIXED 与其余=0.0095）。
     * 换句话说 0.003125 是"躺在架子上的实体刀"，0.0095 是"手上那把刀"，
     * 两者差着 3 倍 —— 早前直接照搬 0.003125，刀自然偏小。
     *
     * <p>本模组用的折中值由几何反推：图标部件 {@code item_blade} 的包围盒对角线约 218 单位，
     * 3D 刀身 {@code blade} 沿 X 长 332.7 单位；要让两者"看起来一样长"，
     * 缩放应为 {@code 0.0095 × 218 / 332.7 ≈ 0.0062}，正好落在用户实测的
     * "0.0095 太大、0.003125 太小"之间。各上下文的具体值见 FixConfig。
     *
     * <p>{@code flatContext} 为真时补上 {@code translate(0.5,0.5,0.5)} 与 180° 转向 ——
     * 那是拔刀剑画 {@code FIXED} 图标前的固定变换，加上它们刀身才会落在图标原来的位置。
     *
     * @return 是否画成功
     */
    private static boolean renderBladeModel(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer,
                                            int light, BladeTransform transform) {
        ISlashBladeState state = BladeStateAccess.of(stack).orElse(null);
        if (state == null) {
            return false;
        }
        ResourceLocation modelLocation = state.getModel().orElse(DefaultResources.resourceDefaultModel);
        ResourceLocation textureLocation = state.getTexture().orElse(DefaultResources.resourceDefaultTexture);

        WavefrontObject model = BladeModelManager.getInstance().getModel(modelLocation);
        if (model == null) {
            return false;
        }

        // 部件名在不同刀的波前模型里不完全一致：优先 3D 刀身，没有就退回图标部件。
        // 关键：必须 blade + sheath 一起画 —— 只画 blade 就是"一把没有鞘的刀"，
        // 这与拔刀剑自己画刀架（renderModel）时的做法一致。
        boolean hasBlade = hasGroup(model, "blade");
        boolean hasIcon = hasGroup(model, "item_blade");
        if (!hasBlade && !hasIcon) {
            if (FixConfig.debugLog) {
                YesSlashBladeFix.LOGGER.warn("[YES-SB] 手持刀：模型 {} 里既没有 blade 也没有 item_blade 部件",
                        modelLocation);
            }
            return false;
        }

        poseStack.pushPose();
        try {
            // 位移放在**最外层**，配置值才是真正的「世界位移（方块）」。
            //
            // 早前它写在 scale 之后，于是会被上面那次 scale 再乘一遍：填 1.0 实际只挪
            // 0.0062 格，等于永远调不动 —— 「第一人称怎么调都没反应」有它一份。
            // 现在填 1.0 = 沿相机 X 轴（右）移动 1 格，与文档里的说法一致。
            poseStack.translate(transform.offsetX(), transform.offsetY(), transform.offsetZ());

            // ★ 这一步是所有上下文都必须的，不是只有 FIXED 才要。
            //
            // ItemRenderer 在把控制权交给 BEWLR 之前，先应用了物品的 display 变换，
            // 然后**无条件**做了一次 translate(-0.5, -0.5, -0.5)
            // （见 ItemRenderer#render 的字节码：ItemTransform.apply → ldc -0.5f ×3 → renderByItem）。
            // 拔刀剑自己的 renderBlade 进非第一人称分支后第一句就是 translate(0.5f, 0.5f, 0.5f)，
            // 正是把这一步抵消回去 —— 它的波前模型是以「方块中心」为原点定义的。
            //
            // 我们早前只给 FIXED 加了这一句，手持/第一人称没加，于是那两处的刀整整偏了
            // 0.5 个世界单位（还要再被 display 的 1.25 倍缩放与 rotY(180) 放大到约 0.6 格）。
            // 第一人称那 0.6 格正好把刀推到画面下方之外 —— 「第一人称看不见刀模」就是这么来的。
            poseStack.translate(0.5F, 0.5F, 0.5F);
            if (transform.flatContext()) {
                // 复刻 renderBlade 画 FIXED 图标前那一步转向
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            poseStack.scale(transform.scale(), transform.scale(), transform.scale());
            poseStack.mulPose(Axis.ZP.rotationDegrees(transform.rotZ()));
            poseStack.mulPose(Axis.YP.rotationDegrees(transform.rotY()));
            poseStack.mulPose(Axis.XP.rotationDegrees(transform.rotX()));

            if (hasBlade) {
                // 平面上下文里我们是在"顶替一个平面图标"，所以要把 3D 刀身挪到图标原来的位置上：
                // 两个部件的包围盒中心差着约 117 个模型单位（主要在 X 上），不补就会横向偏出去一大截。
                if (transform.flatContext()) {
                    double[] icon = groupCenter(model, "item_blade");
                    double[] blade = groupCenter(model, "blade", "sheath");
                    if (icon != null && blade != null) {
                        float s = transform.scale();
                        float dx = (float) ((icon[0] - blade[0]) * s);
                        float dy = (float) ((icon[1] - blade[1]) * s);
                        float dz = (float) ((icon[2] - blade[2]) * s);
                        poseStack.translate(dx, dy, dz);
                        diag("平面上下文几何修正：图标中心=({}) 刀身中心=({}) 位移=({})",
                                fmt(icon), fmt(blade), fmt(dx, dy, dz));
                    } else {
                        diag("平面上下文几何修正：量不到包围盒（icon={} blade={}）⇒ 未修正",
                                icon != null, blade != null);
                    }
                }
                renderPart(stack, model, "blade", textureLocation, poseStack, buffer, light);
                renderPart(stack, model, "sheath", textureLocation, poseStack, buffer, light);
            } else {
                renderPart(stack, model, "item_blade", textureLocation, poseStack, buffer, light);
            }

            // 节流输出：这条每帧都会走到，不加节流会把日志刷爆（早前实测能涨到 2MB）。
            // 顺带把"刀的原点落在相机空间的哪里"报出来 —— 第一人称的调参全靠这三个数：
            // 它们负得太多（|X| / |Y| 超过深度 × tan(FOV/2) 对应的视野半宽半高）就说明刀在画面外。
            yes_sb$diagPlacement(transform, modelLocation, hasBlade, hasIcon, poseStack);
        } finally {
            poseStack.popPose();
        }
        return true;
    }

    /**
     * 诊断一次"画在哪"（节流 2 秒）。
     *
     * <p>报出刀的原点在<b>相机空间</b>的坐标，并按当前 FOV 与窗口宽高比判断它是否落在视野内。
     * 第一人称的调参全靠这三个数：把 {@code firstPersonOffsetX/Y/Z} 加到「在画面外」变成
     * 「在画面内」为止即可，不需要反复进游戏猜。
     */
    private static void yes_sb$diagPlacement(BladeTransform transform, ResourceLocation modelLocation,
                                             boolean hasBlade, boolean hasIcon, PoseStack poseStack) {
        if (!FixConfig.debugLog) {
            return;
        }
        Matrix4f m = poseStack.last().pose();
        float x = m.m30();
        float y = m.m31();
        float z = m.m32();
        diag("手持刀[{}]：模型={} 3D={} 图标={} 缩放={} 平面={} 刀原点(相机空间)=({}) 视野判定={}",
                transform.label(), modelLocation, hasBlade, hasIcon, transform.scale(),
                transform.flatContext(), fmt(x, y, z), yes_sb$visibilityVerdict(x, y, z));
    }

    /** 相机空间里 -Z 是视线方向；据此判断这个点是否落在视野内。 */
    private static String yes_sb$visibilityVerdict(float x, float y, float z) {
        try {
            Minecraft mc = Minecraft.getInstance();
            double depth = -z;
            if (depth <= 0.05D) {
                return "★在相机后面/贴着镜头";
            }
            double fov = mc.options.fov().get();
            double aspect = (double) mc.getWindow().getWidth() / (double) mc.getWindow().getHeight();
            double halfHeight = depth * Math.tan(Math.toRadians(fov / 2.0D));
            double halfWidth = halfHeight * aspect;
            boolean inside = Math.abs(x) <= halfWidth && Math.abs(y) <= halfHeight;
            return (inside ? "在画面内" : "★在画面外")
                    + String.format(java.util.Locale.ROOT, "（视野半宽=%.2f 半高=%.2f）", halfWidth, halfHeight);
        } catch (Throwable t) {
            return "无法判断(" + t.getClass().getSimpleName() + ")";
        }
    }

    // ------------------------------------------------------------------ 诊断

    private static long lastDiag;

    /** 诊断日志节流（2 秒一条），避免刷屏。 */
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

    private static String fmt(double[] v) {
        return String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", v[0], v[1], v[2]);
    }

    private static String fmt(float a, float b, float c) {
        return String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f", a, b, c);
    }

    /** 若干部件的合并包围盒中心（模型单位）；都取不到就返回 null。 */
    private static double[] groupCenter(WavefrontObject model, String... names) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        boolean any = false;
        if (model.groupObjects == null) {
            return null;
        }
        for (Object o : model.groupObjects) {
            if (!(o instanceof mods.flammpfeil.slashblade.client.renderer.model.obj.GroupObject g)
                    || g.faces == null) {
                continue;
            }
            boolean wanted = false;
            for (String n : names) {
                if (n.equals(g.name)) {
                    wanted = true;
                    break;
                }
            }
            if (!wanted) {
                continue;
            }
            for (mods.flammpfeil.slashblade.client.renderer.model.obj.Face f : g.faces) {
                if (f == null || f.vertices == null) {
                    continue;
                }
                for (mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex v : f.vertices) {
                    if (v == null) {
                        continue;
                    }
                    any = true;
                    minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
                    minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
                    minZ = Math.min(minZ, v.z); maxZ = Math.max(maxZ, v.z);
                }
            }
        }
        if (!any) {
            return null;
        }
        return new double[]{(minX + maxX) / 2.0D, (minY + maxY) / 2.0D, (minZ + maxZ) / 2.0D};
    }

    /** 画一个部件及其发光层；该部件不存在就跳过。 */
    private static void renderPart(ItemStack stack, WavefrontObject model, String part,
                                   ResourceLocation texture, PoseStack poseStack,
                                   MultiBufferSource buffer, int light) {
        if (!hasGroup(model, part)) {
            return;
        }
        BladeRenderState.renderOverrided(stack, model, part, texture, poseStack, buffer, light);
        BladeRenderState.renderOverridedLuminous(stack, model, part + "_luminous", texture, poseStack, buffer, light);
    }

    /**
     * 单独画刀鞘（含发光层）。
     *
     * <p>给「刀鞘常驻、刀身按状态出鞘」那种画法用 —— 拔刀剑 1.20.1 时代 TLM 与 YSM 的女仆兼容
     * 都是这么画的（见 {@link dev.yessb.compat.TlmMaidBridge}）。
     *
     * @return 是否画到了东西
     */
    public static boolean renderSheath(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light) {
        return renderNamedPart(stack, poseStack, buffer, light, "sheath");
    }

    /**
     * 单独画刀身（含发光层）。刀断了就用 {@code blade_damaged} 部件，与拔刀剑自己的取法一致。
     *
     * @return 是否画到了东西
     */
    public static boolean renderBladeBody(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light) {
        String part = "blade";
        try {
            ISlashBladeState state = BladeStateAccess.of(stack).orElse(null);
            if (state != null && state.isBroken()) {
                part = "blade_damaged";
            }
        } catch (Throwable ignored) {
            // 取不到状态就按完好的画
        }
        return renderNamedPart(stack, poseStack, buffer, light, part);
    }

    /**
     * 距上次挥刀动作过去了多少刻；取不到返回 -1。
     *
     * <p>女仆那条路要用它判断"是否刚出鞘"（见 {@link TlmMaidBridge}），
     * 所以放在这个拔刀剑隔离层里，别让别的类碰拔刀剑的类型。
     */
    public static long ticksSinceLastAction(LivingEntity entity, ItemStack stack) {
        try {
            if (entity == null) {
                return -1L;
            }
            ISlashBladeState state = BladeStateAccess.of(stack).orElse(null);
            if (state == null) {
                return -1L;
            }
            return entity.level().getGameTime() - state.getLastActionTime();
        } catch (Throwable t) {
            return -1L;
        }
    }

    /**
     * 挥出一记斩击特效（刀光）。
     *
     * <p><b>⚠️ 客户端调用是空转</b>：拔刀剑的 {@code AttackManager.doSlash(...)} 第一句就是
     * {@code if (level.isClientSide()) return null;}（1.9.65 与 2.0.7 逐条一致）。
     * 真正的刀光只能由<b>服务端</b>生成 —— 生成的是 {@code EntitySlashEffect} 实体，
     * 由原版实体同步机制自动发给所有客户端。
     * 所以本方法在两个侧都调一遍是<b>安全</b>的，客户端那次只是白跑一趟。
     *
     * <p>这里只是调用拔刀剑自己的公开 API，不含它的任何实现（MIT，见 NOTICE 第六节）。
     *
     * @return 是否真的生成了特效（客户端恒 false）
     */
    public static boolean showSlashArc(LivingEntity entity, float roll) {
        try {
            if (entity == null) {
                return false;
            }
            // 与上游挥刀兼容相同的参数：绕中心偏移为零、不静音、非暴击、
            // 伤害倍率 1.0（真实伤害由女仆自己的 doHurtTarget 负责，这里纯观赏）、
            // 击退沿用拔刀剑的 smash。
            return AttackManager.doSlash(entity, roll, Vec3.ZERO, false, false, 1.0D, KnockBacks.smash) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 把"刚刚挥过刀"这一刻记到<b>当前这一侧</b>的物品堆上。
     *
     * <p>这是"刀模出鞘"的唯一驱动量：渲染侧读
     * {@link #ticksSinceLastAction(LivingEntity, ItemStack)}，而它算的正是
     * {@code gameTime - lastActionTime}。
     *
     * <p>关键点在于<b>客户端那次 swing 也要写上</b>：客户端手上的 ItemStack 是它自己那份
     * （随装备同步包更新），渲染读的也是那一份。服务端写的值除非重新同步装备，
     * 否则客户端看不到 —— 所以两侧各写各的，才算真的生效。
     *
     * @return 是否写成功
     */
    public static boolean markLastAction(LivingEntity entity, ItemStack stack) {
        try {
            if (entity == null) {
                return false;
            }
            ISlashBladeState state = BladeStateAccess.of(stack).orElse(null);
            if (state == null) {
                return false;
            }
            state.setLastActionTime(entity.level().getGameTime());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 按部件名取模型与贴图并画出（含发光层）。 */
    private static boolean renderNamedPart(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer,
                                           int light, String part) {
        try {
            ISlashBladeState state = BladeStateAccess.of(stack).orElse(null);
            if (state == null) {
                return false;
            }
            ResourceLocation modelLocation = state.getModel().orElse(DefaultResources.resourceDefaultModel);
            ResourceLocation textureLocation = state.getTexture().orElse(DefaultResources.resourceDefaultTexture);
            WavefrontObject model = BladeModelManager.getInstance().getModel(modelLocation);
            if (model == null || !hasGroup(model, part)) {
                return false;
            }
            renderPart(stack, model, part, textureLocation, poseStack, buffer, light);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 该波前模型里是否存在指定部件。 */
    private static boolean hasGroup(WavefrontObject model, String name) {
        if (model.groupObjects == null) {
            return false;
        }
        for (Object group : model.groupObjects) {
            if (group instanceof mods.flammpfeil.slashblade.client.renderer.model.obj.GroupObject g
                    && name.equals(g.name)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Vec3 renderOffset(LivingEntity entity, float partialTicks) {        try {
            EntityRenderer<Entity> renderer =
                    (EntityRenderer<Entity>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            Vec3 offset = renderer.getRenderOffset(entity, partialTicks);
            return offset == null ? Vec3.ZERO : offset;
        } catch (Throwable t) {
            return Vec3.ZERO;
        }
    }
}
