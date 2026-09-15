package dev.yessb;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 极简配置：纯 properties 文件，不依赖任何配置框架。
 *
 * <p>设计原则：任何一项设置为不可用状态时，本模组都应当完全惰性，
 * 等价于没有安装。
 */
public final class FixConfig {

    private static final String FILE_NAME = "yes_sb.properties";

    /** 总开关。 */
    public static boolean enabled = true;

    /** 还原第三人称的腰挂刀 + 连招出鞘（YSM 掐掉原版渲染层后的补偿）。 */
    public static boolean restoreThirdPerson = true;

    /**
     * 第三人称补画时的额外缩放。
     *
     * <p>拔刀剑的腰刀是按**原版玩家模型**的比例做的；而 YSM 的模型通常被整体缩小
     * （内置模型的 {@code height_scale} / {@code width_scale} 是 0.7），
     * 于是同一把刀挂在 YSM 模型上就显得偏大。这个系数就是用来对齐两者的。
     */
    public static double thirdPersonScale = 0.7D;

    /**
     * 第一人称把拔刀剑当作普通手持物渲染（像手一样固定朝向、随视角移动）。
     *
     * <p>拔刀剑默认在第一人称会去画"挂在身体上的整套 MMD 刀 + 鞘"，
     * 于是低头能看见一整把刀悬在腰部 —— 那不是第一人称该有的样子。
     * 打开本项后改为按手持物绘制，只画刀身。
     *
     * <p><b>默认值是"允许介入"，但 {@link #firstPersonMode} 默认 {@code auto}</b> ——
     * 也就是"原版画得出来就别插手，画不出来才兜底"。真正接管第一人称（{@code model}）
     * 需要显式打开，因为那条路要配一组位移参数才好看，见 {@link #firstPersonMode}。
     */
    public static boolean firstPersonAsHeldItem = true;

    /**
     * 第一人称的画法：
     * <ul>
     *   <li>{@code auto} —— <b>默认</b>。先问拔刀剑原生的 {@code BladeFirstPersonRender}
     *       "这一帧你画不画得出来"（条件逐条照抄它的字节码）；画得出来就放手，
     *       画不出来（例如 YSM 把玩家渲染器换成了不是 {@code RenderLayerParent} 的东西、
     *       或玩家在睡觉 / 隐藏了 HUD）才由本模组兜底，避免第一人称彻底没有刀。</li>
     *   <li>{@code off} —— 完全不干预，永远交回原版。这就是 1.20.1 的样子，
     *       也就是参照截图里那"一整把刀斜跨画面"。</li>
     *   <li>{@code model} —— 本模组接管，画 3D 刀身（位置/朝向/缩放用下面的参数微调）。</li>
     *   <li>{@code icon} —— 本模组接管，画平面图标（拔刀剑 GUI/地面图标用的那个部件）。</li>
     * </ul>
     *
     * <p><b>为什么默认不接管。</b>第一人称不经过 YSM 接管的 {@code EntityRenderDispatcher}
     * —— YSM 从来没有掐过它，1.21.1 上原版那套本来就能画。本模组的职责是
     * "把被掐掉的还回去"，第一人称不属于这一类；主动接管只会平白引入一组需要调的变换。
     * （实测：{@code model} 模式下刀会整体偏出画面，根因是漏了一次
     * {@code translate(0.5,0.5,0.5)}，现已修，见 {@link SlashBladeBridge#renderBladeModel}。）
     *
     * <p>另外，两条路画出来的东西<b>本来就不一样</b>：原版那套的刀挂点来自 MMD 模型
     * （{@code LayerMainBlade}），会跟着身体姿态走；{@code model} 那套是把刀当作
     * 普通物品画，位置完全由物品的 display 变换决定。
     */
    public static String firstPersonMode = "auto";

    /**
     * 手持刀（第三方称主手 / 平面上下文）的缩放。
     *
     * <h2>这个数从哪来</h2>
     * 拔刀剑自己有两套尺度，差 3 倍，早前我们照搬错了那一套：
     * <ul>
     *   <li>{@code renderModel} 用 <b>0.003125</b> —— 那是刀<b>躺在刀架/展示框里</b>的尺寸；</li>
     *   <li>{@code renderBlade} 画图标用 <b>0.0095</b>（GROUND 0.005 / GUI 0.008 / FIXED 0.0095）
     *       —— 那才是"拿在手上"的尺度。</li>
     * </ul>
     * 折中值由几何反推：图标部件 {@code item_blade} 包围盒对角线 ≈ 218 单位，
     * 3D 刀身 {@code blade} 沿 X 长 332.7 单位 ⇒ {@code 0.0095 × 218 / 332.7 ≈ 0.0062}，
     * 正好落在实测的"0.0095 太大、0.003125 太小"之间。
     */
    public static double handBladeScale = 0.0062D;

    /** {@code FIXED} 上下文（女仆装饰槽等）的 3D 刀身缩放。 */
    public static double flatBladeScale = 0.0062D;

    /**
     * 第一人称刀身的缩放。
     *
     * <p>同样别再照搬 0.003125（那是挂台的尺度）。第一人称的物品 display 变换本身带
     * 1.25 倍（见拔刀剑 {@code models/item/slashblade.json}），所以这里可以略小一点。
     * <b>一切以实际观感为准，改完存盘约 2 秒生效、不用重启。</b>
     */
    public static double firstPersonScale = 0.0062D;

    /** 第一人称刀身的朝向（角度）。 */
    public static double firstPersonRotX = 0.0D;
    public static double firstPersonRotY = 0.0D;
    public static double firstPersonRotZ = 0.0D;

    /** 第一人称刀身的位移（方块）。 */
    public static double firstPersonOffsetX = 0.0D;
    public static double firstPersonOffsetY = 0.0D;
    public static double firstPersonOffsetZ = 0.0D;

    // ------------------------------------------------------------------ 手持（第三方称）

    /**
     * 第三方称主手刀身的朝向与位移。
     *
     * <p>曾经这三个上下文<b>共用</b>同一组 {@code firstPerson*} 参数，结果调好一个必然弄坏另一个；
     * 现在各自独立。
     */
    public static double handBladeRotX = 0.0D;
    public static double handBladeRotY = 0.0D;
    public static double handBladeRotZ = 0.0D;
    public static double handBladeOffsetX = 0.0D;
    public static double handBladeOffsetY = 0.0D;
    public static double handBladeOffsetZ = 0.0D;

    // ------------------------------------------------------------------ 平面上下文（装饰槽等）

    /**
     * {@code FIXED} 上下文（女仆装饰槽、展示框桌面等）刀身的朝向与位移。
     *
     * <p>这里已经自动补了一个<b>几何修正</b>：拔刀剑画 {@code FIXED} 时用的是平面图标，
     * 而图标与 3D 刀身的包围盒中心差着约 117 个模型单位（≈0.72 格，正是 X 方向）；
     * 不补的话 3D 刀身会整体横向偏出去一大截。默认值就是按这个差值算的，
     * 所以正常情况下这几项保持 0 即可，觉得还要挪再改。
     */
    public static double flatBladeRotX = 0.0D;
    public static double flatBladeRotY = 0.0D;
    public static double flatBladeRotZ = 0.0D;
    public static double flatBladeOffsetX = 0.0D;
    public static double flatBladeOffsetY = 0.0D;
    public static double flatBladeOffsetZ = 0.0D;

    // ------------------------------------------------------------------ 腰挂层补偿

    /**
     * 腰挂层（拔刀剑自己的 {@code LayerMainBlade}）补偿渲染的额外位移，单位是<b>方块</b>。
     *
     * <p><b>正数 = 向上 / 向前</b>（与人的直觉一致）。这是刻意修的：早前这几个值是在
     * {@code scale(-1,-1,1)} 之后施加的，那一步会把 Y 轴翻转，于是填正数反而让刀往下走 ——
     * 等于根本调不出来。现在它们被放在翻转之前，并且预除以缩放系数，
     * 所以配置值就是最终的世界位移。
     *
     * <p>用途：补偿变换是照原版 {@code LivingEntityRenderer} 的环境复刻的，
     * 而 YSM 模型的原点/比例不一定与它一致 ——「刀太低、没接到手上」就调这里。
     */
    public static double waistOffsetX = 0.0D;
    public static double waistOffsetY = 0.0D;
    public static double waistOffsetZ = 0.0D;

    /**
     * 补画腰刀前，是否先把原版模型的姿态补算一遍（<b>默认开启</b>）。
     *
     * <p>YSM 掐掉的是整个原版 {@code LivingEntityRenderer.render}，连带里面的
     * {@code model.setupAnim(...)} 也没执行；而拔刀剑的 {@code LayerMainBlade}
     * 构造 MMD 刀挂点时正是读这些部位。不补这一步，刀就停在静止姿态的位置
     * —— 「玩家的刀太低、没接到手上」就是这个原因。
     *
     * <p>关掉它只影响姿态新鲜度，可以用来做对照实验。
     */
    public static boolean refreshModelPose = true;

    /**
     * 是否启用"拔刀剑剑技动画"的靶向注入（<b>默认开启</b>）。
     *
     * <p>YSM 1.21.1 的拔刀剑联动模块被整个掏空了：判断"这是不是拔刀剑"恒为假、
     * 取剑技动画名恒为空串。于是 YSM 解析器里那段「手持拔刀剑 → 按连招状态取动画」成了死代码，
     * 表现就是怎么砍都只有普通挥动。本项把那两个返回值补回去。
     *
     * <p>为什么可以放心默认开启：YSM 拿到名字后还会自己确认<b>模型包里有没有这条动画</b>，
     * 没有就当作"不覆盖"，所以补错了也只会没效果，不会破坏模型姿态。
     * 前提是你的模型包在 {@code ysm.json} 里声明了 {@code slashblade} 动画文件
     * （官方文档：不声明就沿用普通剑动画）。
     */
    public static boolean slashbladeComboAnimations = true;

    /**
     * 是否把「主动画」也换成拔刀剑专属版本（<b>默认开启</b>）。
     *
     * <p>手持拔刀剑时，待机/走/跑/跳/潜行/飞行这些主动画会先问 {@code slashblade:idle} 之类，
     * 模型包里有就播专属动作，没有就退回原名 —— 这正是 1.20.1 那份兼容模块的另一半。
     * 1.21.1 的空桩同样把这一半掏掉了，所以持刀时连站姿都和普通剑一样。
     *
     * <p>与剑技动画分开开关：若不希望持刀改变站姿/走路动作，把这一项关掉即可。
     */
    public static boolean slashbladeMainStateAnimations = true;

    /**
     * 是否启用"拔刀剑专属动画"的<b>物品分类</b>注入（默认关闭）。
     *
     * <p>YSM 的手持物分类器读了 13 个内置标签中的 12 个，唯独漏掉拔刀剑，
     * 于是拔刀剑会按 {@code SwordItem} 落到 {@code sword} 分类。
     * 打开本项后会把分类改成 {@code slashblade}（得到 {@code hold_mainhand:slashblade} /
     * {@code swing:slashblade} / {@code use_mainhand:slashblade}）。
     *
     * <p>这是<b>行为变更</b>：模型包若没有 {@code swing:slashblade}，原本会用的
     * {@code swing:sword} 也会一起失效，因此默认关闭。剑技动画靠
     * {@link #slashbladeComboAnimations} 就够了，通常不需要动这里。
     */
    public static boolean slashbladeAnimations = false;

    /**
     * 主手持刀时，在**第三方称**把刀画在手上。
     *
     * <p>拔刀剑的腰挂层只挂在玩家/僵尸/骷髅/猪灵这些渲染器上，女仆之类根本没有这一层；
     * 而它的 BEWLR 又对第三方称上下文故意不画 —— 于是"手持"这条路一直是空的。
     * 打开本项后，主手会直接把手里的刀画出来（女仆也因此有了刀），
     * 此时不再补画腰挂层，避免同时出现两把。
     */
    public static boolean handBladeInThirdPerson = true;

    /**
     * 主手手持的那把刀，是否只在"没有腰挂层"的实体上才由本模组画（<b>默认 true</b>）。
     *
     * <p>拔刀剑 2.0.7 的 {@code ClientHandler#addLayers} 会遍历所有已注册实体类型，
     * 给<b>每一个</b> {@code LivingEntityRenderer} 都加上 {@code LayerMainBlade}。
     * 也就是说刀本来就有主（腰挂层会按主手/副手/连招状态自己画），玩家更是如此。
     *
     * <p>本模组当初加"手持补画"是为了女仆那种换掉了渲染器、腰挂层不存在的实体。
     * 但如果对玩家也生效，就会出现两把刀 —— 实测第一人称纸娃娃里正是这样
     * （手里一把小的、腰上一把补画的）。
     *
     * <p>因此默认按"渲染器是不是原版 {@code LivingEntityRenderer}"来分派：
     * 是 ⇒ 交给腰挂层；不是 ⇒ 交给手持补画。若你想让手持那条路强行接管所有实体，
     * 把这一项设为 false 即可（那样腰挂层就不会再画主手那把）。
     */
    public static boolean handBladeRequiresNoWaistLayer = true;

    // ------------------------------------------------------------ 女仆（车万女仆）

    /**
     * 是否补回女仆手里那把拔刀剑（<b>默认开启</b>）。
     *
     * <p>车万女仆 1.20/1.21 分支的 {@code GeckoLayerMaidHeld} 里本来有一段
     * {@code if (SlashBladeCompat.isSlashBladeItem(...)) → SlashBladeRender.renderMaid...}，
     * 但 <b>1.21.1 的发布版把这一段丢了</b>（逐条比对过字节码，其余结构完全一致），
     * 于是拔刀剑掉进普通物品渲染，而拔刀剑的 BEWLR 对第三方称上下文不画 ⇒ 女仆手里空着。
     * 本项按 TLM 原样把那一段补回去（TLM 代码 MIT）。
     */
    public static boolean maidSlashBlade = true;

    /**
     * 是否补回女仆的拔刀剑<b>挥刀逻辑</b>（<b>默认开启</b>）。
     *
     * <p>这是 TLM 1.21.1 丢掉的第三处（前两处是渲染）：{@code EntityMaid} 在 1.20/1.21 分支里
     * 覆写了 {@code swing(InteractionHand)} 去调拔刀剑的挥刀兼容，而发布版里<b>连这个覆写都没有</b>。
     * 于是女仆照常挥刀、照常造成伤害，却<b>没有刀光、刀也不出鞘</b>。
     *
     * <p>补回之后有两个副作用，分别属于两侧：
     * 服务端生成刀光（斩击特效实体，自动同步），客户端写 {@code lastActionTime} 驱动出鞘动作。
     *
     * <p>与上一项 {@link #maidSlashBlade}（渲染）<b>分开</b>，便于像第十一轮那样做对照实验。
     */
    public static boolean maidSlashBladeAttack = true;

    /** 女仆刀的缩放 —— TLM 的 Gecko 路径原值 0.01，按用户要求缩到 **90%** 即 0.009。 */
    public static double maidBladeScale = 0.009D;

    /** 动作发生后多少刻之内算"刚出鞘"。TLM 原值 5。 */
    public static long maidBladeDrawTicks = 5L;

    /** 出鞘那一下位移的分母（TLM 原值 0.007，与它的缩放并列，照抄以对齐手感）。 */
    public static double maidBladeDrawDistanceFactor = 0.007D;

    /**
     * 女仆刀的额外位移与朝向（在 TLM 那套变换之后叠加）。
     *
     * <p>TLM 的变换是挂在<b>女仆模型自己的定位组骨骼</b>上的；如果女仆实际是由 YSM 用
     * 另一套模型渲染的，骨骼位置与看到的身体就可能对不上，表现为刀整体偏出去。
     * 这里给出微调口子。位移单位是方块、正数=向上/向前（与 {@code waistOffset*} 同一约定）。
     */
    public static double maidBladeOffsetX = 0.0D;
    public static double maidBladeOffsetY = 0.0D;
    public static double maidBladeOffsetZ = 0.0D;
    public static double maidBladeRotX = 0.0D;
    public static double maidBladeRotY = 0.0D;
    public static double maidBladeRotZ = 0.0D;

    /**
     * 在"平面上下文"（{@code FIXED}）把平面图标换成 3D 刀身。
     *
     * <p>车万女仆的手持刀走的就是这个上下文 —— 拔刀剑在那里画的是 {@code item_blade} 平面部件，
     * 于是女仆手里是"刀+鞘交叉"的一张平面图。打开本项即改为画 3D 刀身。
     * 已经架在刀架上的（展示框）不受影响。
     */
    public static boolean handBladeInFlatContext = true;

    /** 只对玩家生效（默认）。 */
    public static boolean affectsPlayers = true;

    /** 是否也对车万女仆等其它被 YSM 接管的生物生效。 */
    public static boolean affectsOtherLivingEntities = true;

    /** 打印详细日志（排查用）。 */
    public static boolean debugLog = false;

    /** 允许生效的 YSM 版本前缀（靶向注入是版本敏感的）。 */
    public static String ysmVersionPrefix = "2.6.";

    private static boolean loaded;

    /** 配置文件上次被载入的时间戳，用于热重载。 */
    private static volatile long lastLoadedStamp = -1L;

    private FixConfig() {
    }

    /**
     * 热重载：配置文件被改动过就重新读取，<b>不需要重启游戏</b>。
     *
     * <p>调参（第一人称/手持刀的位置朝向）靠这个才方便：改完存盘，约两秒后生效。
     */
    public static void reloadIfChanged() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
            if (!Files.exists(path)) {
                return;
            }
            long stamp = Files.getLastModifiedTime(path).toMillis();
            if (stamp == lastLoadedStamp) {
                return;
            }
            lastLoadedStamp = stamp;
            if (loaded) {
                load();
                YesSlashBladeFix.LOGGER.info("[YES-SB] 配置已热重载：thirdPersonScale={} handBlade={} firstPerson={}({}) scale={} 剑技动画={} 女仆挥刀={}",
                        thirdPersonScale, handBladeInThirdPerson, firstPersonAsHeldItem, firstPersonMode, firstPersonScale,
                        slashbladeComboAnimations, maidSlashBladeAttack);
            }
        } catch (Throwable ignored) {
            // 热重载失败不影响游戏
        }
    }

    /**
     * 幂等载入。混入准入判断可能早于模组构造发生，因此两边都要能安全触发一次读取；
     * 读不到就保持内置默认值，绝不抛异常。
     */
    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            load();
        } catch (Throwable e) {
            YesSlashBladeFix.LOGGER.warn("[YES-SB] 配置不可用，使用内置默认值: {}", e.toString());
        }
        // 记下本次读取时的文件时间戳。
        //
        // 不记的话，第一个 tick 的 reloadIfChanged() 会拿 -1 去比真实时间戳，判定为"变了"，
        // 于是每次启动都会假报一次「配置已热重载」—— 实测确认过（配置文件启动前就存在、未被改动）。
        // 排查时这种假信号最误导人，所以在这里堵掉。
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
            if (Files.exists(path)) {
                lastLoadedStamp = Files.getLastModifiedTime(path).toMillis();
            }
        } catch (Throwable ignored) {
            // 取不到就算了，最多回到"启动时多打一条"的旧行为
        }
    }

    private static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);

        if (!Files.exists(path)) {
            writeDefault(path);
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            YesSlashBladeFix.LOGGER.warn("[YES-SB] 配置读取失败，使用默认值: {}", e.toString());
            return;
        }

        enabled = read(props, "enabled", enabled);
        restoreThirdPerson = read(props, "restoreThirdPerson", restoreThirdPerson);
        thirdPersonScale = readDouble(props, "thirdPersonScale", thirdPersonScale);
        firstPersonAsHeldItem = read(props, "firstPersonAsHeldItem", firstPersonAsHeldItem);
        firstPersonMode = props.getProperty("firstPersonMode", firstPersonMode).trim();
        firstPersonScale = readDouble(props, "firstPersonScale", firstPersonScale);
        handBladeScale = readDouble(props, "handBladeScale", handBladeScale);
        flatBladeScale = readDouble(props, "flatBladeScale", flatBladeScale);
        firstPersonRotX = readDouble(props, "firstPersonRotX", firstPersonRotX);
        firstPersonRotY = readDouble(props, "firstPersonRotY", firstPersonRotY);
        firstPersonRotZ = readDouble(props, "firstPersonRotZ", firstPersonRotZ);
        firstPersonOffsetX = readDouble(props, "firstPersonOffsetX", firstPersonOffsetX);
        firstPersonOffsetY = readDouble(props, "firstPersonOffsetY", firstPersonOffsetY);
        firstPersonOffsetZ = readDouble(props, "firstPersonOffsetZ", firstPersonOffsetZ);
        handBladeRotX = readDouble(props, "handBladeRotX", handBladeRotX);
        handBladeRotY = readDouble(props, "handBladeRotY", handBladeRotY);
        handBladeRotZ = readDouble(props, "handBladeRotZ", handBladeRotZ);
        handBladeOffsetX = readDouble(props, "handBladeOffsetX", handBladeOffsetX);
        handBladeOffsetY = readDouble(props, "handBladeOffsetY", handBladeOffsetY);
        handBladeOffsetZ = readDouble(props, "handBladeOffsetZ", handBladeOffsetZ);
        flatBladeRotX = readDouble(props, "flatBladeRotX", flatBladeRotX);
        flatBladeRotY = readDouble(props, "flatBladeRotY", flatBladeRotY);
        flatBladeRotZ = readDouble(props, "flatBladeRotZ", flatBladeRotZ);
        flatBladeOffsetX = readDouble(props, "flatBladeOffsetX", flatBladeOffsetX);
        flatBladeOffsetY = readDouble(props, "flatBladeOffsetY", flatBladeOffsetY);
        flatBladeOffsetZ = readDouble(props, "flatBladeOffsetZ", flatBladeOffsetZ);
        waistOffsetX = readDouble(props, "waistOffsetX", waistOffsetX);
        waistOffsetY = readDouble(props, "waistOffsetY", waistOffsetY);
        waistOffsetZ = readDouble(props, "waistOffsetZ", waistOffsetZ);
        refreshModelPose = read(props, "refreshModelPose", refreshModelPose);
        slashbladeAnimations = read(props, "slashbladeAnimations", slashbladeAnimations);
        slashbladeComboAnimations = read(props, "slashbladeComboAnimations", slashbladeComboAnimations);
        slashbladeMainStateAnimations = read(props, "slashbladeMainStateAnimations", slashbladeMainStateAnimations);
        handBladeInThirdPerson = read(props, "handBladeInThirdPerson", handBladeInThirdPerson);
        handBladeRequiresNoWaistLayer = read(props, "handBladeRequiresNoWaistLayer", handBladeRequiresNoWaistLayer);
        maidSlashBlade = read(props, "maidSlashBlade", maidSlashBlade);
        maidSlashBladeAttack = read(props, "maidSlashBladeAttack", maidSlashBladeAttack);
        maidBladeScale = readDouble(props, "maidBladeScale", maidBladeScale);
        maidBladeDrawTicks = readLong(props, "maidBladeDrawTicks", maidBladeDrawTicks);
        maidBladeDrawDistanceFactor = readDouble(props, "maidBladeDrawDistanceFactor", maidBladeDrawDistanceFactor);
        maidBladeOffsetX = readDouble(props, "maidBladeOffsetX", maidBladeOffsetX);
        maidBladeOffsetY = readDouble(props, "maidBladeOffsetY", maidBladeOffsetY);
        maidBladeOffsetZ = readDouble(props, "maidBladeOffsetZ", maidBladeOffsetZ);
        maidBladeRotX = readDouble(props, "maidBladeRotX", maidBladeRotX);
        maidBladeRotY = readDouble(props, "maidBladeRotY", maidBladeRotY);
        maidBladeRotZ = readDouble(props, "maidBladeRotZ", maidBladeRotZ);
        handBladeInFlatContext = read(props, "handBladeInFlatContext", handBladeInFlatContext);
        affectsPlayers = read(props, "affectsPlayers", affectsPlayers);
        affectsOtherLivingEntities = read(props, "affectsOtherLivingEntities", affectsOtherLivingEntities);
        debugLog = read(props, "debugLog", debugLog);
        ysmVersionPrefix = props.getProperty("ysmVersionPrefix", ysmVersionPrefix).trim();
    }

    private static boolean read(Properties props, String key, boolean fallback) {
        String raw = props.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }

    private static double readDouble(Properties props, String key, double fallback) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long readLong(Properties props, String key, long fallback) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void writeDefault(Path path) {
        String text = """
                # YES-SB —— Yes Steve Model × 拔刀剑：重锋 兼容修复
                #
                # 【本文件全部键都支持热重载】改完存盘约 2 秒生效，不需要重启游戏。
                # 缺失的键会自动使用内置默认值，所以可以只写你想改的那几行。
                #
                # ============================ 总开关 ============================
                # enabled            总开关；false 时完全惰性（等价于卸载本模组）
                #
                # affectsPlayers              是否处理玩家模型（默认 true）
                # affectsOtherLivingEntities  是否也处理玩家以外的生物
                #
                # ==================== 一、第三人称腰挂刀 ====================
                # restoreThirdPerson  还原第三人称的腰挂刀与连招出鞘。
                #                     原理：YSM 会跳过被它接管的实体的原版渲染器调用，
                #                     而拔刀剑的腰刀是由挂在实体渲染器上的 RenderLayer 画的，
                #                     于是整层被一起掐掉。本项把这一层补回来。
                #
                # thirdPersonScale    第三人称补画的缩放。拔刀剑的腰刀按原版玩家比例做的，
                #                     而 YSM 模型通常整体缩小（内置模型是 0.7）。
                #                     注意这会影响刀的位置与大小两者。
                #
                # waistOffsetX/Y/Z    腰挂层补偿的额外位移（方块，正数 = 向上/向前）。
                #                     用于吸收"YSM 模型原点/比例与原版不同"造成的固定偏移，
                #                     例如玩家的刀整体偏低。
                #
                # refreshModelPose    补画腰刀前先把原版模型姿态补算一遍（默认 true）。
                #                     YSM 掐掉原版渲染时连带 model.setupAnim(...) 也没跑，
                #                     而腰刀层的刀挂点是从那些部位算出来的。
                #                     ⚠️ 这一步并不能让刀跟上 YSM 的动画 —— 它跟的是原版挥砍动作。
                #
                # ==================== 二、拔刀剑专属动画 ====================
                # slashbladeComboAnimations   补回"剑技动画"的触发（默认 true）。
                #                             YSM 的拔刀剑联动模块是空实现，判断"是不是拔刀剑"
                #                             恒为假、取动画名恒为空，于是怎么砍都只有普通挥动。
                #                             本项把那些返回值补回去，连招动画
                #                             （combo_a1 / standby / judgement_cut ...）就会按
                #                             连招状态播放。
                #                             前提：模型包要在 ysm.json 里声明 slashblade 动画文件。
                #
                # slashbladeMainStateAnimations 主动画也换成拔刀剑专属版（默认 true）。
                #                             持刀时待机/走/跑/跳/潜行/飞行会先问 slashblade:idle
                #                             之类，模型包里有就用专属动作，没有就退回原名。
                #                             不希望持刀改变站姿就关掉这一项。
                #
                # slashbladeAnimations        把拔刀剑的物品分类从 sword 改成 slashblade
                #                             （默认 false）。这是行为变更：模型包若没有
                #                             swing:slashblade，原本能用的 swing:sword 也会
                #                             一起失效，一般不需要开。
                #
                # ==================== 三、手持与平面上下文 ====================
                # handBladeInThirdPerson       第三方称主手是否改画 3D 刀身（默认 true）。
                #                              只对"没有腰挂层"的实体生效，见下一项。
                #
                # handBladeRequiresNoWaistLayer 手持补画是否只对"没有腰挂层"的实体生效
                #                              （默认 true）。拔刀剑给每个实体渲染器都挂了腰挂层，
                #                              有腰挂层的实体（含玩家）应当交给它画，
                #                              否则会出现两把刀。设 false 会强制由本模组接管。
                #
                # handBladeInFlatContext       平面上下文（FIXED）是否改画 3D 刀身（默认 true）。
                #                              已经架在刀架/展示框上的不碰。
                #                              注：车万女仆的装饰槽不走这条，它由女仆那条路处理。
                #
                # handBladeScale / flatBladeScale
                #                     缩放。⚠️ 别填 0.003125 —— 那是拔刀剑画"刀架"用的比例；
                #                     它画图标用的是 0.0095。几何折中值 ≈ 0.0062，
                #                     偏大往小调、偏小往大调。
                #
                # handBladeRotX/Y/Z、handBladeOffsetX/Y/Z
                # flatBladeRotX/Y/Z、flatBladeOffsetX/Y/Z
                #                     朝向（角度）与位移（方块）。三个上下文各自独立，
                #                     调一个不会影响另外两个。
                #
                # ==================== 四、第一人称 ====================
                # 第一人称不经过 YSM 接管的实体渲染，它从来就没被掐过 —— 1.20.1 的第一人称
                # 就是拔刀剑自己的 BladeFirstPersonRender（"一整把刀斜跨画面"），
                # 1.21.1 上本来也是好的。所以默认 auto：原版画得出来就别插手。
                #
                # firstPersonMode        auto  = 原版画得出来就放手，画不出来才由本模组兜底（默认）
                #                        off   = 永远交回原版（= 1.20.1 的样子）
                #                        model = 本模组接管，把手里的刀按 3D 刀身画出来
                #                        icon  = 本模组接管，画平面图标
                # firstPersonAsHeldItem  总闸；false 时一律交回原版（等价于 off）
                #
                # ⚠️ 用 model 前先读这段：那条路是把刀当成"普通手持物品"画的，
                #    位置完全由物品 display 变换决定 —— 而拔刀剑那份 display 变换的位移是
                #    [-15, 5, -11]（单位 1/16 格），它并不是为这种画法调的。
                #    所以一定要配 firstPersonOffsetX/Y/Z 收敛：
                #    开 debugLog 后日志会每 2 秒打一行
                #      「手持刀[firstPerson]：… 刀原点(相机空间)=(x,y,z) 视野判定=在画面内/★在画面外」
                #    把它调到"在画面内"为止即可（单位是方块，+X 向右、+Y 向上、-Z 向前）。
                #
                # firstPersonScale       3D 刀身缩放（别填 0.003125，那是挂台的尺度）
                # firstPersonRotX/Y/Z    朝向角度
                # firstPersonOffsetX/Y/Z 位移（方块，最外层世界位移）
                #
                # ==================== 五、车万女仆 ====================
                # maidSlashBlade     补回女仆手里与背上的那把拔刀剑（默认 true）。
                #                    TLM 1.21.1 的发布版丢失了 compat/slashblade 包，
                #                    连带手部渲染、背槽渲染两处；本项按 TLM 原样补回。
                #
                # maidSlashBladeAttack 补回女仆"挥刀"这件事本身（默认 true）。
                #                    这是发布版丢掉的第三处：TLM 1.20/1.21 分支里
                #                    EntityMaid 覆写了 swing() 去调拔刀剑的挥刀兼容，
                #                    发布版里连这个覆写都没有 —— 于是女仆攻击正常、有伤害，
                #                    但没有刀光（斩击特效），刀也不会出鞘。
                #                    与 maidSlashBlade（渲染）分开，便于对照排查。
                #
                # maidBladeScale     缩放。TLM 原值 0.01，这里取 0.009（原值的 90%）。
                # maidBladeDrawTicks 动作发生后多少刻之内算"刚出鞘"（TLM 原值 5）。
                # maidBladeDrawDistanceFactor
                #                    出鞘那一下位移的分母（TLM 原值 0.007）。
                #
                # maidBladeOffsetX/Y/Z、maidBladeRotX/Y/Z
                #                    额外位移（方块）与朝向（角度），叠加在 TLM 那套变换之后。
                #                    默认全 0 = 与 TLM 完全一致。
                #
                # ============================ 其它 ============================
                # ysmVersionPrefix   允许靶向注入的 YSM 版本前缀（默认 2.6.）。
                #                    靶向注入依赖 YSM 的混淆内部结构，版本不匹配时会自动跳过
                #                    （表现为对应功能不生效，不会崩溃）。
                # debugLog           排查开关（默认 false，关闭时开销为零）。
                #                    ⚠️ 报 bug 时请开一次，把日志里所有 [YES-SB] 开头的行附上 ——
                #                    本模组做的多是"补一层被掐掉的渲染、补一个恒为假的返回值"
                #                    这类**看不见就等于没干活**的事，没有这些行很难定位。
                #
                #                    开启后会打印（都已经过节流，不会刷屏）：
                #                      · 各处失败与早退的原因（例如"剑技动画为空 ⇒ 超时/模型缺这条"）
                #                      · 第一人称"刀在不在画面里"的视野判定（调 firstPersonOffset* 用）
                #                      · 腰挂层补偿的实体与位移（调 waistOffset* 用）
                #                      · 女仆挥砍在**服务端 / 客户端**两侧各自的结果
                #                        （两侧都应当出现；客户端那条"刀光=未生成"是正常的）
                #                      · 女仆刀何时出鞘（只在"出鞘"那一次打一条）
                enabled=true
                affectsPlayers=true
                affectsOtherLivingEntities=true

                # 一、第三人称腰挂刀
                restoreThirdPerson=true
                thirdPersonScale=0.7
                waistOffsetX=0
                waistOffsetY=0
                waistOffsetZ=0
                refreshModelPose=true

                # 二、拔刀剑专属动画
                slashbladeComboAnimations=true
                slashbladeMainStateAnimations=true
                slashbladeAnimations=false

                # 三、手持与平面上下文
                handBladeInThirdPerson=true
                handBladeRequiresNoWaistLayer=true
                handBladeInFlatContext=true
                handBladeScale=0.0062
                handBladeRotX=0
                handBladeRotY=0
                handBladeRotZ=0
                handBladeOffsetX=0
                handBladeOffsetY=0
                handBladeOffsetZ=0
                flatBladeScale=0.0062
                flatBladeRotX=0
                flatBladeRotY=0
                flatBladeRotZ=0
                flatBladeOffsetX=0
                flatBladeOffsetY=0
                flatBladeOffsetZ=0

                # 四、第一人称
                firstPersonAsHeldItem=true
                firstPersonMode=auto
                firstPersonScale=0.0062
                firstPersonRotX=0
                firstPersonRotY=0
                firstPersonRotZ=0
                firstPersonOffsetX=0
                firstPersonOffsetY=0
                firstPersonOffsetZ=0

                # 五、车万女仆
                maidSlashBlade=true
                maidSlashBladeAttack=true
                maidBladeScale=0.009
                maidBladeDrawTicks=5
                maidBladeDrawDistanceFactor=0.007
                maidBladeOffsetX=0
                maidBladeOffsetY=0
                maidBladeOffsetZ=0
                maidBladeRotX=0
                maidBladeRotY=0
                maidBladeRotZ=0

                # 其它
                ysmVersionPrefix=2.6.
                debugLog=false
                """;
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            YesSlashBladeFix.LOGGER.warn("[YES-SB] 无法写入默认配置: {}", e.toString());
        }
    }
}
