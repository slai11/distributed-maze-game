import java.io.Serializable;
import java.util.Vector;

/**
 * POJO to hold bootstrap items
 */
public class Bootstrap implements Serializable {
    public Vector<Player> players;
    public int N;
    public int K;
    public TrackerInfo trackerInfo;

    public Bootstrap(Vector<Player> players, int N, int K, TrackerInfo trackerInfo) {
        this.players = players;
        this.N = N;
        this.K = K;
        this.trackerInfo = trackerInfo;
    }
}
