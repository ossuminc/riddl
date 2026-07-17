function treeMapHierarchy(original_data, width) {

  let format = d3.format(",d")

  let height = width

  let color = d3.scaleLinear()
      .domain([0, 5])
      .range(["hsl(128, 80%,80%)", "hsl(228, 30%,40%)"])
      .interpolate(d3.interpolateHcl)

  let hierarchy = d3.hierarchy(original_data)
      .sum(d => d.value)
      .sort((a, b) => b.value - a.value);

  let treemapLayout = d3.treemap()
      .size([width, height])
      .paddingOuter(3)
      .paddingTop(19)
      .paddingInner(1)
      .round(true)

  // treemapLayout.tile(d3['treemapSquarify'])

  let root = treemapLayout(hierarchy)
  let focus = root;
  let view;

  const svg = d3.create("svg")
      .attr("viewBox", `-${width / 2} -${height / 2} ${width} ${height}`)
      .style("display", "block")
      .style("margin", "0 -14px")
      .style("background", "grey")
      .style("cursor", "pointer")
      .on("click", (event) => zoom(event, root));

  const node = svg.append("g")
      .selectAll('rect')
      .data(root.descendants())
      .join('rect')
      .attr('x', d =>  d.x0 )
      .attr('y', d =>  d.y0 )
      .attr('width', d => d.x1 - d.x0 )
      .attr('height', d => d.y1 - d.y0 )
      .attr("fill", d => d.children ? color(d.depth) : "grey")
      .attr("pointer-events", d => !d.children ? "none" : null)
      .on("mouseover", function() { d3.select(this).attr("stroke", "#000"); })
      .on("mouseout", function() { d3.select(this).attr("stroke", null); })
      .on("click", (event, d) => focus !== d && (zoom(event, d), event.stopPropagation()));

  const label = svg.append("g")
      .style("font", "10px sans-serif")
      .attr("pointer-events", "none")
      .attr("text-anchor", "middle")
      .selectAll("text")
      .data(root.descendants())
      .join("text")
      .style("fill-opacity", d => d.parent === root ? 1 : 0)
      .style("display", d => d.parent === root ? "inline" : "none")
      .text(d => d.data.name);

  {
    const width = root.x1 - root.x0
    const x_center = (width) / 2
    const y_center = (focus.y1 - focus.y0) / 2
    zoomTo([x_center, y_center, width]);
  }

  function zoomTo(v) {
    const k = width / v[2];
    view = v;
    label.attr("transform", d => `translate(${((d.x1-d.x0)/2 - v[0]) * k},${((d.y1-d.y0)/2 - v[1]) * k})`);
    node.attr("transform", d => `translate(${((d.x1-d.x0)/2 - v[0]) * k},${((d.y1-d.y0)/2 - v[1]) * k})`);
  }

  function zoom(event, d) {
    focus = d;
    console.log(d);
    const transition = svg.transition()
        .duration(event.altKey ? 7500 : 750)
        .tween("zoom", d => {
          const width = focus.x1 - focus.x0
          const x_center = (width) / 2
          const y_center = (focus.y1 - focus.y0) / 2
          const i = d3.interpolateZoom(view, [x_center, y_center, width]);
          return t => zoomTo(i(t));
        });

    label
        .filter(function(d) { return d.parent === focus || this.style.display === "inline"; })
        .transition(transition)
        .style("fill-opacity", d => d.parent === focus ? 1 : 0)
        .on("start", function(d) { if (d.parent === focus) this.style.display = "inline"; })
        .on("end", function(d) { if (d.parent !== focus) this.style.display = "none"; });
  }


  return svg.node();
}
