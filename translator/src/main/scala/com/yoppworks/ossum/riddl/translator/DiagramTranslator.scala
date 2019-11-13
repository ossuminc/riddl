package com.yoppworks.ossum.riddl.translator

import java.io.File
import java.io.PrintWriter
import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST._
import org.jfree.graphics2d.svg.SVGGraphics2D
import java.awt.Color
import java.awt.Rectangle

/** Helper Object For Drawing Diagrams In SVG */
object DiagramTranslator {

  private def drawEntity(
    g2: SVGGraphics2D,
    x: Int, // left side coordinate
    y: Int, // top side coordinate
    c: Entity
  ): SVGGraphics2D = {
    val bounds = g2.getFontMetrics().getStringBounds(c.id.value, g2)
    g2
  }

  private def drawContext(
    g2: SVGGraphics2D,
    x: Int, // left side coordinate
    y: Int, // top side coordinate
    c: Context
  ): SVGGraphics2D = {
    val bounds = g2.getFontMetrics().getStringBounds(c.id.value, g2)

    g2.setColor(Color.BLACK)
    g2.drawOval(x, y, bounds.getWidth.toInt * 2, bounds.getHeight.toInt * 2)
    g2.setColor(Color.BLUE)
    g2.fillOval(x, y, bounds.getWidth.toInt * 2, bounds.getHeight.toInt * 2)
    g2.drawString(c.id.value, x, y)
    g2
  }
  private def drawDomain(g2: SVGGraphics2D, d: Domain): SVGGraphics2D = {
    g2
  }

  def makeNestingDiagram(file: File, root: RootContainer): Unit = {
    val g2: SVGGraphics2D = new SVGGraphics2D(1600, 900)
    g2.setPaint(Color.RED)
    g2.draw(new Rectangle(10, 10, 280, 180))
    val svgElement: String = g2.getSVGElement
    val pw = new PrintWriter(file)
    try {
      pw.write(svgElement)
    } finally {
      pw.close()
    }
  }
}
