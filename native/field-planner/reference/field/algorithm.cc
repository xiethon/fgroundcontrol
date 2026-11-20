#include "field/algorithm.hpp"

#include "field/ccpp/coverage_planner.hpp"

#include "geometry.hpp"

#include "ccpp/geo/geo_types.h"
#include "ccpp/geo/converter.h"

#include <algorithm>
#include <cmath>
#include <utility>
#include <vector>

namespace field_path {

namespace {

constexpr double kDuplicatePointToleranceMm = 50.0;

FieldPathResult failure(FieldPathErrorCode code) {
    FieldPathResult result;
    result.error_code = code;
    result.error = fieldPathErrorMessage(code);
    return result;
}

bool workAngleFromReferenceEdge(const geometry::Point2d &from, const geometry::Point2d &to, double &work_angle) {
    const double dx = to.get<0>() - from.get<0>();
    const double dy = to.get<1>() - from.get<1>();
    if (std::hypot(dx, dy) <= 0.000000001) {
        return false;
    }

    work_angle = geometry::normalizeAngle(std::atan2(dy, dx) * 180.0 / std::acos(-1.0));
    // std::cout << "参考边角度: " << work_angle << "°" << std::endl;
    return std::isfinite(work_angle);
}

// 校验多边形：修正自交/环方向后，检查有效性且面积 > 0
bool validPolygon(geometry::Polygon2d &polygon) {
    geometry::bg::correct(polygon);
    return geometry::bg::is_valid(polygon) && geometry::bg::area(polygon) > 0.0;
}

//! 过滤无效多边形并施加边距缓冲，margin_mm 单位为毫米
geometry::MultiPolygon2d normalizedPolygons(geometry::MultiPolygon2d polygons, double margin_mm) {
    using namespace geometry;

    MultiPolygon2d valid_polygons;
    for (auto polygon : polygons) {
        if (!validPolygon(polygon)) {
            continue;
        }
        valid_polygons.push_back(std::move(polygon));
    }

    if (margin_mm > 0.0) {
        valid_polygons = buffer(valid_polygons, margin_mm);
    }

    MultiPolygon2d result;
    for (auto polygon : valid_polygons) {
        if (validPolygon(polygon)) {
            result.push_back(std::move(polygon));
        }
    }
    return result;
}

//! 计算路径总长度（米），用于结果统计
double pathDistanceMeters(const std::vector<FieldPathItem> &points) {
    double total = 0.0;
    for (size_t i = 1; i < points.size(); ++i) {
        total += geometry::bg::distance(points[i - 1].coordinate, points[i].coordinate) / 1000.0;
    }
    return total;
}

double sprayPathDistanceMeters(const std::vector<FieldPathItem> &points) {
    double total = 0.0;
    for (size_t i = 1; i < points.size(); ++i) {
        if (points[i - 1].spray_enabled) {
            total += geometry::bg::distance(points[i - 1].coordinate, points[i].coordinate) / 1000.0;
        }
    }
    return total;
}

double polygonAreaSquareMeters(const geo::Polygon_2 &polygon) {
    return std::abs(polygon.area()) / 1000000.0;
}

std::vector<field_path::WaypointWithSpray> deduplicate(const std::vector<field_path::WaypointWithSpray> &path,
                                                       double tolerance) {
    std::vector<field_path::WaypointWithSpray> result;
    for (const auto &wp : path) {
        if (!result.empty() && geometry::bg::distance(result.back().point, wp.point) < tolerance) {
            continue;
        }
        result.push_back(wp);
    }
    return result;
}

//! 将去重后的 Linestring 转为 FieldPathItem 列表
std::vector<FieldPathItem> toFieldPathItems(const std::vector<field_path::WaypointWithSpray> &path) {
    auto simplified = deduplicate(path, kDuplicatePointToleranceMm);
    std::vector<FieldPathItem> nodes;
    nodes.reserve(simplified.size());
    for (const auto &point : simplified) {
        nodes.push_back(FieldPathItem{point.point, point.spray_on});
    }
    return nodes;
}

//! 根据 noSprayZones 设置喷洒开关：线段穿过禁喷区边界时插入开关节点
std::vector<FieldPathItem> applyNoSprayZones(const std::vector<FieldPathItem> &path,
                                             const geometry::MultiPolygon2d &no_spray_zones) {
    if (no_spray_zones.empty() || path.size() < 2) {
        return path;
    }

    auto outside_no_spray_zone = [&no_spray_zones](const geometry::Point2d &point) {
        return !geometry::bg::covered_by(point, no_spray_zones);
    };

    std::vector<FieldPathItem> result;
    result.reserve(path.size() * 2);

    for (size_t i = 0; i + 1 < path.size(); ++i) {
        const auto &from = path[i];
        const auto &to = path[i + 1];
        geometry::Linestring2d segment{from.coordinate, to.coordinate};
        const double seg_length = geometry::bg::distance(from.coordinate, to.coordinate);

        // 收集该线段与所有禁喷区边界的交点
        struct Crossing {
            double distance = 0.0;
            geometry::Point2d point;
        };
        std::vector<Crossing> crossings;

        for (const auto &zone : no_spray_zones) {
            // 与禁喷区外环求交
            geometry::MultiPoint2d outer_pts;
            geometry::bg::intersection(segment, zone.outer(), outer_pts);
            for (const auto &pt : outer_pts) {
                const double d = geometry::bg::distance(from.coordinate, pt);
                if (d > 1.0 && seg_length - d > 1.0) {
                    crossings.push_back({d, pt});
                }
            }
            // 与禁喷区内环（洞）求交
            for (const auto &inner : zone.inners()) {
                geometry::MultiPoint2d inner_pts;
                geometry::bg::intersection(segment, inner, inner_pts);
                for (const auto &pt : inner_pts) {
                    const double d = geometry::bg::distance(from.coordinate, pt);
                    if (d > 1.0 && seg_length - d > 1.0) {
                        crossings.push_back({d, pt});
                    }
                }
            }
        }

        // 按距离排序并去重
        std::sort(crossings.begin(), crossings.end(),
                  [](const Crossing &a, const Crossing &b) { return a.distance < b.distance; });
        std::vector<Crossing> unique;
        for (const auto &c : crossings) {
            if (unique.empty() || c.distance - unique.back().distance > 5.0) {
                unique.push_back(c);
            }
        }

        if (unique.empty()) {
            if (result.empty() || !geometry::bg::equals(result.back().coordinate, from.coordinate)) {
                FieldPathItem start_node = from;
                start_node.spray_enabled = from.spray_enabled && outside_no_spray_zone(from.coordinate);
                result.push_back(start_node);
            }
            FieldPathItem end_node = to;
            end_node.spray_enabled = to.spray_enabled && outside_no_spray_zone(to.coordinate);
            result.push_back(end_node);
            continue;
        }

        // 推入起点
        if (result.empty() || !geometry::bg::equals(result.back().coordinate, from.coordinate)) {
            FieldPathItem start_node = from;
            start_node.spray_enabled = from.spray_enabled && outside_no_spray_zone(from.coordinate);
            result.push_back(start_node);
        }

        // 检测线段起点是否在禁喷区内：取起点与首个交点之间的中点判断
        bool zone_spraying = true;
        {
            const double check_dist = std::min(unique.front().distance * 0.5, 100.0);
            const double dx = to.coordinate.get<0>() - from.coordinate.get<0>();
            const double dy = to.coordinate.get<1>() - from.coordinate.get<1>();
            const double ratio = check_dist / seg_length;
            geometry::Point2d check_pt(from.coordinate.get<0>() + dx * ratio, from.coordinate.get<1>() + dy * ratio);
            zone_spraying = outside_no_spray_zone(check_pt);
        }

        // 在交点处插入节点，每次穿越边界切换喷洒状态
        for (const auto &crossing : unique) {
            zone_spraying = !zone_spraying;
            FieldPathItem node;
            node.coordinate = crossing.point;
            node.spray_enabled = from.spray_enabled && zone_spraying;
            result.push_back(node);
        }

        // 推入终点
        FieldPathItem end_node = to;
        end_node.spray_enabled = to.spray_enabled && outside_no_spray_zone(to.coordinate);
        result.push_back(end_node);
    }

    return result;
}

} // namespace

//! 大田航线算法入口：预处理 → planner规划 → 后处理
FieldPathResult FieldPathAlgorithm::run(const FieldPathInput &input, const FieldPathParams &params) const {
    using namespace geometry;
    using planner_path_search::Environment;
    using namespace polygon_coverage_planning;

    // 统一单位转换：FieldPathParams 使用米，几何引擎使用毫米
    constexpr double kMeterToMm = 1000.0;
    const double line_spacing_mm = std::max(1.0, params.line_spacing) * kMeterToMm;
    const double boundary_buffer_mm = params.boundary_buffer * kMeterToMm;
    const double obstacle_margin_mm = params.obstacle_margin * kMeterToMm;

    //! 输入预处理
    auto workspace = input.boundary;
    if (!validPolygon(workspace)) {
        return failure(FieldPathErrorCode::InvalidWorkspace);
    }
    const double boundary_area = std::abs(bg::area(workspace));
    geo::Polygon_2 buffered_workspace;
    if (boundary_buffer_mm > 0.0 || std::fabs(boundary_buffer_mm - 0.0) < geo::EPSILON) {
        buffered_workspace = bufferWorkSpace(workspace, boundary_buffer_mm);
    }
    if (buffered_workspace.empty()) {
        return failure(FieldPathErrorCode::EmptyAfterBoundaryBuffer);
    }
    auto blocked_zones = bufferNoFlyZones(input.obstacles, obstacle_margin_mm);

    Environment environment;
    environment.setWorkspace(buffered_workspace);
    environment.setBlockedZones(blocked_zones);
    environment.setLineSpacing(line_spacing_mm);
    environment.setStartPoint(input.start_point);

    double work_angle = 0.0;
    if (!workAngleFromReferenceEdge(input.reference_edge_start, input.reference_edge_end, work_angle)) {
        return failure(FieldPathErrorCode::InvalidReferenceEdge);
    }
    environment.setWorkAngle(work_angle);

    //! 调用路径算法
    ccpp::CoveragePlanner coverage_planner(environment);
    auto path = coverage_planner.getPath();
    if (path.empty()) {
        return failure(FieldPathErrorCode::NoValidPath);
    }

    //! 输出后处理：去重 → 转弯检测 → 喷洒开关
    FieldPathResult result;
    auto nodes = toFieldPathItems(path);
    nodes = applyNoSprayZones(std::move(nodes), input.no_spray_zones);

    result.items = std::move(nodes);
    if (result.items.size() < 2) {
        return failure(FieldPathErrorCode::PathOutsideWorkspace);
    }

    //! 结果统计
    result.total_distance = pathDistanceMeters(result.items);
    result.boundary_area = boundary_area / (kMeterToMm * kMeterToMm);
    result.inset_area = polygonAreaSquareMeters(buffered_workspace);
    result.inset_loss_area = std::max(0.0, result.boundary_area - result.inset_area);
    result.operation_area = sprayPathDistanceMeters(result.items) * (line_spacing_mm / kMeterToMm);
    result.total_area = result.operation_area > 0.0 ? result.operation_area : result.boundary_area;
    result.waypoint_count = static_cast<int>(result.items.size());
    return result;
}

} // namespace field_path
