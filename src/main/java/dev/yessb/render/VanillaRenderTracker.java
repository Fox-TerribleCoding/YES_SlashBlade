package dev.yessb.render;

import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;

/**
 * 记录"这一次实体渲染，原版渲染器到底跑没跑"，并避免嵌套重入导致的重复补偿。
 *
 * <h2>为什么需要它</h2>
 * 补偿渲染只有在原版被跳过时才允许发生，否则会和拔刀剑自己已经画好的那一层重叠（两个刀鞘）。
 * 而"被跳过"是 YSM 在 {@code EntityRenderDispatcher#render} 内部用一个条件包裹调用实现的，
 * 不会留下任何标记。于是我们反向记录：原版 {@code LivingEntityRenderer#render} 一旦执行就打卡，
 * 收尾时没打卡就说明被跳过了。
 *
 * <h2>为什么还要记嵌套</h2>
 * 同一个实体可能在一次渲染里被调度器重复进入（YSM 内部会复用这个入口，
 * 例如左上角的纸娃娃就是一次独立的调度器调用）。这些调用若被嵌套，
 * 就会各自补画一次 —— 结果是两把刀。因此：<b>只有最外层的那一帧负责补画</b>。
 *
 * <p>注意"纸娃娃"与"世界里的角色"并不是嵌套关系：它们分别是两次独立的最外层调用，
 * 因此两边都会各自补画，这正是想要的效果（两者都相当于第三人称）。
 *
 * <p>用线程本地栈而不是布尔标志，是为了正确处理嵌套；
 * 每个渲染线程各有一份，不需要加锁。
 */
public final class VanillaRenderTracker {

    private static final ThreadLocal<ArrayDeque<Frame>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private static final class Frame {
        final Entity entity;
        /** 是否已有同实体的外层帧 —— 说明这次是重入，不该由它补画。 */
        final boolean nested;
        /** 该实体的渲染器是不是原版 LivingEntityRenderer（⇒ 拔刀剑已经把腰挂层挂上去了）。 */
        final boolean hasWaistLayer;
        boolean vanillaRan;

        Frame(Entity entity, boolean nested, boolean hasWaistLayer) {
            this.entity = entity;
            this.nested = nested;
            this.hasWaistLayer = hasWaistLayer;
        }
    }

    private VanillaRenderTracker() {
    }

    /** 实体渲染开始；{@code hasWaistLayer} 表示拔刀剑是否已给这个实体的渲染器挂了腰挂层。 */
    public static void enter(Entity entity, boolean hasWaistLayer) {
        ArrayDeque<Frame> stack = STACK.get();
        boolean nested = false;
        for (Frame frame : stack) {
            if (frame.entity == entity) {
                nested = true;
                break;
            }
        }
        stack.push(new Frame(entity, nested, hasWaistLayer));
    }

    /**
     * 当前正在渲染的实体是否带拔刀剑的腰挂层。
     *
     * <p>拔刀剑 2.0.7 的 {@code ClientHandler#addLayers} 会遍历所有已注册实体类型，
     * 给<b>每一个</b> {@code LivingEntityRenderer} 都加上 {@code LayerMainBlade}。
     * 因此"这个实体的刀该由腰挂层画"等价于"它的渲染器是原版 LivingEntityRenderer"。
     *
     * <p>没有实体帧在栈上（例如第一人称的手持物渲染）时返回 false。
     */
    public static boolean currentHasWaistLayer() {
        Frame top = STACK.get().peek();
        return top != null && top.hasWaistLayer;
    }

    /** 原版渲染器跑了 —— 打卡。 */
    public static void markVanillaRan(Entity entity) {
        ArrayDeque<Frame> stack = STACK.get();
        Frame top = stack.peek();
        if (top != null && top.entity == entity) {
            top.vanillaRan = true;
        }
    }

    /**
     * 实体渲染结束。
     *
     * @return true 表示这次应当补画（原版没跑，且不是嵌套重入）
     */
    public static boolean shouldRestore() {
        ArrayDeque<Frame> stack = STACK.get();
        if (stack.isEmpty()) {
            // 栈不平衡时保守处理：不画，绝不重复渲染
            return false;
        }
        Frame frame = stack.pop();
        return !frame.vanillaRan && !frame.nested;
    }
}
