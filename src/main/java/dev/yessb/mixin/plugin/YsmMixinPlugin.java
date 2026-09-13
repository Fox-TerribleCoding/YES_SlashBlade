package dev.yessb.mixin.plugin;

import dev.yessb.FixConfig;
import dev.yessb.YesSlashBladeFix;
import dev.yessb.compat.YsmBridge;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 靶向 YSM 的那组混入的准入开关。
 *
 * <p>这些混入指向 YSM 的<b>混淆内部结构</b>，跨版本极可能失效，因此设了版本前缀白名单。
 * 但这里遵循"只有明确不支持才拒绝"的原则：
 * <ul>
 *   <li>版本号读不到（多半是尚未就绪）⇒ <b>放行</b>；</li>
 *   <li>版本号明确不匹配白名单 ⇒ 拒绝，并打一条说明性的警告；</li>
 *   <li>配置里对应开关关掉 ⇒ 拒绝。</li>
 * </ul>
 * 刻意<b>不</b>检查"YSM 是否加载"与"拔刀剑是否加载"：本方法执行得极早，那时
 * {@code ModList} 还拿不到东西，把它当成"没装"会让功能永久失效（曾经踩过这个坑）。
 * 而且注入本身是 failure-soft 的（{@code required=false} + {@code defaultRequire=0}），
 * 即使放行了但靶点不存在，也只是这一项不生效，不会影响游戏。
 *
 * <p>本配置里有<b>两个</b>混入，各自的开关不同（见 {@link #shouldApplyMixin}）。
 */
public final class YsmMixinPlugin implements IMixinConfigPlugin {

    /** 负责补「剑技动画 + 主动画变体」的那个混入（与分类器混入分开控制）。 */
    private static final String MIXIN_SLASHBLADE_MODULE = "MixinYsmSlashBladeModule";

    @Override
    public void onLoad(String mixinPackage) {
        // 混入应用时机可能早于模组构造，这里保证配置已就绪
        try {
            FixConfig.ensureLoaded();
        } catch (Throwable ignored) {
            // 配置读不到就按内置默认值（版本前缀 2.6.）继续
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            if (!FixConfig.enabled) {
                return false;
            }
            // 两个混入各自有开关：补剑技/主动画（都默认开）与补物品分类（默认关）。
            // 前两者只补返回值且被 YSM 自己的"模型有没有这条动画"检查兜住，可以放心默认开启；
            // 后者会把拔刀剑的持握/挥动分类从 sword 改成 slashblade，属于行为变更，保持默认关闭。
            if (MIXIN_SLASHBLADE_MODULE.equals(shortName(mixinClassName))) {
                if (!FixConfig.slashbladeComboAnimations && !FixConfig.slashbladeMainStateAnimations) {
                    return false;
                }
            } else if (!FixConfig.slashbladeAnimations) {
                return false;
            }
            // 注意：本方法执行得极早，ModList 往往还没就绪。
            // 因此这里只拒绝"明确不支持"的情况，拿不准一律放行 ——
            // 注入本身是 failure-soft 的（配置里 required=false + defaultRequire=0），
            // 即使 YSM 版本变了、靶点不存在，也只是这一项不生效，不会影响游戏。
            if (YsmBridge.isVersionKnown() && !YsmBridge.isSupportedVersion()) {
                YesSlashBladeFix.LOGGER.warn(
                        "[YES-SB] 当前 YSM 版本 {} 不在白名单前缀 [{}] 内，跳过靶向注入（功能不生效，不影响游戏）。",
                        YsmBridge.version(), FixConfig.ysmVersionPrefix);
                return false;
            }
            // 这里刻意不检查拔刀剑是否加载：注入进去的判断本身在拔刀剑缺席时会安全返回 false，
            // 而早期查询 ModList 并不可靠（曾因此把整个注入误判成"没装 YSM"而全部跳过）。
            return true;
        } catch (Throwable t) {
            // 任何意外都按"不注入"处理
            return false;
        }
    }

    private static String shortName(String mixinClassName) {
        int i = mixinClassName.lastIndexOf('.');
        return i < 0 ? mixinClassName : mixinClassName.substring(i + 1);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
