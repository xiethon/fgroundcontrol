#pragma once

#include "field/types.hpp"

namespace field_path {

//! 大田航线算法入口
class FieldPathAlgorithm {
public:
    FieldPathResult run(const FieldPathInput &input, const FieldPathParams &params) const;
};

} // namespace field_path
