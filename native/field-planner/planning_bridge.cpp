#include "planning_bridge.hpp"
#include "field/ccpp/geo/converter.h"
#include <cmath>
#include <stdexcept>

namespace fgc {
field_path::FieldPathResult planField(const std::vector<double>& coordinates, double spacing, double inset) {
    if (coordinates.size() < 6 || coordinates.size() % 2 || coordinates.size() > 256 ||
        !std::isfinite(spacing) || spacing < 1.5 || spacing > 70 ||
        !std::isfinite(inset) || inset < 0 || inset > 99)
        throw std::invalid_argument("Invalid planning input");
    field_path::FieldPathInput input;
    double min_x = coordinates[0], max_x = min_x, min_y = coordinates[1], max_y = min_y;
    for (size_t i = 0; i < coordinates.size(); i += 2) {
        double x = coordinates[i], y = coordinates[i + 1];
        if (!std::isfinite(x) || !std::isfinite(y) || std::abs(x) > 5000 || std::abs(y) > 5000)
            throw std::invalid_argument("Unsupported coordinate extent");
        min_x = std::min(min_x, x); max_x = std::max(max_x, x);
        min_y = std::min(min_y, y); max_y = std::max(max_y, y);
        for (size_t j = 0; j < i; j += 2)
            if (std::hypot(x - coordinates[j], y - coordinates[j + 1]) <= 0.05)
                throw std::invalid_argument("Boundary points are too close");
        input.boundary.outer().emplace_back(x * 1000, y * 1000);
    }
    double diagonal = std::hypot(max_x - min_x, max_y - min_y);
    // 保守预算先于算法分配；拒绝超规模，不截断几何或航线。
    if (diagonal > 5000 || (std::ceil(diagonal / spacing) + 1) * (coordinates.size() / 2) > 8192)
        throw std::invalid_argument("Planning complexity limit exceeded");
    boost::geometry::correct(input.boundary);
    if (!boost::geometry::is_valid(input.boundary) || boost::geometry::area(input.boundary) <= 0)
        throw std::invalid_argument("Invalid boundary");
    input.start_point = geometry::Point2d(coordinates[0] * 1000, coordinates[1] * 1000);
    input.reference_edge_start = input.start_point;
    input.reference_edge_end = geometry::Point2d(coordinates[2] * 1000, coordinates[3] * 1000);
    field_path::FieldPathParams params;
    params.line_spacing = spacing; params.boundary_buffer = inset;
    auto result = field_path::FieldPathAlgorithm().run(input, params);
    if (!result.ok()) return result;
    if (result.items.size() > 4096) throw std::runtime_error("Planning output limit exceeded");

    // 与算法相同的内缩轮廓；逐段差集检查，不能仅检查端点。
    const auto workspace = polygon_coverage_planning::bufferWorkSpace(input.boundary, inset * 1000);
    geometry::Polygon2d polygon;
    for (auto it = workspace.vertices_begin(); it != workspace.vertices_end(); ++it)
        polygon.outer().emplace_back(it->x(), it->y());
    boost::geometry::correct(polygon);
    geometry::MultiPolygon2d tolerance;
    boost::geometry::buffer(polygon, tolerance,
        boost::geometry::strategy::buffer::distance_symmetric<double>(0.1),
        boost::geometry::strategy::buffer::side_straight(), boost::geometry::strategy::buffer::join_miter(),
        boost::geometry::strategy::buffer::end_flat(), boost::geometry::strategy::buffer::point_circle(16));
    if (tolerance.empty() || !std::isfinite(result.total_distance) || !std::isfinite(result.total_area) ||
        result.total_distance <= 0 || result.total_area < 0) throw std::runtime_error("Invalid planning output");
    for (size_t i = 0; i < result.items.size(); ++i) {
        const auto& point = result.items[i].coordinate;
        if (!std::isfinite(point.get<0>()) || !std::isfinite(point.get<1>()) ||
            !boost::geometry::covered_by(point, tolerance)) throw std::runtime_error("Waypoint outside safe area");
        if (i == 0) continue;
        geometry::Linestring2d segment{result.items[i - 1].coordinate, point};
        std::vector<geometry::Linestring2d> outside;
        boost::geometry::difference(segment, tolerance, outside);
        double length = 0;
        for (const auto& line : outside) length += boost::geometry::length(line);
        if (length > 1e-5) throw std::runtime_error("Route outside safe area");
    }
    return result;
}
}
