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

namespace polygon_coverage_planning {

bool pointInPolygon(const PolygonWithHoles& pwh, const Point_2& p) {
  // 使用 geo::bounded_side 判断点是否在带孔多边形内
  Bounded_side result = pwh.outer_boundary().bounded_side(p);
  if (result == ON_UNBOUNDED_SIDE) return false;

  for (auto hit = pwh.holes_begin(); hit != pwh.holes_end(); ++hit) {
    result = hit->bounded_side(p);
    if (result == ON_BOUNDED_SIDE) return false;  // 在孔洞内部
  }

  return true;
}

bool segmentInPolygon(const PolygonWithHoles& pwh, const Segment_2& seg) {
  // 线段两端点必须在多边形内（边界上也算在内）。
  if (!pointInPolygon(pwh, seg.source()) || !pointInPolygon(pwh, seg.target()))
    return false;

  // 精确检查：线段不得与任何边界边 proper 相交。
  // "proper 相交"指交点不是 seg 端点恰好落在边界上的情况
  // （可见图边以多边形顶点为端点，端点落在边界上是合法的）。
  auto crossesBoundary = [&seg](const Segment_2& edge) -> bool {
    // 若边界边包含 seg 的任一端点，属于端点落在边界上，不算越界。
    if (edge.has_on(seg.source()) || edge.has_on(seg.target()))
      return false;
    // 否则任何相交都意味着线段穿越了边界。
    return geo::do_intersect(seg, edge);
  };

  // 检查外边界各边。
  for (auto eit = pwh.outer_boundary().edges_begin();
       eit != pwh.outer_boundary().edges_end(); ++eit) {
    if (crossesBoundary(*eit)) return false;
  }

  // 检查各孔洞边界边。
  for (auto hit = pwh.holes_begin(); hit != pwh.holes_end(); ++hit) {
    for (auto eit = hit->edges_begin(); eit != hit->edges_end(); ++eit) {
      if (crossesBoundary(*eit)) return false;
    }
  }

  // FIX: 中点兜底检查，捕获 do_intersect 因浮点精度产生的漏判。
  // 当线段穿过凹角时，穿越点紧邻边界顶点，orientation() 的叉积
  // 接近零被舍入为 COLLINEAR，导致 do_intersect 漏掉这次相交。
  // 这种 shortcut 的中点必然落在多边形外部，作为廉价可靠的第二道防线。
  Point_2 mid((seg.source().x() + seg.target().x()) * 0.5,
              (seg.source().y() + seg.target().y()) * 0.5);
  if (!pointInPolygon(pwh, mid)) return false;

  return true;
}

bool pointsInPolygon(const PolygonWithHoles& pwh,
                     std::vector<Point_2>::iterator begin,
                     std::vector<Point_2>::iterator end) {
  for (auto it = begin; it != end; ++it) {
    if (!pointInPolygon(pwh, *it)) return false;
  }
  return true;
}

bool isStrictlySimple(const PolygonWithHoles& pwh) {
  for (auto hi = pwh.holes_begin(); hi != pwh.holes_end(); ++hi)
    if (!hi->is_simple()) return false;
  return pwh.outer_boundary().is_simple();
}

Point_2 projectOnPolygon2(const Polygon_2& poly, const Point_2& p,
                          FT* squared_distance) {
  // 找到多边形上距离点 p 最近的边。
  // 注意：Edge_const_iterator 是值类型迭代器，解引用得到 Segment_2。
  using EdgePair = std::pair<FT, Polygon_2::Edge_const_iterator>;
  std::vector<EdgePair> edge_distances(poly.size());

  size_t idx = 0;
  for (auto eit = poly.edges_begin(); eit != poly.edges_end(); ++eit, ++idx) {
    // squared_distance(Point_2, Segment_2) — point first, segment second
    edge_distances[idx] = {geo::squared_distance(p, *eit), eit};
  }

  auto closest_pair = std::min_element(
      edge_distances.begin(), edge_distances.end(),
      [](const EdgePair& lhs, const EdgePair& rhs) { return lhs.first < rhs.first; });

  auto closest_edge = closest_pair->second;
  *squared_distance = closest_pair->first;

  // 解引用迭代器获取实际的 Segment_2 值
  Segment_2 closest_segment = *closest_edge;

  // 将 p 投影到最近边的支撑直线上。
  Point_2 projection = closest_segment.supporting_line().projection(p);
  // 检查投影点是否在线段上；否则吸附到最近的端点。
  if (!closest_segment.has_on(projection)) {
    FT d_source = geo::squared_distance(p, closest_segment.source());
    FT d_target = geo::squared_distance(p, closest_segment.target());
    projection =
        d_source < d_target ? closest_segment.source() : closest_segment.target();
  }

  return projection;
}

Point_2 projectPointOnHull(const PolygonWithHoles& pwh, const Point_2& p) {
  // 先投影到外边界。
  FT min_distance;
  Point_2 projection =
      projectOnPolygon2(pwh.outer_boundary(), p, &min_distance);

  // 同时投影到各孔洞，保留最近的那个。
  for (auto hit = pwh.holes_begin(); hit != pwh.holes_end(); ++hit) {
    FT temp_distance;
    Point_2 temp_projection = projectOnPolygon2(*hit, p, &temp_distance);
    if (temp_distance < min_distance) {
      min_distance = temp_distance;
      projection = temp_projection;
    }
  }

  return projection;
}

FT computeArea(const PolygonWithHoles& pwh) {
  // 使用 geo::area (鞋带公式)，外边界面积减去孔洞面积
  FT area_val = std::fabs(pwh.outer_boundary().area());
  for (auto hi = pwh.holes_begin(); hi != pwh.holes_end(); ++hi)
    area_val -= std::fabs(hi->area());
  return area_val;
}

void simplifyPolygon(Polygon_2* polygon) {
  if (polygon->size() <= 2) return;

  // Find collinear vertices (3 consecutive points are collinear -> remove middle)
  std::vector<size_t> to_erase;
  size_t n = polygon->size();

  for (size_t i = 0; i < n; ++i) {
    size_t prev = (i + n - 1) % n;
    size_t next = (i + 1) % n;
    if (geo::collinear((*polygon)[prev], (*polygon)[i], (*polygon)[next])) {
      to_erase.push_back(i);
    }
  }

  // Remove in reverse order to preserve indices
  for (auto rit = to_erase.rbegin(); rit != to_erase.rend(); ++rit) {
    polygon->erase_vertex(*rit);
  }
}

void simplifyPolygon(PolygonWithHoles* pwh) {
  simplifyPolygon(&pwh->outer_boundary());
  for (auto hi = pwh->holes_begin(); hi != pwh->holes_end(); ++hi)
    simplifyPolygon(&*hi);
}

PolygonWithHoles rotatePolygon(const PolygonWithHoles& polygon_in,
                               const Direction_2& dir) {
  // 构建旋转矩阵：将 dir 方向旋转到 X 轴正方向
  // 我们使用 Aff_transformation_2(Rotation, sin, cos) 直接构建
  FT dx = dir.dx(), dy = dir.dy();
  // 目标: 将方向向量 (dx, dy) 旋转到 (1, 0)
  // 这等价于旋转角度 = -atan2(dy, dx)
  FT angle = -std::atan2(dy, dx);
  FT cos_v = std::cos(angle);
  FT sin_v = std::sin(angle);

  Aff_transformation_2 rotation(Aff_transformation_2::ROTATION, sin_v, cos_v);

  PolygonWithHoles rotated_polygon;

  // Transform outer boundary
  std::vector<Point_2> new_outer_verts;
  new_outer_verts.reserve(polygon_in.outer_boundary().size());
  for (size_t i = 0; i < polygon_in.outer_boundary().size(); ++i) {
    new_outer_verts.push_back(rotation.transform(polygon_in.outer_boundary()[i]));
  }
  rotated_polygon.outer_boundary() = Polygon_2(new_outer_verts.begin(), new_outer_verts.end());

  // Transform holes
  for (auto hit = polygon_in.holes_begin(); hit != polygon_in.holes_end(); ++hit) {
    std::vector<Point_2> new_hole_verts;
    new_hole_verts.reserve(hit->size());
    for (size_t i = 0; i < hit->size(); ++i) {
      new_hole_verts.push_back(rotation.transform((*hit)[i]));
    }
    rotated_polygon.add_hole(Polygon_2(new_hole_verts.begin(), new_hole_verts.end()));
  }

  return rotated_polygon;
}

void sortVertices(PolygonWithHoles* pwh) {
  // 外边界逆时针，孔洞顺时针
  if (pwh->outer_boundary().is_clockwise_oriented())
    pwh->outer_boundary().reverse_orientation();

  for (auto hi = pwh->holes_begin(); hi != pwh->holes_end(); ++hi)
    if (hi->is_counterclockwise_oriented()) hi->reverse_orientation();
}

std::vector<Point_2> getHullVertices(const PolygonWithHoles& pwh) {
  std::vector<Point_2> vec;
  vec.reserve(pwh.outer_boundary().size());
  for (size_t i = 0; i < pwh.outer_boundary().size(); ++i)
    vec.push_back(pwh.outer_boundary()[i]);
  return vec;
}

std::vector<std::vector<Point_2>> getHoleVertices(const PolygonWithHoles& pwh) {
  std::vector<std::vector<Point_2>> hole_vertices;
  hole_vertices.reserve(pwh.number_of_holes());
  for (auto hi = pwh.holes_begin(); hi != pwh.holes_end(); ++hi) {
    std::vector<Point_2> verts;
    verts.reserve(hi->size());
    for (size_t i = 0; i < hi->size(); ++i)
      verts.push_back((*hi)[i]);
    hole_vertices.push_back(std::move(verts));
  }
  return hole_vertices;
}

}  // namespace polygon_coverage_planning
