#include "converter.h"

#include "geometry.hpp"
#include "geo/geo_types.h"

#include <iostream>
#include <algorithm>
#include <vector>

namespace polygon_coverage_planning {

//! 设置喷洒区域 — 使用 Clipper2 offset 内缩
geo::Polygon_2 bufferWorkSpace(geometry::Polygon2d spray_zone, double spray_zone_offset) {
    geo::Polygon_2 converted_polygon;

    for (auto &point : spray_zone.outer()) {
        converted_polygon.push_back(geo::Point_2(point.get<0>(), point.get<1>()));
    }

    // 去除多边形重复顶点
    {
        auto &v = converted_polygon.container();
        auto it = std::unique(v.begin(), v.end(),
                              [](const geo::Point_2 &a, const geo::Point_2 &b) { return geo::approx_equal(a, b); });
        v.erase(it, v.end());
        // 如果首尾重合，移除尾顶点
        if (v.size() > 1 && geo::approx_equal(v.front(), v.back())) {
            v.pop_back();
        }
    }
    // 检查外边界是否是逆时针（CCW）
    if (converted_polygon.is_clockwise_oriented()) {
        converted_polygon.reverse_orientation();
    }

    // 使用 Clipper2 offset 内缩
    geo::Polygon_2 buffered = converted_polygon.offset(spray_zone_offset);

    if (!buffered.empty() && buffered.size() >= 3) {
        return buffered;
    } else {
        std::cout << "内缩失败" << std::endl;
        return geo::Polygon_2();
    }
}

//! 合并重叠多边形 — 使用 geo::do_intersect 和 Polygon_set
std::vector<geo::Polygon_2> mergeOverlappingPolygon(const std::vector<geo::Polygon_2> &no_fly_zones) {
    std::vector<geo::Polygon_2> result;
    if (no_fly_zones.empty()) {
        return result;
    }

    if (no_fly_zones.size() == 1) {
        auto poly = no_fly_zones[0];
        // 统一输出为 CW 方向（与原始行为一致）
        if (!poly.is_clockwise_oriented()) {
            poly.reverse_orientation();
        }
        result.push_back(poly);
        return result;
    }

    // 使用 Polygon_set 进行合并（union）
    geo::Polygon_set union_set(no_fly_zones[0]);
    for (size_t i = 1; i < no_fly_zones.size(); ++i) {
        union_set.join(no_fly_zones[i]);
    }

    // 提取合并结果
    auto merged_polys = union_set.polygons();
    for (auto &poly : merged_polys) {
        // 统一为 CW 方向（与原始行为一致）
        if (!poly.is_clockwise_oriented()) {
            poly.reverse_orientation();
        }
        result.push_back(poly);
    }

    return result;
}

//! 设置禁飞区域 — 使用 Clipper2 offset 外扩
std::vector<geo::Polygon_2> bufferNoFlyZones(const geometry::MultiPolygon2d &no_fly_zones, double no_fly_zone_offset) {
    std::vector<geo::Polygon_2> converted_polygon;

    for (auto &zone : no_fly_zones) {
        geo::Polygon_2 poly;
        for (auto &point : zone.outer()) {
            poly.push_back(geo::Point_2(point.get<0>(), point.get<1>()));
        }
        // 去除多边形重复顶点
        {
            auto &v = poly.container();
            auto it = std::unique(v.begin(), v.end(),
                                  [](const geo::Point_2 &a, const geo::Point_2 &b) { return geo::approx_equal(a, b); });
            v.erase(it, v.end());
            if (v.size() > 1 && geo::approx_equal(v.front(), v.back())) {
                v.pop_back();
            }
        }
        converted_polygon.push_back(poly);
    }

    std::vector<geo::Polygon_2> buffered_no_fly_zones;
    for (auto &polygon : converted_polygon) {
        // 检查禁飞区方向
        if (polygon.is_clockwise_oriented()) {
            polygon.reverse_orientation();
        }

        // 使用 Clipper2 offset 外扩
        geo::Polygon_2 result_poly = polygon.offset(-no_fly_zone_offset);
        if (!result_poly.empty()) {
            buffered_no_fly_zones.emplace_back(result_poly);
        } else {
            std::cout << "外扩失败,没有产生多边形" << std::endl;
        }
    }
    std::vector<geo::Polygon_2> merged_no_fly_zones = mergeOverlappingPolygon(buffered_no_fly_zones);
    return merged_no_fly_zones;
}

} // namespace polygon_coverage_planning