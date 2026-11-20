#include "sweep.h"
#include "../geo/visibility_polygon.h"
#include "../geo/weakly_monotone.h"
#include "../geo/geo_types.h"
#include "coverage_planner.hpp"
#include <iostream>

using namespace geo;
using field_path::ccpp::GeopointsWithSpray;

namespace polygon_coverage_planning {

bool computeSweep(const Polygon_2 &in, const visibility_graph::VisibilityGraph &visibility_graph, const double offset,
                  const Direction_2 &dir, bool counter_clockwise, std::vector<GeopointsWithSpray> *waypoints,
                  const Line_2 *ref_line) {
    waypoints->clear();

    // Ensure the polygon is CCW-oriented. BCD cells may sometimes arrive CW
    // due to floating-point precision in the decomposition; correct silently.
    Polygon_2 poly = in;
    if (!poly.is_counterclockwise_oriented()) {
        poly.reverse_orientation();
        if (!poly.is_counterclockwise_oriented()) {
            // Degenerate (collinear or zero-area) cell — skip it safely.
            return true;
        }
    }

    // Find start sweep.
    Line_2 sweep(Point_2(0.0, 0.0), dir);
    std::vector<Point_2> sorted_pts = sortVerticesToLine(poly, sweep);
    sweep = Line_2(sorted_pts.front(), dir);
    // rotate sweep by 90 degree
    Vector_2 offset_vec(sweep.a(), sweep.b());
    Vector_2 front_to_back(sorted_pts.back().x() - sorted_pts.front().x(),
                           sorted_pts.back().y() - sorted_pts.front().y());
    if (offset_vec * front_to_back < 0)
        offset_vec = -offset_vec;
    double len = std::sqrt(offset_vec.squared_length());
    offset_vec = Vector_2(offset * offset_vec.x() / len, offset * offset_vec.y() / len);
    const double kTx = offset_vec.x();
    const double kTy = offset_vec.y();
    const Aff_transformation_2 kOffset(geo::Aff_transformation_2::TRANSLATION, kTx, kTy);

    Segment_2 sweep_segment;
    // sweep = sweep.transform(kOffset);

    // 相位对齐：当提供 ref_line 且其方向与 dir 共线（含同向/反向）时，把第一条
    // 扫线对齐到 ref_line 的全局等距栅格上，使所有共线相邻 cell 共享同一套栅格
    // 相位，避免跨 cell 间距不均（一边 2×offset、一边 0）。否则保持现状（贴 cell
    // 最低点起算）。注意此时 sweep 仍过 sorted_pts.front()，下方再做栅格平移。
    if (ref_line != nullptr && !ref_line->is_degenerate()) {
        Vector_2 ref_dir_vec = ref_line->to_vector();
        Vector_2 dir_vec = dir.to_vector();
        if (geo::orientation(ref_dir_vec, dir_vec) == geo::Orientation::COLLINEAR) {
            // offset_vec 已归一化到长度 == offset 且朝 cell 内部（front→back）。
            Vector_2 unit = offset_vec / offset;

            // ref_line 的单位法向量 (a,b)。signed_distance 沿此法向量测量，
            // 其方向可能与 unit 相反，需用 dot_sign 统一方向。
            Vector_2 ref_normal(ref_line->a(), ref_line->b());
            double dot_sign = (ref_normal * unit >= 0) ? 1.0 : -1.0;

            // 将 d0 统一到 unit 方向。
            double d0 = ref_line->signed_distance(sorted_pts.front()) * dot_sign;

            // 最近栅格相位：n = round(d0 / offset)，首线对齐到 L_n。
            long n = std::lround(d0 / offset);
            double shift = static_cast<double>(n) * offset - d0;
            Point_2 base = sorted_pts.front() + unit * shift;
            sweep = Line_2(base, dir);

            // 若该相位首线落在 cell 外/边界上（无有效扫线段），递增 n 直到首线
            // 进入 cell 内部，保证从底部起的内容不丢覆盖。
            Segment_2 probe;
            while (!findSweepSegment(poly, sweep, &probe) && static_cast<double>(n) * offset < d0 + 1e6 * offset) {
                ++n;
                shift = static_cast<double>(n) * offset - d0;
                base = sorted_pts.front() + unit * shift;
                sweep = Line_2(base, dir);
            }
        }
    }

    bool has_sweep_segment = findSweepSegment(poly, sweep, &sweep_segment);

    while (has_sweep_segment) {
        // 规范化：使所有扫线的 source→target 先与 dir 同向（点积非负）。
        // 之前此逻辑仅在 while 外执行一次（仅对 sweep#0），导致 sweep#1 等
        // 后续扫线方向与 sweep#0 归一化后的方向不匹配，ccw 翻转无法正确交替。
        Vector_2 seg_vec = sweep_segment.target() - sweep_segment.source();
        Vector_2 dir_vec = dir.to_vector();
        if (seg_vec * dir_vec < 0)
            sweep_segment = sweep_segment.opposite();

        // 按 counter_clockwise 偏好翻转方向，实现在相邻扫线间蛇形交替。
        if (counter_clockwise)
            sweep_segment = sweep_segment.opposite();

        // Connect previous sweep.
        if (!waypoints->empty()) {
            std::vector<Point_2> shortest_path;
            if (!calculateShortestPath(visibility_graph, waypoints->back().point, sweep_segment.source(),
                                       &shortest_path)) {
                return false;
            }

            for (auto it = std::next(shortest_path.begin()); it != std::prev(shortest_path.end()); ++it) {
                waypoints->push_back(GeopointsWithSpray{*it});
            }
        }
        // Traverse sweep.
        if (!sweep_segment.is_degenerate()) {
            GeopointsWithSpray temp_point{sweep_segment.source(), true}; // 起点开启喷洒
            waypoints->push_back(temp_point);
            waypoints->push_back(GeopointsWithSpray{sweep_segment.target()}); // 终点开启喷洒
        }

        // Offset sweep.
        sweep = sweep.transform(kOffset);
        // Find new sweep segment.
        Segment_2 prev_sweep_segment = counter_clockwise ? sweep_segment.opposite() : sweep_segment;
        has_sweep_segment = findSweepSegment(poly, sweep, &sweep_segment);

        if (!has_sweep_segment) {
            break;
        }

        // Swap directions for next sweep.
        counter_clockwise = !counter_clockwise;
    }

    return true;
}

bool findSweepSegment(const Polygon_2 &p, const Line_2 &l, Segment_2 *sweep_segment) {
    std::vector<Point_2> intersections = findIntersections(p, l);
    if (intersections.empty())
        return false;
    *sweep_segment = Segment_2(intersections.front(), intersections.back());
    return true;
}

void checkObservability(const Segment_2 &prev_sweep, const Segment_2 &sweep, const std::vector<Point_2> &sorted_pts,
                        const double max_sq_distance, std::vector<Point_2>::const_iterator *lowest_unobservable_point) {
    *lowest_unobservable_point = sorted_pts.end();

    // Find first point that is between prev_sweep and sweep and unobservable.
    for (auto it = sorted_pts.begin(); it != sorted_pts.end(); ++it) {
        if (prev_sweep.supporting_line().has_on_positive_side(*it))
            continue;
        if (sweep.supporting_line().has_on_negative_side(*it)) {
            break;
        }
        double sq_distance_prev = geo::squared_distance(prev_sweep, *it);
        double sq_distance_curr = geo::squared_distance(sweep, *it);
        if (sq_distance_prev > max_sq_distance && sq_distance_curr > max_sq_distance) {
            *lowest_unobservable_point = it;
            return;
        }
    }
}

bool calculateShortestPath(const visibility_graph::VisibilityGraph &visibility_graph, const Point_2 &start,
                           const Point_2 &goal, std::vector<Point_2> *shortest_path) {
    shortest_path->clear();

    Polygon_2 start_visibility, goal_visibility;
    bool start_vis_ok = computeVisibilityPolygon(visibility_graph.getPolygon(), start, &start_visibility);
    if (!start_vis_ok) {
        std::cout << "[DEBUG calculateShortestPath] FAIL: cannot compute start visibility polygon, using fallback."
                  << std::endl;

        // start_visibility 保持为空，visibility_graph.solve 内部有 segmentInPolygon fallback
    }
    bool goal_vis_ok = computeVisibilityPolygon(visibility_graph.getPolygon(), goal, &goal_visibility);
    if (!goal_vis_ok) {
        std::cout << "[DEBUG calculateShortestPath] FAIL: cannot compute goal visibility polygon, using fallback."
                  << std::endl;

        // goal_visibility 保持为空，同上
    }

    if (!visibility_graph.solve(start, start_visibility, goal, goal_visibility, shortest_path)) {
        std::cout << "[DEBUG calculateShortestPath] FAIL: solve() returned false" << std::endl;
        return false;
    }

    if (shortest_path->size() < 2) {
        std::cout << "[DEBUG calculateShortestPath] FAIL: path too short (size=" << shortest_path->size() << ")"
                  << std::endl;
        return false;
    }

    return true;
}

std::vector<Point_2> sortVerticesToLine(const Polygon_2 &p, const Line_2 &l) {
    // Copy points.
    std::vector<Point_2> pts(p.size());
    auto pts_it = pts.begin();
    for (auto vit = p.vertices_begin(); vit != p.vertices_end(); ++vit) {
        *(pts_it++) = *vit;
    }

    // Sort by signed distance to line.
    std::sort(pts.begin(), pts.end(), [&l](const Point_2 &a, const Point_2 &b) -> bool {
        return geo::has_smaller_signed_distance_to_line(a, b, l);
    });

    return pts;
}

std::vector<Point_2> findIntersections(const Polygon_2 &p, const Line_2 &l) {
    std::vector<Point_2> intersections;
    intersections.reserve(p.size() * 2);

    for (auto eit = p.edges_begin(); eit != p.edges_end(); ++eit) {
        Point_2 result_pt;

        FT d1 = l.signed_distance((*eit).source());
        FT d2 = l.signed_distance((*eit).target());
        // std::cout << "[DEBUG]   d1=" << d1 << " d2=" << d2 << " product=" << d1*d2 << std::endl;

        if (geo::intersection(*eit, l, result_pt)) {
            // std::cout << "[DEBUG]   SUCCESS: intersection at (" << result_pt.x() << "," << result_pt.y() << ")" <<
            // std::endl;
            intersections.push_back(result_pt);
        } else {
            // std::cout << "[DEBUG]   FAIL: geo::intersection returned false" << std::endl;

            try {
                Line_2 seg_line = (*eit).supporting_line();

                FT det = l.a() * seg_line.b() - l.b() * seg_line.a();

                if (!geo::is_zero(det)) {
                    Point_2 manual_pt = l.intersection_point(seg_line);

                    bool collinear_ok = geo::collinear((*eit).source(), manual_pt, (*eit).target());

                    bool has_on_ok = (*eit).has_on(manual_pt);

                    FT dist = l.signed_distance(manual_pt);
                }
            } catch (const std::exception &e) { std::cout << "[DEBUG]   Exception: " << e.what() << std::endl; }

            if (l.has_on((*eit).source()) && l.has_on((*eit).target())) {
                // std::cout << "[DEBUG]   edge lies on line, adding endpoints" << std::endl;
                intersections.push_back((*eit).source());
                intersections.push_back((*eit).target());
            }
        }
    }

    Line_2 perp_l = l.perpendicular(l.point(0.0));
    std::sort(intersections.begin(), intersections.end(), [&perp_l](const Point_2 &a, const Point_2 &b) -> bool {
        return geo::has_smaller_signed_distance_to_line(a, b, perp_l);
    });

    // 去除重复点：扫线经过多边形顶点时，该顶点会被两条相邻边各加入一次。
    // 按垂向距离排序后重复点相邻，一次 unique 即可去重。
    intersections.erase(std::unique(intersections.begin(), intersections.end(),
                                    [](const Point_2 &a, const Point_2 &b) {
                                        return geo::approx_equal(a.x(), b.x()) && geo::approx_equal(a.y(), b.y());
                                    }),
                        intersections.end());

    return intersections;
}

} // namespace polygon_coverage_planning
