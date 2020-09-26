import java.io.Serializable;

public class TrackerInfo implements Serializable {
    public String host;
    public int port;
    public String name;

    public TrackerInfo(String host, int port, String name) {
        this.host = host;
        this.port = port;
        this.name = name;
    }
}
