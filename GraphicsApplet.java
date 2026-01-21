/*
 * INSTRUCTIONS TO RUN:
 * 1. Save this file as GraphicsApplet.java
 * 2. Compile it: javac GraphicsApplet.java
 * 3. Run it: appletviewer GraphicsApplet.java
 */

/*
<applet code="GraphicsApplet.class" width="400" height="400">
</applet>
*/

import java.applet.Applet;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;

public class GraphicsApplet extends Applet {

    // The paint method is automatically called to draw the applet
    public void paint(Graphics g) {

        // 1. Draw Text
        g.setColor(Color.black);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString("Basic Graphics Demo", 100, 30);

        // 2. Draw a Line
        g.setColor(Color.blue);
        g.drawString("Line:", 20, 60);
        // drawLine(x1, y1, x2, y2)
        g.drawLine(20, 70, 150, 70);

        // 3. Draw a Rectangle (Outline)
        g.setColor(Color.red);
        g.drawString("Rectangle:", 20, 100);
        // drawRect(x, y, width, height)
        g.drawRect(20, 110, 100, 50);

        // 4. Draw a Filled Rectangle
        g.setColor(Color.green);
        g.drawString("Filled Rect:", 200, 100);
        // fillRect(x, y, width, height)
        g.fillRect(200, 110, 100, 50);

        // 5. Draw an Oval (Outline)
        g.setColor(Color.magenta);
        g.drawString("Oval:", 20, 190);
        // drawOval(x, y, width, height)
        g.drawOval(20, 200, 100, 60);

        // 6. Draw a Filled Oval (Circle if width == height)
        g.setColor(Color.orange);
        g.drawString("Filled Circle:", 200, 190);
        // fillOval(x, y, width, height)
        g.fillOval(200, 200, 60, 60);

        // 7. Draw an Arc
        g.setColor(Color.darkGray);
        g.drawString("Arc:", 20, 300);
        // drawArc(x, y, width, height, startAngle, arcAngle)
        g.drawArc(20, 310, 80, 80, 0, 180); // A smile shape
    }
}