#include "field/algorithm.hpp"
#include "planning_bridge.hpp"
#include "field/ccpp/geo/geo_types.h"
#include "field/ccpp/path_planner/utils.h"
#include <cmath>
#include <iostream>
#include <stdexcept>
#include <set>

void require(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}
field_path::FieldPathInput polygon(std::initializer_list<geometry::Point2d> vertices) {
    field_path::FieldPathInput input;
    input.boundary.outer().assign(vertices);
    boost::geometry::correct(input.boundary);
    input.start_point = *vertices.begin();
    input.reference_edge_start = *vertices.begin();
    input.reference_edge_end = *(vertices.begin() + 1);
    return input;
}
void checkPath(const field_path::FieldPathInput& input, const field_path::FieldPathParams& params, const char* name) {
    const auto result = field_path::FieldPathAlgorithm().run(input, params);
    std::cout << name << ": code=" << int(result.error_code) << " points=" << result.items.size()
              << " distance=" << result.total_distance << " error=" << result.error << std::endl;
    require(result.ok(), "valid polygon produced no route");
    // 对边界上的连接段留0.1mm浮点容差，再作整段差集，避免舍入把共线段判为外侧。
    geometry::MultiPolygon2d tolerant_boundary;
    boost::geometry::buffer(input.boundary, tolerant_boundary,
        boost::geometry::strategy::buffer::distance_symmetric<double>(0.1),
        boost::geometry::strategy::buffer::side_straight(),
        boost::geometry::strategy::buffer::join_miter(),
        boost::geometry::strategy::buffer::end_flat(),
        boost::geometry::strategy::buffer::point_circle(16));
    for (size_t index = 0; index < result.items.size(); ++index) {
        const auto& item = result.items[index];
        require(std::isfinite(item.coordinate.get<0>()) && std::isfinite(item.coordinate.get<1>()), "non-finite waypoint");
        require((boost::geometry::covered_by(item.coordinate, input.boundary) || boost::geometry::distance(item.coordinate, input.boundary) < 1e-5), "waypoint outside boundary");
        if (index > 0) {
            geometry::Point2d a = result.items[index - 1].coordinate;
            const auto& b = item.coordinate;
            geometry::Linestring2d segment{a, b};
            std::vector<geometry::Linestring2d> outside;
            boost::geometry::difference(segment, tolerant_boundary, outside);
            double outside_length = 0;
            for (const auto& line : outside) outside_length += boost::geometry::length(line);
            require(outside_length < 1e-5, "connection outside boundary");
        }
    }
}
int main() {
    try {
        // 轴向直线必须和一般直线具有相同的左右侧语义。
        require(geo::Line_2(geo::Point_2(10, 0), geo::Point_2(0, 0)).signed_distance({5, 1}) < 0, "horizontal orientation");
        require(geo::Line_2(geo::Point_2(0, 0), geo::Point_2(0, 10)).signed_distance({1, 5}) < 0, "vertical orientation");
        geo::Polygon_2 almost_horizontal;
        for (auto p : {geo::Point_2(-100000, -1e-11), geo::Point_2(-100000, -60000.00000000001),
                       geo::Point_2(0, -60000), geo::Point_2(0, 0)}) almost_horizontal.push_back(p);
        require(almost_horizontal.bounded_side({-100000.1, -60000.00000000001}) == geo::ON_UNBOUNDED_SIDE,
                "near-horizontal edge parity");
        require(calculateDecompositionAdjacency({}).empty(), "empty decomposition adjacency");
        auto rectangle = polygon({{0,0}, {100000,0}, {100000,60000}, {0,60000}});
        field_path::FieldPathParams params; params.line_spacing = 10; params.boundary_buffer = 0;
        checkPath(rectangle, params, "rectangle");
        const auto result = field_path::FieldPathAlgorithm().run(rectangle, params);
        require(result.items.size() == 12 && std::abs(result.total_distance - 650) < 1e-6, "rectangle route length");
        std::set<long long> rows;
        for (const auto& item : result.items) rows.insert(std::llround(item.coordinate.get<1>()));
        require(rows == std::set<long long>({5000,15000,25000,35000,45000,55000}), "half-spacing and row spacing");
        auto reversed = rectangle;
        std::reverse(reversed.boundary.outer().begin(), reversed.boundary.outer().end());
        boost::geometry::correct(reversed.boundary);
        std::swap(reversed.reference_edge_start, reversed.reference_edge_end);
        checkPath(reversed, params, "reversed reference edge");
        params.boundary_buffer = 2;
        checkPath(rectangle, params, "rectangle inset");
        params.boundary_buffer = 40;
        require(!field_path::FieldPathAlgorithm().run(rectangle, params).ok(), "collapsed inset accepted");
        params.boundary_buffer = 0;
        checkPath(polygon({{0,0},{80000,60000},{44000,108000},{-36000,48000}}), params, "rotated rectangle");
        checkPath(polygon({{0,0},{100000,0},{100000,30000},{60000,30000},{60000,60000},{0,60000}}), params, "concave L");
        auto invalid = polygon({{0,0},{100000,60000},{0,60000},{100000,0}});
        require(!field_path::FieldPathAlgorithm().run(invalid, params).ok(), "self-intersection accepted");
        const std::vector<double> meters{0,0,100,0,100,60,0,60};
        require(fgc::planField({0,0,100,0,0,60},10,0).ok(), "minimum vertex count");
        std::vector<double> convex;
        for(int vertex=0;vertex<128;++vertex) {
            const double angle=vertex*2*std::acos(-1.0)/128;
            convex.push_back(100*std::cos(angle));convex.push_back(100*std::sin(angle));
        }
        require(fgc::planField(convex,10,0).ok(), "maximum vertex count");
        require(fgc::planField(meters,1.5,0).ok(), "minimum spacing");
        require(fgc::planField(meters,70,0).ok(), "maximum spacing");
        require(fgc::planField(meters,10,0).items.size()==12, "guarded rectangle");
        require(std::abs(fgc::planField(meters,10,2).total_distance-626)<1e-6, "guarded inset");
        require(!fgc::planField(meters,10,40).ok(), "guarded collapsed inset");
        require(fgc::planField({0,0,80,60,44,108,-36,48},10,0).ok(), "guarded rotated rectangle");
        require(fgc::planField({0,0,100,0,100,30,60,30,60,60,0,60},10,0).ok(), "guarded concave polygon");
        for (auto bad : {std::vector<double>{0,0,6000,0,6000,60,0,60},
                         std::vector<double>{0,0,0.01,0,100,60},
                         std::vector<double>{0,0,100,60,0,60,100,0},
                         std::vector<double>(258,0.0),
                         std::vector<double>{0,0,4000,0,4000,2500,0,2500}}) {
            bool rejected=false;
            try { fgc::planField(bad,1.5,0); } catch(const std::invalid_argument&) { rejected=true; }
            require(rejected,"unsafe input accepted");
        }
        bool disconnected=false;
        try { fgc::planField({0,0,40,0,40,18,60,18,60,0,100,0,100,40,60,40,60,22,40,22,40,40,0,40},10,3); }
        catch(const std::runtime_error& error) { disconnected=std::string(error.what()).find("Disconnected")!=std::string::npos; }
        require(disconnected,"disconnected inset silently accepted");
        std::cout << "All native regression checks passed" << std::endl;
        return 0;
    } catch (const std::exception& e) {
        std::cerr << e.what() << std::endl;
        return 1;
    }
}
