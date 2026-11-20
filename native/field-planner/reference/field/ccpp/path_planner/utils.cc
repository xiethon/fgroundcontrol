#include "utils.h"
#include <cmath>
#include <limits>
#include "coverage_planner.hpp"

std::vector<CellNode> calculateDecompositionAdjacency(const std::vector<Polygon_2> &decompositions) {
    std::vector<CellNode> polygon_adj_graph(decompositions.size());
    if (decompositions.empty()) return polygon_adj_graph;
    for (size_t i = 0; i + 1 < decompositions.size(); ++i) {
        polygon_adj_graph[i].cell_index = i;
        for (size_t j = i + 1; j < decompositions.size(); ++j) {
            // 使用 geo::do_intersect 检测两个多边形是否相邻（有重叠/接触）
            if (geo::do_intersect(decompositions[i], decompositions[j])) {
                polygon_adj_graph[i].neighbor_indices.emplace_back(j);
                polygon_adj_graph[j].neighbor_indices.emplace_back(i);
            }
        }
    }
    polygon_adj_graph.back().cell_index = decompositions.size() - 1;

    return polygon_adj_graph;
}

// DFS
void walkThroughGraph(std::vector<CellNode> &cell_graph, int cell_index, int &unvisited_counter,
                      std::deque<CellNode> &path) {
    if (!cell_graph[cell_index].is_visited) {
        cell_graph[cell_index].is_visited = true;
        unvisited_counter--;
    }
    path.emplace_front(cell_graph[cell_index]);

    //    for debugging
    //    std::cout<< "cell: " <<cell_graph[cell_index].cell_index<<std::endl;
    //

    CellNode neighbor;
    int neighbor_idx = INT_MAX;

    for (int i = 0; i < cell_graph[cell_index].neighbor_indices.size(); i++) {
        neighbor = cell_graph[cell_graph[cell_index].neighbor_indices[i]];
        neighbor_idx = cell_graph[cell_index].neighbor_indices[i];
        if (!neighbor.is_visited) {
            break;
        }
    }

    // unvisited neighbor found
    if (!neighbor.is_visited) {
        cell_graph[neighbor_idx].parent_index = cell_graph[cell_index].cell_index;
        walkThroughGraph(cell_graph, neighbor_idx, unvisited_counter, path);
    }
    // unvisited neighbor not found
    else {
        // cannot go on back-tracking
        if (cell_graph[cell_index].parent_index == INT_MAX) {
            return;
        } else if (unvisited_counter == 0) {
            return;
        } else {
            walkThroughGraph(cell_graph, cell_graph[cell_index].parent_index, unvisited_counter, path);
        }
    }
}

std::deque<int> getTravellingPath(const std::vector<CellNode> &cell_graph, int firstcell_index) {
    std::deque<int> travelling_path;

    std::deque<CellNode> _cell_path;
    std::vector<CellNode> _cell_graph = cell_graph;

    if (_cell_graph.size() == 1) {
        travelling_path.emplace_back(0);
    } else {
        int unvisited_counter = _cell_graph.size();
        walkThroughGraph(_cell_graph, firstcell_index, unvisited_counter, _cell_path);
        std::reverse(_cell_path.begin(), _cell_path.end());
    }

    for (auto &cell : _cell_path) {
        travelling_path.emplace_back(cell.cell_index);
    }

    return travelling_path;
}

std::vector<std::map<int, std::list<Point_2>>> calculateCellIntersections(std::vector<Polygon_2> &decompositions,
                                                                          std::vector<CellNode> &cell_graph) {
    std::vector<std::map<int, std::list<Point_2>>> cell_intersections(cell_graph.size());

    for (size_t i = 0; i < cell_graph.size(); ++i) {
        for (size_t j = 0; j < cell_graph[i].neighbor_indices.size(); ++j) {
            std::list<Point_2> pts;
            // 边界匹配（线段相交）
            for (auto m = decompositions[i].edges_begin(); m != decompositions[i].edges_end(); ++m) {
                for (auto n = decompositions[cell_graph[i].neighbor_indices[j]].edges_begin();
                     n != decompositions[cell_graph[i].neighbor_indices[j]].edges_end(); ++n) {
                    Point_2 inter_pt;
                    geo::intersection_segment_result inter_res = geo::intersection(*m, *n);
                    if (inter_res.success) {
                        Point_2 inter_pt = inter_res.point;
                        pts.push_back(inter_pt);
                    }
                }
            }

            // 顶点匹配（精确坐标比较）
            for (auto p = decompositions[i].vertices_begin(); p != decompositions[i].vertices_end(); ++p) {
                for (auto q = decompositions[cell_graph[i].neighbor_indices[j]].vertices_begin();
                     q != decompositions[cell_graph[i].neighbor_indices[j]].vertices_end(); ++q) {
                    if (geo::approx_equal(p->x(), q->x()) && geo::approx_equal(p->y(), q->y())) {
                        pts.insert(pts.end(), *p);
                    }
                }
            }

            auto verbose = std::unique(pts.begin(), pts.end());
            pts.erase(verbose, pts.end());
            cell_intersections[i].insert(std::make_pair(cell_graph[i].neighbor_indices[j], pts));
            cell_intersections[cell_graph[i].neighbor_indices[j]].insert(std::make_pair(i, pts));
        }
    }

    return cell_intersections;
}

Point_2 findNextGoal(const Point_2 &start, const Point_2 &goal, const std::list<Point_2> &candidates) {
    double min_cost = std::numeric_limits<double>::max();
    double cost;
    Point_2 next_point = start;
    Segment_2 seg_from_start, seg_to_goal;
    for (auto point = candidates.begin(); point != candidates.end(); ++point) {
        seg_from_start = Segment_2(start, *point);
        seg_to_goal = Segment_2(*point, goal);
        cost = geo::squared_distance(start, *point) + geo::squared_distance(*point, goal);
        if (cost < min_cost) {
            min_cost = cost;
            next_point = *point;
        }
    }
    return next_point;
}

// 判断是否需要反转下一个扫线的顺序，原则是选择距离当前点更近的端点作为下一个目标点
bool doReverseNextSweep(const Point_2 &curr_point, const std::vector<GeopointsWithSpray> &next_sweep) {
    return geo::squared_distance(curr_point, next_sweep.front().point)
           > geo::squared_distance(curr_point, next_sweep.back().point);
}

int getCellIndexOfPoint(const std::vector<Polygon_2> &decompositions, const Point_2 &point) {
    int index = -1;
    for (int i = 0; i < decompositions.size(); i++) {
        if (polygon_coverage_planning::pointInPolygon(decompositions[i], point)) {
            index = i;
            break;
        }
    }
    return index;
}

// 处理hole超出boundary的情况，直接将hole从boundary中剪切掉
bool processInnerOverOuterBoundary(Polygon_2 &boundary_poly, std::vector<Polygon_2> &holes) {
    bool ret = true;
    // 使用迭代器遍历，这样可以在满足条件时安全删除当前元素
    for (auto it_h = holes.begin(); it_h != holes.end();) {
        Polygon_2 &hole = *it_h;
        if (hole.empty()) {
            ++it_h;
            continue;
        }

        bool erased = false;
        // 检查 hole 是否完全在 boundary_poly 内部
        for (auto it = hole.vertices_begin(); it != hole.vertices_end(); ++it) {
            if (boundary_poly.bounded_side(*it) != geo::Bounded_side::ON_BOUNDED_SIDE) {
                std::cout << "发现一个 hole 不完全在 boundary 内部，正在处理..." << std::endl;

                // 确保 hole 方向正确（CCW 外边界，CW 用于差集操作）
                Polygon_2 temp_hole = hole;
                if (temp_hole.is_clockwise_oriented()) {
                    temp_hole.reverse_orientation();
                }

                geo::Polygon_set ps_boundary(boundary_poly);
                ps_boundary.difference(temp_hole);
                std::vector<geo::PolygonWithHoles> result_vec = ps_boundary.polygons_with_holes();

                if (!result_vec.empty()) {
                    // 直接用第一个结果替换
                    boundary_poly = result_vec.front().outer_boundary();

                    // 如果产生了多个结果，可能需要合并或选择性地处理
                    if (result_vec.size() > 1) {
                        ret = false;
                        std::cout << "警告：产生了 " << result_vec.size() << " 个分离的多边形区域！！！" << std::endl;
                    }
                } else {
                    // 差集结果为空，处理空情况
                    ret = false;
                    std::cout << "差集结果为空，多边形被完全剪除" << std::endl;
                    boundary_poly = Polygon_2(); // 赋值为空
                }

                // 从 holes 中移除当前 hole，并更新迭代器
                it_h = holes.erase(it_h);
                erased = true;
                break; // 退出顶点循环
            }
        }
        if (!erased) {
            ++it_h; // 只有没删才前进
        }
    }
    return ret;
}

/**
 * @brief AB-T 作业模式航线规划 API
 *
 * @param a_x A点X坐标
 * @param a_y A点Y坐标
 * @param a_heading A点航向 (度)
 * @param b_x B点X坐标
 * @param b_y B点Y坐标
 * @param b_heading B点航向 (度)
 * @return std::vector<Point_2> 规划出的航线航点
 */

// 辅助函数：将角度(度)转换为方向向量
static Vector_2 degToVector(double deg) {
    double rad = deg * M_PI / 180.0;
    return Vector_2(std::cos(rad), std::sin(rad));
}

// 辅助函数：计算两个向量之间的夹角(弧度)，范围 [0, PI]
static double computeVectorAngle(const Vector_2 &v1, const Vector_2 &v2) {
    // 使用叉积和点积计算夹角
    double cross = v1.determinant(v2);
    double dot = v1.x() * v2.x() + v1.y() * v2.y();
    double angle_rad = std::atan2(std::fabs(cross), dot);
    return angle_rad;
}

std::vector<Point_2> generateABTWaypoints(double a_x, double a_y, double a_heading, double b_x, double b_y,
                                          double b_heading, double spacing, int max_lines) {
    std::vector<Point_2> waypoints;
    Point_2 a(a_x, a_y);
    Point_2 b(b_x, b_y);

    // --- 1. 新增判断：A点方向和B点方向不能大于150度 ---
    double heading_diff = std::fabs(a_heading - b_heading);
    if (heading_diff > 180.0) {
        heading_diff = 360.0 - heading_diff;
    }
    if (heading_diff > 150.0) {
        std::cerr << "日志提示: A、B点方向夹角过大 (超过150度)" << std::endl;
        return waypoints;
    }

    // --- 2. 计算基础向量 ---
    Vector_2 base_vec = b - a;
    Vector_2 vec_a = degToVector(a_heading);
    Vector_2 vec_b = degToVector(b_heading);

    // --- 3. 新增判断：A点方向和B点方向形成的夹角不能大于A点方向与A -> B向量方向的夹角 ---
    double angle_a_to_base_rad = computeVectorAngle(vec_a, base_vec);
    double angle_a_to_base_deg = angle_a_to_base_rad * 180.0 / M_PI;

    if (heading_diff >= angle_a_to_base_deg) {
        std::cerr << "日志提示: A、B点方向夹角过大 (扇形开口大于AB张角)" << std::endl;
        return waypoints;
    }

    // --- 4. 计算垂直向量 (根据AB相对于A航向的位置选择方向) ---
    Vector_2 normal_ccw(-base_vec.y(), base_vec.x());
    Vector_2 normal_cw(base_vec.y(), -base_vec.x());

    double cross_dir = geo::determinant(vec_a, base_vec);
    Vector_2 normal_vec = (cross_dir < 0) ? normal_ccw : normal_cw;

    double norm_len = std::sqrt(normal_vec.squared_length());
    normal_vec = normal_vec / norm_len;

    // --- 5. 构建边界射线 ---
    Ray_2 ray_a(a, vec_a);
    Ray_2 ray_b(b, vec_b);

    // --- 6. 新增：计算 A、B 射线的交点 ---
    Point_2 intersect_pt_result;
    bool has_intersection = geo::intersection(ray_a, ray_b, intersect_pt_result);
    Point_2 intersect_point;
    if (has_intersection) {
        intersect_point = intersect_pt_result;
    }

    // --- 7. 循环生成航线 ---
    for (int i = 0; i < max_lines; ++i) {
        double offset_dist = i * spacing;
        Point_2 offset_a = a + (normal_vec * offset_dist);
        Point_2 offset_b = b + (normal_vec * offset_dist);
        Line_2 line_i(offset_a, offset_b);

        // 计算与 A 射线的交点（Line/Ray intersection）
        Point_2 start_point;
        bool has_start = geo::intersection(line_i, ray_a, start_point);
        if (!has_start) {
            std::cerr << "平移方向航线与A点射线无交点" << std::endl;
            break;
        }

        // 计算与 B 射线的交点
        Point_2 end_point;
        bool has_end = geo::intersection(line_i, ray_b, end_point);
        if (!has_end) {
            std::cerr << "平移方向航线与B点射线无交点" << std::endl;
            break;
        }

        // --- 8. 新增：判断航线是否已超过交点 ---
        if (has_intersection) {
            // 计算航线起点到A点的距离（平方）
            double dist_to_start_sq = geo::squared_distance(start_point, a);

            // 计算交点到A点的距离（平方）
            double dist_to_intersect_sq = geo::squared_distance(intersect_point, a);

            // 如果航线起点已经超过了交点
            if (dist_to_start_sq > dist_to_intersect_sq) {
                break;
            }

            // 额外保护：检查航线长度是否小于间距的一半
            double seg_len_sq = geo::squared_distance(end_point, start_point);
            if (seg_len_sq < (spacing * spacing * 0.25)) {
                break;
            }
        }

        // AB-T 逻辑
        if (i % 2 != 0) {
            waypoints.push_back(end_point);
            waypoints.push_back(start_point);
        } else {
            waypoints.push_back(start_point);
            waypoints.push_back(end_point);
        }
    }

    return waypoints;
}
