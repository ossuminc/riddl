function zoomableCirclePack(original_data, width) {

  let format = d3.format(",d")

  let height = width

  let color = d3.scaleLinear()
      .domain([0, 5])
      .range(["hsl(80%,80%,128)", "hsl(20%,20%,228)"])
      .interpolate(d3.interpolateHcl)

  let hierarchy = d3.hierarchy(original_data)
      .sum(d => d.children.length)
      .sort((a, b) => b.value - a.value);

  console.log("hierarchy", hierarchy)

  let pack = d3.pack()
      .size([width, height])
      .padding(3)

  let root = pack(hierarchy)
  let focus = root;
  let view;

  root.r = width / 2
  console.log("root", root)

  const svg = d3.create("svg")
    .attr("viewBox", `-${width / 2} -${height / 2} ${width} ${height}`)
    .style("display", "block")
    .style("margin", "0 -14px")
    .style("background", "grey")
    .style("cursor", "pointer")
    .on("click", (event) => zoom(event, root));

  const node = svg.append("g")
    .selectAll("circle")
    .data(root.descendants().slice(1))
    .join("circle")
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

  zoomTo([root.x, root.y, root.r * 2]);

  function zoomTo(v) {
    const k = width / v[2];
    console.log("k is " + k)
    view = v;
    console.log("v is (" + v + ")")
    label.attr("transform", d => `translate(${(d.x - v[0]) * k},${(d.y - v[1]) * k})`);
    node.attr("transform", d => `translate(${(d.x - v[0]) * k},${(d.y - v[1]) * k})`);
    node.attr("r", d =>  d.r * k );
  }

  function zoom(event, d) {
    const focus0 = focus;
    focus = d;
    const transition = svg.transition()
      .duration(event.altKey ? 7500 : 750)
      .tween("zoom", d => {
          const i = d3.interpolateZoom(view, [focus.x, focus.y, focus.r * 2]);
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
