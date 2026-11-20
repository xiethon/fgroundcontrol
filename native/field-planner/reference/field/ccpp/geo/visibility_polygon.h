#pragma once

#include "geo_types.h"

namespace polygon_coverage_planning {

// Compute the visibility polygon given a point inside a strictly simple
// polygon. Uses ray-casting algorithm (angular sweep).
// Original: Francisc Bungiu et al., "Efficient computation of visibility polygons", 2014.
bool computeVisibilityPolygon(const geo::PolygonWithHoles& pwh,
                              const geo::Point_2& query_point,
                              geo::Polygon_2* visibility_polygon);

}  // namespace polygon_coverage_planning
