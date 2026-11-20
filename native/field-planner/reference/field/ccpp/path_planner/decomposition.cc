/*
 * polygon_coverage_planning implements algorithms for coverage planning in
 * general polygons with holes. Copyright (C) 2019, Rik Bähnemann, Autonomous
 * Systems Lab, ETH Zürich
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include "bcd.h"
#include "decomposition.h"
#include "../geo/weakly_monotone.h"
#include "../geo/geo_types.h"

#include <cmath>
#include <set>

namespace polygon_coverage_planning {

std::vector<Direction_2> findEdgeDirections(const PolygonWithHoles &pwh) {
    // Get all possible polygon directions.
    std::vector<Direction_2> directions;
    for (size_t i = 0; i < pwh.outer_boundary().size(); ++i) {
        directions.push_back(Segment_2(pwh.outer_boundary().vertex(i),
                                       pwh.outer_boundary().vertex((i + 1) % pwh.outer_boundary().size()))
                                 .direction());
    }
    for (auto hit = pwh.holes_begin(); hit != pwh.holes_end(); ++hit) {
        for (size_t i = 0; i < hit->size(); i++) {
            directions.push_back(Segment_2(hit->vertex(i), hit->vertex((i + 1) % hit->size())).direction());
        }
    }

    // Remove redundant directions (collinear).
    std::set<size_t> to_remove;
    for (size_t i = 0; i < directions.size() - 1; ++i) {
        for (size_t j = i + 1; j < directions.size(); ++j) {
            if (geo::orientation(directions[i].to_vector(), directions[j].to_vector()) == geo::Orientation::COLLINEAR)
                to_remove.insert(j);
        }
    }
    for (auto rit = to_remove.rbegin(); rit != to_remove.rend(); ++rit) {
        directions.erase(std::next(directions.begin(), *rit));
    }

    // Add opposite directions.
    std::vector<Direction_2> temp_directions = directions;
    for (size_t i = 0; i < temp_directions.size(); ++i) {
        directions.push_back(-temp_directions[i]);
    }

    return directions;
}

std::vector<Direction_2> findPerpEdgeDirections(const PolygonWithHoles &pwh) {
    std::vector<Direction_2> directions = findEdgeDirections(pwh);
    for (auto &d : directions) {
        d = Direction_2(-d.dy(), d.dx());
    }

    return directions;
}

double findBestSweepDir(const Polygon_2 &cell, Direction_2 *best_dir) {
    // Get all sweepable edges.
    PolygonWithHoles pwh(cell);
    std::vector<Direction_2> edge_dirs = getAllSweepableEdgeDirections(cell);

    // Find minimum altitude.
    double min_altitude = std::numeric_limits<double>::max();
    for (const auto &dir : edge_dirs) {
        auto s = findSouth(cell, Line_2(geo::Point_2(0.0, 0.0), dir));
        auto n = findNorth(cell, Line_2(geo::Point_2(0.0, 0.0), dir));
        auto orthogonal_vec = dir.to_vector().perpendicular();
        Line_2 line_through_n(*n, Direction_2(orthogonal_vec.x(), orthogonal_vec.y()));
        auto s_proj = line_through_n.projection(*s);
        double altitude = std::sqrt(geo::squared_distance(*n, s_proj));

        if (altitude < min_altitude) {
            min_altitude = altitude;
            if (best_dir)
                *best_dir = dir;
        }
    }

    return min_altitude;
}

bool computeBestBCDFromPolygonWithHoles(const PolygonWithHoles &pwh, std::vector<Polygon_2> *bcd_polygons,
                                        double work_angle) {
    bcd_polygons->clear();
    double min_altitude_sum = std::numeric_limits<double>::max();

    // Get all possible decomposition directions.
    std::vector<Direction_2> directions;
    if (work_angle - 0.0f < EPSILON) {
        directions = findPerpEdgeDirections(pwh);
    } else {
        Direction_2 custom_dir(-std::sin(work_angle * M_PI / 180.0), std::cos(work_angle * M_PI / 180.0));
        directions.push_back(custom_dir);
        directions.push_back(-custom_dir);
    }

    // For all possible rotations:
    int dir_idx = 0;
    for (const auto &dir : directions) {
        // Calculate decomposition.
        std::vector<Polygon_2> cells = computeBCD(pwh, dir);

        // Calculate minimum altitude sum for each cell.
        double min_altitude_sum_tmp = 0.0;
        for (const auto &cell : cells) {
            min_altitude_sum_tmp += findBestSweepDir(cell);
        }

        // Update best decomposition (skip empty results from defective directions).
        if (min_altitude_sum_tmp < min_altitude_sum) {
            min_altitude_sum = min_altitude_sum_tmp;
            *bcd_polygons = cells;
        }
        dir_idx++;
    }

    // geometry::MultiPolygon2d final_multi_polygon;
    // std::vector<Polygon_2>::iterator closed_poly_itr = bcd_polygons->begin();
    // for (; closed_poly_itr != bcd_polygons->end(); ++closed_poly_itr) {
    //     geometry::Polygon2d poly;
    //     Polygon_2::Vertex_const_iterator vit = closed_poly_itr->vertices_begin();
    //     for (; vit != closed_poly_itr->vertices_end(); ++vit) {
    //         geometry::Point2d p(vit->x(), vit->y());
    //         poly.outer().push_back(p);
    //     }
    //     final_multi_polygon.push_back(poly);
    // }

    if (bcd_polygons->empty())
        return false;
    else
        return true;
}

} // namespace polygon_coverage_planning
