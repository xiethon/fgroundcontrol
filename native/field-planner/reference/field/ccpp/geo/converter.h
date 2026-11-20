#pragma once

#include "geo_types.h"
#include "geometry.hpp"

namespace polygon_coverage_planning {

geo::Polygon_2 bufferWorkSpace(geometry::Polygon2d spray_zone,
                               double spray_zone_offset); //! 设置喷洒区域 — 使用 Clipper2 offset 内缩
std::vector<geo::Polygon_2> mergeOverlappingPolygon(
    const std::vector<geo::Polygon_2> &no_fly_zones); //! 合并重叠多边形 — 使用 geo::do_intersect 和 Polygon_set
std::vector<geo::Polygon_2> bufferNoFlyZones(const geometry::MultiPolygon2d &no_fly_zones,
                                             double no_fly_zone_offset); //! 设置禁飞

} // namespace polygon_coverage_planning