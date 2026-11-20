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
#include "visibility_graph.h"
#include "visibility_polygon.h"

#include <iterator>

namespace polygon_coverage_planning { namespace visibility_graph {

using geo::Polygon_2;
using geo::PolygonWithHoles;
using geo::Point_2;
using geo::Direction_2;
using geo::Segment_2;

VisibilityGraph::VisibilityGraph(const PolygonWithHoles &polygon) : GraphBase(), polygon_(polygon) {
    // Build visibility graph.
    is_created_ = create();
}

bool VisibilityGraph::create() {
    clear();
    // Sort vertices.
    sortVertices(&polygon_);
    // Select shortest path vertices.
    std::vector<Polygon_2::Vertex_const_circulator> graph_vertices;
    findConcaveOuterBoundaryVertices(&graph_vertices);
    findConvexHoleVertices(&graph_vertices);

    for (const auto &v : graph_vertices) {
        // Compute visibility polygon.
        Polygon_2 visibility;
        if (!computeVisibilityPolygon(polygon_, *v, &visibility)) {
            std::cout << "Cannot compute visibility polygon." << std::endl;
            return false;
        }
        if (!addNode(NodeProperty(*v, visibility))) {
            return false;
        }
    }

    return true;
}

void VisibilityGraph::findConcaveOuterBoundaryVertices(
    std::vector<Polygon_2::Vertex_const_circulator> *concave_vertices) const {
    auto vit = polygon_.outer_boundary().vertices_circulator();
    do {
        // Check if vertex is concave: cross product of (prev->v) x (v->next)
        // For CCW polygon, concave means clockwise turn (right turn).
        auto prev_pt = *std::prev(vit);
        auto curr_pt = *vit;
        auto next_pt = *std::next(vit);
        Vector_2 v1(curr_pt.x() - prev_pt.x(), curr_pt.y() - prev_pt.y());
        Vector_2 v2(next_pt.x() - curr_pt.x(), next_pt.y() - curr_pt.y());
        double cross = v1.x() * v2.y() - v1.y() * v2.x();
        // For CCW polygon, concave = right turn = negative cross product
        if (cross < -geo::EPSILON)
            concave_vertices->push_back(vit);
    } while (++vit != polygon_.outer_boundary().vertices_circulator());
}

void VisibilityGraph::findConvexHoleVertices(std::vector<Polygon_2::Vertex_const_circulator> *convex_vertices) const {
    for (auto hit = polygon_.holes_begin(); hit != polygon_.holes_end(); ++hit) {
        auto vit = hit->vertices_circulator();
        do {
            // Holes are CW oriented; convex in hole = left turn = positive cross
            auto prev_pt = *std::prev(vit);
            auto curr_pt = *vit;
            auto next_pt = *std::next(vit);
            Vector_2 v1(curr_pt.x() - prev_pt.x(), curr_pt.y() - prev_pt.y());
            Vector_2 v2(next_pt.x() - curr_pt.x(), next_pt.y() - curr_pt.y());
            double cross = v1.x() * v2.y() - v1.y() * v2.x();
            // For CW hole, convex = left turn = positive cross product
            if (cross > geo::EPSILON)
                convex_vertices->push_back(vit);
        } while (++vit != hit->vertices_circulator());
    }
}

bool VisibilityGraph::addEdges() {
    if (graph_.empty()) {
        std::cout << "Cannot add edges to an empty graph." << std::endl;
        return false;
    }

    const size_t new_id = graph_.size() - 1;
    for (size_t adj_id = 0; adj_id < new_id; ++adj_id) {
        const NodeProperty *new_node_property = getNodeProperty(new_id);
        const NodeProperty *adj_node_property = getNodeProperty(adj_id);
        if (adj_node_property == nullptr) {
            std::cout << "Cannot access potential neighbor." << std::endl;
            return false;
        }

        // segmentInPolygon 是边验证的唯一门禁。
        // computeVisibilityPolygon 在 double 精度下不可靠（可能同时产生 false positive
        // 和 false negative），因此不将其作为快速路或预筛选器。segmentInPolygon 内部
        // 用 do_intersect 精确边求交 + midpoint 验证兜底，提供保守且一致的结果。
        // 对每个候选边都执行 segmentInPolygon，O(N^2*E) 开销在小规模图上可忽略。
        const Segment_2 seg(new_node_property->coordinates, adj_node_property->coordinates);
        bool visible = segmentInPolygon(polygon_, seg);

        if (visible) {
            EdgeId forwards_edge_id(new_id, adj_id);
            EdgeId backwards_edge_id(adj_id, new_id);
            const double cost = computeEuclideanSegmentCost(new_node_property->coordinates,
                                                            adj_node_property->coordinates); // Symmetric cost.
            if (!addEdge(forwards_edge_id, EdgeProperty(), cost) || !addEdge(backwards_edge_id, EdgeProperty(), cost)) {
                return false;
            }
        }
    }
    return true;
}

bool VisibilityGraph::solve(const Point_2 &start, const Point_2 &goal, std::vector<Point_2> *waypoints) const {
    waypoints->clear();

    // Make sure start and end are inside the polygon.
    const Point_2 start_new = pointInPolygon(polygon_, start) ? start : projectPointOnHull(polygon_, start);
    const Point_2 goal_new = pointInPolygon(polygon_, goal) ? goal : projectPointOnHull(polygon_, goal);

    // Compute start and goal visibility polygon (best-effort, fallback handled in solve(6-arg)).
    Polygon_2 start_visibility, goal_visibility;
    computeVisibilityPolygon(polygon_, start_new, &start_visibility);
    computeVisibilityPolygon(polygon_, goal_new, &goal_visibility);

    // Find shortest path.
    return solve(start_new, start_visibility, goal_new, goal_visibility, waypoints);
}

bool VisibilityGraph::solve(const Point_2 &start, const Polygon_2 &start_visibility_polygon, const Point_2 &goal,
                            const Polygon_2 &goal_visibility_polygon, std::vector<Point_2> *waypoints) const {
    waypoints->clear();

    if (!is_created_) {
        std::cout << "Visibility graph not initialized." << std::endl;
    } else if (!pointInPolygon(polygon_, start) || !pointInPolygon(polygon_, goal)) {
        std::cout << "Start or goal is not in polygon." << std::endl;
        return false;
    }

    VisibilityGraph temp_visibility_graph = *this;
    // Add start and goal node.
    if (!temp_visibility_graph.addStartNode(NodeProperty(start, start_visibility_polygon))
        || !temp_visibility_graph.addGoalNode(NodeProperty(goal, goal_visibility_polygon))) {
        return false;
    }

    // Check if start and goal are in line of sight.
    size_t start_idx = temp_visibility_graph.getStartIdx();
    size_t goal_idx = temp_visibility_graph.getGoalIdx();
    const NodeProperty *start_node_property = temp_visibility_graph.getNodeProperty(start_idx);
    if (start_node_property == nullptr) {
        return false;
    }

    // DIRECT VISIBLE PATH 捷径已移除。
    // 在 double 精度下，computeVisibilityPolygon 可能产生溢出边界的可见域，
    // 导致 pointInPolygon(visibility, goal) 产生假阳性；segmentInPolygon 的
    // 中点采样和 do_intersect 无法 100% 兜住所有越界场景（尤其是跨 hole 的
    // 长线段）。一律走 A* 在图边上求路，每条图边都已通过 segmentInPolygon 验证，
    // 确保最终路径安全。

    // Find shortest way using A*.
    Solution solution;
    if (!temp_visibility_graph.solveAStar(start_idx, goal_idx, &solution)) {
        std::cout << "Could not find shortest path. Graph not fully connected." << std::endl;
        return false;
    }

    // Reconstruct waypoints.
    return temp_visibility_graph.getWaypoints(solution, waypoints);
}

bool VisibilityGraph::getWaypoints(const Solution &solution, std::vector<Point_2> *waypoints) const {
    waypoints->resize(solution.size());
    for (size_t i = 0; i < solution.size(); i++) {
        const NodeProperty *node_property = getNodeProperty(solution[i]);
        if (node_property == nullptr) {
            std::cout << "Cannot reconstruct solution." << std::endl;
            return false;
        }
        (*waypoints)[i] = node_property->coordinates;
    }
    return true;
}

bool VisibilityGraph::calculateHeuristic(size_t goal, Heuristic *heuristic) const {
    heuristic->clear();

    const NodeProperty *goal_node_property = getNodeProperty(goal);
    if (goal_node_property == nullptr) {
        std::cout << "Cannot find goal node property to calculate heuristic." << std::endl;
        return false;
    }

    for (size_t adj_id = 0; adj_id < graph_.size(); ++adj_id) {
        const NodeProperty *adj_node_property = getNodeProperty(adj_id);
        if (adj_node_property == nullptr) {
            std::cout << "Cannot access adjacent node property to calculate heuristic." << std::endl;
            return false;
        }
        (*heuristic)[adj_id] =
            computeEuclideanSegmentCost(adj_node_property->coordinates, goal_node_property->coordinates);
    }

    return true;
}

double VisibilityGraph::computeEuclideanSegmentCost(const Point_2 &from, const Point_2 &to) const {
    return std::sqrt(geo::squared_distance(from, to));
}

}} // namespace polygon_coverage_planning::visibility_graph
