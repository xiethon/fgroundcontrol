#pragma once

#include "planner_path_search.hpp"
#include "ccpp/geo/geo_types.h"
#include "../types.hpp"

namespace field_path::ccpp {

struct GeopointsWithSpray {
    geo::Point_2 point;
    bool spray_on = false; // 喷洒开关状态
};

//! 基于梯形分解的覆盖规划器：分解 → 生成扫线 → 贪心连接单元 → 输出完整路径
class CoveragePlanner {
public:
    explicit CoveragePlanner(const planner_path_search::Environment &environment);

    //! 执行完整覆盖规划流程，返回有序路径点序列
    std::vector<WaypointWithSpray> getPath();

private:
    const planner_path_search::Environment &_environment;

    bool _validWorkspace(); //!< 判断工作区域是否有效
};

} // namespace field_path::ccpp
