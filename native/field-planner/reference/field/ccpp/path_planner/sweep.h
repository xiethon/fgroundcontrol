#pragma once

#include "../geo/geo_types.h"
#include "../geo/visibility_graph.h"
#include "../coverage_planner.hpp"

using geo::Point_2;
using geo::Vector_2;
using geo::Direction_2;
using geo::Segment_2;
using geo::Line_2;
using geo::Polygon_2;
using field_path::ccpp::GeopointsWithSpray;

namespace polygon_coverage_planning {

// Compute the sweep by moving from the bottom to the top of the polygon.
//
// ref_line: 可选的全局相位参考线。当其方向与 dir 共线（含同向/反向）时，本 cell
// 的第一条扫线会对齐到 ref_line 的等距栅格上（ref_line + n*offset），从而让所有
// 与之共线的相邻 cell 共享同一套栅格相位，避免跨 cell 间距不均。传入 nullptr 或
// 方向不共线时退化为现状行为（贴 cell 最低点起算）。
bool computeSweep(const Polygon_2 &in, const visibility_graph::VisibilityGraph &visibility_graph, const double offset,
                  const Direction_2 &dir, bool counter_clockwise, std::vector<GeopointsWithSpray> *waypoints,
                  const Line_2 *ref_line = nullptr);

// A segment is observable if all vertices between two sweeps are observable.
void checkObservability(const Segment_2 &prev_sweep, const Segment_2 &sweep, const std::vector<Point_2> &sorted_pts,
                        const double max_sq_distance, std::vector<Point_2>::const_iterator *lowest_unobservable_point);

// Find the intersections between a polygon and a line and sort them by the
// distance to the perpendicular direction of the line.
std::vector<Point_2> findIntersections(const Polygon_2 &p, const Line_2 &l);

bool findSweepSegment(const Polygon_2 &p, const Line_2 &l, Segment_2 *sweep_segment);

// Sort vertices of polygon based on signed distance to line l.
std::vector<Point_2> sortVerticesToLine(const Polygon_2 &p, const Line_2 &l);

// Connect to points in the polygon using the visibility graph.
bool calculateShortestPath(const visibility_graph::VisibilityGraph &visibility_graph, const Point_2 &start,
                           const Point_2 &goal, std::vector<Point_2> *shortest_path);

} // namespace polygon_coverage_planning
