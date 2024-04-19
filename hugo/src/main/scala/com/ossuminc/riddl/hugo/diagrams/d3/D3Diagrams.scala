/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.diagrams.d3

import com.ossuminc.riddl.utils.PathUtils

import java.nio.file.{Files, Path}

object D3Diagrams {

  case class Level(name: String, href: String, children: Seq[Level]) {
    override def toString: String = {
      s"{name:\"$name\",href:\"$href\",children:[${children.map(_.toString).mkString(",")}]}"
    }
  }

  def overview(staticDir: Path, levels: Level): String = {
    val resourceName = "hugo/static/js/tree-map-hierarchy2.js"
    val jsPath = staticDir.resolve(resourceName)
    if !Files.exists(jsPath) then {
      Files.createDirectories(jsPath.getParent)
      PathUtils.copyResource(resourceName, jsPath)
    }
    val json = levels.toString

    s"""
       |<script src="/$resourceName"></script>
       |<script type="module">
       |
       |  import * as d3 from "https://cdn.skypack.dev/d3@7";
       |  console.log('d3', d3.version);
       |  let data = $json ;
       |  let svg = treeMapHierarchy(data, 932);
       |  var element = document.getElementById("graphical-index");
       |  element.appendChild(svg);
       |</script>
       |<div id="graphical-index"></div>
        """.stripMargin
  }
}
