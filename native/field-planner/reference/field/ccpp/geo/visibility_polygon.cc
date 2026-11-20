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

#include "geo_comm.h"
#include "visibility_polygon.h"

#include <algorithm>
#include <cmath>
#include <iostream>

namespace polygon_coverage_planning {

using geo::Point_2;
using geo::Segment_2;
using geo::Polygon_2;
using geo::PolygonWithHoles;
using geo::FT;
using geo::EPSILON;

// Internal structure for angular sweep visibility algorithm
struct VisibilityEvent {
    double angle;     // Angle from query point (radians)
    Point_2 point;    // The vertex or intersection point
    double distance;  // Distance from query_point to this event
    int type;         // 0 = vertex (continue), 1 = start (enter), -1 = end (leave)
    bool is_blocking; // Whether this event blocks vision

    bool operator<(const VisibilityEvent &other) const {
        if (std::abs(angle - other.angle) > 1e-12)
            return angle < other.angle;
        return distance < other.distance;
    }
};

// Compute angle of vector from origin to point (atan2)
static double computeAngle(const Point_2 &origin, const Point_2 &pt) {
    return std::atan2(pt.y() - origin.y(), pt.x() - origin.x());
}

// Normalize angle to [0, 2*PI)
static double normalizeAngle(double a) {
    static const double TWO_PI = 6.283185307179586;
    while (a < 0)
        a += TWO_PI;
    while (a >= TWO_PI)
        a -= TWO_PI;
    return a;
}

// Check if point p is on segment s (including endpoints)
static bool pointOnSegment(const Point_2 &p, const Segment_2 &s) {
    double cross = (p.x() - s.source().x()) * (s.target().y() - s.source().y())
                   - (p.y() - s.source().y()) * (s.target().x() - s.source().x());
    if (std::abs(cross) > 1e-9)
        return false;
    double dot = (p.x() - s.source().x()) * (s.target().x() - s.source().x())
                 + (p.y() - s.source().y()) * (s.target().y() - s.source().y());
    double len_sq = (s.target().x() - s.source().x()) * (s.target().x() - s.source().x())
                    + (s.target().y() - s.source().y()) * (s.target().y() - s.source().y());
    return dot >= -1e-9 && dot <= len_sq + 1e-9;
}

// Find closest intersection of ray from origin in direction (dx, dy) with all edges
static bool rayIntersection(const Point_2 &origin, double dx, double dy, const std::vector<Segment_2> &edges,
                            Point_2 *hit_pt, double *hit_dist, int *hit_edge_idx) {
    bool found = false;
    double best_dist = std::numeric_limits<double>::max();
    Point_2 best_pt;

    for (size_t i = 0; i < edges.size(); ++i) {
        const Segment_2 &seg = edges[i];
        // Ray-segment intersection using parametric form
        double sx = seg.source().x(), sy = seg.source().y();
        double ex = seg.target().x(), ey = seg.target().y();

        double seg_dx = ex - sx, seg_dy = ey - sy;
        double denom = dx * seg_dy - dy * seg_dx;

        if (std::abs(denom) < 1e-14) {
            // Parallel: check if origin lies on the segment line
            continue;
        }

        double t = ((sx - origin.x()) * seg_dy - (sy - origin.y()) * seg_dx) / denom;
        double u = ((sx - origin.x()) * dy - (sy - origin.y()) * dx) / denom;

        // t >= 0 means forward along ray, u in [0,1] means on segment
        if (t >= 1e-10 && u >= -1e-9 && u <= 1.0 + 1e-9) {
            double ix = origin.x() + t * dx;
            double iy = origin.y() + t * dy;
            double dist = t * std::sqrt(dx * dx + dy * dy);
            if (dist < best_dist) {
                best_dist = dist;
                best_pt = Point_2(ix, iy);
                found = true;
                if (hit_edge_idx)
                    *hit_edge_idx = static_cast<int>(i);
            }
        }
    }

    if (found && hit_pt)
        *hit_pt = best_pt;
    if (found && hit_dist)
        *hit_dist = best_dist;
    return found;
}

bool computeVisibilityPolygon(const PolygonWithHoles &pwh, const Point_2 &query_point, Polygon_2 *visibility_polygon) {
    // Preconditions.
    if (!pointInPolygon(pwh, query_point)) {
        std::cout << "Query point outside of polygon." << std::endl;
    }
    if (!isStrictlySimple(pwh)) {
        std::cout << "Polygon is not strictly simple." << std::endl;
    }

    // Collect ALL edges from outer boundary + holes into one list.
    std::vector<Segment_2> all_edges;

    // Outer boundary edges
    for (auto eit = pwh.outer_boundary().edges_begin(); eit != pwh.outer_boundary().edges_end(); ++eit) {
        all_edges.push_back(*eit);
    }

    // Hole edges
    for (auto hit = pwh.holes_begin(); hit != pwh.holes_end(); ++hit) {
        for (auto eit = hit->edges_begin(); eit != hit->edges_end(); ++eit) {
            all_edges.push_back(*eit);
        }
    }

    // Step 1: Collect events — each vertex creates up to 3 angular events.
    std::vector<VisibilityEvent> events;

    // Process each edge's vertices
    for (const auto &edge : all_edges) {
        for (int vi = 0; vi <= 1; ++vi) {
            const Point_2 &vertex = (vi == 0) ? edge.source() : edge.target();

            // Skip query point itself
            if ((vertex.x() - query_point.x()) * (vertex.x() - query_point.x())
                    + (vertex.y() - query_point.y()) * (vertex.y() - query_point.y())
                < EPSILON)
                continue;

            double angle = normalizeAngle(computeAngle(query_point, vertex));
            double dist = std::sqrt(geo::squared_distance(query_point, vertex));

            // Add vertex event
            VisibilityEvent ev;
            ev.angle = angle;
            ev.point = vertex;
            ev.distance = dist;
            ev.type = 0; // vertex
            ev.is_blocking = true;
            events.push_back(ev);

            // For collinear/adjacent edges, also add slightly offset events
            // to handle the case where the vertex is a "corner" that blocks/unblocks.
            // We add epsilon-offset events just inside and outside the angle.
            const double eps_angle = 1e-8;
            VisibilityEvent ev_in = ev;
            ev_in.angle = normalizeAngle(angle - eps_angle);
            ev_in.type = -1; // leaving
            events.push_back(ev_in);

            VisibilityEvent ev_out = ev;
            ev_out.angle = normalizeAngle(angle + eps_angle);
            ev_out.type = 1; // entering
            events.push_back(ev_out);
        }
    }

    // Step 2: Sort events by angle
    std::sort(events.begin(), events.end());

    // Step 3: Angular sweep — trace visibility boundary
    std::vector<Point_2> result_vertices;
    result_vertices.reserve(events.size() + 2);

    // Start from the first visible point at angle 0 (or smallest angle)
    // Find first ray hit
    double start_angle = events.empty() ? 0.0 : events.front().angle;
    Point_2 first_hit;
    double first_dist;
    double dir_x = std::cos(start_angle);
    double dir_y = std::sin(start_angle);

    if (!rayIntersection(query_point, dir_x, dir_y, all_edges, &first_hit, &first_dist, nullptr)) {
        // Fallback: use max range
        first_hit = Point_2(query_point.x() + dir_x * 1e9, query_point.y() + dir_y * 1e9);
    }
    result_vertices.push_back(first_hit);

    // Sweep through events
    size_t idx = 0;
    while (idx < events.size()) {
        // Group all events at the same (or very close) angle
        double current_angle = events[idx].angle;
        double min_dist_at_angle = std::numeric_limits<double>::max();
        Point_2 best_vertex;

        while (idx < events.size() && std::abs(events[idx].angle - current_angle) < 1e-10) {
            if (events[idx].type == 0) { // actual vertex
                if (events[idx].distance < min_dist_at_angle) {
                    min_dist_at_angle = events[idx].distance;
                    best_vertex = events[idx].point;
                }
            }
            ++idx;
        }

        if (min_dist_at_angle < std::numeric_limits<double>::max()) {
            result_vertices.push_back(best_vertex);

            // Shoot ray at current angle to find blocking point
            Point_2 block_pt;
            double block_dist;
            double bx = std::cos(current_angle), by = std::sin(current_angle);
            if (rayIntersection(query_point, bx, by, all_edges, &block_pt, &block_dist, nullptr)) {
                // Only add blocking point if it's further than the vertex
                if (block_dist > min_dist_at_angle + EPSILON) {
                    result_vertices.push_back(block_pt);
                }
            }
        } else {
            // No vertex at this angle, just find the ray-polygon intersection
            Point_2 hit_pt;
            double hit_dist;
            double bx = std::cos(current_angle), by = std::sin(current_angle);
            if (rayIntersection(query_point, bx, by, all_edges, &hit_pt, &hit_dist, nullptr)) {
                result_vertices.push_back(hit_pt);
            }
        }
    }

    // Close back to first point
    if (!result_vertices.empty()) {
        result_vertices.push_back(result_vertices[0]);
    }

    // Build output polygon
    *visibility_polygon = Polygon_2();
    for (const auto &pt : result_vertices) {
        visibility_polygon->push_back(pt);
    }

    // Post-process
    simplifyPolygon(visibility_polygon);
    if (visibility_polygon->size() >= 3) {
        if (visibility_polygon->orientation() == geo::Orientation::CLOCKWISE)
            visibility_polygon->reverse_orientation();
    }

    return !visibility_polygon->empty();
}

} // namespace polygon_coverage_planning
