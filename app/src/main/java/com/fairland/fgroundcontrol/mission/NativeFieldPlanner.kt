package com.fairland.fgroundcontrol.mission

/** 米制局部坐标接口，JNI内部转换参考算法的毫米单位；不含飞行操作。 */
object NativeFieldPlanner {
    init { System.loadLibrary("fgc_field") }
    external fun plan(vertices: DoubleArray, spacing: Double, inset: Double): DoubleArray
}
