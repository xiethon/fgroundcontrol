#include "merge_plots.h"
#include <iostream>

// --- 辅助函数 ---

double point2SegmentDistance(const geo::Point_2 &p, const geo::Segment_2 &s) {
    return std::sqrt(geo::squared_distance(p, s));
}

// 计算两个 PolygonWithHoles 之间的最小距离（使用外边界）
double polygonDistance(const geo::PolygonWithHoles &poly1, const geo::PolygonWithHoles &poly2) {
    const geo::Polygon_2 &boundary1 = poly1.outer_boundary();
    const geo::Polygon_2 &boundary2 = poly2.outer_boundary();

    if (boundary1.size() < 3 || boundary2.size() < 3)
        return std::numeric_limits<double>::max();
    double min_dist = std::numeric_limits<double>::max();

    std::vector<geo::Segment_2> edges1, edges2;
    for (auto it1 = boundary1.edges_begin(); it1 != boundary1.edges_end(); ++it1) {
        edges1.push_back(*it1);
    }
    for (auto it2 = boundary2.edges_begin(); it2 != boundary2.edges_end(); ++it2) {
        edges2.push_back(*it2);
    }

    for (const auto &seg1 : edges1) {
        for (const auto &seg2 : edges2) {
            double dist = std::sqrt(geo::squared_distance(seg1, seg2));
            if (dist < min_dist)
                min_dist = dist;
        }
    }

    for (auto it = boundary1.vertices_begin(); it != boundary1.vertices_end(); ++it) {
        for (const auto &seg : edges2) {
            double dist = point2SegmentDistance(*it, seg);
            if (dist < min_dist)
                min_dist = dist;
        }
    }
    for (auto it = boundary2.vertices_begin(); it != boundary2.vertices_end(); ++it) {
        for (const auto &seg : edges1) {
            double dist = point2SegmentDistance(*it, seg);
            if (dist < min_dist)
                min_dist = dist;
        }
    }

    return min_dist;
}

geo::Polygon_2 cleanPolygon(const geo::Polygon_2 &poly) {
    if (poly.size() < 3) {
        return geo::Polygon_2();
    }

    std::vector<geo::Point_2> cleaned_pts;
    cleaned_pts.reserve(poly.size());

    // Remove consecutive duplicate points.
    for (auto it = poly.vertices_begin(); it != poly.vertices_end(); ++it) {
        if (cleaned_pts.empty() || !(*it == cleaned_pts.back())) {
            cleaned_pts.push_back(*it);
        }
    }
    if (cleaned_pts.size() > 1 && cleaned_pts.front() == cleaned_pts.back()) {
        cleaned_pts.pop_back();
    }

    if (cleaned_pts.size() < 3) {
        return geo::Polygon_2();
    }

    // Remove collinear points.
    geo::Polygon_2 filtered;
    const std::size_t n = cleaned_pts.size();
    for (std::size_t i = 0; i < n; ++i) {
        const geo::Point_2 &prev = cleaned_pts[(i + n - 1) % n];
        const geo::Point_2 &curr = cleaned_pts[i];
        const geo::Point_2 &next = cleaned_pts[(i + 1) % n];

        if (geo::orientation(prev, curr, next) != geo::Orientation::COLLINEAR) {
            filtered.push_back(curr);
        }
    }

    if (filtered.size() < 3) {
        return geo::Polygon_2();
    }

    return filtered;
}

bool isValidPolygonWithHoles(const geo::PolygonWithHoles &poly) {
    const geo::Polygon_2 &outer = poly.outer_boundary();
    if (outer.size() < 3 || !outer.is_simple() || !outer.is_counterclockwise_oriented() || outer.area() <= 0) {
        return false;
    }

    for (auto hit = poly.holes_begin(); hit != poly.holes_end(); ++hit) {
        const geo::Polygon_2 &hole = *hit;
        if (hole.size() < 3 || !hole.is_simple() || hole.is_counterclockwise_oriented() || hole.area() >= 0) {
            return false;
        }
    }

    // Basic validity check: no self-intersection
    return outer.is_simple();
}

geo::PolygonWithHoles normalizePolygonWithHoles(const geo::PolygonWithHoles &poly) {
    geo::Polygon_2 outer = cleanPolygon(poly.outer_boundary());
    if (outer.size() < 3) {
        return geo::PolygonWithHoles();
    }
    if (!outer.is_counterclockwise_oriented()) {
        outer.reverse_orientation();
    }

    geo::PolygonWithHoles normalized(outer);
    for (auto hit = poly.holes_begin(); hit != poly.holes_end(); ++hit) {
        geo::Polygon_2 hole = cleanPolygon(*hit);
        if (hole.size() < 3) {
            continue;
        }
        if (hole.is_counterclockwise_oriented()) {
            hole.reverse_orientation();
        }
        normalized.add_hole(hole);
    }
    return normalized;
}

std::vector<geo::PolygonWithHoles> exteriorOffset(const geo::PolygonWithHoles &poly, double radius) {
    std::vector<geo::PolygonWithHoles> result;

    // Use Clipper2 offset to expand polygon outward.
    // exteriorOffset: positive delta expands the polygon.
    geo::Polygon_2 outer_offset = poly.outer_boundary().offset(radius);

    if (outer_offset.size() < 3) {
        return result;
    }
    if (!outer_offset.is_counterclockwise_oriented()) {
        outer_offset.reverse_orientation();
    }

    geo::PolygonWithHoles normalized(outer_offset);
    if (isValidPolygonWithHoles(normalized)) {
        result.push_back(std::move(normalized));
    } else {
        std::cerr << "警告:外扩结果无效，已跳过。" << std::endl;
    }

    return result;
}

std::vector<geo::PolygonWithHoles> interiorInset(const geo::PolygonWithHoles &poly, double radius) {
    std::vector<geo::PolygonWithHoles> result;

    // Use Clipper2 offset to shrink polygon inward (negative delta).
    // interiorInset: negative delta shrinks the polygon.
    geo::Polygon_2 outer_inset = poly.outer_boundary().offset(-radius);

    if (outer_inset.size() < 3) {
        std::cerr << "警告: 内缩结果为空（可能半径太大），已跳过。" << std::endl;
        return result;
    }
    if (!outer_inset.is_counterclockwise_oriented()) {
        outer_inset.reverse_orientation();
    }

    geo::PolygonWithHoles normalized(outer_inset);

    // Re-add holes with inward inset as well
    for (auto hit = poly.holes_begin(); hit != poly.holes_end(); ++hit) {
        // Holes shrink when we inset -> expand holes (positive offset on hole)
        geo::Polygon_2 hole_expanded = hit->offset(radius);
        if (hole_expanded.size() >= 3) {
            // Keep holes CW
            if (hole_expanded.is_counterclockwise_oriented()) {
                hole_expanded.reverse_orientation();
            }
            normalized.add_hole(hole_expanded);
        }
    }

    if (isValidPolygonWithHoles(normalized)) {
        result.push_back(std::move(normalized));
    } else {
        std::cerr << "警告: 内缩结果无效，已跳过。" << std::endl;
    }

    return result;
}

bool mergePlots(const std::vector<geo::PolygonWithHoles> &fields, std::vector<geo::PolygonWithHoles> &final_results,
                double threshold, double buffer_radius) {
    if (fields.empty()) {
        std::cerr << "错误: 输入地块列表为空，无法合并。" << std::endl;
        return false;
    }
    // --- 1. 收集所有原始孔洞 ---
    std::vector<geo::Polygon_2> original_holes;
    for (const auto &field : fields) {
        for (auto hit = field.holes_begin(); hit != field.holes_end(); ++hit) {
            original_holes.push_back(*hit);
        }
    }
    std::cout << "收集到 " << original_holes.size() << " 个原始孔洞。" << std::endl;

    // --- 2. 首先检查所有地块之间的距离，判断是否都能合并 ---
    bool all_can_merge = true;
    double max_dist = 0.0;

    for (size_t i = 0; i < fields.size() && all_can_merge; ++i) {
        for (size_t j = i + 1; j < fields.size(); ++j) {
            double dist = polygonDistance(fields[i], fields[j]);
            max_dist = std::max(max_dist, dist);
            if (dist >= threshold) {
                all_can_merge = false;
                std::cerr << "警告: 地块 " << i << " 和 " << j << " 之间的距离为 " << dist << "，大于等于阈值 "
                          << threshold << "，无法合并所有地块！" << std::endl;
                break;
            } else {
                std::cout << "地块 " << i << " 和 " << j << " 之间的距离为 " << dist << "，小于阈值 " << threshold
                          << std::endl;
            }
        }
    }

    if (!all_can_merge) {
        std::cerr << "\n错误: 无法合并所有地块！要求所有地块之间的距离必须小于 " << threshold << "。当前最大距离为 "
                  << max_dist << "。" << std::endl;
        std::cerr << "程序终止，未生成合并结果。" << std::endl;
        return false;
    }

    std::cout << "\n所有地块之间的距离均小于阈值 " << threshold << "，可以进行合并。" << std::endl;

    // --- 3. 合并所有地块集合 ---
    geo::Polygon_set merged_union;

    for (const auto &field : fields) {
        merged_union.join(field);
    }

    auto merged_results = merged_union.polygons();

    if (merged_results.empty()) {
        std::cerr << "错误: 合并后没有有效的地块。" << std::endl;
        return false;
    }

    // --- 4. 对每一个地块进行外扩后合并 ---
    geo::Polygon_set expanded_union;

    for (const auto &poly : merged_results) {
        auto expanded = exteriorOffset(poly, buffer_radius);
        for (const auto &out_poly : expanded) {
            const geo::Polygon_2 &outer = out_poly.outer_boundary();
            if (outer.size() < 3 || !outer.is_simple() || !isValidPolygonWithHoles(out_poly)) {
                std::cerr << "警告: 外扩多边形无效或退化，跳过。" << std::endl;
                continue;
            }
            expanded_union.join(outer);
        }
    }
    std::cout << "合并后得到 " << expanded_union.polygons().size() << " 个地块。" << std::endl;

    // --- 5. 对 expanded_union 进行内缩 ---
    auto expanded_results = expanded_union.polygons();

    std::vector<geo::PolygonWithHoles> temp_results;
    for (const auto &poly : expanded_results) {
        auto shrunk = interiorInset(poly, buffer_radius);
        temp_results.insert(temp_results.end(), shrunk.begin(), shrunk.end());
    }

    // --- 6. 将原始孔洞添加回最终结果 ---
    for (const auto &result : temp_results) {
        geo::PolygonWithHoles final_poly = result;
        for (const auto &hole : original_holes) {
            geo::Polygon_2 hole_clean = cleanPolygon(hole);
            if (hole_clean.size() >= 3) {
                if (hole_clean.is_counterclockwise_oriented()) {
                    hole_clean.reverse_orientation();
                }
                final_poly.add_hole(hole_clean);
            }
        }
        final_results.push_back(final_poly);
    }

    // 如果 temp_results 为空，直接使用原始合并结果并添加孔洞
    if (temp_results.empty()) {
        for (const auto &result : merged_results) {
            geo::PolygonWithHoles final_poly = result;
            for (const auto &hole : original_holes) {
                geo::Polygon_2 hole_clean = cleanPolygon(hole);
                if (hole_clean.size() >= 3) {
                    if (hole_clean.is_counterclockwise_oriented()) {
                        hole_clean.reverse_orientation();
                    }
                    final_poly.add_hole(hole_clean);
                }
            }
            final_results.push_back(final_poly);
        }
    }

    // --- 7. 输出 ---
    if (final_results.empty()) {
        std::cerr << "错误: 最终结果为空，可能是合并过程中所有地块都被过滤掉了。" << std::endl;
        return false;
    }
    std::cout << "处理完成！共生成 " << final_results.size() << " 个合并后的地块。" << std::endl;
    std::cout << "已保留 " << original_holes.size() << " 个原始孔洞。" << std::endl;

    return true;
}
