#pragma once

#include "geometry.hpp"
#include "geo/geo_types.h"

#include <vector>

namespace planner_path_search {

using geometry::Point2d;

//! 路径搜索环境配置：作业区域、障碍物区域、起点、作业角度、线间距
class Environment {
public:
    void setWorkspace(const geo::Polygon_2 &workspace);
    void setBlockedZones(const std::vector<geo::Polygon_2> &blocked_zones);
    void setStartPoint(const Point2d &start_point);
    void setWorkAngle(double angle);
    void setLineSpacing(double spacing);

    const geo::Polygon_2 &workspace() const;
    const std::vector<geo::Polygon_2> &blockedZones() const;
    const Point2d &startPoint() const;
    double workAngle() const;
    double lineSpacing() const;

private:
    geo::Polygon_2 _workspace;
    std::vector<geo::Polygon_2> _blocked_zones;
    Point2d _start_point;
    double _work_angle = 0.0;
    double _line_spacing = 7500.0;
};

} // namespace planner_path_search
