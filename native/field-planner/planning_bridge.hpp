#pragma once
#include "field/algorithm.hpp"
#include <vector>

namespace fgc {
// 局部米制输入；只开放无障碍单地块规划。
field_path::FieldPathResult planField(const std::vector<double>& coordinates, double spacing, double inset);
}
