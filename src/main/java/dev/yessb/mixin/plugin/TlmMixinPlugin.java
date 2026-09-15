package dev.yessb.mixin.plugin;

import dev.yessb.FixConfig;
import dev.yessb.YesSlashBladeFix;
import net.neoforged.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 靶向车万女仆的那组混入的准入开关。
 *
 * <p>这些混入指向 TLM 的内部结构（`GeckoLayerMaidHeld` / `GeckoLayerMaidBackItem`），
 * 或者指向原版 `LivingEntity#swing`（因为 1.21.1 的 `EntityMaid` 已经没有那个覆写了），
 * 跨版本可能失效。
 * 因此策略与 YSM 那组一致：拿不准就放行，只有"明确不支持"才拒绝 ——
 * 注入本身是 failure-soft 的（`required=false` + `defaultRequire=0`），
 * 靶点不存在时只是这一项不生效，不会影响游戏。
 *
 * <p>与 {@link YsmMixinPlugin} 一样，这里<b>绝不缓存</b>模组加载完成之前的查询结果：
 * 混入准入判断发生得极早，那时 {@code ModList} 往往还拿不到东西，
 * 把"问不到"当成"没装"并缓存下来，会让功能永久失效。
 */
public final class TlmMixinPlugin implements IMixinConfigPlugin {

    private static final String MOD_ID = "touhou_little_maid";

    @Override
    public void onLoad(String mixinPackage) {
        try {
            FixConfig.ensureLoaded();
        } catch (Throwable ignored) {
            // 配置读不到就按内置默认值继续
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            if (!FixConfig.enabled || !(FixConfig.maidSlashBlade || FixConfig.maidSlashBladeAttack)) {
                return false;
            }
            // 早期查询拿不到就放行，绝不据此下结论
            ModList list = ModList.get();
            if (list == null) {
                return true;
            }
            return list.isLoaded(MOD_ID);
        } catch (Throwable t) {
            return true;
        }
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
        if (FixConfig.debugLog) {
            YesSlashBladeFix.LOGGER.info("[YES-SB] 注入女仆渲染层：{}", targetClassName);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
