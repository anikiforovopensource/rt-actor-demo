/*
 * Copyright (c) 2014, PagerDuty
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.pagerduty.sonar.demo

import java.awt._
import java.awt.geom._
import javax.swing._
import java.awt.event._
import akka.actor._
import akka.pattern._
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import com.pagerduty.sonar.demo.model._
import com.pagerduty.sonar.demo.Demo.Colors


/**
 * @author Aleksey Nikiforov
 */
object SwingDisplay {

  def notMain(args: Array[String]): Unit = {
    val d = new SwingDisplay(stubActor())
    d.show()
  }

  def stubActor(): ActorRef = {
    import akka.testkit.TestProbe
    val sys = ActorSystem("stub")
    TestProbe()(sys).ref
  }
}


/**
 * A hastily written mess of swing rendering code. Read it at your own risk!
 */
class SwingDisplay(val displayAdapter: ActorRef) {

  private val renderHints = new RenderingHints(
    RenderingHints.KEY_ANTIALIASING,
    RenderingHints.VALUE_ANTIALIAS_ON)
  renderHints.put(
    RenderingHints.KEY_RENDERING,
    RenderingHints.VALUE_RENDER_QUALITY)

  private val outlineWidth = 3
  private val thinOutline = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL)
  private val outline = new BasicStroke(outlineWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL)


  private def transform(offset: Pos, scale: Pos, point: Pos): Pos = {
    val x = offset.x + scale.x * point.x
    val y = offset.y + scale.y * point.y
    Pos(x, y)
  }

  private def circleFor(pos: Pos, r: Double): Ellipse2D = {
    val d = r*2
    new Ellipse2D.Double(pos.x - r, pos.y - r, d, d)
  }

  private def drawFrame(g: Graphics2D, frame: DisplayAdapter.Frame): Unit = {

    val dim = panel.getSize()
    val canvasScale = Pos(dim.getWidth, dim.getHeight)
    val lineScale = math.min(canvasScale.x, canvasScale.y)

    val offsetScaleMap: Map[Int, (Pos, Pos)] = {
      val demoSliceHeight = 0.10
      val topSliceHeight = canvasScale.y * demoSliceHeight
      val demoScaleMap = Map(0 -> (Pos(0, 0), canvasScale))

      val sonarNodes = frame.actorsByNode - 0
      val nodeWidth = canvasScale.x / sonarNodes.size
      val nodeHeight = canvasScale.y * (1 - demoSliceHeight*2)
      val sonarNodesMap = sonarNodes.keys.map { id =>
        val index = id - 1
        val offset = Pos(index*nodeWidth, topSliceHeight)
        val scale = Pos(nodeWidth, nodeHeight)
        id -> (offset, scale)
      }.toMap

      demoScaleMap ++ sonarNodesMap
    }

    def heatWidth(heat: Double) = {
      val amount = heat*lineScale*Demo.LineWidthScale
      math.max(1.5, amount)
    }
    def stroke(width: Double) = {
      new BasicStroke(width.toFloat, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL)
    }

    def line(link: DisplayLink): Unit = {
      val apos = {
        val (offset, scale) = offsetScaleMap(link.a.node)
        transform(offset, scale, link.a.position)
      }
      val bpos = {
        val (offset, scale) = offsetScaleMap(link.b.node)
        transform(offset, scale, link.b.position)
      }
      val shape = new Line2D.Double(apos.x, apos.y, bpos.x, bpos.y)

      val original = Colors.Message
      val colorAttenualtion = math.min(link.info.heat, 5)*0.2
      val cappedAttenuation = math.max(colorAttenualtion, 0.5)
      val color = new Color(
        (original.getRed*cappedAttenuation).toInt,
        (original.getGreen*cappedAttenuation).toInt,
        (original.getBlue*cappedAttenuation).toInt)

      g.setColor(color)
      g.setStroke(stroke(heatWidth(link.info.heat)))
      g.draw(shape)
    }

    def circle(offset: Pos, scale: Pos, slot: DisplaySlot): Unit = {
      val pos = transform(offset, scale, slot.position)
      val r = math.min(scale.x*1.25, scale.y) * slot.radius

      g.setColor(slot.fillColor)
      g.fill(circleFor(pos, r))
      g.setColor(slot.borderColor)

      if (slot.borderColor != Colors.Message) {
        if (slot.radius <= 0.02) g.setStroke(thinOutline) else g.setStroke(outline)
        g.draw(circleFor(pos, r))
      }
      else {
        val width = math.max(heatWidth(slot.heat), outlineWidth)
        g.setStroke(stroke(width))
        g.draw(circleFor(pos, r + width*0.5))
      }
    }

    for (link <- frame.links) {
      line(link)
    }

    for (node <- frame.actorsByNode.keys) {
      val (offset, scale) = offsetScaleMap(node)
      val actors = frame.actorsByNode(node)
      for (actor <- actors) {
        circle(offset, scale, actor)
      }
    }
  }

  def drawLegend(g: Graphics2D): Unit = {
    val dim = legend.getSize()
    val canvasScale = Pos(dim.getWidth, dim.getHeight)

    def drawLable(y: Double, border: Color, fill: Color, text: String): Unit = {
      val r = 10
      val yoffset = r * 0.5
      val xfactor = 0.15
      val xoffset = xfactor*canvasScale.x

      val pos = transform(Pos(0, 0), canvasScale, Pos(xfactor, y))
      val shape = circleFor(pos, r)
      g.setColor(fill)
      g.fill(shape)
      g.setColor(border)
      g.setStroke(outline)
      g.draw(shape)

      g.setColor(Color.LIGHT_GRAY)
      g.drawString(text, (xoffset + r + 10).toFloat, (canvasScale.y*y + yoffset).toFloat)
    }

    // These depend on displayAdapter values and node layout values.
    drawLable(0.05, Colors.Outline, Colors.ExternalClients, "Heartbeat Clients")
    drawLable(0.18, Colors.Outline, Colors.Supervisor, "Supervisor")
    drawLable(0.32, Colors.Outline, Colors.Router, "Router")
    drawLable(0.46, Colors.Outline, Colors.Bucket, "Bucket Manager")
    drawLable(0.64, Colors.Outline, Colors.Watcher, "Watcher")
    drawLable(0.82, Colors.Outline, Colors.AlertManager, "Alert Manager")
    drawLable(1 - 0.05, Colors.Outline, Colors.ExternalAlertingSystem, "Alerting System")
  }


  @volatile var frameData = DisplayAdapter.Frame.empty

  private val panel = new JPanel with ActionListener {
    override def paintComponent(graphics: Graphics) {
      super.paintComponent(graphics)

      val g = graphics.asInstanceOf[Graphics2D]
      g.setRenderingHints(renderHints)
      g.setComposite(AlphaComposite.SrcOver)

      drawFrame(g, frameData)
    }

    def actionPerformed(e: ActionEvent): Unit = {
      updateFrameData()
      repaint()
    }

    def updateFrameData(): Unit = {
      implicit val askTimeout = Timeout(1.second)
      (displayAdapter ? DisplayAdapter.NextFrame).mapTo[DisplayAdapter.Frame].map { newFrame =>
        frameData = newFrame
      }
    }
  }
  panel.setDoubleBuffered(true)
  panel.setBackground(Colors.Background)

  private val legend = new JPanel {
    override def paintComponent(graphics: Graphics) {
      super.paintComponent(graphics)

      val g = graphics.asInstanceOf[Graphics2D]
      g.setRenderingHints(renderHints)
      g.setComposite(AlphaComposite.SrcOver)

      drawLegend(g)
    }
  }
  legend.setBackground(new Color(40, 40, 40))
  legend.setPreferredSize(new Dimension(160, 0))


  private def buildAndShow() {
    val frameRate = 30
    val displayTimer = new Timer(1000/frameRate, panel)
    displayTimer.setInitialDelay(100)
    displayTimer.start()

    val frame = new JFrame
    frame.setLayout(new BorderLayout())
    frame.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT)
    frame.add(panel, BorderLayout.CENTER)
    frame.add(legend, BorderLayout.EAST)
    frame.setMinimumSize(new Dimension(600, 400))
    frame.setSize(1000, 600)
    frame.setVisible(true)

    if (displayAdapter.path.name.contains("testActor")) {
      frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
    }
    else {
      frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
    }

    frame.addWindowListener(new WindowAdapter {
      override def windowClosing(e: WindowEvent): Unit = {
        displayTimer.stop()
      }
    })
  }

  def show(): Unit = {
    SwingUtilities.invokeLater(new Runnable {
      def run(): Unit = {
        buildAndShow()
      }
    })
  }
}
