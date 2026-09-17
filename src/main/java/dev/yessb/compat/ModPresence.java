package dev.yessb.compat;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/**
 * "某个可选模组装没装" + "某个实体是不是车万女仆" —— <b>本类不引用任何第三方的类型</b>。
 *
 * <h2>为什么必须单独有这么一个类</h2>
 * 这是 <b>1.0.13 一次真实崩溃</b>换来的：
 * 车万女仆的挥刀钩子打在原版 {@code LivingEntity#swing} 上（每个实体挥刀都会经过），
 * 而判定"是不是女仆"用的是 {@code entity instanceof EntityMaid} —— 这<b>等于要求 JVM
 * 在解析/校验那个类时加载 {@code EntityMaid}</b>。没装 TLM 的整合包（例如 ATM10）里
 * 那个类不存在 ⇒ 进入方法体的 <b>try 之前</b>就抛 {@code NoClassDefFoundError}
 * ⇒ 左键一挥就崩，而 {@code catch (Throwable)} 根本兜不住（异常发生在链接阶段，
 * 不在 try 的范围内 —— 栈里只有注入点那一帧、没有桥接层的帧，正是这个原因）。
 *
 * <p>⇒ <b>凡是"在依赖缺失时也必须能安全加载"的代码路径，一行都不能出现第三方的类型引用。</b>
 * 判定"装没装"只能查 {@code ModList}；判定"是不是女仆"只能<b>按类名爬父类链</b>。
 *
 * <h2>缓存策略</h2>
 * 沿用 {@link YsmBridge} 定的规矩：<b>只有拿到可信答案才缓存</b>。
 * 拿不到 {@code ModList}（模组尚未就绪）时返回 {@code false} 但<b>不写缓存</b>，
 * 免得把"还没就绪"永久固化成"没装"（第七节记过这个坑）。
 * 本类的调用点都在运行期（渲染 / 挥刀），那时模组列表必定已就绪。
 */
public final class ModPresence {

    /** 拔刀剑：重锋。 */
    public static final String SLASHBLADE = "slashblade";

    /** 车万女仆。 */
    public static final String TLM = "touhou_little_maid";

    /** 车万女仆的实体类名 —— 只做字符串比较，绝不 import。 */
    private static final String MAID_CLASS =
            "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid";

    private static volatile Boolean slashBlade;
    private static volatile Boolean tlm;

    private ModPresence() {
    }

    /** 装了拔刀剑：重锋吗。 */
    public static boolean hasSlashBlade() {
        Boolean cached = slashBlade;
        if (cached != null) {
            return cached;
        }
        try {
            ModList list = ModList.get();
            if (list == null) {
                // 过早查询：不缓存（见类注释）
                return false;
            }
            boolean loaded = list.isLoaded(SLASHBLADE);
            slashBlade = loaded;
            return loaded;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 装了车万女仆吗。 */
    public static boolean hasTlm() {
        Boolean cached = tlm;
        if (cached != null) {
            return cached;
        }
        try {
            ModList list = ModList.get();
            if (list == null) {
                return false;
            }
            boolean loaded = list.isLoaded(TLM);
            tlm = loaded;
            return loaded;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 这个实体是不是车万女仆 —— <b>按类名爬父类链，不引用 TLM 的类型</b>。
     *
     * <p>结果按类缓存（同一个实体类只爬一次）。车万女仆的实体就是
     * {@code com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid}。
     *
     * <p>与 {@code BladeLayerRestorer} 里那份老实现同义；这里收口成一处，
     * 避免"同一件事有两个判定"再次漂掉。
     */
    private static final ClassValue<Boolean> IS_MAID = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                if (MAID_CLASS.equals(c.getName())) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }
    };

    public static boolean isMaid(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        try {
            return IS_MAID.get(entity.getClass());
        } catch (Throwable t) {
            return false;
        }
    }
}
