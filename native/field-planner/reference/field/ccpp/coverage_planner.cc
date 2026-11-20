#include "field/ccpp/coverage_planner.hpp"
#include "decomposition.h"
#include "field/ccpp/geo/geo_types.h"
#include "field/ccpp/path_planner/utils.h"
#include "field/planner_path_search.hpp"
#include "types.hpp"

#include <algorithm>
#include <cstddef>
#include <iterator>
#include <limits>
#include <vector>
#include <stdexcept>

using namespace field_path::ccpp;
//! 判断工作区域是否有效
bool CoveragePlanner::_validWorkspace() {
    auto work_space = _environment.workspace();
    if (geo::is_valid(work_space)) {
        return true;
    }
    return false;
}

CoveragePlanner::CoveragePlanner(const planner_path_search::Environment &environment) : _environment(environment) {}

//! 获取覆盖路径
// FIX: 最近扫线端点信息
struct ClosestSweepInfo {
    size_t cell_idx = 0;
    bool is_clockwise = true;
    bool is_first = true;
};

// 当航线点从后到前排序时，重新修改喷洒开关
void ReverseSpraySwithInPlace(std::vector<GeopointsWithSpray> &cell_sweep) {
    std::reverse(cell_sweep.begin(), cell_sweep.end()); // 先反转
    for (size_t i = 0; i + 1 < cell_sweep.size(); i++) {
        if (cell_sweep[i].spray_on != cell_sweep[i + 1].spray_on) {
            cell_sweep[i].spray_on = !cell_sweep[i].spray_on;
        }
    }
    cell_sweep.back().spray_on = false; // 最后一个点关闭喷洒
}

// FIX: 更新 start_point 到单个 cell 扫线端点的最近距离记录
static void updateClosestEndpoint(const Point_2 &start, const std::vector<GeopointsWithSpray> &sweep_cw,
                                  const std::vector<GeopointsWithSpray> &sweep_acw, size_t cell_idx,
                                  ClosestSweepInfo &closest, double &min_dist) {
    double d_cw_first = geo::squared_distance(start, sweep_cw.front().point);
    double d_cw_last = geo::squared_distance(start, sweep_cw.back().point);
    double d_acw_first = geo::squared_distance(start, sweep_acw.front().point);
    double d_acw_last = geo::squared_distance(start, sweep_acw.back().point);

    if (d_cw_first < min_dist) {
        min_dist = d_cw_first;
        closest.cell_idx = cell_idx;
        closest.is_clockwise = true;
        closest.is_first = true;
    }
    if (d_cw_last < min_dist) {
        min_dist = d_cw_last;
        closest.cell_idx = cell_idx;
        closest.is_clockwise = true;
        closest.is_first = false;
    }
    if (d_acw_first < min_dist) {
        min_dist = d_acw_first;
        closest.cell_idx = cell_idx;
        closest.is_clockwise = false;
        closest.is_first = true;
    }
    if (d_acw_last < min_dist) {
        min_dist = d_acw_last;
        closest.cell_idx = cell_idx;
        closest.is_clockwise = false;
        closest.is_first = false;
    }
}

// FIX: 封装处理剩余cell的主循环，提取为独立函数以减少 getPath() 的复杂度
static void processRemainingCells(const std::vector<Polygon_2> &bcd_cells,
                                  std::vector<std::vector<GeopointsWithSpray>> &cells_sweeps_clockwise,
                                  std::vector<std::vector<GeopointsWithSpray>> &cells_sweeps_anticlockwise,
                                  std::vector<std::map<int, std::list<Point_2>>> &cell_intersections,
                                  const PolygonWithHoles &pwh, std::vector<CellNode> &cell_graph,
                                  std::vector<GeopointsWithSpray> &way_points, Point_2 &point, int &current_cell_idx) {
    std::vector<GeopointsWithSpray> shortest_path;
    std::list<Point_2> next_candidates;
    GeopointsWithSpray next_point;

    while (true) {
        double min_dist = std::numeric_limits<double>::max();
        int next_cell_idx = -1;
        bool next_reverse = false;
        GeopointsWithSpray next_target;
        bool is_clockwise = false;

        // 遍历所有cell，寻找最近的未清理cell
        for (size_t i = 0; i < bcd_cells.size(); ++i) {
            if (cell_graph[i].is_cleaned)
                continue;
            double dist_cw = geo::squared_distance(point, cells_sweeps_clockwise[i].front().point)
                                     < geo::squared_distance(point, cells_sweeps_clockwise[i].back().point) ?
                                 geo::squared_distance(point, cells_sweeps_clockwise[i].front().point) :
                                 geo::squared_distance(point, cells_sweeps_clockwise[i].back().point);
            double dist_acw = geo::squared_distance(point, cells_sweeps_anticlockwise[i].front().point)
                                      < geo::squared_distance(point, cells_sweeps_anticlockwise[i].back().point) ?
                                  geo::squared_distance(point, cells_sweeps_anticlockwise[i].front().point) :
                                  geo::squared_distance(point, cells_sweeps_anticlockwise[i].back().point);

            double dist = std::min(dist_cw, dist_acw);
            if (dist < min_dist) {
                min_dist = dist;
                next_cell_idx = static_cast<int>(i);
                // 根据距离选择顺时针还是逆时针扫线，并确定下一个目标点
                if (dist_cw < dist_acw) {
                    is_clockwise = true;
                    next_reverse = doReverseNextSweep(point, cells_sweeps_clockwise[i]);
                    next_target = next_reverse ? cells_sweeps_clockwise[i].back() : cells_sweeps_clockwise[i].front();
                } else {
                    is_clockwise = false;
                    next_reverse = doReverseNextSweep(point, cells_sweeps_anticlockwise[i]);
                    next_target =
                        next_reverse ? cells_sweeps_anticlockwise[i].back() : cells_sweeps_anticlockwise[i].front();
                }
            }
        }

        if (next_cell_idx == -1)
            break;

        next_candidates = cell_intersections[current_cell_idx][next_cell_idx];
        // 如果next_cell和当前cell是非邻接关系,即没有公共边界，则next_candidates为空，此时直接连接当前点和下一个cell的航线起点的最短路径，最后添加航线
        if (next_candidates.empty()) {
            shortest_path = getShortestPath(pwh, point, next_target.point);
            way_points.insert(way_points.end(), std::next(shortest_path.begin()), std::prev(shortest_path.end()));
        } else {
            // 如果next_cell和当前cell是邻接关系,即有公共边界，则从候选点中选择一个点作为下一个目标点，连接当前点和目标点的最短路径，再连接目标点和航线的最短路径，最后添加航线
            next_point.point = findNextGoal(point, next_target.point, next_candidates);
            shortest_path = getShortestPath(bcd_cells[current_cell_idx], point, next_point.point);
            way_points.insert(way_points.end(), std::next(shortest_path.begin()), shortest_path.end());
            shortest_path = getShortestPath(bcd_cells[next_cell_idx], next_point.point, next_target.point);
            way_points.insert(way_points.end(), std::next(shortest_path.begin()), std::prev(shortest_path.end()));
        }

        // 添加扫线
        if (is_clockwise) {
            if (next_reverse)
                ReverseSpraySwithInPlace(cells_sweeps_clockwise[next_cell_idx]);
            way_points.insert(way_points.end(), cells_sweeps_clockwise[next_cell_idx].begin(),
                              cells_sweeps_clockwise[next_cell_idx].end());

        } else {
            if (next_reverse)
                ReverseSpraySwithInPlace(cells_sweeps_anticlockwise[next_cell_idx]);
            way_points.insert(way_points.end(), cells_sweeps_anticlockwise[next_cell_idx].begin(),
                              cells_sweeps_anticlockwise[next_cell_idx].end());
        }

        cell_graph[next_cell_idx].is_cleaned = true;
        current_cell_idx = next_cell_idx;
        point = way_points.back().point;
    }
}

// FIX: 从pwh中查找与work_angle方向一致的边
// 原问题：此逻辑原本内联在getPath()中，增加函数复杂度
// 修改：提取为独立函数，输入pwh和work_angle，
// 输出找到的边的端点（work_edge_src/tgt）及work_dir方向
// 返回true表示找到匹配边，false表示未找到
static bool findWorkAngleEdge(const PolygonWithHoles &pwh, double work_angle, Direction_2 &work_dir,
                              Point_2 &work_edge_src, Point_2 &work_edge_tgt) {
    if (work_angle - 0.0f <= EPSILON)
        return false;
    work_dir = Direction_2(std::cos(work_angle * M_PI / 180.0), std::sin(work_angle * M_PI / 180.0));
    // 遍历pwh外边界的所有边，找到与work_angle方向一致的那条边
    for (size_t j = 0; j < pwh.outer_boundary().size(); ++j) {
        geo::Segment_2 edge(pwh.outer_boundary().vertex(j),
                            pwh.outer_boundary().vertex((j + 1) % pwh.outer_boundary().size()));
        if (geo::orientation(edge.direction().to_vector(), work_dir.to_vector()) == geo::Orientation::COLLINEAR) {
            work_edge_src = edge.source();
            work_edge_tgt = edge.target();
            return true;
        }
    }

    std::cout << "没有找到作业参考边！！！" << std::endl;
    return false;
}

std::vector<field_path::WaypointWithSpray> CoveragePlanner::getPath() {
    std::vector<field_path::WaypointWithSpray> path;
    if (!_validWorkspace()) {
        std::cout << "工作区域无效，无法生成路径" << std::endl;
        return path;
    }

    auto work_space = _environment.workspace();
    auto obstacles = _environment.blockedZones();
    double sweep_step = _environment.lineSpacing();
    double work_angle = _environment.workAngle();

    // 处理hole超出外边界的情况，确保每个hole都完全在外边界内部
    if (!processInnerOverOuterBoundary(work_space, obstacles)) {
        std::cout << "处理hole超出外边界失败，无法生成路径" << std::endl;
        return path;
    }

    PolygonWithHoles pwh(work_space, obstacles.begin(), obstacles.end());

    //! 1. 基于BCD的分解
    std::vector<Polygon_2> bcd_cells;
    polygon_coverage_planning::computeBestBCDFromPolygonWithHoles(pwh, &bcd_cells, work_angle);
    // std::cout << "bcd_cells_size:" << bcd_cells.size() << std::endl;

    // 2. 计算每个cell的邻接关系
    if (bcd_cells.empty()) return path;
    double expected_area = std::abs(work_space.area());
    for (const auto& obstacle : obstacles) expected_area -= std::abs(obstacle.area());
    double decomposed_area = 0;
    for (const auto& cell : bcd_cells) decomposed_area += std::abs(cell.area());
    if (std::abs(decomposed_area - expected_area) > std::max(1.0, std::abs(expected_area) * 1e-6))
        throw std::runtime_error("Incomplete cell decomposition");
    auto cell_graph = calculateDecompositionAdjacency(bcd_cells);

    // 3. 计算起点所在cell
    auto bg_start = _environment.startPoint();
    Point_2 start(bg_start.get<0>(), bg_start.get<1>());

    std::vector<std::vector<GeopointsWithSpray>> cells_sweeps_clockwise;
    std::vector<std::vector<GeopointsWithSpray>> cells_sweeps_anticlockwise;

    // 4. 计算每个cell的扫线
    ClosestSweepInfo closest;
    double global_min_dist = std::numeric_limits<double>::max();

    // FIX: 调用独立函数从pwh中查找与work_angle方向一致的边
    Direction_2 work_dir;
    Point_2 work_edge_src, work_edge_tgt;
    bool has_work_angle_edge = findWorkAngleEdge(pwh, work_angle, work_dir, work_edge_src, work_edge_tgt);

    // 共线分组参考栅格池：每个元素 (方向, 参考线)。所有扫线方向与之共线（含同向/
    // 反向）的 cell 共享同一套栅格相位，从而相邻共线 cell 的跨 cell 扫线间距保持为
    // offset，而非随机叠加（2×offset）或抵消（0）。方向不共线的 cell 各自成组。
    std::vector<std::pair<Direction_2, Line_2>> ref_grid_pool;

    for (size_t i = 0; i < bcd_cells.size(); ++i) {
        // Compute all cluster sweeps.
        std::vector<GeopointsWithSpray> cell_sweep;
        Direction_2 best_dir;
        // 计算最佳扫线方向
        polygon_coverage_planning::findBestSweepDir(bcd_cells[i], &best_dir);
        // FIX: 用边的几何端点判断该边属于哪个cell，避免方向匹配到内部分割线
        // 修改：使用has_on_boundary检测pwh原始边的端点是否在cell边界上，
        // 而非在cell内部按方向搜索，从而精确定位到唯一cell
        if (has_work_angle_edge && bcd_cells[i].has_on_boundary(work_edge_src)
            && bcd_cells[i].has_on_boundary(work_edge_tgt)) {
            best_dir = work_dir;
        }
        polygon_coverage_planning::visibility_graph::VisibilityGraph vis_graph(bcd_cells[i]);

        // 查找/建立本 cell 的共线参考栅格。best_dir 与池中某方向共线（同向/反向）
        // 则复用其 ref_line；否则取 best_dir 共线边所在直线向 cell 内平移 step/2 新建一组并入池。
        // 同组 cell 共享同一套栅格相位，跨 cell 间距即为 offset。
        const Line_2 *cell_ref_line = nullptr;
        Line_2 new_ref_line;
        bool found_group = false;
        for (const auto &entry : ref_grid_pool) {
            if (geo::orientation(entry.first.to_vector(), best_dir.to_vector()) == geo::Orientation::COLLINEAR) {
                cell_ref_line = &entry.second;
                found_group = true;
                break;
            }
        }
        if (!found_group) {
            // 找到与 best_dir 共线的 cell 边，取其所在直线向 cell 内部平移 sweep_step/2，
            // 使首条扫线距该边界半个行距。
            bool found_collinear_edge = false;
            Line_2 edge_line;
            for (auto eit = bcd_cells[i].edges_begin(); eit != bcd_cells[i].edges_end(); ++eit) {
                Segment_2 edge = *eit;
                if (geo::orientation(edge.to_vector(), best_dir.to_vector()) == geo::Orientation::COLLINEAR) {
                    edge_line = edge.supporting_line();
                    found_collinear_edge = true;
                    break;
                }
            }
            if (found_collinear_edge) {
                // 用 cell 质心判断内部方向：质心在有符号距离正侧则沿正法向平移，否则反向
                double cx = 0.0, cy = 0.0;
                for (auto vit = bcd_cells[i].vertices_begin(); vit != bcd_cells[i].vertices_end(); ++vit) {
                    cx += vit->x();
                    cy += vit->y();
                }
                std::size_t nv = bcd_cells[i].size();
                if (nv > 0) {
                    cx /= static_cast<double>(nv);
                    cy /= static_cast<double>(nv);
                }
                Point_2 centroid(cx, cy);
                FT sd = edge_line.signed_distance(centroid);
                FT sign = (sd >= 0.0) ? 1.0 : -1.0;
                Vector_2 normal(edge_line.a(), edge_line.b());
                Point_2 edge_point = edge_line.point(0.0);
                Point_2 shifted_point = edge_point + normal * (sign * sweep_step / 2.0);
                new_ref_line = Line_2(shifted_point, best_dir);
            } else {
                // 回退：取质心 + best_dir
                double cx = 0.0, cy = 0.0;
                for (auto vit = bcd_cells[i].vertices_begin(); vit != bcd_cells[i].vertices_end(); ++vit) {
                    cx += vit->x();
                    cy += vit->y();
                }
                std::size_t nv = bcd_cells[i].size();
                if (nv > 0) {
                    cx /= static_cast<double>(nv);
                    cy /= static_cast<double>(nv);
                }
                new_ref_line = Line_2(Point_2(cx, cy), best_dir);
            }
            ref_grid_pool.emplace_back(best_dir, new_ref_line);
            cell_ref_line = &new_ref_line;
        }

        // 计算逆时针和顺时针两种扫线，并分别存储，后续根据起点位置选择更合适的一种
        bool counter_clockwise = true;
        if (!polygon_coverage_planning::computeSweep(bcd_cells[i], vis_graph, sweep_step, best_dir, counter_clockwise,
                                                     &cell_sweep, cell_ref_line)) {
            std::cout << "错误：无法计算cell " << i << " 的逆时针扫线" << std::endl;
            return path;
        }
        if (cell_sweep.empty()) {
            return path; // 空扫线不能冒称该分区已覆盖。
            std::cout << "警告：cell " << i << " 的逆时针扫线为空" << std::endl;
        }
        cells_sweeps_clockwise.emplace_back(cell_sweep);

        cell_sweep.clear();
        counter_clockwise = false;
        if (!polygon_coverage_planning::computeSweep(bcd_cells[i], vis_graph, sweep_step, best_dir, counter_clockwise,
                                                     &cell_sweep, cell_ref_line)) {
            std::cout << "错误：无法计算cell " << i << " 的顺时针扫线" << std::endl;
            return path;
        }
        if (cell_sweep.empty()) {
            return path; // 空扫线不能冒称该分区已覆盖。
            std::cout << "警告：cell " << i << " 的顺时针扫线为空" << std::endl;
        }
        cells_sweeps_anticlockwise.emplace_back(cell_sweep);

        // FIX: 更新 start 到当前 cell 扫线端点的最近距离
        if (!cell_graph[i].is_cleaned) {
            updateClosestEndpoint(start, cells_sweeps_clockwise[i], cells_sweeps_anticlockwise[i], i, closest,
                                  global_min_dist);
        }
    }

    // std::cout << "cells_sweeps_size:" << cells_sweeps_clockwise.size() << std::endl;
    // 处理起始cell
    // FIX: 使用 closest 选择扫线方向
    std::vector<GeopointsWithSpray> way_points;
    Point_2 point = start;
    int current_cell_idx = static_cast<int>(closest.cell_idx);

    std::vector<GeopointsWithSpray> &cell_sweeps =
        closest.is_clockwise ? cells_sweeps_clockwise[current_cell_idx] : cells_sweeps_anticlockwise[current_cell_idx];

    if (cell_sweeps.empty()) {
        std::cout << "错误：cell_sweeps为空" << std::endl;
        return path;
    }

    if (!closest.is_first)
        ReverseSpraySwithInPlace(cell_sweeps);
    way_points.insert(way_points.end(), cell_sweeps.begin(), cell_sweeps.end());

    cell_graph[current_cell_idx].is_cleaned = true;
    point = way_points.back().point;

    // std::cout << "first cell.is_reverse:" << closest.is_first << std::endl;
    // for (size_t i = 0; i < way_points.size(); ++i) {
    //     std::cout << "初始cell扫线waypoint " << (i + 1) << ": (" << way_points[i].point.x() << ", "
    //               << way_points[i].point.y() << "), spray_on: " << way_points[i].spray_on << std::endl;
    // }

    auto cell_intersections = calculateCellIntersections(bcd_cells, cell_graph);
    // FIX: 封装处理剩余cell的主循环为独立函数
    processRemainingCells(bcd_cells, cells_sweeps_clockwise, cells_sweeps_anticlockwise, cell_intersections, pwh,
                          cell_graph, way_points, point, current_cell_idx);

    if (std::any_of(cell_graph.begin(), cell_graph.end(), [](const auto& cell) { return !cell.is_cleaned; }))
        throw std::runtime_error("Incomplete cell coverage");

    // 将 geo Point_2 转换为 geometry::Point2d（直接使用 double 坐标值）
    for (auto &p : way_points) {
        field_path::WaypointWithSpray wp;
        wp.point = geometry::Point2d(p.point.x(), p.point.y());
        wp.spray_on = p.spray_on;
        path.push_back(wp);
    }

    return path;
}
