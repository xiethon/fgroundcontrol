#pragma once
#include "../geo/geo_types.h"

double point2SegmentDistance(const geo::Point_2 &p, const geo::Segment_2 &s);
double polygonDistance(const geo::PolygonWithHoles &poly1, const geo::PolygonWithHoles &poly2);
geo::Polygon_2 cleanPolygon(const geo::Polygon_2 &poly);
bool isValidPolygonWithHoles(const geo::PolygonWithHoles &poly);
geo::PolygonWithHoles normalizePolygonWithHoles(const geo::PolygonWithHoles &poly);
std::vector<geo::PolygonWithHoles> exteriorOffset(const geo::PolygonWithHoles &poly, double radius);
std::vector<geo::PolygonWithHoles> interiorInset(const geo::PolygonWithHoles &poly, double radius);
bool mergePlots(const std::vector<geo::PolygonWithHoles> &fields, std::vector<geo::PolygonWithHoles> &final_results,
                double threshold = 20.0, double buffer_radius = 10.0);
