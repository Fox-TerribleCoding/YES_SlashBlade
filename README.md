# YES-SB —— YSM × 拔刀剑重锋 兼容修复

**简体中文**（当前）· [English](README_EN.md)

> ## ⚠️ 临时补丁，上游修好即退役
>
> 本模组解决的是 **1.21.1 上 YSM 侧的拔刀剑联动缺失**。已联系 YSM 作者并获答复：
> **「我后面会做的，因为之前做的时候没有 1.21 的拔刀剑」**，并同意本模组**保留到官方正式修复为止**。
> 上游修复发布后，本项目会声明对应功能失效、必要时从 CurseForge 撤下。
> 详见 §七「与上游的关系」。
>
> **本模组已获 YSM 作者同意**就运行时注入进行修复；成品不含任何第三方代码或资源。

**独立的兼容修复模组（客户端）**，解决 Minecraft 1.21.1 / NeoForge 下
**Yes Steve Model（YSM）** 与 **拔刀剑：重锋（SlashBlade:Resharped 2.x）** 的兼容问题，
把 YSM 在 1.20.1 上本来就正常的拔刀剑表现**还回去**。

修复内容共 **四块**：

| # | 内容 | 对应现象 |
|---|---|---|
| ① | **第三人称腰挂刀补偿渲染** | 第三人称的腰挂刀 / 刀鞘完全不显示 |
| ② | **剑技动画触发** | 连招时只播普通挥动，剑技动作不出现 |
| ③ | **主动画的拔刀剑变体** | 持刀时站姿 / 走路动作与普通剑无异 |
| ④ | **车万女仆的拔刀剑** | 女仆手里的刀不显示 / 背槽显示成平面图标 |

设计原则：

- 不修改 YSM 的任何文件
- 不修改拔刀剑的任何文件
- **不包含、不再分发任何第三方的代码或资源**
- 不依赖反射去读写别人的私有结构

**以 [MIT](LICENSE) 协议开源**（第三方归属见 [NOTICE](NOTICE)）。
构建方式见 §九，验证状态与环境指纹见 §八，版本变更见 [CHANGELOG.md](CHANGELOG.md)。

---

## 一、问题现象

| 现象 | 1.20.1 | 1.21.1 |
|---|---|---|
| 第三人称腰挂刀 / 刀鞘 | 正常 | **完全不渲染**（只剩刀光特效） |
| 拔刀剑专属剑技动画 | 正常 | **退化为默认挥动动画** |
| 持刀时的待机 / 行走姿态 | 拔刀剑专属 | 与普通剑相同 |
| 车万女仆持刀 | 正常 | **同样丢失** |
| 女仆背槽（装饰槽）的刀 | 正常 | 显示成**平面图标** |

> 关键对照：同一个 YSM 版本号（2.6.5），1.20.1 与 1.21.1 两个构建的表现天差地别。
> 也就是说，问题出在 **YSM 的 1.21.1 构建**，而不是"拔刀剑 2.x 重构了什么东西"。

---

## 二、根因

### 2.1 第三人称的刀，本来就是"拔刀剑自己画的"

拔刀剑的物品模型 `assets/slashblade/models/item/slashblade.json` 的 `parent` 是
`builtin/entity`，由 `SlashBladeTEISR`（BEWLR）渲染。但它的 `renderBlade()` 对
**所有第三人称上下文直接返回、一个像素都不画** —— 这是刻意设计：

```
第三人称的"刀 + 鞘"不是手持物，而是挂在实体渲染器上的一个 RenderLayer
  └─ LayerMainBlade：用 MMD 的 bladeholder 模型 + 连招动作绘制
     平时：刀插在腰间的鞘里
     连招：按动作把刀从鞘里拔出来挥砍
```

补充一个容易踩的细节：拔刀剑 2.0.7 的 `ClientHandler#addLayers` 会遍历**所有**已注册实体类型，
给**每一个** `LivingEntityRenderer` 都挂上 `LayerMainBlade`：

```java
addPlayerLayer(event, WIDE); addPlayerLayer(event, SLIM);
for (EntityType<?> t : event.getEntityTypes()) addEntityLayer(event, event.getRenderer(t));
// addEntityLayer: if (renderer instanceof LivingEntityRenderer) renderer.addLayer(new LayerMainBlade<>(...))
```

也就是说"腰挂层存不存在"精确等价于"这个实体的渲染器是不是原版 `LivingEntityRenderer`"。
本模组后面所有"谁负责画刀"的判定都建立在这条事实上。

### 2.2 YSM 1.21.1 把这一整层掐掉了

YSM 在 `EntityRenderDispatcher#render` 内部，用一个条件包裹了对
`renderer.render(...)` 的调用：被它接管的实体，**原版渲染器不再执行**。
原版渲染器一停，挂在它上面遍历的 RenderLayer 自然全部停摆 —— 拔刀剑的腰刀层就这样消失了。

（刀光之所以还在，是因为斩击特效是独立实体/独立渲染，不走这条路。）

### 2.3 动画：YSM 的 1.21.1 构建里，拔刀剑兼容是空实现

YSM 支持"条件动画"，其中 `hold_mainhand:slashblade`、`swing:slashblade`、
`use_mainhand:slashblade` 是官方文档明确列出的内置分类，YSM 自带的默认模型里也带着这些动画。

对照两个构建的兼容模块：

| | 1.20.1 构建 | 1.21.1 构建 |
|---|---|---|
| 提到 `slashblade` 的类 | **8 个** | **2 个**（一个空桩 + 一个没人读的标签定义） |
| 引用 `mods.flammpfeil.*` 的类 | **4 个**（编译期直接依赖拔刀剑） | **0 个** |
| 兼容模块本体 | 真实现（还会按拔刀剑版本分流） | **全是 `return false` / `return ""` / `return null`** |

而**调用这套 API 的地方是完好的** —— YSM 自己的持握/挥动解析器里写着：

```java
if (!entity.isSleeping() && isSlashBlade(entity.getMainHandItem())) {
    String name = getAnimationName(ctx);
    if (!name.isBlank()) {
        if (model.hasAnimation(name)) return play(ctx, name, ...);
        return CONTINUE;                    // 模型没有这条动画 ⇒ 到此为止
    }
}
... 普通持握 / 挥动逻辑
```

因为空桩的 `isSlashBlade` 恒为 `false`，**上面整段是死代码** —— 这才是剑技动画不触发的确切位置。

> **关于手持物分类器**：YSM 的分类器读了 13 个内置标签中的 12 个，唯独漏掉拔刀剑。
> 由于 `ItemSlashBlade extends SwordItem`（已核实），拔刀剑会被归到 **`sword`**，
> 而不是官方约定的 **`slashblade`**。这是**另一条**独立路径，本模组默认不动它（见 §5 `slashbladeAnimations`）。

### 2.4 车万女仆：1.21.1 发布版丢了两处分支

车万女仆（TLM）自己实现了女仆的拔刀剑渲染，在 `1.20` 与 `1.21` 分支源码里都还在。
但 **1.21.1 的发布版 jar 里，`compat/slashblade` 整个包都不存在**。

对照源码与发布版的字节码，丢的是这两处**渲染分支**：

**手部**（`GeckoLayerMaidHeld.render`）—— 源码里是：

```java
if (SlashBladeCompat.isSlashBladeItem(mainHandItem)) {
    SlashBladeRender.renderMaidMainhandSlashBlade(entity, geoModel, poseStack, buffer, packedLight, mainHandItem, partialTicks);
} else {
    renderArmWithItem(entity, mainHandItem, geoModel, THIRD_PERSON_RIGHT_HAND, RIGHT, poseStack, buffer, packedLight);
}
```

发布版只剩 `else` 那支。于是拔刀剑掉进 `ItemInHandRenderer` → `ItemRenderer.renderStatic(..., THIRD_PERSON_*)`，
而拔刀剑的 BEWLR 对第三方称上下文**什么都不画** ⇒ 女仆手里空着。

**背槽**（`GeckoLayerMaidBackItem.render`）—— 源码里是：

```java
if (SlashBladeCompat.isSlashBladeItem(stack)) {
    SlashBladeRender.renderGeckoMaidBackSlashBlade(matrixStack, buffer, packedLight, stack);
} else {
    Minecraft.getInstance().getItemRenderer().renderStatic(entity, stack, FIXED, ...);
}
```

发布版同样只剩 `else`。于是装饰槽里的拔刀剑掉进 `FIXED` 上下文，被画成**平面图标**。

> 证据（可复现）：
> 1. `1.21` 分支的 `GeckoLayerMaidHeld.java` 与 `1.20` 分支**逐字节完全相同**（均 5371 字节），
>    且其中 `SlashBladeCompat` / `SlashBladeRender` 的调用**仍在**；
> 2. 扫描发布 jar 的**类内容**（不只是条目名），对 `slashblade` / `SlashBlade` 的引用数为 **0**；
> 3. 反编译发布版的 `GeckoLayerMaidHeld.render`，结构与其 1.20 源码逐条一致
>    （`offhand`/`mainHand` 取值、`geoModel` 判空、`rightHandBones()`/`leftHandBones()` 判空、
>    `RenderFixer.isCarryOnRender` 调用、`pushPose`/`popPose` 全都在），
>    **唯一差别就是少了 `isSlashBladeItem` 那个分支**。

---

## 三、修复内容

一句话：**把该跑而没跑的那一层跑起来，把该被补上的返回值补回去。**

### 3.1 ① 第三人称腰挂层补偿（渲染）

```
EntityRenderDispatcher#render  ──HEAD──▶  记一笔"这次实体渲染开始了"
LivingEntityRenderer#render    ──HEAD──▶  原版跑了？打卡
EntityRenderDispatcher#render  ──RETURN─▶ 没打卡 ⇒ 原版被跳过了
                                            ⇒ 复刻原版 RenderLayer 的调用环境
                                            ⇒ 调用拔刀剑自己的 LayerMainBlade
```

复刻的变换（与原版 `LivingEntityRenderer#render` 遍历 RenderLayer 之前一致）：

```
translate(实体位置 + 渲染偏移)
scale(getScale × thirdPersonScale)
mulPose(Y, 180 - 身体朝向)      ← setupRotations 的常态分支
scale(-1, -1, 1)
translate(0, -1.501, 0)
```

**"谁负责画刀"的判定**（三条路只走一条，否则就是两把刀）：

| 实体 | 由谁绘制 |
|---|---|
| 车万女仆 | TLM 的渲染层（见 §3.4） |
| 渲染器是原版 `LivingEntityRenderer`（含玩家） | 拔刀剑的 `LayerMainBlade`（本节的补偿） |
| 其余（实体换过渲染器） | 本模组的"手持补画"（见 §3.3） |

**为什么用"打卡"而不是去猜是谁接了管**：这样本模组不需要知道 YSM 的任何内部结构，
也不需要写"如果装了 YSM 就……"这种判断。谁掐掉了原版渲染，就由谁来补 —— 兼容性天然更好。

**为什么不会重影**：只要原版渲染器跑过（没有装 YSM、或这个实体不归 YSM 管），
本模组一个像素都不会画，拔刀剑自己那一层已经画好了。

### 3.2 ② 剑技动画触发

YSM 的持握/挥动解析器会按"连招状态"取一个动画名再播放。本模组把空桩缺的**两个返回值**补回去：

1. `isSlashBlade(stack)` —— 让"这是不是拔刀剑"真的成立；
2. `getAnimationName(ctx)` —— 按拔刀剑主手物品的连招状态（`getComboSeq()` + 超时窗口）算出动画名，
   形如 `slashblade:combo_a1` / `slashblade:standby` / `slashblade:judgement_cut`。

规则完全对齐 1.20.1 的实现（包括 `combo_a4_ex → combo_a4ex` 的改名、
以及居合斩的空中变体）。**只补返回值，不含 YSM 的任何实现。**

> **一个重要的边界条件**：如果算出的动画名不在模型包里，本模组会**返回空串**而不是返回该名字。
> 原因是 YSM 解析器拿到"非空但模型没有"的名字时会 `return CONTINUE`，
> **跳过**后面那段普通挥动逻辑，模型会卡在上一帧姿态出不来。

### 3.3 ③ 主动画的拔刀剑变体 + 手持补画

**主动画变体**：手持拔刀剑时，待机 / 走 / 跑 / 跳 / 潜行 / 飞行这些主动画会先问
`slashblade:idle` 之类；模型包里有就播专属动作，没有就**退回原名**。
这正是 1.20.1 那份兼容模块的另一半，同样在 1.21.1 被掏空了。

**手持补画**：对于"没有腰挂层"的实体（换掉了渲染器的那些），
本模组在第三方称主手上下文直接画出 3D 刀身，避免手里空着。
缩放取值有依据：拔刀剑自己有两套尺度 ——
`renderModel` 的 `0.003125` 是刀**躺在刀架/展示框里**的尺寸，
`renderBlade` 画图标的 `0.0095` 才是**拿在手上**的尺度。
本模组取几何折中值 `0.0095 × 218 / 332.7 ≈ 0.0062`。

### 3.4 ④ 车万女仆

按 TLM 1.20/1.21 分支的源码**原样补回**那两处渲染分支（变换数值一个没改）：

| 位置 | 变换（TLM 原值） |
|---|---|
| 手部 | 移到腰位定位组（主手 `LeftWaistLocator` / 副手 `RightWaistLocator`）→ `translate(0,0,-0.7)` → `scale(0.01)` → `rotY(-90)` → `rotZ(180)`；刀鞘常驻，**动作发生后 5 刻内**刀身才转到出鞘姿态 |
| 背槽 | `translate(1.25, -0.25, 0)` → `rotZ(-15)` → `scale(0.01)`，即"斜挂在背上" |

> TLM 源码里主手的刀是画在**左侧腰位**上的（源码注释 `// 主手的刀渲染在左边`），
> 不是画在手里 —— 这是 TLM 的设计，本模组保持一致。

### 3.5 注入的失败保护

四处靶向注入都指向第三方模组的内部结构，因此统一采用：

- 只在「对应模组已加载 **且** YSM 版本命中白名单」时才注入
- 混入配置 `required=false` + `defaultRequire=0`
- 任何一条不满足 → **整体不注入**：表现为功能不生效，**绝不会崩溃**
- 白名单前缀可在配置里改（默认 `2.6.`）

---

## 四、安装

把 `YES_SB-1.0.1.jar` 放进 `.minecraft/mods/`
（版本隔离时是 `versions/<版本名>/mods/`）。

**依赖**（均为可选，缺失时对应部分自动失效，不会崩溃）：

| 模组 | 版本 | 说明 |
|---|---|---|
| [Yes Steve Model](https://modrinth.com/mod/yes-steve-model) | 2.6.x | 补偿渲染只在它存在时才有意义 |
| [拔刀剑：重锋](https://modrinth.com/mod/slashblade-resharped) | 2.x | 渲染还原依赖它公开的 `LayerMainBlade` |
| [车万女仆](https://modrinth.com/mod/touhou-little-maid) | 1.5.x | 补回它的女仆拔刀剑渲染分支 |

启动后日志中应出现（版本与开关状态随实际环境变化）：

```
[YES-SB] 已就绪：YSM 2.6.5-neoforge+mc1.21.1，拔刀剑 已安装。第三人称腰刀补偿=开，靶向动画注入=开。
[YES-SB] 注入女仆渲染层：com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidHeld
```

若未安装 YSM，会输出 `[YES-SB] 未检测到 YSM：原版渲染本就正常，无需补偿。` 并保持惰性。

> **提示**：本模组的许多参数是"手感调参"，改配置**存盘约 2 秒即生效，不需要重启游戏**。

---

## 五、配置项

首次启动生成 `config/yes_sb.properties`。**全部键都支持热重载。**

### 总开关与生效对象

| 键 | 默认 | 作用 |
|---|---|---|
| `enabled` | `true` | 总开关；`false` 时完全惰性（等价于卸载本模组） |
| `affectsPlayers` | `true` | 是否处理玩家 |
| `affectsOtherLivingEntities` | `true` | 是否也处理车万女仆等其它被 YSM 接管的生物 |
| `ysmVersionPrefix` | `2.6.` | 靶向注入允许生效的 YSM 版本前缀 |
| `debugLog` | `false` | 详细日志（排查用；会明显增大日志体积） |

### ① 第三人称腰挂层补偿

| 键 | 默认 | 作用 |
|---|---|---|
| `restoreThirdPerson` | `true` | 还原第三人称的腰挂刀与连招出鞘 |
| `thirdPersonScale` | `0.7` | 补偿缩放；对齐 YSM 模型的整体缩小比例 |
| `refreshModelPose` | `true` | 补画前先把原版模型姿态补算一遍（见下方说明） |
| `waistOffsetX/Y/Z` | `0` | 补偿的额外位移，**单位方块，正数 = 向上 / 向前** |

> `refreshModelPose`：YSM 掐掉的是整个原版 `LivingEntityRenderer.render`，
> 连带里面的 `model.setupAnim(...)` 也没执行；而 `LayerMainBlade` 构造 MMD 刀挂点时正是读这些部位。
> 补上这一步，刀的姿态才是新的。关掉它只影响姿态新鲜度，可用于对照实验。

### ② 手持补画（第三方称主手 / 平面上下文）

| 键 | 默认 | 作用 |
|---|---|---|
| `handBladeInThirdPerson` | `true` | 主手在第三方称画在手上（"没有腰挂层"的实体靠这条） |
| `handBladeRequiresNoWaistLayer` | `true` | 手持补画只对**没有腰挂层**的实体生效；有腰挂层的交回腰挂层，避免两把刀 |
| `handBladeInFlatContext` | `true` | 在 `FIXED` 上下文把平面图标换成 3D 刀身 |
| `handBladeScale` | `0.0062` | 第三方称主手 3D 刀身的缩放 |
| `flatBladeScale` | `0.0062` | `FIXED` 上下文 3D 刀身的缩放 |
| `handBladeRotX/Y/Z` | `0` | 第三方称主手刀身朝向（角度） |
| `handBladeOffsetX/Y/Z` | `0` | 第三方称主手刀身位移（方块） |
| `flatBladeRotX/Y/Z` | `0` | `FIXED` 刀身朝向（角度） |
| `flatBladeOffsetX/Y/Z` | `0` | `FIXED` 刀身位移（方块） |

> `FIXED` 上下文里已经内置了一个**几何修正**：拔刀剑原本画的是平面图标，
> 而图标与 3D 刀身的包围盒中心差着约 117 个模型单位（≈0.72 格）。
> 所以正常情况下 `flatBlade*` 保持 0 即可。
>
> **女仆的背槽不走这条** —— 它由 §3.4 的 TLM 路径负责（在那条路上会被提前接管），
> 所以这两条不会互相抢。`handBladeInFlatContext` 影响的是其它 `FIXED` 场合（如展示框）。

### ③ 第一人称

| 键 | 默认 | 作用 |
|---|---|---|
| `firstPersonMode` | `auto` | `auto` = 原版画得出来就放手、画不出来才兜底 / `off` = 永远交回原版 / `model` = 本模组接管画 3D 刀身 / `icon` = 画平面图标 |
| `firstPersonAsHeldItem` | `true` | 总闸；`false` 时一律交回原版（等价于 `off`） |
| `firstPersonScale` | `0.0062` | `model` 模式的 3D 刀身缩放 |
| `firstPersonRotX/Y/Z` | `0` | `model` 模式的刀身朝向（角度） |
| `firstPersonOffsetX/Y/Z` | `0` | `model` 模式的刀身位移（方块，最外层世界位移） |

> **默认为什么是 `auto` 而不是 `model`。**
> 第一人称**不经过** YSM 接管的 `EntityRenderDispatcher` —— YSM 从来没有掐过它，
> 1.20.1 的第一人称就是拔刀剑自己那套 `BladeFirstPersonRender`（"一整把刀斜跨画面"），
> 1.21.1 上它本来就是好的。本模组的职责是"把被掐掉的还回去"，第一人称不属于这一类，
> 主动接管只会平白引入一组需要调的变换。
>
> `auto` 会先问原版"这一帧你画不画得出来"（条件逐条照抄它的字节码），
> 画得出来就放手，画不出来（渲染器不再是 `RenderLayerParent`、玩家在睡觉、隐藏了 HUD 等）
> 才由本模组兜底，避免第一人称彻底没有刀。
>
> `model` 模式是**把刀当成普通手持物品**画的，位置完全由物品的 display 变换决定 ——
> 而拔刀剑那份 display 变换的位移是 `[-15, 5, -11]`（单位 1/16 格），并不是为这种画法调的。
> 想用 `model` 就必须配 `firstPersonOffset*` 收敛：开 `debugLog` 后日志每 2 秒会打一行
> `手持刀[firstPerson]：… 刀原点(相机空间)=(x,y,z) 视野判定=在画面内/★在画面外`，
> 调到"在画面内"为止即可（`+X` 向右、`+Y` 向上、`-Z` 向前）。

### ④ 拔刀剑动画

| 键 | 默认 | 作用 |
|---|---|---|
| `slashbladeComboAnimations` | `true` | **剑技动画触发**（补回"是不是拔刀剑"与"取动画名"两个返回值） |
| `slashbladeMainStateAnimations` | `true` | **主动画变体**（持刀时 `slashblade:idle/walk/...`，模型包没有则退回原名） |
| `slashbladeAnimations` | `false` | 物品分类器注入（把分类从 `sword` 改成 `slashblade`）。**属行为变更**，默认关闭 |

> `slashbladeAnimations` 为什么默认关：模型包若没有 `swing:slashblade`，
> 原本会用的 `swing:sword` 也会一起失效。剑技动画靠上面两项就够了。
>
> 另注：剑技动画要求模型包在 `ysm.json` 里声明 `slashblade` 动画文件
> （官方文档：不声明就沿用普通剑动画）。

### ⑤ 车万女仆

| 键 | 默认 | 作用 |
|---|---|---|
| `maidSlashBlade` | `true` | 补回女仆的拔刀剑渲染分支（手部 + 背槽） |
| `maidBladeScale` | `0.009` | 女仆刀缩放（TLM 原值 0.01 的 90%） |
| `maidBladeDrawTicks` | `5` | 动作发生后多少刻之内算"刚出鞘"（TLM 原值） |
| `maidBladeDrawDistanceFactor` | `0.007` | 出鞘那一下位移的分母（TLM 原值，照抄以对齐手感） |
| `maidBladeOffsetX/Y/Z` | `0` | 女仆刀额外位移（方块，正数 = 向上 / 向前） |
| `maidBladeRotX/Y/Z` | `0` | 女仆刀额外朝向（角度） |

> `maidBlade*Offset/Rot` 默认全 0，即**与 TLM 完全一致**。只有在女仆实际由另一套模型渲染、
> 骨骼与看到的身体对不上时才需要动。

---

## 六、已知限制 / 尚未对齐

本模组把 1.21.1 上丢失的**渲染与动画触发**补了回来，但下面几条**尚未**做到 1.20.1 的效果，
列出来以免误导：

### 6.1 玩家的刀跟不上 YSM 的动画 —— **未解决**

玩家这条走的是拔刀剑自己的 `LayerMainBlade`（即 §3.1 的补偿），
它的 MMD 刀挂点是**从原版玩家模型的各部位**算出来的；而屏幕上显示的那个角色是
**YSM 的模型在播 YSM 的动画**。

> **两套动画，所以刀永远对不上。** 目前的表现是：**只有"手持非攻击姿态"时刀在手上**，
> 整套攻击流程中刀与模型动作不同步。

`refreshModelPose` 补的是"原版模型有没有被更新过"，让它跟的是**原版**挥砍动作 ——
所以它最多让刀"动起来"，**不可能**与 YSM 的动画同步。

**要彻底对齐，需要把刀绑定到 YSM 模型自身的定位组骨骼上**（1.20.1 的 YSM 就是这么做的）。
这属于**未完成事项**。

> 作者已答复：空桩的原因是**当时（1.21.1）还没有发布拔刀剑，适配还没做** —— 不是有意停用，
> 本项目也已获得作者同意。所以这条不再有"是否该做"的争议，
> 只是**现在不做**：YSM 2.6.5 的类全部混淆、注入后一旦出错是崩游戏而非"没效果"，
> 而当前"固定偏移 + `waistOffset*`"已经够用。
>
> **重新评估的时机有两个**：① YSM 升到带**不混淆正式接口**（`com.elfmcys.ysm.natives`）的版本；
> ② YSM 自己把这份拔刀剑适配补上 —— 届时本模组直接退役对应部分即可。

### 6.2 女仆的**攻击逻辑**未恢复 —— 未解决

TLM 的 `compat/slashblade` 包里除了渲染，还有一处**行为**代码
`SlashBladeCompat.swingSlashBlade`（让女仆真的打出拔刀剑斩击并盖 `lastActionTime` 时间戳）。
这也随同一个包在 1.21.1 丢失，**本模组未恢复**。

后果：女仆攻击时用的是普通动画，而不是拔刀剑剑技动画。
（剑技动画的**触发钩子**是好的，缺的是"进入连招状态"这一步。）

### 6.3 第一人称 —— 已定案（默认不再接管）

第一人称**不经过** YSM 接管的 `EntityRenderDispatcher`，YSM 从来没有掐过它 ——
1.20.1 的样子就是拔刀剑自己那套 `BladeFirstPersonRender`（一整把刀斜跨画面），
1.21.1 上它本来也是好的。所以默认为 `firstPersonMode=auto`：原版画得出来就放手，
画不出来才由本模组兜底。

1.0.1 修掉了 `model` 模式下"刀被推出视野"的坐标 bug（漏了一次 `translate(0.5,0.5,0.5)`），
以及 `*Offset*` 被缩放系数乘两遍导致调不动的问题。想改用 `model` 模式，
按日志里的"刀原点(相机空间)"配 `firstPersonOffset*` 收敛即可。

### 6.4 复刻变换的覆盖范围

§3.1 复刻的是 `setupRotations` 的**常态分支**。死亡旋转、游泳等特殊姿态下原版还有额外分支，
暂未逐条复刻，可能有轻微偏差（**未验证**）。

### 6.5 靶向注入的版本敏感性

四处靶向注入都依赖第三方模组的内部结构（YSM 的类经过混淆）。
**YSM 更新后这些靶点极可能失效**，届时对应功能会自动跳过（表现为功能不生效，不会崩溃），
需要按新版本更新白名单或靶点。

---

## 七、与上游的关系

本模组的定位是**临时补丁**，不是替代品 —— **上游修复后即退役**。
本模组作者已就 YSM 与 TLM 两侧的问题**主动联系上游作者**，并把这段沟通的记录留档如下
（截图原话，逐句对应）。

### 7.1 已向 YSM 作者说明的内容

联系内容（要点）：本模组是为 MC 1.21.1 / NeoForge 下 YSM × 拔刀剑的兼容问题所做的独立修复模组；
**参考了 TLM 与 YSM 的开源部分，仿照 1.20.1 的写法**自写；**没有尝试反编译加密的代码**，
但在排查根因的过程中**对部分代码做了反编译**，对此表示歉意；**模组内不含任何第三方源码**；
仓库地址 `github.com/Fox-TerribleCoding/YES_SlashBlade`，遵循 MIT；本模组只作补丁存在，
**若日后 YSM 与 TLM 有更新修复，会做好声明并标注失效，或从 CurseForge 上撤下**。

### 7.2 作者的答复（原话）

| 提问 | 作者答复 |
|---|---|
| （说明与致歉） | 「好的」「非常感谢你」 |
| YSM 与 TLM 在这方面的修复安排？ | **「我后面会做的，因为之前做的时候没有 1.21 的拔刀剑」** |
| 「关于我写的模组 —— 目前是先保留，直到官方正式修复吗？」 | **「可以的」** |

由此确认两件事：

1. **空桩不是"有意停用"，而是当时还没做适配** —— YSM 2.6.5 开发时 1.21.1 上还没有拔刀剑，
   那份联动没有可适配的对象。**上游的修复是可预期的（作者明确表示"后面会做"）。**
2. **本模组获得作者认可**：作者同意本模组**保留到官方正式修复为止**。

### 7.3 本模组的退役承诺

- 上游（YSM / TLM）发布对应的正式修复后，本模组会**做出声明并标注对应功能失效**，
  必要时**从 CurseForge 撤下**；
- 四处靶向注入全部带**版本白名单 + failure-soft**：上游一改靶点，对应功能**自动跳过**
  （表现为不生效，不会崩溃），退役不需要紧急改代码；
- 具体退役顺序与影响面见 [进展与结论.md](进展与结论.md) 的 **§9.2**。

### 7.4 车万女仆（TLM）

§2.4 那两处**是发布版的移植遗漏**：源码在、发布 jar 里没有，可确证：
TLM 仓库的 `1.21` 分支里 `compat/slashblade/` 三个文件都在，且 `GeckoLayerMaidHeld.java`
与 `1.20` 分支**逐字节完全相同**，但发布 jar 的类内容里对 `slashblade` 的引用数为 **0**。
本模组只是把它补回去，变换数值一个没改。**上游修复后，本模组的女仆部分即可退役。**

### 7.5 Yes Steve Model（YSM）

见 §7.2：**作者表示会做这份适配**，本模组保留至其正式修复。

**这意味着什么**：上游一旦补上适配，本模组的 ②③ 两部分即可退役；
而 §6.1 那条"绑定骨骼"的方案**不再因授权/意图问题被否决** ——
它现在只是因为技术性价比不足而暂缓（全混淆、错了会崩游戏、且当前固定偏移已够用），
重新评估的时机见 §6.1。

---

## 八、验证状态与本模组的边界

### 8.1 已核验的运行环境（指纹）

本模组的注入点全部指向第三方模组的**内部结构**，因此"对哪个构建验证过"必须写清楚：

| 组件 | 文件 | 大小 | SHA256（前 16 位） |
|---|---|---|---|
| YSM | `ysm-2.6.5-neoforge+mc1.21.1-release.jar` | 63,463,229 B | `B285C73D4EC010D9` |
| 拔刀剑：重锋 | `SlashBladeResharped-2.0.7-1.21.1.jar` | 3,886,797 B | `C67653EC0D7E08A7` |
| 车万女仆 | `touhoulittlemaid-1.5.3-neoforge+mc1.21.1.jar` | 24,408,776 B | `F6DB04195820C850` |
| 本模组 | `YES_SB-1.0.1.jar` | 130,068 B | `56232F6BCD04CE32` |

环境：NeoForge 21.1.x / Minecraft 1.21.1。**换版本请先看 §6.5。**

### 8.2 核验是怎么做的（可复现）

1. **对照公开源码**：YSM 的下一代重构版源码已在 GitHub 以 Apache-2.0 公开
   （`YesSteveModel/YesSteveModel`，分支 `dev/1.20`，`3.0-dev-forge+mc1.20.1`）。
   本项目把 `client/compat/slashblade/` 与 `ModItemTags` 逐条比对本模组的实现 ——
   **剑技动画名的 4 条规则（含 `standby` 超时 `-553`、`combo_a4_ex → combo_a4ex`、空中次元斩两变体）
   与主动画变体语义完全一致**。
   > 注意：该仓库是**下一代重构版**，**不是**本项目注入的 2.6.5 构建（后者混淆且无对应源码），
   > 所以它只能作**语义对照**，不能替代注入靶点。
2. **反汇编本模组自己的产物**，抽出全部第三方符号，回到目标 jar 里逐个查存在性：
   **类型 22/22、成员 34/34 全部命中**，没有一处失配。
3. **复核靶向事实**：2.6.5 jar 共 933 个类；引用 `mods.flammpfeil` 的类 **0 个**；
   提到 `slashblade` 的类**只有 2 个**（空桩 + 13 个标签的持有者）；
   `builtin/default/animations/slashblade.animation.json` 为 **1,652,964 字节**。
   ⇒ 这些与 §二 的根因分析逐条吻合。

### 8.3 本模组**不做**什么

- **不恢复女仆的攻击逻辑**（§6.2）。原因不是"做不到"，而是：
  这属于**服务端 AI 行为**，需要让本模组变成双端模组，并处理伤害结算、连招状态推进等一串新问题，
  收益与风险不成比例；且**已有专门的模组负责女仆的拔刀剑使用**（见下），本模组不与其重叠。
- **不接管第一人称**（§6.3）—— 那是拔刀剑自己的行为，本来就没被 YSM 掐掉。
- **不绑定 YSM 模型骨骼**（§6.1）—— 原因与技术代价见该节。
- 因此本模组**只解决"渲染被掐掉"与"动画触发被掏空"这两类问题**，其余保持上游原样。

> **关于女仆的剑技动画**：本模组只负责"**触发钩子可用**"（补回空桩的返回值）；
> 至于女仆**如何打出拔刀剑斩击、如何进入连招状态**，请交给专门做这件事的模组
> —— [车万女仆：真正的力量（TLM: True POWER）](https://modrinth.com/mod/true-power-of-maid)。
> 两者不冲突：那条路走通后，本模组补的触发钩子会让动画正常播出来。

---

## 九、从源码构建

无需 Gradle、无需联网 —— 只要本机存在游戏目录
（自带官方映射的 Minecraft、NeoForge、Sponge Mixin，以及 `mods/` 下的拔刀剑、YSM、车万女仆）即可：

```powershell
.\build.ps1 -Clean                          # 默认目标版本目录为 T
.\build.ps1 -Clean -GameVersion X           # 指定其它版本目录
.\build.ps1 -Clean -Install                 # 构建完直接装进 mods\
```

### 工程结构

```
src/main/java/dev/yessb/                         (18 个 Java 文件)
├── YesSlashBladeFix.java                 # 模组入口；注册客户端初始化 + 配置热重载
├── FixConfig.java                        # 纯 properties 配置；ensureLoaded() / reloadIfChanged()
├── render/
│   ├── VanillaRenderTracker.java         # "原版渲染跑没跑"的打卡栈（线程本地，支持嵌套）
│   └── BladeLayerRestorer.java           # 补偿渲染的判定与入口
├── compat/                               # 与第三方之间的隔离层
│   ├── SlashBladeBridge.java             # 唯一接触拔刀剑类型的隔离层（复刻变换、连招动画名也在这里）
│   ├── BladeTransform.java               # 一个上下文对应的完整变换（缩放/朝向/位移）
│   ├── YsmBridge.java                    # 只查 YSM 的 modid 与版本号，不引用其任何类型
│   ├── YsmSlashBladeModule.java          # 与 YSM 拔刀剑模块的隔离层（补返回值）
│   └── TlmMaidBridge.java                # 与车万女仆的隔离层（手部 / 背槽 的刀）
└── mixin/
    ├── MixinEntityRenderDispatcher.java  # 进场 / 收尾
    ├── MixinLivingEntityRenderer.java    # 打卡
    ├── plugin/YsmMixinPlugin.java        # YSM 靶向注入闸门（版本白名单）
    ├── plugin/TlmMixinPlugin.java        # 车万女仆靶向注入闸门
    ├── sb/MixinSlashBladeTEISR.java      # 拔刀剑 BEWLR：第一人称 / 手持 / 平面上下文
    ├── ysm/MixinYsmSlashBladeModule.java # 补空桩的返回值（isSlashBlade / 剑技动画名 / 主动画变体）
    ├── ysm/MixinYsmHeldItemClassifier.java # 补回 slashblade 分类（默认关闭）
    ├── tlm/MixinTlmMaidHeldLayer.java    # 补回女仆手部的拔刀剑分支
    └── tlm/MixinTlmMaidBackItemLayer.java# 补回女仆背槽的拔刀剑分支

src/main/resources/
├── META-INF/neoforge.mods.toml
├── icon.png                                        # 模组图标，由 icon-src/ 生成
└── yessb.mixins.json / yessb.slashblade.mixins.json /
    yessb.ysm.mixins.json / yessb.tlm.mixins.json    (4 个混入配置)

icon-src/make_icon.ps1                              # 图标生成脚本（纯 GDI+，无外部素材）
icon/                                               # 生成结果：1024 / 512 / 256 / 128
```

### 给贡献者的两点提示

1. **`build.ps1` 必须保持纯 ASCII** —— Windows PowerShell 会按 ANSI 读取它，中文注释会导致解析失败。
2. **构建脚本的安装是"原子替换"**：先写临时文件再 `Move-Item` 就位。
   并且**检测到游戏在运行时会拒绝安装**。这是刻意的：早期版本用 `Copy-Item` 覆盖，
   而它会先截断目标文件再写入 —— 游戏若正好在那一刻启动，会读到残缺的 jar，
   表现为 `NoClassDefFoundError`。请勿改回 `Copy-Item`。

---

## 十、许可与第三方关系

本项目以 [MIT](LICENSE) 协议开源 —— 可自由使用、修改、分发，仅需保留署名。
第三方归属的完整声明见 **[NOTICE](NOTICE)**。

> **关于逆向与授权（一句话）**：本模组**不反编译、不复制任何加密/混淆代码**，
> 只在运行时以 Mixin 补充返回值；为定位问题做过**局部字节码层面的分析**，此事已向 YSM 作者说明并获其理解，
> 运行时注入**已获作者同意**（完整对话记录见 §七）。

| 项目 | 协议 | 本项目的关系 |
|---|---|---|
| [SlashBlade:Resharped](https://github.com/Quarkrus/SlashBlade_Resharped) | 代码 MIT / 美术 All Rights Reserved | **不含、不复刻其代码**：仅在运行时调用其公开类（`LayerMainBlade`、`BladeRenderState` 等，编译期依赖、运行时不打包）；不引用、不再分发其任何美术资源 |
| [车万女仆](https://github.com/TartaricAcid/TouhouLittleMaid) | **MIT**（`Copyright (c) 2019-2024 tartaric_acid, for the code part`） | **移植了其 `SlashBladeRender` 的变换逻辑**（数值原样照抄），按 MIT 要求保留其版权声明；仅调用其公开 API。注意其 MIT **只覆盖 code part**，美术资源不在内 —— 本项目不使用其任何美术资源 |
| [Yes Steve Model](https://modrinth.com/mod/yes-steve-model) | 发布版 **All Rights Reserved** | **不复制、不内嵌、不再分发其任何代码或资源**；为定位兼容性问题做过字节码层面的分析；仅在运行时以 Mixin 补返回值，且带版本白名单与 failure-soft。**注意：本项目注入的是 1.21.1 发布版构建，它是混淆的、且没有对应源码** |
| [Yes Steve Model 公开源码仓库](https://github.com/YesSteveModel/YesSteveModel)（分支 `dev/1.20`） | **Apache-2.0** | **参考了其 `client/compat/slashblade` 的语义**（动画名规则、主动画变体、归一化规则）。**但它是下一代重构版（`3.0-dev` / Forge / 1.20.1），不是本项目注入的那个 2.6.5 构建** —— 只用于语义对照，不替代注入靶点 |
| [YesSteveModel-Native](https://github.com/YesSteveModel/YesSteveModel-Native) | Apache-2.0 | 仅用于**阅读**其架构以确认"骨骼与渲染的组织方式"，未使用其任何代码 |
| Minecraft / NeoForge / Sponge Mixin | 各自协议 | 通过官方公开 API 与标准 Mixin 机制接入 |

> **本模组不包含、不再分发上述任何项目的代码或美术资源。**
> 所有第三方内容都只在**运行时**通过其公开 API 访问。
>
> MIT 只覆盖本项目自己的代码。若你要基于本项目二次开发并分发，
> 上述各方的权利与义务仍需你自行确认。

### 关于上游授权

本模组对 **Yes Steve Model** 的运行时注入**已获其作者同意**：
YSM 侧拔刀剑模块之所以是空实现，是因为 2.6.5 开发时 **1.21.1 尚未发布拔刀剑**，
属于"还没做适配"，而非有意停用（详见 §七）。

### 致谢

- **拔刀剑：重锋** 作者 —— 提供了干净、可读、MIT 的渲染层实现，
  使得"把这一层还回去"成为可能。
- **车万女仆** 作者 —— 其拔刀剑兼容实现（渲染变换与出鞘判定）是本模组女仆部分的直接依据。
- **YSM** 团队 —— 其 1.20.1 构建完整实现了拔刀剑兼容，本项目的目标即是把那份表现对齐到 1.21.1。
