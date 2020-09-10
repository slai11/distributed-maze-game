import java.io.Serializable;

public class Position implements Serializable {
    int x;
    int y;

    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
