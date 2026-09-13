package dev.yessb.compat;

import dev.yessb.FixConfig;

/**
 * 一次"把 3D 刀身画出来"所需的完整变换。
 *
 * <p>三个上下文（第一人称 / 第三方称主手 / {@code FIXED}）各自独立取参数 ——
 * 早前它们共用同一组 {@code firstPerson*}，导致调好一个必然弄坏另一个。
 *
 * @param scale       缩放
 * @param rotX        绕 X 轴的角度（俯仰）
 * @param rotY        绕 Y 轴的角度（偏航）
 * @param rotZ        绕 Z 轴的角度（翻滚）
 * @param offsetX     位移 X（方块）
 * @param offsetY     位移 Y（方块，正数向上）
 * @param offsetZ     位移 Z（方块）
 * @param flatContext 是否是 {@code FIXED} 这类"平面"上下文：为真时会补上拔刀剑画图标前
 *                    那套固定变换，还会做一次几何修正（见
 *                    {@link SlashBladeBridge#renderBladeModel}）
 */
public record BladeTransform(float scale,
                             float rotX, float rotY, float rotZ,
                             float offsetX, float offsetY, float offsetZ,
                             boolean flatContext) {

    /** 第一人称用的一组（从配置读）。 */
    public static BladeTransform firstPerson() {
        return new BladeTransform(
                (float) FixConfig.firstPersonScale,
                (float) FixConfig.firstPersonRotX,
                (float) FixConfig.firstPersonRotY,
                (float) FixConfig.firstPersonRotZ,
                (float) FixConfig.firstPersonOffsetX,
                (float) FixConfig.firstPersonOffsetY,
                (float) FixConfig.firstPersonOffsetZ,
                false);
    }

    /** 第三方称主手用的一组（从配置读）。 */
    public static BladeTransform hand() {
        return new BladeTransform(
                (float) FixConfig.handBladeScale,
                (float) FixConfig.handBladeRotX,
                (float) FixConfig.handBladeRotY,
                (float) FixConfig.handBladeRotZ,
                (float) FixConfig.handBladeOffsetX,
                (float) FixConfig.handBladeOffsetY,
                (float) FixConfig.handBladeOffsetZ,
                false);
    }

    /** {@code FIXED}（女仆装饰槽、桌面展示等）用的一组（从配置读）。 */
    public static BladeTransform flat() {
        return new BladeTransform(
                (float) FixConfig.flatBladeScale,
                (float) FixConfig.flatBladeRotX,
                (float) FixConfig.flatBladeRotY,
                (float) FixConfig.flatBladeRotZ,
                (float) FixConfig.flatBladeOffsetX,
                (float) FixConfig.flatBladeOffsetY,
                (float) FixConfig.flatBladeOffsetZ,
                true);
    }
}
