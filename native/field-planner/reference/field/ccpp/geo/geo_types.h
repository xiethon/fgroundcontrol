#pragma once

#include <iostream>
#include <vector>
#include <cmath>
#include <algorithm>
#include <stdexcept>

// ============================================================================
// geo 命名空间 — 替代 CGAL 的几何类型系统
// 基于 double 精度，提供 CGAL 兼容接口
// ============================================================================

namespace geo {

// ---- 枚举类型定义 (CGAL兼容) ----
enum Orientation {
    COLLINEAR = 0,
    LEFT_TURN = 1,
    RIGHT_TURN = 2,
    CLOCKWISE = RIGHT_TURN,
    COUNTERCLOCKWISE = LEFT_TURN,
    CCW = LEFT_TURN,
    CW = RIGHT_TURN
};
enum Bounded_side { ON_BOUNDED_SIDE = -1, ON_BOUNDARY = 0, ON_UNBOUNDED_SIDE = 1 };
enum Oriented_side { ON_NEGATIVE_SIDE = -1, ON_ORIENTED_BOUNDARY = 0, ON_POSITIVE_SIDE = 1 };
enum Sign { NEGATIVE = -1, ZERO = 0, POSITIVE = 1 };
enum Comparison_result { SMALLER = -1, EQUAL = 0, LARGER = 1 };

// ---- 基础类型定义 ----
using FT = double;

static constexpr FT EPSILON = 1e-9;

inline bool approx_equal(FT a, FT b, FT eps = EPSILON) {
    return std::fabs(a - b) < eps;
}
inline bool is_zero(FT v, FT eps = EPSILON) {
    return std::fabs(v) < eps;
}

// ---- 前向声明 与 类型之间交互函数 (C++ 类型不完整只能前向声明) ----
class Point_2;
class Vector_2;
class Direction_2;
class Segment_2;
class Line_2;
class Ray_2;
class Aff_transformation_2;
class Polygon_2;

// =====================================================================
// Point_2 — 二维点
// =====================================================================
class Point_2 {
public:
    FT x_, y_;
    Point_2() : x_(0), y_(0) {}
    Point_2(FT x, FT y) : x_(x), y_(y) {}
    FT x() const {
        return x_;
    }
    FT y() const {
        return y_;
    }

    Point_2 operator+(const Vector_2 &v) const;
    Point_2 operator-(const Vector_2 &v) const;
    Vector_2 operator-(const Point_2 &p) const;
    Point_2 operator*(FT s) const {
        return Point_2(x_ * s, y_ * s);
    }
    Point_2 operator/(FT s) const {
        return Point_2(x_ / s, y_ / s);
    }

    bool operator==(const Point_2 &other) const {
        return approx_equal(x_, other.x_) && approx_equal(y_, other.y_);
    }
    bool operator!=(const Point_2 &other) const {
        return !(*this == other);
    }

    struct BBox {
        FT xmin, ymin, xmax, ymax;
        BBox() : xmin(0), ymin(0), xmax(0), ymax(0) {}
        int dimension() const {
            return 2;
        }
        FT min(int d) const {
            return d == 0 ? xmin : ymin;
        }
        FT max(int d) const {
            return d == 0 ? xmax : ymax;
        }
    };
    BBox bbox() const {
        BBox b;
        b.xmin = b.xmax = x_;
        b.ymin = b.ymax = y_;
        return b;
    }
};

// =====================================================================
// Vector_2 — 二维向量
// =====================================================================
class Vector_2 {
public:
    FT x_, y_;

    Vector_2() : x_(0), y_(0) {}
    Vector_2(FT x, FT y) : x_(x), y_(y) {}
    explicit Vector_2(const Point_2 &p) : x_(p.x_), y_(p.y_) {}

    FT x() const {
        return x_;
    }
    FT y() const {
        return y_;
    }
    FT hx() const {
        return x_;
    }
    FT hy() const {
        return y_;
    }

    Vector_2 operator+(const Vector_2 &v) const {
        return Vector_2(x_ + v.x_, y_ + v.y_);
    }
    Vector_2 operator-(const Vector_2 &v) const {
        return Vector_2(x_ - v.x_, y_ - v.y_);
    }
    Vector_2 operator-() const {
        return Vector_2(-x_, -y_);
    }
    Vector_2 operator*(FT s) const {
        return Vector_2(x_ * s, y_ * s);
    }
    Vector_2 operator/(FT s) const {
        return Vector_2(x_ / s, y_ / s);
    }

    // 点积
    FT operator*(const Vector_2 &v) const {
        return x_ * v.x_ + y_ * v.y_;
    }
    // 叉积 (2D scalar result)
    FT determinant(const Vector_2 &v) const {
        return x_ * v.y_ - y_ * v.x_;
    }
    FT squared_length() const {
        return x_ * x_ + y_ * y_;
    }
    FT length() const {
        return std::sqrt(squared_length());
    }

    Vector_2 perpendicular(Orientation o = COUNTERCLOCKWISE) const {
        if (o == COUNTERCLOCKWISE || o == CCW)
            return Vector_2(-y_, x_);
        else
            return Vector_2(y_, -x_);
    }

    Vector_2 transform(const Aff_transformation_2 &t) const;

    class Cartesian_const_iterator_2 {
    public:
        const FT *ptr_;
        int index_;
        Cartesian_const_iterator_2(const FT *p, int i = 0) : ptr_(p), index_(i) {}
        const FT &operator*() const {
            return ptr_[index_];
        }
        Cartesian_const_iterator_2 &operator++() {
            ++index_;
            return *this;
        }
        Cartesian_const_iterator_2 operator++(int) {
            auto t = *this;
            ++index_;
            return t;
        }
        bool operator==(const Cartesian_const_iterator_2 &o) const {
            return index_ == o.index_;
        }
        bool operator!=(const Cartesian_const_iterator_2 &o) const {
            return index_ != o.index_;
        }
    };

    Cartesian_const_iterator_2 cartesian_begin() const {
        return Cartesian_const_iterator_2(&x_, 0);
    }
    Cartesian_const_iterator_2 cartesian_end() const {
        return Cartesian_const_iterator_2(&x_, 2);
    }
};

// Point_2 依赖 Vector_2 的运算符后置实现
inline Point_2 Point_2::operator+(const Vector_2 &v) const {
    return Point_2(x_ + v.x_, y_ + v.y_);
}
inline Point_2 Point_2::operator-(const Vector_2 &v) const {
    return Point_2(x_ - v.x_, y_ - v.y_);
}
inline Vector_2 Point_2::operator-(const Point_2 &p) const {
    return Vector_2(x_ - p.x_, y_ - p.y_);
}

// approx_equal 的 Point_2 特化 (需 Point_2 完整定义后)
inline bool approx_equal(const Point_2 &a, const Point_2 &b, FT eps = EPSILON) {
    return approx_equal(a.x(), b.x(), eps) && approx_equal(a.y(), b.y(), eps);
}

// =====================================================================
// Direction_2 — 方向向量（归一化）
// =====================================================================
class Direction_2 {
public:
    FT dx_, dy_;
    Direction_2() : dx_(1), dy_(0) {}
    Direction_2(FT dx, FT dy) : dx_(dx), dy_(dy) {
        normalize();
    }
    FT dx() const {
        return dx_;
    }
    FT dy() const {
        return dy_;
    }
    Vector_2 to_vector() const {
        return Vector_2(dx_, dy_);
    }
    Vector_2 vector() const {
        return Vector_2(dx_, dy_);
    }
    Direction_2 operator-() const {
        return Direction_2(-dx_, -dy_);
    }
    bool operator==(const Direction_2 &other) const {
        return approx_equal(dx_, other.dx_) && approx_equal(dy_, other.dy_);
    }
    bool operator!=(const Direction_2 &other) const {
        return !(*this == other);
    }

private:
    void normalize() {
        FT len = std::sqrt(dx_ * dx_ + dy_ * dy_);
        if (!is_zero(len)) {
            dx_ /= len;
            dy_ /= len;
        }
    }
};

// =====================================================================
// Segment_2 — 线段
// =====================================================================
class Segment_2 {
public:
    Point_2 s_, e_;
    Segment_2() : s_(), e_() {}
    Segment_2(const Point_2 &s, const Point_2 &e) : s_(s), e_(e) {}

    const Point_2 &source() const {
        return s_;
    }
    const Point_2 &target() const {
        return e_;
    }
    const Point_2 &start() const {
        return s_;
    }
    const Point_2 &end() const {
        return e_;
    }
    const Point_2 &min() const {
        return s_;
    }
    const Point_2 &max() const {
        return e_;
    }
    Point_2 vertex(int i) const {
        return i == 0 ? s_ : e_;
    }
    Point_2 operator[](int i) const {
        return vertex(i);
    }

    Vector_2 to_vector() const {
        return e_ - s_;
    }
    Vector_2 supporting_vector() const {
        return e_ - s_;
    }
    FT squared_length() const {
        return to_vector().squared_length();
    }
    Direction_2 direction() const {
        auto v = to_vector();
        return Direction_2(v.x(), v.y());
    }

    Line_2 supporting_line() const;
    Line_2 line() const;

    bool is_degenerate() const {
        return s_ == e_;
    }
    bool is_horizontal() const {
        return approx_equal(s_.y(), e_.y());
    }
    bool is_vertical() const {
        return approx_equal(s_.x(), e_.x());
    }
    bool operator==(const Segment_2 &other) const {
        return s_ == other.s_ && e_ == other.e_;
    }
    bool has_on(const Point_2 &p) const;

    // 返回反向线段 (交换起点终点)
    Segment_2 opposite() const {
        return Segment_2(e_, s_);
    }
};

// =====================================================================
// Line_2 — 直线 (ax + by + c = 0)
// =====================================================================
class Line_2 {
public:
    FT a_, b_, c_;
    Line_2() : a_(0), b_(1), c_(0) {}
    Line_2(const Point_2 &p, const Point_2 &q) {
        // Degenerate case: p ≈ q
        if (approx_equal(p, q)) {
            a_ = 0;
            b_ = 0;
            c_ = 0;
            return;
        }
        // Vertical line: x = c (p.x ≈ q.x, p.y ≠ q.y)
        if (approx_equal(p.x(), q.x()) && !approx_equal(p.y(), q.y())) {
            // 保留有向直线的左右侧，与一般分支一致。
            a_ = p.y() > q.y() ? 1 : -1;
            b_ = 0;
            c_ = -a_ * p.x();
            return;
        }
        // Horizontal line: y = c (p.y ≈ q.y, p.x ≠ q.x)
        if (approx_equal(p.y(), q.y()) && !approx_equal(p.x(), q.x())) {
            a_ = 0;
            b_ = q.x() > p.x() ? 1 : -1;
            c_ = -b_ * p.y();
            return;
        }
        // General case
        a_ = p.y() - q.y();
        b_ = q.x() - p.x();
        c_ = -(a_ * p.x() + b_ * p.y());
        FT len = std::sqrt(a_ * a_ + b_ * b_);
        if (!is_zero(len)) {
            a_ /= len;
            b_ /= len;
            c_ /= len;
        }
    }
    Line_2(const Point_2 &p, const Direction_2 &d);
    static Line_2 from_coefficients(FT a, FT b, FT c) {
        Line_2 l;
        l.a_ = a;
        l.b_ = b;
        l.c_ = c;
        return l;
    }

    FT a() const {
        return a_;
    }
    FT b() const {
        return b_;
    }
    FT c() const {
        return c_;
    }

    // 点到直线的有符号距离
    FT signed_distance(const Point_2 &p) const {
        FT denom = std::sqrt(a_ * a_ + b_ * b_);
        if (is_zero(denom))
            return 0;
        return (a_ * p.x() + b_ * p.y() + c_) / denom;
    }

    // 点在直线上的投影
    Point_2 projection(const Point_2 &p) const {
        if (is_zero(b_) && !is_zero(a_))
            return Point_2(-c_ / a_, p.y());
        if (is_zero(a_) && !is_zero(b_))
            return Point_2(p.x(), -c_ / b_);
        FT denom = a_ * a_ + b_ * b_;
        if (is_zero(denom))
            return p;
        return Point_2((b_ * (b_ * p.x() - a_ * p.y()) - a_ * c_) / denom,
                       (a_ * (-b_ * p.x() + a_ * p.y()) - b_ * c_) / denom);
    }

    Line_2 perpendicular(const Point_2 &p) const;

    bool is_degenerate() const {
        return is_zero(a_) && is_zero(b_);
    }
    bool is_horizontal() const {
        return is_zero(a_) && !is_zero(b_);
    }
    bool is_vertical() const {
        return is_zero(b_) && !is_zero(a_);
    }
    bool has_on(const Point_2 &p) const {
        return is_zero(signed_distance(p));
    }

    Direction_2 direction() const {
        if (is_zero(b_))
            return Direction_2(0, 1);
        return Direction_2(1, -a_ / b_);
    }
    Vector_2 to_vector() const {
        return direction().to_vector();
    }

    // 返回反向直线 (ax + by + c = 0 → -ax - by - c = 0)
    Line_2 opposite() const {
        return from_coefficients(-a_, -b_, -c_);
    }

    // 判断点在直线的正侧/负侧 (基于有符号距离)
    bool has_on_positive_side(const Point_2 &p) const {
        return signed_distance(p) > EPSILON;
    }
    bool has_on_negative_side(const Point_2 &p) const {
        return signed_distance(p) < -EPSILON;
    }

    // 返回直线上对应参数 t 的点 (以方向向量为基准)
    Point_2 point(FT t = 0.0) const {
        // 使用直线上一点 + t * 方向向量
        // 取直线上一点: 若 b != 0 时 y = (-a*x-c)/b, 令 x=0 得 (0, -c/b)
        // 若 a != 0 时 x = (-b*y-c)/a, 令 y=0 得 (-c/a, 0)
        Point_2 base;
        if (!is_zero(b_))
            base = Point_2(0, -c_ / b_);
        else if (!is_zero(a_))
            base = Point_2(-c_ / a_, 0);
        else
            return Point_2(0, 0);
        Vector_2 d = to_vector();
        return base + d * t;
    }

    // 对直线应用仿射变换：通过变换两点重建
    Line_2 transform(const Aff_transformation_2 &t) const;

    Point_2 intersection_point(const Line_2 &other) const;
    bool operator==(const Line_2 &other) const;
    bool operator!=(const Line_2 &other) const {
        return !(*this == other);
    }
    int id() const {
        return 0;
    }
};

// =====================================================================
// Ray_2 — 射线
// =====================================================================
class Ray_2 {
public:
    Point_2 origin_;
    Direction_2 dir_;
    Ray_2() : origin_(), dir_() {}
    Ray_2(const Point_2 &o, const Point_2 &second) : origin_(o), dir_((second - o).x(), (second - o).y()) {}
    Ray_2(const Point_2 &o, const Direction_2 &d) : origin_(o), dir_(d) {}
    Ray_2(const Point_2 &o, const Vector_2 &d) : origin_(o), dir_(d.x(), d.y()) {}

    const Point_2 &source() const {
        return origin_;
    }
    const Point_2 &start() const {
        return origin_;
    }
    Point_2 second_point() const {
        return origin_ + dir_.to_vector() * 10000.0;
    }
    Point_2 point(FT t) const {
        return origin_ + dir_.to_vector() * t;
    }
    Direction_2 direction() const {
        return dir_;
    }
    Line_2 supporting_line() const {
        return Line_2(origin_, point(1));
    }
    Ray_2 opposite() const {
        return Ray_2(origin_, -dir_);
    }
    bool is_degenerate() const {
        return false;
    }
    bool has_on(const Point_2 &p) const;
    bool collinear_has_on(const Point_2 &p) const;
};

// =====================================================================
// Aff_transformation_2 — 仿射变换 [2x3 matrix]
// x' = m[0]*x + m[1]*y + m[2]
// y' = m[3]*x + m[4]*y + m[5]
// =====================================================================
class Aff_transformation_2 {
public:
    FT m_[6];

    enum Identity { IDENTITY };
    enum Scaling { SCALING };
    enum Rotation { ROTATION };
    enum Translation { TRANSLATION };

    Aff_transformation_2(Identity) {
        m_[0] = 1;
        m_[1] = 0;
        m_[2] = 0;
        m_[3] = 0;
        m_[4] = 1;
        m_[5] = 0;
    }
    Aff_transformation_2(Scaling, FT sx, FT sy) {
        m_[0] = sx;
        m_[1] = 0;
        m_[2] = 0;
        m_[3] = 0;
        m_[4] = sy;
        m_[5] = 0;
    }
    Aff_transformation_2(Rotation, FT sin_v, FT cos_v, FT /*w*/ = 1.0) {
        m_[0] = cos_v;
        m_[1] = -sin_v;
        m_[2] = 0;
        m_[3] = sin_v;
        m_[4] = cos_v;
        m_[5] = 0;
    }
    Aff_transformation_2(Translation, FT tx, FT ty) {
        m_[0] = 1;
        m_[1] = 0;
        m_[2] = tx;
        m_[3] = 0;
        m_[4] = 1;
        m_[5] = ty;
    }
    Aff_transformation_2(FT m00, FT m01, FT m02, FT m10, FT m11, FT m12) {
        m_[0] = m00;
        m_[1] = m01;
        m_[2] = m02;
        m_[3] = m10;
        m_[4] = m11;
        m_[5] = m12;
    }

    Point_2 transform(const Point_2 &p) const {
        return Point_2(m_[0] * p.x() + m_[1] * p.y() + m_[2], m_[3] * p.x() + m_[4] * p.y() + m_[5]);
    }
    Vector_2 transform(const Vector_2 &v) const {
        return Vector_2(m_[0] * v.x() + m_[1] * v.y(), m_[3] * v.x() + m_[4] * v.y());
    }
    Aff_transformation_2 inverse() const {
        FT det = m_[0] * m_[4] - m_[1] * m_[3];
        if (is_zero(det))
            throw std::runtime_error("Singular transformation");
        FT inv = 1.0 / det;
        return Aff_transformation_2(m_[4] * inv, -m_[1] * inv, (m_[1] * m_[5] - m_[2] * m_[4]) * inv, -m_[3] * inv,
                                    m_[0] * inv, (m_[2] * m_[3] - m_[0] * m_[5]) * inv);
    }
    Aff_transformation_2 operator*(const Aff_transformation_2 &o) const {
        return Aff_transformation_2(m_[0] * o.m_[0] + m_[1] * o.m_[3], m_[0] * o.m_[1] + m_[1] * o.m_[4],
                                    m_[0] * o.m_[2] + m_[1] * o.m_[5] + m_[2], m_[3] * o.m_[0] + m_[4] * o.m_[3],
                                    m_[3] * o.m_[1] + m_[4] * o.m_[4], m_[3] * o.m_[2] + m_[4] * o.m_[5] + m_[5]);
    }
    Point_2 operator()(const Point_2 &p) const {
        return transform(p);
    }
    Vector_2 operator()(const Vector_2 &v) const {
        return transform(v);
    }
};

inline Vector_2 Vector_2::transform(const Aff_transformation_2 &t) const {
    return t.transform(*this);
}

// =====================================================================
// 内部辅助空间：循环迭代器实现 (CGAL Circulator 仿制)
// =====================================================================
namespace internal {

class Vertex_circulator_impl {
public:
    using iterator_category = std::bidirectional_iterator_tag;
    using value_type = Point_2;
    using difference_type = std::ptrdiff_t;
    using pointer = Point_2 *;
    using reference = Point_2 &;

    std::vector<Point_2> *verts_;
    // 使用有符号类型，确保 idx_==0 时 -- 能正确回绕到底部
    std::ptrdiff_t idx_;
    Vertex_circulator_impl() : verts_(nullptr), idx_(0) {}
    explicit Vertex_circulator_impl(std::vector<Point_2> &v) : verts_(&v), idx_(0) {}
    // 循环取模运算确保非负 idx_：C++ 中 % 对负数的行为为负，手动 n 取模
    size_t norm_idx() const {
        if (!verts_ || verts_->empty())
            return 0;
        std::ptrdiff_t n = static_cast<std::ptrdiff_t>(verts_->size());
        return static_cast<size_t>(((idx_ % n) + n) % n);
    }
    const Point_2 &operator*() const {
        return (*verts_)[norm_idx()];
    }
    Point_2 &operator*() {
        return (*verts_)[norm_idx()];
    }
    Point_2 *operator->() {
        return &(*(*this));
    }
    const Point_2 *operator->() const {
        return &(*(*this));
    }
    Vertex_circulator_impl &operator++() {
        ++idx_;
        return *this;
    }
    Vertex_circulator_impl operator++(int) {
        auto t = *this;
        ++idx_;
        return t;
    }
    Vertex_circulator_impl &operator--() {
        --idx_;
        return *this;
    }
    Vertex_circulator_impl operator--(int) {
        auto t = *this;
        --idx_;
        return t;
    }
    Vertex_circulator_impl &operator+=(difference_type n) {
        idx_ += n;
        return *this;
    }
    Vertex_circulator_impl &operator-=(difference_type n) {
        idx_ -= n;
        return *this;
    }
    Vertex_circulator_impl operator+(difference_type n) const {
        auto t = *this;
        t += n;
        return t;
    }
    Vertex_circulator_impl operator-(difference_type n) const {
        auto t = *this;
        t -= n;
        return t;
    }
    bool operator==(const Vertex_circulator_impl &o) const {
        return norm_idx() == o.norm_idx() && verts_ == o.verts_;
    }
    bool operator!=(const Vertex_circulator_impl &o) const {
        return !(*this == o);
    }
    size_t index() const {
        return norm_idx();
    }
};

class Vertex_const_circulator_impl {
public:
    using iterator_category = std::bidirectional_iterator_tag;
    using value_type = Point_2;
    using difference_type = std::ptrdiff_t;
    using pointer = const Point_2 *;
    using reference = const Point_2 &;

    const std::vector<Point_2> *verts_;
    // 使用有符号类型，确保 idx_==0 时 -- 能正确回绕到底部
    std::ptrdiff_t idx_;
    Vertex_const_circulator_impl() : verts_(nullptr), idx_(0) {}
    explicit Vertex_const_circulator_impl(const std::vector<Point_2> &v) : verts_(&v), idx_(0) {}
    // 循环取模运算确保非负 idx_：C++ 中 % 对负数的行为为负，手动 n 取模
    size_t norm_idx() const {
        if (!verts_ || verts_->empty())
            return 0;
        std::ptrdiff_t n = static_cast<std::ptrdiff_t>(verts_->size());
        return static_cast<size_t>(((idx_ % n) + n) % n);
    }
    const Point_2 &operator*() const {
        return (*verts_)[norm_idx()];
    }
    const Point_2 *operator->() const {
        return &(*(*this));
    }
    Vertex_const_circulator_impl &operator++() {
        ++idx_;
        return *this;
    }
    Vertex_const_circulator_impl operator++(int) {
        auto t = *this;
        ++idx_;
        return t;
    }
    Vertex_const_circulator_impl &operator--() {
        --idx_;
        return *this;
    }
    Vertex_const_circulator_impl operator--(int) {
        auto t = *this;
        --idx_;
        return t;
    }
    Vertex_const_circulator_impl &operator+=(difference_type n) {
        idx_ += n;
        return *this;
    }
    Vertex_const_circulator_impl &operator-=(difference_type n) {
        idx_ -= n;
        return *this;
    }
    Vertex_const_circulator_impl operator+(difference_type n) const {
        auto t = *this;
        t += n;
        return t;
    }
    Vertex_const_circulator_impl operator-(difference_type n) const {
        auto t = *this;
        t -= n;
        return t;
    }
    bool operator==(const Vertex_const_circulator_impl &o) const {
        return norm_idx() == o.norm_idx() && verts_ == o.verts_;
    }
    bool operator!=(const Vertex_const_circulator_impl &o) const {
        return !(*this == o);
    }
    size_t index() const {
        return norm_idx();
    }
};

class Edge_iterator_impl {
public:
    const Polygon_2 *poly_;
    size_t idx_;
    Edge_iterator_impl(const Polygon_2 *p = nullptr, size_t i = 0) : poly_(p), idx_(i) {}
    Segment_2 operator*() const; // 实现在 geo_types.cc (需要完整 Polygon_2 定义)
    // 代理指针操作符，支持 it->method() 语法 (CGAL 迭代器兼容)
    struct ArrowProxy {
        Segment_2 seg;
        Segment_2 *operator->() {
            return &seg;
        }
    };
    ArrowProxy operator->() const {
        return ArrowProxy{operator*()};
    }
    Edge_iterator_impl &operator++() {
        ++idx_;
        return *this;
    }
    Edge_iterator_impl operator++(int) {
        auto t = *this;
        ++idx_;
        return t;
    }
    bool operator==(const Edge_iterator_impl &o) const {
        return idx_ == o.idx_;
    }
    bool operator!=(const Edge_iterator_impl &o) const {
        return idx_ != o.idx_;
    }
};

class Edge_const_iterator_impl {
public:
    const Polygon_2 *poly_;
    size_t idx_;
    Edge_const_iterator_impl(const Polygon_2 *p = nullptr, size_t i = 0) : poly_(p), idx_(i) {}
    Segment_2 operator*() const; // 实现在 geo_types.cc (需要完整 Polygon_2 定义)
    // 代理指针操作符，支持 it->method() 语法 (CGAL 迭代器兼容)
    struct ArrowProxy {
        Segment_2 seg;
        const Segment_2 *operator->() {
            return &seg;
        }
    };
    ArrowProxy operator->() const {
        return ArrowProxy{operator*()};
    }
    Edge_const_iterator_impl &operator++() {
        ++idx_;
        return *this;
    }
    Edge_const_iterator_impl operator++(int) {
        auto t = *this;
        ++idx_;
        return t;
    }
    bool operator==(const Edge_const_iterator_impl &o) const {
        return idx_ == o.idx_;
    }
    bool operator!=(const Edge_const_iterator_impl &o) const {
        return idx_ != o.idx_;
    }
};

} // namespace internal

// =====================================================================
// Polygon_2 — 多边形 (CGAL 兼容接口)
// =====================================================================
class Polygon_2 {
public:
    using Vertex_iterator = std::vector<Point_2>::iterator;
    using Vertex_const_iterator = std::vector<Point_2>::const_iterator;

    // 循环迭代器和边迭代器的别名 (内部实现此时已完整 sufficient)
    using Vertex_circulator = internal::Vertex_circulator_impl;
    using Vertex_const_circulator = internal::Vertex_const_circulator_impl;
    using Edge_iterator = internal::Edge_iterator_impl;
    using Edge_const_iterator = internal::Edge_const_iterator_impl;
    using Edge_circulator = internal::Edge_iterator_impl;
    using Edge_const_circulator = internal::Edge_const_iterator_impl;

private:
    std::vector<Point_2> vertices_;

public:
    Polygon_2() {}
    Polygon_2(std::initializer_list<Point_2> init) : vertices_(init) {}

    template <typename InputIterator>
    Polygon_2(InputIterator first, InputIterator last) : vertices_(first, last) {}

    bool empty() const {
        return vertices_.empty();
    }
    std::size_t size() const {
        return vertices_.size();
    }
    void clear() {
        vertices_.clear();
    }

    // --- 顶点访问 ---
    Vertex_iterator vertices_begin() {
        return vertices_.begin();
    }
    Vertex_iterator vertices_end() {
        return vertices_.end();
    }
    Vertex_const_iterator vertices_begin() const {
        return vertices_.begin();
    }
    Vertex_const_iterator vertices_end() const {
        return vertices_.end();
    }

    Vertex_circulator vertices_circulator() {
        return Vertex_circulator(vertices_);
    }
    Vertex_const_circulator vertices_circulator() const {
        return Vertex_const_circulator(vertices_);
    }

    const Point_2 &vertex(size_t i) const {
        return vertices_[i];
    }
    Point_2 &vertex(size_t i) {
        return vertices_[i];
    }
    const Point_2 &operator[](size_t i) const {
        return vertices_[i];
    }
    Point_2 &operator[](size_t i) {
        return vertices_[i];
    }

    // --- 边访问 ---
    Edge_iterator edges_begin() {
        return Edge_iterator(this);
    }
    Edge_iterator edges_end() {
        return Edge_iterator(this, size());
    }
    Edge_const_iterator edges_begin() const {
        return Edge_const_iterator(this);
    }
    Edge_const_iterator edges_end() const {
        return Edge_const_iterator(this, size());
    }

    Edge_circulator edges_circulator() {
        return Edge_circulator(this);
    }
    Edge_const_circulator edges_circulator() const {
        return Edge_const_circulator(this);
    }

    // --- 修改 ---
    void push_back(const Point_2 &p) {
        vertices_.push_back(p);
    }
    void erase_vertex(Vertex_iterator it) {
        vertices_.erase(it);
    }
    void erase_vertex(size_t idx) {
        vertices_.erase(vertices_.begin() + idx);
    }
    void reverse_orientation() {
        std::reverse(vertices_.begin(), vertices_.end());
    }

    template <typename InputIterator>
    void insert(Vertex_iterator pos, InputIterator first, InputIterator last) {
        vertices_.insert(pos, first, last);
    }
    // 范围插入 (CGAL 兼容)
    void insert(Vertex_iterator pos, const Point_2 &p) {
        vertices_.insert(pos, p);
    }
    void reserve(size_t n) {
        vertices_.reserve(n);
    }

    // --- 查询 ---
    bool is_simple() const;
    bool is_convex() const;
    Orientation orientation() const;
    bool is_clockwise_oriented() const {
        return orientation() == CLOCKWISE;
    }
    bool is_counterclockwise_oriented() const {
        return orientation() == COUNTERCLOCKWISE;
    }

    Oriented_side oriented_side(const Point_2 &p) const;
    Bounded_side bounded_side(const Point_2 &p) const;
    bool has_on_boundary(const Point_2 &p) const;
    bool has_on_bounded_side(const Point_2 &p) const {
        return bounded_side(p) == ON_BOUNDED_SIDE;
    }
    bool has_on_unbounded_side(const Point_2 &p) const {
        return bounded_side(p) == ON_UNBOUNDED_SIDE;
    }
    bool has_on_negative_side(const Point_2 &p) const;
    bool has_on_positive_side(const Point_2 &p) const;
    Point_2::BBox bbox() const;
    FT area() const;
    void simplify(FT tolerance = 1e-3);
    Polygon_2 offset(FT distance) const;

    class Vertex_compare {
    public:
        bool operator()(Vertex_const_iterator a, Vertex_const_iterator b) const {
            return &*a < &*b;
        }
    };

    bool operator==(const Polygon_2 &other) const {
        if (size() != other.size())
            return false;
        for (size_t i = 0; i < size(); ++i)
            if (vertex(i) != other.vertex(i))
                return false;
        return true;
    }
    bool operator!=(const Polygon_2 &other) const {
        return !(*this == other);
    }

    const std::vector<Point_2> &container() const {
        return vertices_;
    }
    std::vector<Point_2> &container() {
        return vertices_;
    }

    std::pair<Polygon_2, Polygon_2> split(Vertex_const_iterator it, Vertex_const_iterator jt) const;
    void remove_redundant_vertices();
    void reverse() {
        reverse_orientation();
    }
};

// Polygon_2 的循环迭代器和边迭代器类型别名 (Polygon_2 完整定义后)
using Vertex_circulator = internal::Vertex_circulator_impl;
using Vertex_const_circulator = internal::Vertex_const_circulator_impl;
using Edge_iterator = internal::Edge_iterator_impl;
using Edge_const_iterator = internal::Edge_const_iterator_impl;
using Edge_circulator = internal::Edge_iterator_impl;
using Edge_const_circulator = internal::Edge_const_iterator_impl;

// =====================================================================
// PolygonWithHoles — 带孔多边形
// =====================================================================
class PolygonWithHoles {
public:
    using Hole_iterator = std::vector<Polygon_2>::iterator;
    using Hole_const_iterator = std::vector<Polygon_2>::const_iterator;

private:
    Polygon_2 outer_boundary_;
    std::vector<Polygon_2> holes_;

public:
    PolygonWithHoles() {}
    PolygonWithHoles(const Polygon_2 &outer) : outer_boundary_(outer) {}

    // 带孔迭代器范围构造函数 (CGAL 兼容)
    template <typename InputIterator>
    PolygonWithHoles(const Polygon_2 &outer, InputIterator holes_begin, InputIterator holes_end) :
        outer_boundary_(outer), holes_(holes_begin, holes_end) {}

    const Polygon_2 &outer_boundary() const {
        return outer_boundary_;
    }
    Polygon_2 &outer_boundary() {
        return outer_boundary_;
    }
    size_t number_of_holes() const {
        return holes_.size();
    }
    bool has_holes() const {
        return !holes_.empty();
    }
    Hole_iterator holes_begin() {
        return holes_.begin();
    }
    Hole_iterator holes_end() {
        return holes_.end();
    }
    Hole_const_iterator holes_begin() const {
        return holes_.begin();
    }
    Hole_const_iterator holes_end() const {
        return holes_.end();
    }
    void add_hole(const Polygon_2 &hole) {
        holes_.push_back(hole);
    }
    void clear_holes() {
        holes_.clear();
    }
    bool is_unbounded() const {
        return outer_boundary_.empty();
    }
    bool is_plane() const {
        return outer_boundary_.empty() && holes_.empty();
    }
    bool is_empty() const {
        return outer_boundary_.empty();
    }
    bool operator==(const PolygonWithHoles &other) const {
        return outer_boundary_ == other.outer_boundary_ && holes_ == other.holes_;
    }
    Point_2::BBox bbox() const {
        return outer_boundary_.bbox();
    }
    FT area() const {
        FT a = outer_boundary_.area();
        for (const auto &h : holes_)
            a += h.area();
        return a;
    }

    // STL-compatible iteration for range-based for
    Hole_iterator begin() {
        return holes_.begin();
    }
    Hole_iterator end() {
        return holes_.end();
    }
    Hole_const_iterator begin() const {
        return holes_.begin();
    }
    Hole_const_iterator end() const {
        return holes_.end();
    }
};

// =====================================================================
// Polygon_set — 多边形集合 (集合运算，使用 Clipper2 实现)
// =====================================================================
struct Polygon_set {
    std::vector<PolygonWithHoles> polygons_;

    Polygon_set() {}
    Polygon_set(const Polygon_2 &p) : polygons_{PolygonWithHoles(p)} {}
    Polygon_set(const PolygonWithHoles &pwh) : polygons_{pwh} {}

    void clear() {
        polygons_.clear();
    }
    bool empty() const {
        return polygons_.empty();
    }
    bool is_plane() const {
        return polygons_.empty();
    }
    size_t number_of_polygons_with_holes() const {
        return polygons_.size();
    }

    // 集合运算 (Clipper2 backend)
    void join(const Polygon_set &other);
    void intersection(const Polygon_set &other);
    void difference(const Polygon_set &other);
    void symmetric_difference(const Polygon_set &other);

    std::vector<PolygonWithHoles> polygons_with_holes() const {
        return polygons_;
    }

    template <typename OutputIterator>
    void polygons_with_holes(OutputIterator out) const {
        for (const auto &pwh : polygons_)
            *out++ = pwh;
    }

    std::vector<Polygon_2> polygons() const;
    void polygons(std::vector<Polygon_2> &result) const;
    void insert(const Polygon_2 &p) {
        polygons_.push_back(PolygonWithHoles(p));
    }
    void insert(const PolygonWithHoles &pwh) {
        polygons_.push_back(pwh);
    }
    void complement();
    void transform(const Aff_transformation_2 &t);
    Point_2::BBox bbox() const;
    bool operator==(const Polygon_set &other) const {
        return polygons_ == other.polygons_;
    }
};

// 交集结果模板
template <typename T>
struct intersection_result;

// Segment_2 与 Segment_2 的 intersection_result，结果为：空、一个点或一条线段
template <>
struct intersection_result<Segment_2> {
    bool success;
    Point_2 point; // 交点 (当 type == POINT 时有效)
    // Type 字段保留供高级用法
    enum Type { EMPTY, POINT, SEGMENT } type = EMPTY;
};

// 类型别名 (coverage_planner 使用)
using intersection_segment_result = intersection_result<Segment_2>;

// ---- 自由函数 (CGAL 风格签名) ----

// 转向判断: p->q->r 的转向
inline Orientation orientation(const Point_2 &p, const Point_2 &q, const Point_2 &r) {
    FT cross = (q.x() - p.x()) * (r.y() - q.y()) - (q.y() - p.y()) * (r.x() - q.x());
    FT scale =
        (std::fabs(q.x() - p.x()) + std::fabs(q.y() - p.y())) * (std::fabs(r.x() - q.x()) + std::fabs(r.y() - q.y()));
    if (cross > EPSILON * scale)
        return LEFT_TURN;
    if (cross < -EPSILON * scale)
        return RIGHT_TURN;
    return COLLINEAR;
}

inline Orientation orientation(const Vector_2 &v1, const Vector_2 &v2) {
    FT cross = v1.determinant(v2);
    FT scale = (std::fabs(v1.x()) + std::fabs(v1.y())) * (std::fabs(v2.x()) + std::fabs(v2.y()));
    if (cross > EPSILON * scale)
        return LEFT_TURN;
    if (cross < -EPSILON * scale)
        return RIGHT_TURN;
    return COLLINEAR;
}

inline bool collinear(const Point_2 &a, const Point_2 &b, const Point_2 &c) {
    return orientation(a, b, c) == COLLINEAR;
}

inline Comparison_result compare_lexicographically(const Point_2 &p, const Point_2 &q) {
    if (approx_equal(p.x(), q.x())) {
        if (approx_equal(p.y(), q.y()))
            return EQUAL;
        return p.y() < q.y() ? SMALLER : LARGER;
    }
    return p.x() < q.x() ? SMALLER : LARGER;
}

inline bool lexicographically_xy_smaller(const Point_2 &p, const Point_2 &q) {
    return compare_lexicographically(p, q) == SMALLER;
}

inline bool has_smaller_signed_distance_to_line(const Point_2 &p, const Point_2 &q, const Line_2 &line) {
    FT dp = line.signed_distance(p), dq = line.signed_distance(q);
    if (approx_equal(dp, dq))
        return lexicographically_xy_smaller(p, q);
    return dp < dq;
}

// 重载: 直线在前面的版本 (某些旧代码的调用习惯)
inline bool has_smaller_signed_distance_to_line(const Line_2 &line, const Point_2 &p, const Point_2 &q) {
    return has_smaller_signed_distance_to_line(p, q, line);
}

// 平方距离
inline FT squared_distance(const Point_2 &p1, const Point_2 &p2) {
    FT dx = p1.x() - p2.x(), dy = p1.y() - p2.y();
    return dx * dx + dy * dy;
}

inline FT squared_distance(const Point_2 &p, const Segment_2 &s) {
    Vector_2 v = s.to_vector(), w = p - s.source();
    FT c1 = w * v;
    if (c1 <= 0)
        return squared_distance(p, s.source());
    FT c2 = v.squared_length();
    if (c2 <= c1)
        return squared_distance(p, s.target());
    FT b = c1 / c2;
    Point_2 pb = s.source() + v * b;
    return squared_distance(p, pb);
}

// 平方距离参数顺序重载 (Segment_2 在前)
inline FT squared_distance(const Segment_2 &s, const Point_2 &p) {
    return squared_distance(p, s);
}

// Segment-Segment 平方距离 (用于 merge_plots 等)
inline FT squared_distance(const Segment_2 &s1, const Segment_2 &s2) {
    // 快速相交检测：使用两条线段的支撑线和参数投影
    // 安全回退到 do_intersect/intersection 的前向声明
    const Vector_2 d1 = s1.to_vector();
    const Vector_2 d2 = s2.to_vector();
    const Vector_2 r = s1.source() - s2.source();
    FT denom = d1.determinant(d2);
    FT t_nom = r.determinant(d2);
    FT u_nom = r.determinant(d1);
    if (std::fabs(denom) > 1e-12) {
        FT t = t_nom / denom;
        FT u = u_nom / denom;
        if (t >= FT(0) && t <= FT(1) && u >= FT(0) && u <= FT(1))
            return FT(0);
    }
    // 返回各端点到另一线段的最小平方距离
    FT d1s = squared_distance(s1.source(), s2);
    FT d2s = squared_distance(s1.target(), s2);
    FT d3s = squared_distance(s2.source(), s1);
    FT d4s = squared_distance(s2.target(), s1);
    FT min12 = (d1s < d2s) ? d1s : d2s;
    FT min34 = (d3s < d4s) ? d3s : d4s;
    return (min12 < min34) ? min12 : min34;
}

inline FT squared_distance(const Point_2 &p, const Line_2 &l) {
    FT d = l.signed_distance(p);
    return d * d;
}

inline FT distance(const Point_2 &p1, const Point_2 &p2) {
    return std::sqrt(squared_distance(p1, p2));
}

inline FT distance(const Point_2 &p, const Line_2 &l) {
    return std::fabs(l.signed_distance(p));
}

// 相交判断 (函数实现在 geo_types.cc)
bool do_intersect(const Segment_2 &s1, const Segment_2 &s2);
bool do_intersect(const Point_2 &p, const Polygon_2 &poly);
bool do_intersect(const Polygon_2 &a, const Polygon_2 &b);

// 求交函数 (函数实现在 geo_types.cc)
// 注: 这些重载必须在此处显式声明，因为包含 Boost Geometry 的 TU 中
//      ADL 无法通过 qualified lookup (geo::intersection) 找到它们
bool intersection(const Segment_2 &s1, const Segment_2 &s2, Point_2 &out_point);
bool intersection(const Segment_2 &seg, const Line_2 &line, Point_2 &out_point);
bool intersection(const Line_2 &line, const Segment_2 &seg, Point_2 &out_point);
bool intersection(const Ray_2 &ray, const Line_2 &line, Point_2 &out_point);
bool intersection(const Line_2 &line, const Ray_2 &ray, Point_2 &out_point);
bool intersection(const Ray_2 &r1, const Ray_2 &r2, Point_2 &out_point);
intersection_result<Segment_2> intersection(const Segment_2 &s1, const Segment_2 &s2);

// 叉积 (自由函数版本，CGAL 兼容)
inline FT determinant(const Vector_2 &v1, const Vector_2 &v2) {
    return v1.determinant(v2);
}

// 有效性检查
inline bool is_valid(const Polygon_2 &poly, bool /*verbose*/ = false) {
    return poly.size() >= 3 && poly.is_simple();
}

inline bool is_valid(const PolygonWithHoles &pwh, bool verbose = false) {
    if (!is_valid(pwh.outer_boundary(), verbose))
        return false;
    for (const auto &h : pwh)
        if (!is_valid(h, verbose))
            return false;
    return true;
}
} // namespace geo
