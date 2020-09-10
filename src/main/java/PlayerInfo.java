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
}
