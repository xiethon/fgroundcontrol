#include "planner_path_search.hpp"

namespace planner_path_search {

void Environment::setWorkspace(const geo::Polygon_2 &workspace) {
    _workspace = workspace;
}
void Environment::setBlockedZones(const std::vector<geo::Polygon_2> &blocked_zones) {
    _blocked_zones = blocked_zones;
}
void Environment::setStartPoint(const Point2d &start_point) {
    _start_point = start_point;
}
void Environment::setWorkAngle(double angle) {
    _work_angle = geometry::normalizeAngle(angle);
}
void Environment::setLineSpacing(double spacing) {
    _line_spacing = spacing;
}
const geo::Polygon_2 &Environment::workspace() const {
    return _workspace;
}
const std::vector<geo::Polygon_2> &Environment::blockedZones() const {
    return _blocked_zones;
}
const Point2d &Environment::startPoint() const {
    return _start_point;
}
double Environment::workAngle() const {
    return _work_angle;
}
double Environment::lineSpacing() const {
    return _line_spacing;
}

} // namespace planner_path_search
