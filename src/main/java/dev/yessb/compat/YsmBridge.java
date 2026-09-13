package dev.yessb.compat;

import dev.yessb.FixConfig;
import net.neoforged.fml.ModList;

/**
 * 与 YSM 之间的隔离层。
 *
 * <p>这里刻意<b>不</b>引用 YSM 的任何类型：只查 modid 与版本号。
 * 靶向注入的目标类名以字符串形式写在 mixin 注解里，由混入插件按版本放行。
 *
 * <h2>为什么要区分"还没就绪"和"确实没装"</h2>
 * 混入准入判断发生得非常早 —— 早于模组加载完成，那时 {@code ModList.get()} 还拿不到东西。
 * 如果把这个"问不到"当成"没装"并缓存下来，就会出现：明明装了 YSM，本模组却永久性地
 * 判定它不存在，于是所有功能静默失效。因此这里的规则是：
 * <ul>
 *   <li>查询失败 ⇒ 返回"未就绪"，<b>不缓存</b>；</li>
 *   <li>只有在模组列表可用、且能明确读到 YSM 的版本号时，结论才被缓存；</li>
 *   <li>版本未知时按"放行"处理 —— 注入本身是 failure-soft 的（required=false）。</li>
 * </ul>
 */
public final class YsmBridge {

    public static final String MOD_ID = "yes_steve_model";

    /** 只有在拿到可信答案时才写入；null 表示尚未确定。 */
    private static volatile Boolean loaded;

    private static volatile String version;

    private YsmBridge() {
    }

    /**
     * 在模组加载完成之后调用，给出可信结论并缓存。
     * 早于此时机的任何查询都只是"尽力而为"，不影响最终结果。
     */
    public static void resolve() {
        try {
            ModList list = ModList.get();
            if (list == null) {
                return;
            }
            list.getModContainerById(MOD_ID).ifPresent(container -> {
                loaded = Boolean.TRUE;
                try {
                    version = container.getModInfo().getVersion().toString();
                } catch (Throwable ignored) {
                    version = "";
                }
            });
            if (loaded == null) {
                // 能读到模组列表却找不到 YSM：这才是可信的"确实没装"
                loaded = Boolean.FALSE;
            }
        } catch (Throwable t) {
            // 还没就绪：保持未确定
        }
    }

    public static boolean isLoaded() {
        Boolean cached = loaded;
        if (cached != null) {
            return cached;
        }
        // 未确定时做一次实时查询，但绝不把这个结果固化下来
        try {
            ModList list = ModList.get();
            return list != null && list.isLoaded(MOD_ID);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 取 YSM 的版本号字符串；还没拿到就实时查一次，仍然拿不到返回空串。 */
    public static String version() {
        String cached = version;
        if (cached != null) {
            return cached;
        }
        try {
            ModList list = ModList.get();
            if (list != null) {
                String now = list.getModContainerById(MOD_ID)
                        .map(c -> {
                            try {
                                return c.getModInfo().getVersion().toString();
                            } catch (Throwable t) {
                                return "";
                            }
                        })
                        .orElse("");
                if (!now.isEmpty()) {
                    version = now;
                }
                return now;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * 当前 YSM 版本是否允许靶向注入。
     *
     * <p>靶向注入依赖 YSM 的内部结构（混淆类名与方法描述符），跨版本极可能失效，
     * 因此设了版本前缀白名单。但这里遵循"只有明确不支持才拒绝"的原则：
     * 版本号读不到时返回 true，让注入照常尝试 —— 它本身挂了也不会影响游戏。
     */
    public static boolean isSupportedVersion() {
        String prefix = FixConfig.ysmVersionPrefix == null ? "" : FixConfig.ysmVersionPrefix.trim();
        if (prefix.isEmpty()) {
            return true;
        }
        String current = version();
        if (current.isEmpty()) {
            // 版本未知（多半是尚未就绪）：不做判断，放行
            return true;
        }
        return current.startsWith(prefix);
    }

    /** 版本号是否已确定 —— 用于日志区分"确实不支持"与"暂时未知"。 */
    public static boolean isVersionKnown() {
        return !version().isEmpty();
    }
}
