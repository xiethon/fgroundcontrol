#pragma once
#include "geo_types.h"
using namespace geo;

namespace polygon_coverage_planning {

// Helper to check whether a point is inside or on the boundary of the polygon.
bool pointInPolygon(const PolygonWithHoles& pwh, const Point_2& p);
inline bool pointInPolygon(const Polygon_2& poly, const Point_2& p) {
  return pointInPolygon(PolygonWithHoles(poly), p);
}

// Check whether a segment is fully contained within the polygon with holes.
// Uses interior-point sampling to verify the segment does not cross any boundary.
bool segmentInPolygon(const PolygonWithHoles& pwh, const Segment_2& seg);
inline bool segmentInPolygon(const Polygon_2& poly, const Segment_2& seg) {
  return segmentInPolygon(PolygonWithHoles(poly), seg);
}
bool pointsInPolygon(const PolygonWithHoles& pwh,
                     std::vector<Point_2>::iterator begin,
                     std::vector<Point_2>::iterator end);

// Definition: a polygon is strictly simple if it has no self-intersections.
bool isStrictlySimple(const PolygonWithHoles& pwh);

// Project a point on a polygon (closest point on any edge).
Point_2 projectOnPolygon2(const Polygon_2& poly, const Point_2& p,
                          FT* squared_distance);
// Project a point on the polygon outer hull or hole boundaries (closest).
Point_2 projectPointOnHull(const PolygonWithHoles& pwh, const Point_2& p);

FT computeArea(const PolygonWithHoles& pwh);
inline FT computeArea(const Polygon_2& poly) {
  return computeArea(PolygonWithHoles(poly));
}

// Remove collinear vertices from polygon.
void simplifyPolygon(Polygon_2* polygon);
void simplifyPolygon(PolygonWithHoles* pwh);

// Rotate polygon so that the given direction aligns with the positive X axis.
PolygonWithHoles rotatePolygon(const PolygonWithHoles& polygon_in,
                               const Direction_2& dir);

// Sort boundary vertices counter-clockwise and holes clockwise.
void sortVertices(PolygonWithHoles* pwh);

std::vector<Point_2> getHullVertices(const PolygonWithHoles& pwh);
std::vector<std::vector<Point_2>> getHoleVertices(const PolygonWithHoles& pwh);

}  // namespace polygon_coverage_planning
