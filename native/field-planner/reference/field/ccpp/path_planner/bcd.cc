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

#include <vector>
#include <list>
#include <algorithm>
#include <iostream>
#include <iterator>
#include <cmath>

#include "bcd.h"
#include "../geo/geo_comm.h"


namespace polygon_coverage_planning {

// =====================================================================
// 辅助: 使用标准的前向/后向循环访问（现在 circulator 已具备 STL 迭代器语义）
// =====================================================================

// Approximate segment equality: compare endpoints with geo::approx_equal.
// Treat segments equal regardless of orientation (source/target swapped).
static inline bool segmentApproxEqual(const Segment_2 &a, const Segment_2 &b) {
    if ((geo::approx_equal(a.source(), b.source()) && geo::approx_equal(a.target(), b.target()))
        || (geo::approx_equal(a.source(), b.target()) && geo::approx_equal(a.target(), b.source())))
        return true;
    return false;
}

static geo::FT sampleShift(const PolygonWithHoles &pwh, const Point_2 &v) {
    geo::FT min_x = std::numeric_limits<geo::FT>::max();
    geo::FT max_x = -min_x;
    geo::FT min_y = min_x;
    geo::FT max_y = max_x;

    const auto update_bounds = [&](const Point_2 &p) {
        min_x = std::min(min_x, p.x());
        max_x = std::max(max_x, p.x());
        min_y = std::min(min_y, p.y());
        max_y = std::max(max_y, p.y());
    };

    for (auto it = pwh.outer_boundary().vertices_begin(); it != pwh.outer_boundary().vertices_end(); ++it) {
        update_bounds(*it);
    }
    for (auto hit = pwh.holes_begin(); hit != pwh.holes_end(); ++hit) {
        for (auto it = hit->vertices_begin(); it != hit->vertices_end(); ++it) {
            update_bounds(*it);
        }
    }

    const geo::FT bbox_scale = std::max<geo::FT>(
        {1.0, std::fabs(max_x - min_x), std::fabs(max_y - min_y), std::fabs(v.x()), std::fabs(v.y())});

    // 采用与多边形整体尺度一致的偏移量；只依赖顶点坐标的旧写法在
    // 大尺度/近边界场景下容易把采样点仍然判成 ON_BOUNDARY。
    return std::max<geo::FT>(1e-6 * bbox_scale, 10.0 * std::numeric_limits<geo::FT>::epsilon() * bbox_scale);
}

static Point_2 sampleOutsidePoint(const PolygonWithHoles &pwh, const Point_2 &v, bool toward_left) {
    const geo::FT shift = sampleShift(pwh, v);
    return toward_left ? Point_2(v.x() - shift, v.y()) : Point_2(v.x() + shift, v.y());
}

// =====================================================================
// BCD (Boustrophedon Cell Decomposition) 主算法
// =====================================================================

std::vector<Polygon_2> computeBCD(const PolygonWithHoles &polygon_in, const Direction_2 &dir) {
    // Step 1: 旋转多边形使方向对齐到 X 轴
    PolygonWithHoles rotated_polygon = rotatePolygon(polygon_in, dir);
    sortPolygon(&rotated_polygon);
    simplifyPolygon(&rotated_polygon);

    // Step 2: 按 X 值排序所有顶点
    VertexConstCirculatorVec sorted_vertices = getXSortedVertices(rotated_polygon);

    // Step 3: 初始化边列表 L，扫描线处理
    std::list<Segment_2> L;
    std::list<Polygon_2> open_polygons;
    std::vector<Polygon_2> closed_polygons;
    std::vector<Point_2> processed_vertices;

    for (size_t i = 0; i < sorted_vertices.size(); ++i) {
        const VertexConstCirculator &v = sorted_vertices[i];
        // 跳过已处理过的顶点
        if (std::find(processed_vertices.begin(), processed_vertices.end(), *v) != processed_vertices.end())
            continue;
        processEvent(rotated_polygon, v, &sorted_vertices, &processed_vertices, &L, &open_polygons, &closed_polygons);
    }

    // Step 4: 将所有分解后的单元旋转回原始坐标系
    for (Polygon_2 &p : closed_polygons) {
        // 反向旋转: 将 X 轴方向旋转回 dir 方向
        FT dx = dir.dx(), dy = dir.dy();
        FT angle = std::atan2(dy, dx); // 正向旋转角度
        FT cos_v = std::cos(angle);
        FT sin_v = std::sin(angle);
        Aff_transformation_2 rotation(Aff_transformation_2::ROTATION, sin_v, cos_v);

        std::vector<Point_2> new_verts;
        new_verts.reserve(p.size());
        for (size_t i = 0; i < p.size(); ++i) {
            new_verts.push_back(rotation.transform(p[i]));
        }
        p = Polygon_2(new_verts.begin(), new_verts.end());
    }

    return closed_polygons;
}

// =====================================================================
// 辅助函数: 按 X 坐标排序所有顶点
// =====================================================================

VertexConstCirculatorVec getXSortedVertices(const PolygonWithHoles &p) {
    // 计算总顶点数并预分配
    size_t total_vertices = p.outer_boundary().size();
    for (auto hit = p.holes_begin(); hit != p.holes_end(); ++hit) {
        total_vertices += hit->size();
    }

    VertexConstCirculatorVec sorted_vertices;
    sorted_vertices.reserve(total_vertices);

    // 外边界顶点
    auto vc = p.outer_boundary().vertices_circulator();
    do {
        sorted_vertices.push_back(vc);
    } while (++vc != p.outer_boundary().vertices_circulator());

    // 孔洞顶点
    for (auto hit = p.holes_begin(); hit != p.holes_end(); ++hit) {
        auto vh = hit->vertices_circulator();
        do {
            sorted_vertices.push_back(vh);
        } while (++vh != hit->vertices_circulator());
    }

    // 按 (x, y) 字典序排序 (替代 Polygon_2::Traits::Less_xy_2)
    std::sort(sorted_vertices.begin(), sorted_vertices.end(),
              [](const VertexConstCirculator &a, const VertexConstCirculator &b) -> bool {
                  return geo::lexicographically_xy_smaller(*a, *b);
              });

    return sorted_vertices;
}

// =====================================================================
// 核心函数: 扫描线事件处理
// =====================================================================

void processEvent(const PolygonWithHoles &pwh, const VertexConstCirculator &v,
                  VertexConstCirculatorVec *sorted_vertices, std::vector<Point_2> *processed_vertices,
                  std::list<Segment_2> *L, std::list<Polygon_2> *open_polygons,
                  std::vector<Polygon_2> *closed_polygons) {
    // 计算过顶点 v 的垂直扫描线与 L 中所有边的交点
    Line_2 l(*v, Direction_2(0, 1)); // 垂直线 (x = const)
    std::vector<Point_2> intersections = getIntersections(*L, l);

    // 获取 v 的前驱边和后继边
    Segment_2 e_prev(*v, *std::prev(v));
    Segment_2 e_next(*v, *std::next(v));

    // 处理垂直边的情况 (替代 Equal_x_2)
    if (geo::approx_equal(e_prev.source().x(), e_prev.target().x())) {
        e_prev = Segment_2(*std::prev(v), *std::prev(v, 2));
    } else if (geo::approx_equal(e_next.source().x(), e_next.target().x())) {
        e_next = Segment_2(*std::next(v), *std::next(v, 2));
    }

    // 替代 Less_y_2 / Less_x_2 的 lambda 比较器
    // NOTE: 不能加 EPSILON 容差。CGAL 的 Less_x_2 / Less_y_2 是精确谓词，
    // 加 EPSILON 会在差值 < EPSILON 时导致 less_x() 和 !less_x() 同时为 false，
    // 从而将 OUT 事件误判为 UPDATE 事件，导致 L 列表管理错误、cell 无法闭合。
    auto less_y = [](const Point_2 &a, const Point_2 &b) -> bool { return a.y() < b.y(); };
    auto less_x = [](const Point_2 &a, const Point_2 &b) -> bool { return a.x() < b.x(); };

    Segment_2 e_lower = e_prev;
    Segment_2 e_upper = e_next;
    const geo::FT shift = sampleShift(pwh, *v);

    // ====== 分支 1: OUT 事件 (两条边的终点都在左侧) ======
    if (less_x(e_prev.target(), e_prev.source()) && less_x(e_next.target(), e_next.source())) {
        Point_2 p_on_upper =
            geo::approx_equal(e_lower.source(), e_upper.source()) ? e_upper.target() : e_upper.source();
        if (e_lower.supporting_line().signed_distance(p_on_upper) > 0.0)
            std::swap(e_lower, e_upper);

        // 判断是关闭一个还是关闭两个并打开一个
        // 垂直边事件取两端中点，避免旋转舍入使采样落在端点延长线上。
        const Point_2 sample_out = sampleOutsidePoint(pwh, Point_2(v->x(), (e_lower.source().y() + e_upper.source().y()) / 2.0), false);
        bool close_one = outOfPWH(pwh, sample_out);

        // 在边列表中定位 e_lower (使用近似端点匹配)
        auto e_lower_it = L->begin();
        size_t e_lower_id = 0;
        for (; e_lower_it != L->end(); ++e_lower_it) {
            if (segmentApproxEqual(*e_lower_it, e_lower))
                break;
            e_lower_id++;
        }

        if (e_lower_it == L->end()) {
            std::cerr << "[WARN] processEvent: e_lower not found in L at OUT event, skipping" << std::endl;
            return;
        }

        auto e_upper_it = std::next(e_lower_it);

        size_t e_upper_id = e_lower_id + 1;
        size_t lower_cell_id = e_lower_id / 2;
        size_t upper_cell_id = e_upper_id / 2;

        if (open_polygons->empty() || lower_cell_id >= open_polygons->size()
            || upper_cell_id >= open_polygons->size()) {
            std::cerr << "[WARN] processEvent: invalid cell_id at OUT event lower=" << lower_cell_id
                      << " upper=" << upper_cell_id << " size=" << open_polygons->size() << ", skipping" << std::endl;
            return;
        }

        if (close_one) {
            // 关闭一个单元格
            auto cell = std::next(open_polygons->begin(), lower_cell_id);
            cell->push_back(e_lower.source());
            if (!geo::approx_equal(e_lower.source(), e_upper.source())) {
                cell->push_back(e_upper.source());
            }
            bool cleaned = cleanupPolygon(&*cell);
            if (cleaned) {
                closed_polygons->push_back(*cell);
            } else {
                std::cerr << "[DEBUG] processEvent: OUT close_one, cell not cleaned, skipping adding to closed_polygons"
                          << std::endl;
            }
            L->erase(e_lower_it);
            L->erase(e_upper_it);
            open_polygons->erase(cell);
        } else {
            // 关闭两个单元格，打开一个新单元格
            if (e_lower_id == 0 || e_lower_id >= intersections.size() || e_upper_id + 1 >= intersections.size()) {
                std::cerr << "[WARN] processEvent: intersections index OOB at OUT close_two"
                          << ", e_lower_id=" << e_lower_id << ", e_upper_id=" << e_upper_id
                          << ", intersections_size=" << intersections.size() << ", skipping" << std::endl;
                return;
            }

            // 关闭下侧单元格
            auto lower_cell = std::next(open_polygons->begin(), lower_cell_id);
            lower_cell->push_back(intersections[e_lower_id - 1]);
            lower_cell->push_back(intersections[e_lower_id]);
            bool lower_cleaned = cleanupPolygon(&*lower_cell);
            if (lower_cleaned) {
                closed_polygons->push_back(*lower_cell);
            } else {
                std::cerr
                    << "[DEBUG] processEvent: OUT close_two, lower cell not cleaned, skipping adding to closed_polygons"
                    << std::endl;
            }

            // 关闭上侧单元格
            auto upper_cell = std::next(open_polygons->begin(), upper_cell_id);
            upper_cell->push_back(intersections[e_upper_id]);
            upper_cell->push_back(intersections[e_upper_id + 1]);
            bool upper_cleaned = cleanupPolygon(&*upper_cell);
            if (upper_cleaned) {
                closed_polygons->push_back(*upper_cell);
            } else {
                std::cerr
                    << "[DEBUG] processEvent: OUT close_two, upper cell not cleaned, skipping adding to closed_polygons"
                    << std::endl;
            }

            // 从边列表中删除旧边
            L->erase(e_lower_it);
            L->erase(e_upper_it);

            // 打开一个新单元格
            auto new_polygon = open_polygons->insert(lower_cell, Polygon_2());
            new_polygon->push_back(intersections[e_upper_id + 1]);
            new_polygon->push_back(intersections[e_lower_id - 1]);

            open_polygons->erase(lower_cell);
            open_polygons->erase(upper_cell);
        }
        processed_vertices->push_back(e_lower.source());
        if (!geo::approx_equal(e_lower.source(), e_upper.source())) {
            processed_vertices->push_back(e_upper.source());
        }
    }
    // ====== 分支 2: IN 事件 (两条边的起点都在右侧) ======
    else if (!less_x(e_lower.target(), e_lower.source()) && !less_x(e_upper.target(), e_upper.source())) {
        Point_2 p_on_lower =
            geo::approx_equal(e_lower.source(), e_upper.source()) ? e_lower.target() : e_lower.source();
        if (e_upper.supporting_line().signed_distance(p_on_lower) > 0.0)
            std::swap(e_lower, e_upper);

        // 判断是打开一个还是关闭一个并打开两个
        // 与 OUT 事件采用同一垂直边中点规则。
        const Point_2 sample_in = sampleOutsidePoint(pwh, Point_2(v->x(), (e_lower.source().y() + e_upper.source().y()) / 2.0), true);
        bool open_one = outOfPWH(pwh, sample_in);
        // Dump current L contents

        // 定位插入位置
        size_t e_LOWER_id = 0;
        bool found_e_lower_id = false;
        for (size_t i = 0; i + 1 < intersections.size(); i = i + 2) {
            if (open_one) {
                if (less_y(intersections[i], e_lower.source()) && less_y(intersections[i + 1], e_upper.source())) {
                    e_LOWER_id = i;
                    found_e_lower_id = true;
                }
            } else {
                if (less_y(intersections[i], e_lower.source()) && less_y(e_upper.source(), intersections[i + 1])) {
                    e_LOWER_id = i;
                }
            }
        }

        if (open_one) {
            // 打开一个新单元格
            auto e_UPPER = L->begin();
            auto open_cell = open_polygons->begin();
            if (!L->empty() && found_e_lower_id) {
                e_UPPER = std::next(e_UPPER, e_LOWER_id + 1);
                open_cell = std::next(open_cell, e_LOWER_id / 2 + 1);
            }

            // 更新边列表
            if (L->empty()) {
                L->insert(L->end(), e_lower);
                L->insert(L->end(), e_upper);
            } else if (!L->empty() && !found_e_lower_id) {
                L->insert(L->begin(), e_upper);
                L->insert(L->begin(), e_lower);
            } else {
                auto inserter = std::next(e_UPPER);
                L->insert(inserter, e_lower);
                L->insert(inserter, e_upper);
            }

            // 创建新多边形
            auto open_polygon = open_polygons->insert(open_cell, Polygon_2());
            open_polygon->push_back(e_upper.source());
            if (!geo::approx_equal(e_lower.source(), e_upper.source())) {
                open_polygon->push_back(e_lower.source());
            }
        } else {
            // JNI中不得解引用end；不完整分解必须明确失败而非继续输出路线。
            if (e_LOWER_id >= L->size() || e_LOWER_id / 2 >= open_polygons->size() || e_LOWER_id + 1 >= intersections.size())
                throw std::runtime_error("Invalid decomposition IN-event cell");
            auto e_LOWER = std::next(L->begin(), e_LOWER_id);
            auto cell = std::next(open_polygons->begin(), e_LOWER_id / 2);

            // 插入新的 e_lower, e_upper 到边列表（跳过退化边和重复边）
            std::list<Segment_2>::iterator e_lower_it = L->insert(std::next(e_LOWER), e_lower);
            L->insert(std::next(e_lower_it), e_upper);

            // 添加新单元格
            auto new_polygon = open_polygons->insert(cell, Polygon_2());

            // 关闭当前单元格
            cell->push_back(intersections[e_LOWER_id]);
            cell->push_back(intersections[e_LOWER_id + 1]);
            bool in_close_cleaned = cleanupPolygon(&*cell);
            if (in_close_cleaned) {
                closed_polygons->push_back(*cell);
            } else {
                std::cerr << "[DEBUG] processEvent: IN close_one, cell not cleaned, skipping adding to closed_polygons"
                          << std::endl;
            }
            // 下侧新单元格
            new_polygon->push_back(e_lower.source());
            new_polygon->push_back(intersections[e_LOWER_id]);

            // 上侧新单元格
            new_polygon = open_polygons->insert(cell, Polygon_2());
            new_polygon->push_back(intersections[e_LOWER_id + 1]);
            new_polygon->push_back(e_upper.source());

            // 移除已关闭的单元格
            open_polygons->erase(cell);
        }
        processed_vertices->push_back(e_lower.source());
        if (!geo::approx_equal(e_lower.source(), e_upper.source())) {
            processed_vertices->push_back(e_upper.source());
        }
    }
    // ====== 分支 3: 边更新事件 (一条边进一条边出) ======
    else {
        // 在边列表中查找匹配的边
        VertexConstCirculator v_middle = v;
        auto it = L->end();

        while (it == L->end()) {
            for (it = L->begin(); it != L->end(); it++) {
                if (geo::approx_equal(*v_middle, it->source()) || geo::approx_equal(*v_middle, it->target())) {
                    // 如果需要，交换排序位置
                    if (!geo::approx_equal((*v).x(), (*v_middle).x())
                        || !geo::approx_equal((*v).y(), (*v_middle).y())) {
                        auto i_v = sorted_vertices->end();
                        auto i_v_middle = sorted_vertices->end();
                        for (auto sit = sorted_vertices->begin(); sit != sorted_vertices->end(); ++sit) {
                            if (*sit == v)
                                i_v = sit;
                            if (*sit == v_middle)
                                i_v_middle = sit;
                        }
                        std::iter_swap(i_v, i_v_middle);
                    }
                    break;
                }
            }
            if (it == L->end()) {
                // 尝试相邻顶点（使用 circ_prev/circ_next）
                auto v_prev = std::prev(v_middle);
                auto v_next = std::next(v_middle);

                if (geo::approx_equal((*v_prev).x(), (*v_middle).x()))
                    v_middle = v_prev;
                else
                    v_middle = v_next;
            }
        }

        // 更新边引用
        e_prev = Segment_2(*v_middle, *std::prev(v_middle));
        e_next = Segment_2(*v_middle, *std::next(v_middle));

        // 在边列表中找到要替换的边
        auto old_e_it = L->begin();
        Segment_2 new_edge;
        size_t edge_id = 0;
        for (; old_e_it != L->end(); ++old_e_it) {
            if (segmentApproxEqual(*old_e_it, e_next)) {
                new_edge = e_prev;
                break;
            } else if (segmentApproxEqual(*old_e_it, e_prev)) {
                new_edge = e_next;
                break;
            }
            edge_id++;
        }

        // 检查是否找到了匹配的边
        if (old_e_it == L->end()) {
            std::cerr << "[WARN] processEvent: UPDATE: no matching edge found in L at (" << (*v).x() << ", " << (*v).y()
                      << "), skipping" << std::endl;
            return;
        }

        // 更新对应的多边形单元格
        size_t cell_id = edge_id / 2;

        // 安全检查：确保 open_polygons 不为空且 cell_id 有效
        if (open_polygons->empty() || cell_id >= open_polygons->size()) {
            std::cerr << "[WARN] processEvent: open_polygons empty or invalid cell_id=" << cell_id
                      << ", size=" << open_polygons->size() << std::endl;
            return; // 跳过此事件
        }

        auto cell = std::next(open_polygons->begin(), cell_id);
        if ((edge_id % 2) == 0) {
            cell->push_back(new_edge.source());
        } else {
            cell->insert(cell->vertices_begin(), new_edge.source());
        }

        // 替换边
        L->insert(old_e_it, new_edge);
        L->erase(old_e_it);

        processed_vertices->push_back(*v_middle);
    }

    // geometry::Polygon2d out_polygon;

    // Polygon_2::Vertex_const_iterator d = pwh.outer_boundary().vertices_begin();
    // do {
    //     geometry::Point2d p(d->x(), d->y());
    //     out_polygon.outer().push_back(p);
    // } while (++d != pwh.outer_boundary().vertices_end());

    // geometry::MultiPolygon2d multi_open_polygon;
    // std::list<Polygon_2>::iterator poly_itr = open_polygons->begin();
    // for (; poly_itr != open_polygons->end(); ++poly_itr) {
    //     geometry::Polygon2d poly;
    //     Polygon_2::Vertex_const_iterator vit = poly_itr->vertices_begin();
    //     for (; vit != poly_itr->vertices_end(); ++vit) {
    //         geometry::Point2d p(vit->x(), vit->y());
    //         poly.outer().push_back(p);
    //     }
    //     multi_open_polygon.push_back(poly);
    // }

    // geometry::MultiPolygon2d multi_closed_polygon;
    // std::vector<Polygon_2>::iterator closed_poly_itr = closed_polygons->begin();
    // for (; closed_poly_itr != closed_polygons->end(); ++closed_poly_itr) {
    //     geometry::Polygon2d poly;
    //     Polygon_2::Vertex_const_iterator vit = closed_poly_itr->vertices_begin();
    //     for (; vit != closed_poly_itr->vertices_end(); ++vit) {
    //         geometry::Point2d p(vit->x(), vit->y());
    //         poly.outer().push_back(p);
    //     }
    //     multi_closed_polygon.push_back(poly);
    // }

    // geometry::MultiLinestring2d multi_lines;
    // std::list<Segment_2>::iterator itr = L->begin();
    // for (; itr != L->end(); ++itr) {
    //     geometry::Point2d p1(itr->source().x(), itr->source().y());
    //     geometry::Point2d p2(itr->target().x(), itr->target().y());
    //     geometry::Linestring2d line{p1, p2};
    //     multi_lines.push_back(line);
    // }

    // geometry::MultiPoint2d multi_points;
    // for (const Point_2& p : intersections) {
    //     geometry::Point2d point(p.x(),p.y());
    //     multi_points.push_back(point);
    // }
}

// =====================================================================
// 辅助函数: 计算扫描线与边列表的所有交点
// =====================================================================

std::vector<Point_2> getIntersections(const std::list<Segment_2> &L, const Line_2 &l) {
    std::vector<Point_2> intersections;
    intersections.reserve(L.size());

    // BCD scanline is always vertical: Line_2(*v, Direction_2(0, 1))
    FT scanline_x = l.point().x();

    int edge_idx = 0;
    for (auto it = L.begin(); it != L.end(); ++it) {
        // Check for degenerate segment (source == target)
        if (geo::approx_equal(it->source(), it->target())) {
            // Always push the source point to preserve L<->intersection index mapping
            intersections.push_back(it->source());
            if (geo::approx_equal(it->source().x(), scanline_x)) {
                std::cerr << "[DEBUG] getIntersections: edge[" << edge_idx << "] degenerate, on scanline" << std::endl;
            } else {
                std::cerr << "[DEBUG] getIntersections: edge[" << edge_idx
                          << "] degenerate, OFF scanline but pushed for index" << std::endl;
            }
            edge_idx++;
            continue;
        }

        // --- Explicit vertical segment handling ---
        // Vertical Line_2 constructor (a_=1, b_=0) is parallel to vertical
        // scanline (a_=1, b_=0), so intersection_point() throws.  Handle
        // this case directly before falling into the generic three-step
        // logic.
        if (geo::approx_equal(it->source().x(), it->target().x())) {
            FT segment_x = it->source().x();
            if (geo::approx_equal(segment_x, scanline_x)) {
                intersections.push_back(it->target());
            } else {
                std::cerr << "[DEBUG] getIntersections: edge[" << edge_idx
                          << "] vertical OFFSET from scanline (seg_x=" << segment_x << " scanline_x=" << scanline_x
                          << ")" << std::endl;
            }
            edge_idx++;
            continue;
        }

        Point_2 intersection_pt;

        // Step 1: primary — geo::intersection
        if (geo::intersection(*it, l, intersection_pt)) {
            intersections.push_back(intersection_pt);
            edge_idx++;
            continue;
        }

        // Step 2: fallback — supporting_line intersection, verify on segment
        try {
            Point_2 pt = it->supporting_line().intersection_point(l);
            if (it->has_on(pt)) {
                intersections.push_back(pt);
                std::cerr << "[DEBUG] getIntersections: edge[" << edge_idx << "] Step2 supporting_line OK at ("
                          << pt.x() << "," << pt.y() << ")" << std::endl;
                edge_idx++;
                continue;
            } else {
                std::cerr << "[DEBUG] getIntersections: edge[" << edge_idx
                          << "] Step2 intersection_point OK but has_on FAILED " << "pt=(" << pt.x() << "," << pt.y()
                          << ")" << std::endl;
            }
        } catch (...) {
            std::cerr << "[DEBUG] getIntersections: edge[" << edge_idx
                      << "] Step2 supporting_line parallel to scanline (exception)" << std::endl;
        }

        // Step 3: collinear — segment lies on the scanline
        if (l.has_on(it->source()) && l.has_on(it->target())) {
            intersections.push_back(it->target());
            std::cerr << "[DEBUG] getIntersections: edge[" << edge_idx << "] Step3 collinear, pushed target"
                      << std::endl;
            edge_idx++;
            continue;
        } else {
            bool src_on = l.has_on(it->source());
            bool tgt_on = l.has_on(it->target());
            std::cerr << "[DEBUG] getIntersections: edge[" << edge_idx << "] Step3 collinear check: src_on=" << src_on
                      << " tgt_on=" << tgt_on << std::endl;
        }

        // All methods exhausted — use manual linear interpolation fallback
        // (can happen when segment is still in L past its x-range due to
        //  upstream classification edge cases; defense-in-depth)
        FT x1s = it->source().x(), y1s = it->source().y();
        FT x2s = it->target().x(), y2s = it->target().y();
        if (!geo::approx_equal(x1s, x2s)) {
            FT t_val = (scanline_x - x1s) / (x2s - x1s);
            FT y_interp = y1s + t_val * (y2s - y1s);
            intersections.push_back(Point_2(scanline_x, y_interp));
            std::cerr << "[WARN] getIntersections: all methods failed for segment " << "source=(" << x1s << ", " << y1s
                      << ") " << "target=(" << x2s << ", " << y2s << ") " << "scanline x=" << scanline_x
                      << " — using interpolated pt=(" << scanline_x << "," << y_interp << ")" << std::endl;
        } else {
            std::cerr << "[WARN] getIntersections: all methods failed for vertical segment " << "source=(" << x1s
                      << ", " << y1s << ") " << "target=(" << x2s << ", " << y2s << ") " << "scanline x=" << scanline_x
                      << " — no intersection possible, skipping" << std::endl;
        }
        edge_idx++;
    }

    return intersections;
}

// =====================================================================
// 辅助函数
// =====================================================================

void sortPolygon(PolygonWithHoles *pwh) {
    if (pwh->outer_boundary().is_clockwise_oriented())
        pwh->outer_boundary().reverse_orientation();

    for (auto hi = pwh->holes_begin(); hi != pwh->holes_end(); ++hi)
        if (hi->is_counterclockwise_oriented())
            hi->reverse_orientation();
}

bool cleanupPolygon(Polygon_2 *poly) {
    if (poly->size() <= 2)
        return false;

    // 使用容器直接操作移除连续重复的顶点（替代不可靠的 circulator erase）
    auto &verts = poly->container();

    // 第一遍：移除相邻重复顶点
    bool changed = true;
    while (changed) {
        changed = false;
        size_t n = verts.size();
        if (n <= 2)
            break;
        for (size_t i = 0; i < n; ++i) {
            size_t j = (i + 1) % n;
            if (geo::approx_equal(verts[i].x(), verts[j].x()) && geo::approx_equal(verts[i].y(), verts[j].y())) {
                verts.erase(verts.begin() + j);
                changed = true;
                break; // 重启循环因为迭代器已失效
            }
        }
    }

    // 第二遍：用 std::unique 全局去重
    if (verts.size() > 2) {
        auto last = std::unique(verts.begin(), verts.end(), [](const Point_2 &a, const Point_2 &b) {
            return geo::approx_equal(a.x(), b.x()) && geo::approx_equal(a.y(), b.y());
        });
        verts.erase(last, verts.end());
    }

    return poly->is_simple() && std::fabs(poly->area()) > geo::EPSILON;
}

bool outOfPWH(const PolygonWithHoles &pwh, const Point_2 &p) {
    // BCD 扫描线在顶点附近会用极小偏移去采样左右侧点；由于数值误差，
    // 采样点有时会落在外边界或孔洞边界上。对这类“边界点”也应视为
    // “在多边形外侧”，否则会把 IN/OUT 事件误判成同一类。
    const auto outer_side = pwh.outer_boundary().bounded_side(p);
    if (outer_side == geo::ON_BOUNDARY) {
        std::cerr << "[DEBUG] outOfPWH: point (" << p.x() << "," << p.y() << ") is ON BOUNDARY of outer polygon"
                  << std::endl;
    }
    if (outer_side == geo::ON_UNBOUNDED_SIDE) {
        return true;
    }

    for (auto hit = pwh.holes_begin(); hit != pwh.holes_end(); ++hit) {
        const auto hole_side = hit->bounded_side(p);
        if (hole_side == geo::ON_BOUNDARY) {
            std::cerr << "[DEBUG] outOfPWH: point (" << p.x() << "," << p.y() << ") is ON BOUNDARY of hole polygon"
                      << std::endl;
        }
        if (hole_side == geo::ON_BOUNDED_SIDE) {
            return true; // 点在孔洞内部或其边界上 → 视为"外部"
        }
    }

    return false;
}

} // namespace polygon_coverage_planning