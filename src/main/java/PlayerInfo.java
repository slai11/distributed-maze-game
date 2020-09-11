import java.awt.*;
import java.io.Serializable;

public class PlayerInfo implements Serializable {
    public Position pos;
    public String name;
    public int score;

    public PlayerInfo(Position pos, String name) {
        this.pos = pos;
        this.name = name;
        this.score = 0;
    }

    public void draw(Graphics g, int spacing, int cellSize, int offset, int i) {
        //Draw the name into the maze
        Font f = new Font("Arial", Font.BOLD, cellSize/2);
        g.setFont(f);
        g.setColor(Color.black);
        g.drawString(name,  offset + pos.x * cellSize + cellSize/4, (pos.y+1) * cellSize - spacing);

        f = new Font("Arial", Font.BOLD, 25);
        // Draw player's name
        g.drawString(name, cellSize, (i+1) * cellSize);

        // Draw player's score
        g.setColor(Color.RED);
        g.drawString(Integer.toString(score), cellSize * 2, (i+1) * cellSize);

        // Draw whether the player is server or not
        if (i == 0) {
            g.setColor(Color.RED);
            g.drawString("Primary Server", cellSize * 3, (i+1) * cellSize);
        }
        if (i == 1) {
            g.setColor(Color.RED);
            g.drawString("Backup Server", cellSize * 3, (i+1) * cellSize);
        }

    }
}
