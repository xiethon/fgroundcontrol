#pragma once

#include "graph_base_impl.h"
#include "geo_types.h"

namespace polygon_coverage_planning { namespace visibility_graph {

struct NodeProperty {
    NodeProperty() : coordinates(geo::Point_2(0.0, 0.0)) {}
    NodeProperty(const geo::Point_2 &coordinates, const geo::Polygon_2 &visibility) :
        coordinates(coordinates), visibility(visibility) {}
    geo::Point_2 coordinates;  // The 2D coordinates.
    geo::Polygon_2 visibility; // The visibile polygon from the vertex.
};

struct EdgeProperty {};

// Shortest path calculation in the reduced visibility graph.
class VisibilityGraph : public GraphBase<NodeProperty, EdgeProperty> {
public:
    // Creates an undirected, weighted visibility graph.
    VisibilityGraph(const geo::PolygonWithHoles &polygon);
    VisibilityGraph(const geo::Polygon_2 &polygon) : VisibilityGraph(geo::PolygonWithHoles(polygon)) {}

    VisibilityGraph() : GraphBase() {}

    virtual bool create() override;

    // Compute the shortest path in a polygon with holes using A* and the
    // precomputed visibility graph.
    bool solve(const geo::Point_2 &start, const geo::Point_2 &goal, std::vector<geo::Point_2> *waypoints) const;
    // Same as solve but provide a precomputed visibility graph for the polygon.
    bool solve(const geo::Point_2 &start, const geo::Polygon_2 &start_visibility_polygon, const geo::Point_2 &goal,
               const geo::Polygon_2 &goal_visibility_polygon, std::vector<geo::Point_2> *waypoints) const;

    // Given a solution, get the concatenated 2D waypoints.
    bool getWaypoints(const Solution &solution, std::vector<geo::Point_2> *waypoints) const;

    inline geo::PolygonWithHoles getPolygon() const {
        return polygon_;
    }

private:
    virtual bool addEdges() override;
    virtual bool calculateHeuristic(size_t goal, Heuristic *heuristic) const override;

    void findConcaveOuterBoundaryVertices(std::vector<geo::Polygon_2::Vertex_const_circulator> *concave_vertices) const;
    void findConvexHoleVertices(std::vector<geo::Polygon_2::Vertex_const_circulator> *convex_vertices) const;

    double computeEuclideanSegmentCost(const geo::Point_2 &from, const geo::Point_2 &to) const;

    geo::PolygonWithHoles polygon_;
};

}} // namespace polygon_coverage_planning::visibility_graph
