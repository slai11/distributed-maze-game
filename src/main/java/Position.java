import java.awt.*;
import java.io.Serializable;

public class Position implements Serializable {
    int x;
    int y;

    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void draw(Graphics g, int spacing, int cellSize, int offset) {
        g.drawString("*", offset + x * cellSize + cellSize/4, spacing + (y+1) * cellSize);
    }
}
