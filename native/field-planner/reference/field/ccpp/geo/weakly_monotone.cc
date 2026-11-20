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

#include "weakly_monotone.h"

namespace polygon_coverage_planning {

bool isWeaklyMonotone(const Polygon_2& in, const Line_2& x_axis) {
  // Find north and south.
  auto north = findNorth(in, x_axis);
  auto south = findSouth(in, x_axis);

  // Go from south to north vertex.
  auto c = south;
  auto c_prev = south;
  for (c++; c != north; c++) {
    if (geo::has_smaller_signed_distance_to_line(x_axis, *c, *c_prev))
      return false;
    c_prev = c;
  }

  // Go opposite direction.
  c = south;
  c_prev = south;
  for (c--; c != north; c--) {
    if (geo::has_smaller_signed_distance_to_line(x_axis, *c, *c_prev))
      return false;
    c_prev = c;
  }

  return true;
}

std::vector<Direction_2> getAllSweepableEdgeDirections(const Polygon_2& in) {
  std::vector<Direction_2> dirs;
  int edge_idx = 0;
  for (auto it = in.edges_begin(); it != in.edges_end(); ++it, ++edge_idx) {
    auto last = std::find_if(dirs.begin(), dirs.end(), [&it](const Direction_2& dir) {
      return geo::orientation(dir.to_vector(), it->to_vector()) == geo::Orientation::COLLINEAR;
    });
    if (last != dirs.end()) continue;
    if (isWeaklyMonotone(in, it->supporting_line())) {
      dirs.push_back(it->direction());

    }
  }
  return dirs;
}


Polygon_2::Vertex_const_circulator findSouth(const Polygon_2& in, const Line_2& x_axis) {
  auto vc = in.vertices_circulator();
  auto v = vc;
  do {
    v = geo::has_smaller_signed_distance_to_line(x_axis, *vc, *v) ? vc : v;
  } while (++vc != in.vertices_circulator());
  return v;
}

Polygon_2::Vertex_const_circulator findNorth(const Polygon_2& in, const Line_2& x_axis) {
  return findSouth(in, x_axis.opposite());
}

}  // namespace polygon_coverage_planning
