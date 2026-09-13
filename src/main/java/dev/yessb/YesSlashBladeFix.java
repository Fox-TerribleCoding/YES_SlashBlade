package dev.yessb;

import dev.yessb.compat.SlashBladeBridge;
import dev.yessb.compat.YsmBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * YES-SB —— YSM × 拔刀剑重锋 兼容修复（客户端）。
 *
 * <p>本模组只做一类事：<b>把 1.20.1 上本来有、1.21.1 上丢了的表现还回去</b>。
 * 不修改任何第三方文件，不含任何第三方代码或资源。
 *
 * <h2>四块功能</h2>
 * <ol>
 *   <li><b>第三人称腰挂刀</b>（{@code restoreThirdPerson}）：
 *       YSM 在 1.21.1 的原生重写里会跳过被它接管的实体的原版渲染器调用，
 *       而拔刀剑的"腰挂刀 + 连招出鞘"正是由挂在实体渲染器上的 {@code LayerMainBlade}
 *       绘制的，于是整层被一起掐掉。这里在实体渲染收尾处，用与原版一致的变换把它补画回来。</li>
 *
 *   <li><b>剑技动画</b>（{@code slashbladeComboAnimations}）：
 *       YSM 2.6.5 的拔刀剑联动模块是一组空实现桩，导致它解析器里那段
 *       「手持拔刀剑 → 取剑技动画名 → 播」成为死代码。这里补回那个返回值，
 *       让 {@code slashblade:combo_a1} / {@code standby} / {@code judgement_cut} 等重新被选中。</li>
 *
 *   <li><b>主动画的拔刀剑变体</b>（{@code slashbladeMainStateAnimations}）：
 *       同一个桩的另一半 —— 持刀时待机/走/跑/跳/潜行会先问 {@code slashblade:idle} 之类，
 *       模型包里有就用专属动作，没有就退回原名。</li>
 *
 *   <li><b>车万女仆</b>（{@code maidSlashBlade}）：
 *       TLM 1.21.1 的发布版把它的 {@code compat/slashblade} 整个包丢了，
 *       连带手部渲染、背槽渲染、拔刀剑斩击逻辑三处。这里按 TLM 原样补回前两处渲染。</li>
 * </ol>
 *
 * <p>所有注入都是 failure-soft 的：靶点不存在、版本不匹配、任意一步抛异常，
 * 都只表现为"这一项不生效"，不会影响游戏，更不会崩溃。
 */
@Mod(YesSlashBladeFix.MOD_ID)
public final class YesSlashBladeFix {

    public static final String MOD_ID = "yes_sb";
    public static final Logger LOGGER = LoggerFactory.getLogger("YES-SB");

    /** 车万女仆的 modid；只用于启动日志与状态判断，不引用其任何类型。 */
    private static final String TLM_MOD_ID = "touhou_little_maid";

    public YesSlashBladeFix(IEventBus modBus, ModContainer container) {
        FixConfig.ensureLoaded();

        modBus.addListener(this::onClientSetup);
        // 配置热重载：改完配置存盘即生效，方便现场调参（约每 tick 检查一次时间戳，开销可忽略）
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> FixConfig.reloadIfChanged());
        // 注意：此处不要查 YSM。模组加载尚未完成，那时查不到任何东西。
        // 真正可信的检测放在客户端初始化阶段（见 onClientSetup）。
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        YsmBridge.resolve();

        if (!FixConfig.enabled) {
            LOGGER.info("[YES-SB] 配置中已关闭，本模组保持惰性。");
            return;
        }

        boolean blade = SlashBladeBridge.isAvailable();
        if (!blade) {
            LOGGER.info("[YES-SB] 未检测到拔刀剑：所有还原功能都不会生效。");
        }
        if (!YsmBridge.isLoaded()) {
            LOGGER.info("[YES-SB] 未检测到 YSM：原版渲染本就正常，无需补偿。");
        } else {
            LOGGER.info("[YES-SB] 已就绪：YSM {}，拔刀剑 {}，车万女仆 {}。"
                            + "第三人称腰刀补偿={}，剑技动画={}，主动画变体={}，女仆拔刀剑={}，靶向注入版本白名单={}",
                    YsmBridge.version(),
                    blade ? "已安装" : "未安装",
                    isTlmLoaded() ? "已安装" : "未安装",
                    FixConfig.restoreThirdPerson ? "开" : "关",
                    FixConfig.slashbladeComboAnimations ? "开" : "关",
                    FixConfig.slashbladeMainStateAnimations ? "开" : "关",
                    FixConfig.maidSlashBlade ? "开" : "关",
                    YsmBridge.isSupportedVersion() ? "命中" : "未命中（靶向注入已跳过）");
        }
    }

    /** 车万女仆在不在。只查 modid，不引用其类型。 */
    private static boolean isTlmLoaded() {
        try {
            ModList list = ModList.get();
            return list != null && list.isLoaded(TLM_MOD_ID);
        } catch (Throwable t) {
            return false;
        }
    }
}
