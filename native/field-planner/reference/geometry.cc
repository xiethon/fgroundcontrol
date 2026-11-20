#include "geometry.hpp"

#include <algorithm>
#include <limits>
#include "types.hpp"

namespace geometry {

double normalizeAngle(double angle) {
    angle = std::fmod(angle, 360.0);
    if (angle < 0.0) {
        angle += 360.0;
    }
    return angle;
}

Polygon2d buffer(const Polygon2d &polygon, double distance) {
    Polygon2d result;
    MultiPolygon2d buffered;

    bg::strategy::buffer::distance_symmetric<double> distance_strategy(distance);
    // 规划边界和障碍物需要保持直边形态，避免圆角缓冲改变作业边界语义。
    bg::strategy::buffer::join_miter join_strategy;
    bg::strategy::buffer::end_flat end_strategy;
    bg::strategy::buffer::point_square point_strategy;
    bg::strategy::buffer::side_straight side_strategy;

    bg::buffer(polygon, buffered, distance_strategy, side_strategy, join_strategy, end_strategy, point_strategy);
    if (!buffered.empty()) {
        result = buffered.front();
    }
    return result;
}

MultiPolygon2d buffer(const MultiPolygon2d &polygons, double distance) {
    MultiPolygon2d result;
    for (const auto &polygon : polygons) {
        auto buffered_polygon = buffer(polygon, distance);
        if (!bg::is_empty(buffered_polygon)) {
            result.push_back(buffered_polygon);
        }
    }
    return result;
}

Point2d closestPointOnSegment(const Point2d &point, const Point2d &segment_start, const Point2d &segment_end) {
    const double start_x = segment_start.get<0>();
    const double start_y = segment_start.get<1>();
    const double end_x = segment_end.get<0>();
    const double end_y = segment_end.get<1>();
    const double point_x = point.get<0>();
    const double point_y = point.get<1>();

    const double segment_x = end_x - start_x;
    const double segment_y = end_y - start_y;
    const double segment_length_squared = segment_x * segment_x + segment_y * segment_y;
    if (segment_length_squared <= 0.0) {
        return segment_start;
    }

    const double projection =
        ((point_x - start_x) * segment_x + (point_y - start_y) * segment_y) / segment_length_squared;
    const double clamped_projection = std::clamp(projection, 0.0, 1.0);
    return Point2d(start_x + clamped_projection * segment_x, start_y + clamped_projection * segment_y);
}

Point2d closestPointOnLinestring(const Point2d &point, const Linestring2d &line) {
    if (line.empty()) {
        return point;
    }
    if (line.size() == 1) {
        return line.front();
    }

    double min_distance = std::numeric_limits<double>::max();
    Point2d closest_point = line.front();
    for (size_t index = 1; index < line.size(); ++index) {
        const Point2d candidate = closestPointOnSegment(point, line[index - 1], line[index]);
        const double distance = bg::distance(point, candidate);
        if (distance < min_distance) {
            min_distance = distance;
            closest_point = candidate;
        }
    }
    return closest_point;
}

Point2d clampToPolygon(const Point2d &point, const Polygon2d &polygon) {
    if (isInside(point, polygon)) {
        return point;
    }

    const auto &outer_ring = polygon.outer();
    if (outer_ring.size() < 2) {
        return {};
    }

    double min_distance = std::numeric_limits<double>::max();
    Point2d closest_point = outer_ring.front();
    for (size_t index = 1; index < outer_ring.size(); ++index) {
        const auto candidate = closestPointOnSegment(point, outer_ring[index - 1], outer_ring[index]);
        const double distance = bg::distance(point, candidate);
        if (distance < min_distance) {
            min_distance = distance;
            closest_point = candidate;
        }
    }

    return closest_point;
}

} // namespace geometry
