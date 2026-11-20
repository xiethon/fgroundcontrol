#pragma once

#include "../geo/geo_types.h"

using geo::Point_2;
using geo::Vector_2;
using geo::Direction_2;
using geo::Segment_2;
using geo::Line_2;
using geo::Polygon_2;
using geo::PolygonWithHoles;
using geo::FT;

namespace polygon_coverage_planning {

// Get all unique polygon edge directions including opposite directions.
std::vector<Direction_2> findEdgeDirections(const PolygonWithHoles &pwh);

// Get all directions that are perpendicular to the edges found with
// findEdgeDirections.
std::vector<Direction_2> findPerpEdgeDirections(const PolygonWithHoles &pwh);

// Find the best edge direction to sweep. The best direction is the direction
// with the smallest polygon altitude. Returns the smallest altitude.
double findBestSweepDir(const Polygon_2 &cell, Direction_2 *best_dir = nullptr);

// Compute BCDs for every edge direction. Return any with the smallest possible
// altitude sum.
bool computeBestBCDFromPolygonWithHoles(const PolygonWithHoles &pwh, std::vector<Polygon_2> *bcd_polygons,
                                        double work_angle = 0.0);

// Compute TCDs for every edge direction. Return any with the smallest possible
// altitude sum.
bool computeBestTCDFromPolygonWithHoles(const PolygonWithHoles &pwh, std::vector<Polygon_2> *trap_polygons);

enum DecompositionType {
    kBCD = 0, // Boustrophedon.
    kTCD      // Trapezoidal.
};

inline bool checkDecompositionTypeValid(const int type) {
    return (type == DecompositionType::kBCD) || (type == DecompositionType::kTCD);
}

inline std::string getDecompositionTypeName(const DecompositionType &type) {
    switch (type) {
    case DecompositionType::kBCD: return "Boustrophedon Cell Decomposition";
    case DecompositionType::kTCD: return "Trapezoidal Cell Decomposition";
    default: return "Unknown!";
    }
}

} // namespace polygon_coverage_planning
