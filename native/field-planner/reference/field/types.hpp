#pragma once

#include "geometry.hpp"

#include <string>
#include <vector>

namespace field_path {

//! 大田航线规划错误码
enum class FieldPathErrorCode {
    None = 0,
    InvalidStartPoint,
    InvalidWorkspace,
    EmptyAfterBoundaryBuffer,
    NoValidPath,
    PathOutsideWorkspace,
    InvalidReferenceEdge,
};

inline std::string fieldPathErrorMessage(FieldPathErrorCode code) {
    switch (code) {
    case FieldPathErrorCode::None: return {};
    case FieldPathErrorCode::InvalidStartPoint: return "Field path start point is invalid";
    case FieldPathErrorCode::InvalidWorkspace: return "Field path workspace is invalid";
    case FieldPathErrorCode::InvalidReferenceEdge: return "Field path reference edge is invalid";
    case FieldPathErrorCode::EmptyAfterBoundaryBuffer:
        return "Field path workspace became invalid after boundary buffer";
    case FieldPathErrorCode::NoValidPath: return "Planner did not generate a valid path";
    case FieldPathErrorCode::PathOutsideWorkspace: return "Planner path fell outside workspace";
    }
    return "Unknown field path planner error";
}

//! 大田航线规划输入
struct FieldPathInput {
    geometry::Polygon2d boundary;
    geometry::MultiPolygon2d obstacles;
    geometry::MultiPolygon2d no_spray_zones;
    geometry::Point2d start_point;
    geometry::Point2d reference_edge_start;
    geometry::Point2d reference_edge_end;
    bool optimal_route_enabled = false;
};

//! 大田航线规划参数
struct FieldPathParams {
    double line_spacing = 5.0;
    double boundary_buffer = 1.0;
    double obstacle_margin = 1.0;
};

//! 航线上的单个路径点
struct FieldPathItem {
    geometry::Point2d coordinate;
    bool spray_enabled = true; // 该点是否开启喷洒
};

//! 大田航线规划结果
struct FieldPathResult {
    std::vector<FieldPathItem> items;
    double total_distance = 0.0;
    double total_area = 0.0;
    double boundary_area = 0.0;
    double inset_area = 0.0;
    double inset_loss_area = 0.0;
    double operation_area = 0.0;
    int waypoint_count = 0;
    FieldPathErrorCode error_code = FieldPathErrorCode::None;
    std::string error;

    bool ok() const {
        return error.empty() && !items.empty();
    }
};

struct WaypointWithSpray {
    geometry::Point2d point;
    bool spray_on = false; // 喷洒开关状态
};

} // namespace field_path
