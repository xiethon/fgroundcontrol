#include <jni.h>
#include <exception>
#include "planning_bridge.hpp"

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_fairland_fgroundcontrol_mission_NativeFieldPlanner_plan(
    JNIEnv* env, jobject, jdoubleArray vertices, jdouble spacing, jdouble inset) {
    try {
        if (!vertices) throw std::invalid_argument("Missing boundary");
        const auto count = env->GetArrayLength(vertices);
        if (count < 6 || count % 2 || count > 256) throw std::invalid_argument("Invalid boundary size");
        std::vector<double> data(count);
        env->GetDoubleArrayRegion(vertices, 0, count, data.data());
        if (env->ExceptionCheck()) return nullptr;
        const auto result = fgc::planField(data, spacing, inset);
        std::vector<double> output{double(result.error_code), result.total_distance, result.total_area};
        if (result.ok()) for (const auto& item : result.items) {
            output.push_back(item.coordinate.get<0>() / 1000);
            output.push_back(item.coordinate.get<1>() / 1000);
        }
        auto array = env->NewDoubleArray(output.size());
        if (array) env->SetDoubleArrayRegion(array, 0, output.size(), output.data());
        return array;
    } catch (const std::invalid_argument& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), error.what());
    } catch (const std::exception& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    } catch (...) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Native planner failed");
    }
    return nullptr;
}
