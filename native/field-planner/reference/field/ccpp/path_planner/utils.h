#pragma once

#include <vector>
#include <deque>
#include <list>
#include <map>
#include <climits>
#include <iostream>
#include <math.h>

#include "coverage_planner.hpp"
#include "decomposition.h"
#include "../geo/visibility_graph.h"
#include "sweep.h"
#include "../geo/geo_comm.h"

// 使用 geo 命名空间类型（geo_types.h 底部已定义 namespace geo { using geo::...; }）
using namespace geo;

class CellNode {
public:
    CellNode() {
        is_visited = false;
        is_cleaned = false;
        parent_index = INT_MAX;
        cell_index = INT_MAX;
    }
    bool is_visited;
    bool is_cleaned;

    int parent_index;
    std::deque<int> neighbor_indices;

    int cell_index;
};

std::vector<CellNode> calculateDecompositionAdjacency(const std::vector<Polygon_2> &decompositions);
// DFS
void walkThroughGraph(std::vector<CellNode> &cell_graph, int cell_index, int &unvisited_counter,
                      std::deque<CellNode> &path);

std::deque<int> getTravellingPath(const std::vector<CellNode> &cell_graph, int first_cell_index);

std::vector<std::map<int, std::list<Point_2>>> calculateCellIntersections(std::vector<Polygon_2> &decompositions,
                                                                          std::vector<CellNode> &cell_graph);

Point_2 findNextGoal(const Point_2 &start, const Point_2 &goal, const std::list<Point_2> &candidates);

bool doReverseNextSweep(const Point_2 &curr_point, const std::vector<GeopointsWithSpray> &next_sweep);

template <typename _Tp>
std::vector<GeopointsWithSpray> getShortestPath(const _Tp &polygon, const Point_2 &start, const Point_2 &goal) {
    polygon_coverage_planning::visibility_graph::VisibilityGraph vis_graph(polygon);
    std::vector<GeopointsWithSpray> final_path;
    std::vector<Point_2> shortest_path;
    if (!polygon_coverage_planning::calculateShortestPath(vis_graph, start, goal, &shortest_path)) {
        // Fallback: direct segment when visibility graph not fully connected.
        // Validate both endpoints are inside polygon before using direct segment.
        if (!polygon_coverage_planning::pointInPolygon(polygon, start)
            || !polygon_coverage_planning::pointInPolygon(polygon, goal)) {
            std::cerr << "ERROR: getShortestPath fallback - start or goal outside polygon, " << "returning empty path."
                      << std::endl;
            return {};
        }
        std::cerr << "WARNING: getShortestPath A* failed, fallback to direct segment " << "(no intermediate waypoints)."
                  << std::endl;
        shortest_path = {start, goal};
    }
    for (auto &pt : shortest_path) {
        final_path.push_back(GeopointsWithSpray{pt});
    }
    return final_path;
}

int getCellIndexOfPoint(const std::vector<Polygon_2> &decompositions, const Point_2 &point);

bool processInnerOverOuterBoundary(Polygon_2 &boundary_poly, std::vector<Polygon_2> &holes);

std::vector<Point_2> generateABTWaypoints(double a_x, double a_y, double a_heading, double b_x, double b_y,
                                          double b_heading, double spacing = 5000, int max_lines = 30);
