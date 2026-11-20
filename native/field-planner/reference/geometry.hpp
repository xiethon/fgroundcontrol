#pragma once

#include <boost/geometry/geometry.hpp>

#include <cmath>

//! Planner 通用二维几何算法
namespace geometry {

namespace bg = boost::geometry;

using Point2d = bg::model::point<double, 2, bg::cs::cartesian>;
using Linestring2d = bg::model::linestring<Point2d>;
using MultiLinestring2d = bg::model::multi_linestring<Linestring2d>;
using Polygon2d = bg::model::polygon<Point2d>;
using MultiPolygon2d = bg::model::multi_polygon<Polygon2d>;
using MultiPoint2d = bg::model::multi_point<Point2d>;
using AABB = bg::model::box<Point2d>;

//! 将角度规范化到 [0, 360) 范围内
double normalizeAngle(double angle);

//! 计算点到线段的最短投影点，投影参数限制在 [0,1] 区间
Point2d closestPointOnSegment(const Point2d &point, const Point2d &segment_start, const Point2d &segment_end);

//! 计算点到折线的最近点，遍历各段取最小距离
Point2d closestPointOnLinestring(const Point2d &point, const Linestring2d &line);

//! 判断点是否在多边形内（含边界）
inline bool isInside(const Point2d &point, const Polygon2d &polygon) {
    return bg::covered_by(point, polygon);
}

//! 点限制在多边形内：内部则原样返回，否则投影到最近的边界
Point2d clampToPolygon(const Point2d &point, const Polygon2d &polygon);

//! 去重 Linestring：移除距离 < tolerance 的连续冗余点

//! 将几何体沿 x、y 方向平移指定距离
template <typename Geometry>
Geometry translate(const Geometry &geometry, double x, double y) {
    using Translator = bg::strategy::transform::translate_transformer<double, 2, 2>;

    Geometry translated;
    Translator translator(x, y);
    bg::transform(geometry, translated, translator);
    return translated;
}

//! 将几何体绕原点旋转指定角度（度）
template <typename Geometry>
Geometry rotate(const Geometry &geometry, double angle) {
    Geometry rotated;
    bg::strategy::transform::rotate_transformer<bg::degree, double, 2, 2> transform(angle);
    bg::transform(geometry, rotated, transform);
    return rotated;
}

//! 将几何体绕指定点旋转指定角度（度）
template <typename Geometry>
Geometry rotate(const Geometry &geometry, const Point2d origin, double angle) {
    const double origin_x = origin.get<0>();
    const double origin_y = origin.get<1>();
    return translate(rotate(translate(geometry, -origin_x, -origin_y), angle), origin_x, origin_y);
}

//! 将几何体沿给定角度方向平移指定距离，angle 为角度制（正东 0°，逆时针为正）
template <typename Geometry>
Geometry translateByDirection(const Geometry &geometry, double angle, double distance) {
    if (std::abs(distance) <= 0.000000001) {
        return geometry;
    }

    // acos(-1.0) = π，用于角度转弧度
    const double radians = normalizeAngle(angle) * std::acos(-1.0) / 180.0;
    return translate(geometry, distance * std::cos(radians), distance * std::sin(radians));
}

//! 对多边形进行等距缓冲（distance > 0 扩张，< 0 收缩），保持直边形态
Polygon2d buffer(const Polygon2d &polygon, double distance);
//! 对多个多边形进行等距缓冲，跳过退化为空的多边形
MultiPolygon2d buffer(const MultiPolygon2d &polygons, double distance);

//! 计算几何体的轴对齐包围盒 (AABB)
template <typename Geometry>
AABB toAABB(const Geometry &geometry) {
    return bg::return_envelope<AABB>(geometry);
}

//! 计算几何体在指定角度下的有向包围盒 (OBB)，返回旋转回原始坐标系的多边形
template <typename Geometry>
Polygon2d orientedAabb(const Geometry &geometry, double angle) {
    Point2d centroid;
    bg::centroid(geometry, centroid);
    const auto rotated_geometry = rotate(geometry, centroid, angle);
    const auto rotated_box = toAABB(rotated_geometry);

    Polygon2d polygon;
    polygon.outer().push_back(rotated_box.min_corner());
    polygon.outer().push_back(
        Point2d(rotated_box.max_corner().template get<0>(), rotated_box.min_corner().template get<1>()));
    polygon.outer().push_back(rotated_box.max_corner());
    polygon.outer().push_back(
        Point2d(rotated_box.min_corner().template get<0>(), rotated_box.max_corner().template get<1>()));
    polygon.outer().push_back(rotated_box.min_corner());
    bg::correct(polygon);
    return rotate(polygon, centroid, -angle);
}

//! 计算几何体在指定角度下的有向包围盒宽度
template <typename Geometry>
double aabbWidth(const Geometry &geometry, double angle) {
    const auto box_polygon = orientedAabb(geometry, angle);
    const auto box = toAABB(box_polygon);
    return box.max_corner().template get<0>() - box.min_corner().template get<0>();
}

//! 计算几何体在指定角度下的有向包围盒高度
template <typename Geometry>
double aabbHeight(const Geometry &geometry, double angle) {
    const auto box_polygon = orientedAabb(geometry, angle);
    const auto box = toAABB(box_polygon);
    return box.max_corner().template get<1>() - box.min_corner().template get<1>();
}

} // namespace geometry
