#pragma once
#include "geo_types.h"
using namespace geo;

namespace polygon_coverage_planning {

// Check whether polygon 'in' is weakly monotone perpendicular to 'x_axis'.
bool isWeaklyMonotone(const Polygon_2& in, const Line_2& x_axis);
// For all edges check whether polygon 'in' is weakly monotone perpendicular to
// that edge.
std::vector<Direction_2> getAllSweepableEdgeDirections(const Polygon_2& in);

Polygon_2::Vertex_const_circulator findSouth(const Polygon_2& in, const Line_2& x_axis);
Polygon_2::Vertex_const_circulator findNorth(const Polygon_2& in, const Line_2& x_axis);

}  // namespace polygon_coverage_planning
