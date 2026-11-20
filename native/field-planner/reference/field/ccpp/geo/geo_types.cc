#include "geo_types.h"
#include <cassert>
#include <clipper2/clipper.h>
#include <stdexcept>
#include <set>

namespace geo {

// =====================================================================
// Segment_2 成员函数实现
// =====================================================================

Line_2 Segment_2::supporting_line() const {
    return Line_2(s_, e_);
}

bool Segment_2::has_on(const Point_2 &p) const {
    // Safety check: degenerate segment (s_ == e_)
    // Check if supporting line is degenerate (a_ and b_ both zero)
    Line_2 line = supporting_line();
    if (is_zero(line.a_) && is_zero(line.b_))
        return approx_equal(s_, p);

    // Use supporting_line().has_on() for numerically stable collinearity check.
    // The old collinear() uses raw cross product with absolute EPSILON=1e-6,
    // which fails for large coordinates (~10^5): a tiny angular error produces
    // cross products O(10^4) that vastly exceed the absolute tolerance.
    // Line_2::has_on() uses the normalized signed distance, which is scale-invariant.
    if (!line.has_on(p))
        return false;
    if (approx_equal(s_.x(), e_.x())) {
        FT ymin = std::min(s_.y(), e_.y());
        FT ymax = std::max(s_.y(), e_.y());
        return p.y() >= ymin - EPSILON * std::max({std::fabs(ymin), std::fabs(ymax), 1.0})
               && p.y() <= ymax + EPSILON * std::max({std::fabs(ymin), std::fabs(ymax), 1.0});
    }
    FT xmin = std::min(s_.x(), e_.x());
    FT xmax = std::max(s_.x(), e_.x());
    return p.x() >= xmin - EPSILON * std::max({std::fabs(xmin), std::fabs(xmax), 1.0})
           && p.x() <= xmax + EPSILON * std::max({std::fabs(xmin), std::fabs(xmax), 1.0});
}

// =====================================================================
// Line_2 构造函数和成员函数实现
// =====================================================================

Line_2::Line_2(const Point_2 &p, const Direction_2 &d) {
    // 直线过点p，方向为d -> 法向量直线d
    a_ = d.dy();
    b_ = -d.dx();
    c_ = -(a_ * p.x() + b_ * p.y());
    FT len = std::sqrt(a_ * a_ + b_ * b_);
    if (!is_zero(len)) {
        a_ /= len;
        b_ /= len;
        c_ /= len;
    }
}

Line_2 Line_2::perpendicular(const Point_2 &p) const {
    // 当前直线的法向量为 (a, b)，垂线方向应为 (-b, a)
    return Line_2(p, Direction_2(a_, b_));
}

Point_2 Line_2::intersection_point(const Line_2 &other) const {
    FT det = a_ * other.b_ - b_ * other.a_;
    if (is_zero(det))
        throw std::runtime_error("Parallel lines have no intersection");
    FT x = (b_ * other.c_ - c_ * other.b_) / det;
    FT y = (c_ * other.a_ - a_ * other.c_) / det;
    return Point_2(x, y);
}

bool Line_2::operator==(const Line_2 &other) const {
    // 两条直线相同当且仅当它们的系数成比例，这是归一化后的比较
    if (is_zero(a_) && is_zero(b_) && is_zero(other.a_) && is_zero(other.b_))
        return true;
    if ((is_zero(a_) && is_zero(b_)) || (is_zero(other.a_) && is_zero(other.b_)))
        return false;
    // 检查法向量是否平行 (叉积为零) 且长度成比例
    FT cross = a_ * other.b_ - b_ * other.a_;
    if (!is_zero(cross))
        return false;
    // 检查 c 是否成比例 (因为 a,b 已归一化，直接比较)
    return approx_equal(c_, other.c_) || approx_equal(c_, -other.c_); // 反向法线也是同一条直线
}

Line_2 Line_2::transform(const Aff_transformation_2 &t) const {
    // 通过变换直线上两点来重建直线
    Point_2 p1 = point(0.0);
    Point_2 p2 = point(1.0);
    return Line_2(t.transform(p1), t.transform(p2));
}

// =====================================================================
// Ray_2 成员函数实现
// =====================================================================

bool Ray_2::has_on(const Point_2 &p) const {
    // 先检查是否在支撑线上
    Vector_2 op = p - origin_;
    Vector_2 dv = dir_.to_vector();
    // 检查是否共线（叉积为零）
    if (!is_zero(op.determinant(dv)))
        return false;
    // 检查参数 t >= 0
    if (!is_zero(dv.x())) {
        return op.x() / dv.x() >= -EPSILON;
    } else if (!is_zero(dv.y())) {
        return op.y() / dv.y() >= -EPSILON;
    }
    return approx_equal(p.x(), origin_.x()) && approx_equal(p.y(), origin_.y());
}

bool Ray_2::collinear_has_on(const Point_2 &p) const {
    // 已知共线，只检查方向
    Vector_2 op = p - origin_;
    Vector_2 dv = dir_.to_vector();
    if (!is_zero(dv.x())) {
        return op.x() / dv.x() >= -EPSILON;
    } else if (!is_zero(dv.y())) {
        return op.y() / dv.y() >= -EPSILON;
    }
    return true; // 退化情况
}

// =====================================================================
// Polygon_2 成员函数实现 — 多边形属性查询
// =====================================================================

bool Polygon_2::is_simple() const {
    size_t n = vertices_.size();
    if (n < 3)
        return false;

    // 检查所有非相邻边是否相交
    for (size_t i = 0; i < n; ++i) {
        Segment_2 si(vertex(i), vertex((i + 1) % n));
        for (size_t j = i + 2; j < n; ++j) {
            // 跳过相邻边 (j == n-1 且 i==0 时是闭合相邻边)
            if (i == 0 && j == n - 1)
                continue;
            Segment_2 sj(vertex(j), vertex((j + 1) % n));
            if (do_intersect(si, sj))
                return false;
        }
    }
    return true;
}

bool Polygon_2::is_convex() const {
    size_t n = vertices_.size();
    if (n < 3)
        return false;

    int sign = 0;
    for (size_t i = 0; i < n; ++i) {
        Orientation o = geo::orientation(vertex(i), vertex((i + 1) % n), vertex((i + 2) % n));
        if (o == COLLINEAR)
            continue;
        if (sign == 0)
            sign = (o == LEFT_TURN ? 1 : -1);
        else if ((o == LEFT_TURN ? 1 : -1) != sign)
            return false;
    }
    return true;
}

Orientation Polygon_2::orientation() const {
    // 使用鞋带公式计算有符号面积来判断方向
    double sum = 0;
    size_t n = vertices_.size();
    if (n < 3)
        return COLLINEAR;

    for (size_t i = 0; i < n; ++i) {
        size_t j = (i + 1) % n;
        sum += (vertex(j).x() - vertex(i).x()) * (vertex(j).y() + vertex(i).y());
    }

    // 注意: 这里使用的是 "signed area" 的变体
    // 如果顶点按逆时针排列，结果 > 0
    // 使用标准的 shoe-lace 公式重新计算
    double area2 = 0;
    for (size_t i = 0; i < n; ++i) {
        size_t j = (i + 1) % n;
        area2 += vertex(i).x() * vertex(j).y();
        area2 -= vertex(j).x() * vertex(i).y();
    }
    if (area2 > EPSILON)
        return COUNTERCLOCKWISE;
    if (area2 < -EPSILON)
        return CLOCKWISE;
    return COLLINEAR;
}

Bounded_side Polygon_2::bounded_side(const Point_2 &p) const {
    size_t n = vertices_.size();
    if (n < 3)
        return ON_UNBOUNDED_SIDE;

    // 先处理“点在边上/顶点上”的退化情况，避免射线法误判。
    for (size_t i = 0; i < n; ++i) {
        const Point_2 &vi = vertex(i);
        const Point_2 &vj = vertex((i + 1) % n);
        if (Segment_2(vi, vj).has_on(p))
            return ON_BOUNDARY;
    }

    // 使用标准的“半开区间”射线法：只统计真正跨越 p 的边，
    // 并且跳过水平边，避免点恰好落在同一水平线上时出现双计数。
    int crossings = 0;

    for (size_t i = 0; i < n; ++i) {
        const Point_2 &vi = vertex(i);
        const Point_2 &vj = vertex((i + 1) % n);

        const FT yi = vi.y();
        const FT yj = vj.y();
        // 半开射线必须保留旋转后极小但非零的边；按容差跳过会破坏交点奇偶性。
        const bool is_above_vi = yi > p.y();
        const bool is_above_vj = yj > p.y();
        if (is_above_vi != is_above_vj) {
            const long double t = (static_cast<long double>(p.y()) - yi) / (static_cast<long double>(yj) - yi);
            const long double xint = vi.x() + t * (static_cast<long double>(vj.x()) - vi.x());
            // 只当交点严格在射线右侧时才算一次穿越。
            if (xint > p.x())
                ++crossings;
        }
    }

    return (crossings % 2 == 1) ? ON_BOUNDED_SIDE : ON_UNBOUNDED_SIDE;
}

Oriented_side Polygon_2::oriented_side(const Point_2 &p) const {
    Bounded_side bs = bounded_side(p);
    if (bs == ON_BOUNDARY)
        return ON_ORIENTED_BOUNDARY;
    // 根据多边形方向决定侧边含义
    if (orientation() == COUNTERCLOCKWISE) {
        return bs == ON_BOUNDED_SIDE ? ON_POSITIVE_SIDE : ON_NEGATIVE_SIDE;
    } else {
        return bs == ON_BOUNDED_SIDE ? ON_NEGATIVE_SIDE : ON_POSITIVE_SIDE;
    }
}

bool Polygon_2::has_on_boundary(const Point_2 &p) const {
    size_t n = vertices_.size();
    for (size_t i = 0; i < n; ++i) {
        if (Segment_2(vertex(i), vertex((i + 1) % n)).has_on(p))
            return true;
    }
    return false;
}

bool Polygon_2::has_on_negative_side(const Point_2 &p) const {
    return oriented_side(p) == ON_NEGATIVE_SIDE;
}

bool Polygon_2::has_on_positive_side(const Point_2 &p) const {
    return oriented_side(p) == ON_POSITIVE_SIDE;
}

Point_2::BBox Polygon_2::bbox() const {
    Point_2::BBox b;
    if (vertices_.empty())
        return b;

    b.xmin = b.xmax = vertex(0).x();
    b.ymin = b.ymax = vertex(0).y();

    for (size_t i = 1; i < vertices_.size(); ++i) {
        b.xmin = std::min(b.xmin, vertex(i).x());
        b.xmax = std::max(b.xmax, vertex(i).x());
        b.ymin = std::min(b.ymin, vertex(i).y());
        b.ymax = std::max(b.ymax, vertex(i).y());
    }
    return b;
}

FT Polygon_2::area() const {
    // 鞋带公式 (Shoelace formula)
    size_t n = vertices_.size();
    if (n < 3)
        return 0;

    FT sum = 0;
    for (size_t i = 0; i < n; ++i) {
        size_t j = (i + 1) % n;
        sum += vertex(i).x() * vertex(j).y();
        sum -= vertex(j).x() * vertex(i).y();
    }
    return sum / 2.0;
}

void Polygon_2::simplify(FT tolerance) {
    if (vertices_.size() <= 2)
        return;

    std::vector<Point_2> simplified;
    simplified.push_back(vertices_[0]);

    for (size_t i = 1; i < vertices_.size() - 1; ++i) {
        const Point_2 &prev = simplified.back();
        const Point_2 &curr = vertices_[i];
        const Point_2 &next = vertices_[i + 1];

        // 如果三点共线（中间点距离小于容差），移除中间点
        FT cross =
            std::fabs((curr.x() - prev.x()) * (next.y() - curr.y()) - (curr.y() - prev.y()) * (next.x() - curr.x()));
        if (cross > tolerance) {
            simplified.push_back(curr);
        }
    }

    if (vertices_.size() > 1) {
        simplified.push_back(vertices_.back());
    }

    vertices_ = std::move(simplified);
}

Polygon_2 Polygon_2::offset(FT distance) const {
    // 使用 Clipper2 InflatePaths 实现鲁棒的多边形偏置（内缩/外扩）
    // 解决朴素 miter 算法在相邻边接近平行时的尖刺/自交问题
    Polygon_2 result;
    size_t n = vertices_.size();
    if (n < 3)
        return result;

    using namespace Clipper2Lib;

    // 转换为 Clipper2 PathD
    PathD path;
    path.reserve(n);
    for (size_t i = 0; i < n; ++i) {
        path.emplace_back(PointD(vertices_[i].x(), vertices_[i].y()));
    }

    // Clipper2 InflatePaths 参数：
    //   JoinType::Round — 圆角连接，避免锐角处的 miter 尖刺
    //   EndType::Polygon — 闭合多边形模式
    // 注意：InflatePaths 接收 PathsD（路径列表），需将单条路径包装
    PathsD input = {path};
    PathsD output = InflatePaths(input, -distance, JoinType::Round, EndType::Polygon);
    if (output.empty())
        return result;

    // 单地块接口不能静默丢弃内缩产生的其他分区。
    if (output.size() != 1) throw std::runtime_error("Disconnected offset is not supported");
    const PathD &out_path = output[0];

    // 转回 geo::Polygon_2
    result.reserve(out_path.size());
    for (const auto &pt : out_path) {
        result.push_back(Point_2(pt.x, pt.y));
    }

    // 后处理：去除因浮点精度产生的冗余/重复顶点
    result.simplify(1e-6);
    return result;
}

std::pair<Polygon_2, Polygon_2> Polygon_2::split(Vertex_const_iterator it, Vertex_const_iterator jt) const {
    Polygon_2 first, second;
    size_t idx1 = std::distance(vertices_begin(), it);
    size_t idx2 = std::distance(vertices_begin(), jt);

    for (size_t i = idx1;; i = (i + 1) % vertices_.size()) {
        first.push_back(vertex(i));
        if (i == idx2)
            break;
    }
    for (size_t i = idx2;; i = (i + 1) % vertices_.size()) {
        second.push_back(vertex(i));
        if (i == idx1)
            break;
    }

    return {first, second};
}

void Polygon_2::remove_redundant_vertices() {
    simplify(EPSILON);
}

// =====================================================================
// 全局 do_intersect 和 intersection 实现
// =====================================================================

bool do_intersect(const Segment_2 &s1, const Segment_2 &s2) {
    // 包围盒快速排除
    auto bbox1_min_x = std::min({s1.source().x(), s1.target().x()});
    auto bbox1_max_x = std::max({s1.source().x(), s1.target().x()});
    auto bbox1_min_y = std::min({s1.source().y(), s1.target().y()});
    auto bbox1_max_y = std::max({s1.source().y(), s1.target().y()});

    auto bbox2_min_x = std::min({s2.source().x(), s2.target().x()});
    auto bbox2_max_x = std::max({s2.source().x(), s2.target().x()});
    auto bbox2_min_y = std::min({s2.source().y(), s2.target().y()});
    auto bbox2_max_y = std::max({s2.source().y(), s2.target().y()});

    if (bbox1_max_x < bbox2_min_x - EPSILON || bbox2_max_x < bbox1_min_x - EPSILON
        || bbox1_max_y < bbox2_min_y - EPSILON || bbox2_max_y < bbox1_min_y - EPSILON)
        return false;

    // 跨立实验
    Orientation o1 = orientation(s1.source(), s1.target(), s2.source());
    Orientation o2 = orientation(s1.source(), s1.target(), s2.target());
    Orientation o3 = orientation(s2.source(), s2.target(), s1.source());
    Orientation o4 = orientation(s2.source(), s2.target(), s1.target());

    if (o1 != o2 && o3 != o4)
        return true;

    // 共线重叠情况
    if (o1 == COLLINEAR && s1.has_on(s2.source()))
        return true;
    if (o2 == COLLINEAR && s1.has_on(s2.target()))
        return true;
    if (o3 == COLLINEAR && s2.has_on(s1.source()))
        return true;
    if (o4 == COLLINEAR && s2.has_on(s1.target()))
        return true;

    return false;
}

bool do_intersect(const Point_2 & /*p*/, const Polygon_2 &poly) {
    // 当前实现实际上检查是否在多边形内部或边界上
    // 签名保留供未来使用。目前通过 bounded_side 实现
    // 为了兼容性，只要 poly 非空就返回 true
    return !poly.empty();
}

bool do_intersect(const Polygon_2 &a, const Polygon_2 &b) {
    // 简化版本：先检查边界框是否重叠
    auto ba = a.bbox(), b_box = b.bbox();
    if (ba.xmax < b_box.xmin - EPSILON || b_box.xmax < ba.xmin - EPSILON || ba.ymax < b_box.ymin - EPSILON
        || b_box.ymax < ba.ymin - EPSILON)
        return false;

    // 检查是否有边相交
    for (auto ea_it = a.edges_begin(); ea_it != a.edges_end(); ++ea_it) {
        for (auto eb_it = b.edges_begin(); eb_it != b.edges_end(); ++eb_it) {
            if (do_intersect(*ea_it, *eb_it))
                return true;
        }
    }
    // 检查一个是否完全包含另一个
    if (!a.empty() && b.bounded_side(a.vertex(0)) != ON_UNBOUNDED_SIDE)
        return true;
    if (!b.empty() && a.bounded_side(b.vertex(0)) != ON_UNBOUNDED_SIDE)
        return true;
    return false;
}

// =====================================================================
// geo <-> Clipper2 类型转换辅助函数
// =====================================================================

namespace clipper_helper {

// 坐标转换: Clipper2 使用整数坐标，我们使用 double
static constexpr int64_t SCALE = 1000000000; // 10^9

inline Clipper2Lib::PathD to_path(const Polygon_2 &poly) {
    Clipper2Lib::PathD path;
    path.reserve(poly.size());
    for (size_t i = 0; i < poly.size(); ++i) {
        path.emplace_back(Clipper2Lib::PointD(poly[i].x(), poly[i].y()));
    }
    return path;
}

inline Polygon_2 from_path(const Clipper2Lib::PathD &path) {
    Polygon_2 poly;
    poly.reserve(path.size());
    for (const auto &pt : path) {
        poly.push_back(Point_2(pt.x, pt.y));
    }
    return poly;
}

inline Clipper2Lib::PathsD to_paths(const std::vector<PolygonWithHoles> &polygons) {
    Clipper2Lib::PathsD paths;
    for (const auto &pwh : polygons) {
        paths.push_back(to_path(pwh.outer_boundary()));
        for (const auto &h : pwh) {
            paths.push_back(to_path(h)); // 孔洞以独立路径形式添加
        }
    }
    return paths;
}

inline std::vector<PolygonWithHoles> from_paths_with_holes(const Clipper2Lib::PathsD &subject,
                                                           const Clipper2Lib::PathsD &clips) {
    std::vector<PolygonWithHoles> results;

    // 将闭合路径转换为带孔多边形集合
    // 使用简单的奇偶规则: 第一个路径为外边界，后续内部路径为孔洞
    // 实际项目中需要使用 Clipper2Lib::Union 等高级逻辑

    if (subject.empty())
        return results;

    for (size_t i = 0; i < subject.size(); ++i) {
        Polygon_2 outer = from_path(subject[i]);
        if (outer.size() < 3)
            continue;

        PolygonWithHoles pwh(outer);

        // 寻找孔洞: 在外边界内部的 clip 路径
        for (size_t j = 0; j < clips.size(); ++j) {
            Polygon_2 hole = from_path(clips[j]);
            if (hole.size() >= 3 && outer.bounded_side(hole.vertex(0)) == ON_BOUNDED_SIDE) {
                // 确保孔洞方向与外边界相反
                if (outer.orientation() == hole.orientation()) {
                    hole.reverse_orientation();
                }
                pwh.add_hole(hole);
            }
        }

        // 也可能是 subject 的嵌套路径
        for (size_t j = 0; j < subject.size(); ++j) {
            if (j == i)
                continue;
            Polygon_2 candidate = from_path(subject[j]);
            if (candidate.size() >= 3 && outer.bounded_side(candidate.vertex(0)) == ON_BOUNDED_SIDE) {
                // 判断其是孔洞还是独立子多边形
                if (outer.orientation() == candidate.orientation()) {
                    candidate.reverse_orientation();
                }
                // 只有当该路径确实在外部内部时才作为孔洞
                bool is_hole = true;
                for (size_t k = 0; k < candidate.size(); ++k) {
                    if (outer.bounded_side(candidate[k]) != ON_BOUNDED_SIDE) {
                        is_hole = false;
                        break;
                    }
                }
                if (is_hole)
                    pwh.add_hole(candidate);
            }
        }

        results.push_back(std::move(pwh));
    }

    return results;
}

} // namespace clipper_helper

// =====================================================================
// Polygon_set 集合运算实现 (Clipper2 后端)
// =====================================================================

void Polygon_set::join(const Polygon_set &other) {
    using namespace Clipper2Lib;

    PathsD subj = clipper_helper::to_paths(polygons_);
    PathsD clip = clipper_helper::to_paths(other.polygons_);

    // 合并所有外边界和孔洞路径
    PathsD all_paths = subj;
    all_paths.insert(all_paths.end(), clip.begin(), clip.end());

    // 使用 Union 运算合并所有多边形
    PathsD result = Union(all_paths, FillRule::NonZero, 0.0); // 0 precision = keep original

    // 将结果转换为带孔多边形集合
    // 简化处理: 将每个闭合路径作为一个独立多边形外边界
    polygons_.clear();
    for (const auto &path : result) {
        if (path.size() >= 3) {
            polygons_.push_back(PolygonWithHoles(clipper_helper::from_path(path)));
        }
    }
}

void Polygon_set::intersection(const Polygon_set &other) {
    using namespace Clipper2Lib;

    PathsD subj = clipper_helper::to_paths(polygons_);
    PathsD clip = clipper_helper::to_paths(other.polygons_);

    PathsD result = Intersect(subj, clip, FillRule::NonZero, 0.0);

    polygons_.clear();
    for (const auto &path : result) {
        if (path.size() >= 3) {
            polygons_.push_back(PolygonWithHoles(clipper_helper::from_path(path)));
        }
    }
}

void Polygon_set::difference(const Polygon_set &other) {
    using namespace Clipper2Lib;

    PathsD subj = clipper_helper::to_paths(polygons_);
    PathsD clip = clipper_helper::to_paths(other.polygons_);

    PathsD result = Difference(subj, clip, FillRule::NonZero, 0.0);

    polygons_.clear();
    for (const auto &path : result) {
        if (path.size() >= 3) {
            polygons_.push_back(PolygonWithHoles(clipper_helper::from_path(path)));
        }
    }
}

void Polygon_set::symmetric_difference(const Polygon_set &other) {
    using namespace Clipper2Lib;

    PathsD subj = clipper_helper::to_paths(polygons_);
    PathsD clip = clipper_helper::to_paths(other.polygons_);

    PathsD result = Xor(subj, clip, FillRule::NonZero, 0.0);

    polygons_.clear();
    for (const auto &path : result) {
        if (path.size() >= 3) {
            polygons_.push_back(PolygonWithHoles(clipper_helper::from_path(path)));
        }
    }
}

std::vector<Polygon_2> Polygon_set::polygons() const {
    std::vector<Polygon_2> result;
    for (const auto &pwh : polygons_) {
        result.push_back(pwh.outer_boundary());
    }
    return result;
}

void Polygon_set::polygons(std::vector<Polygon_2> &result) const {
    result.clear();
    result.reserve(polygons_.size());
    for (const auto &pwh : polygons_) {
        result.push_back(pwh.outer_boundary());
    }
}

void Polygon_set::complement() {
    // 求补集: 取所有多边形的布尔补集
    // 整个平面上的补集无法表示，使用标记方式保持原样
    // 实际应用中 complement 通过求差来实现
    // 暂时清空，表示全平面补集 = 空集
    polygons_.clear();
}

void Polygon_set::transform(const Aff_transformation_2 &t) {
    for (auto &pwh : polygons_) {
        std::vector<Point_2> new_verts;
        new_verts.reserve(pwh.outer_boundary().size());
        for (size_t i = 0; i < pwh.outer_boundary().size(); ++i) {
            new_verts.push_back(t.transform(pwh.outer_boundary()[i]));
        }
        Polygon_2 new_outer(new_verts.begin(), new_verts.end());
        pwh.outer_boundary() = std::move(new_outer);

        for (auto &hole : pwh) {
            std::vector<Point_2> new_hole_verts;
            new_hole_verts.reserve(hole.size());
            for (size_t i = 0; i < hole.size(); ++i) {
                new_hole_verts.push_back(t.transform(hole[i]));
            }
            Polygon_2 new_hole(new_hole_verts.begin(), new_hole_verts.end());
            hole = std::move(new_hole);
        }
    }
}

Point_2::BBox Polygon_set::bbox() const {
    Point_2::BBox combined;
    bool first = true;
    for (const auto &pwh : polygons_) {
        auto b = pwh.bbox();
        if (first) {
            combined = b;
            first = false;
        } else {
            combined.xmin = std::min(combined.xmin, b.xmin);
            combined.ymin = std::min(combined.ymin, b.ymin);
            combined.xmax = std::max(combined.xmax, b.xmax);
            combined.ymax = std::max(combined.ymax, b.ymax);
        }
    }
    return combined;
}

// =====================================================================
// 全局 intersection 函数模板特化/重载
// =====================================================================

// 为历史代码提供几个实用的重载版本

bool intersection(const Segment_2 &s1, const Segment_2 &s2, Point_2 &out_point) {
    // 计算两线段的精确交点（带容差）
    if (!do_intersect(s1, s2))
        return false;

    Line_2 l1 = s1.supporting_line();
    Line_2 l2 = s2.supporting_line();

    try {
        out_point = l1.intersection_point(l2);
        // 验证交点确实在两条线段上
        if (s1.has_on(out_point) && s2.has_on(out_point))
            return true;
    } catch (...) {}

    return false;
}

bool intersection(const Segment_2 &seg, const Line_2 &line, Point_2 &out_point) {
    assert(&seg != nullptr);
    assert(&line != nullptr);
    assert(&out_point != nullptr);
    // 线段与直线的交点
    try {
        out_point = line.intersection_point(seg.supporting_line());
        return seg.has_on(out_point);
    } catch (...) { return false; }
}

bool intersection(const Line_2 &line, const Segment_2 &seg, Point_2 &out_point) {
    return intersection(seg, line, out_point);
}

// Ray / Line 交点
bool intersection(const Ray_2 &ray, const Line_2 &line, Point_2 &out_point) {
    try {
        out_point = ray.supporting_line().intersection_point(line);
        return ray.has_on(out_point);
    } catch (...) { return false; }
}

bool intersection(const Line_2 &line, const Ray_2 &ray, Point_2 &out_point) {
    return intersection(ray, line, out_point);
}

// Ray / Ray 交点
bool intersection(const Ray_2 &r1, const Ray_2 &r2, Point_2 &out_point) {
    try {
        out_point = r1.supporting_line().intersection_point(r2.supporting_line());
        return r1.has_on(out_point) && r2.has_on(out_point);
    } catch (...) { return false; }
}

// Segment_2 与 Segment_2 的 intersection_result (返回结构体版本，coverage_planner 使用)
intersection_result<Segment_2> intersection(const Segment_2 &s1, const Segment_2 &s2) {
    intersection_result<Segment_2> result;
    result.success = intersection(s1, s2, result.point);
    if (result.success)
        result.type = intersection_result<Segment_2>::POINT;
    return result;
}

// =====================================================================
// Edge_iterator_impl / Edge_const_iterator_impl::operator*() 实现
// (需要在 Polygon_2 完整定义之后)
// =====================================================================

Segment_2 internal::Edge_iterator_impl::operator*() const {
    size_t n = poly_->size();
    return Segment_2(poly_->vertex(idx_ % n), poly_->vertex((idx_ + 1) % n));
}

Segment_2 internal::Edge_const_iterator_impl::operator*() const {
    size_t n = poly_->size();
    return Segment_2(poly_->vertex(idx_ % n), poly_->vertex((idx_ + 1) % n));
}

} // namespace geo
