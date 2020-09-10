import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.Registry;
import java.util.Vector;

public class Tracker implements TrackerRMI {
    // players here should not have game state, just player info
    private Vector<PlayerImpl> players;
    private Registry registry;
    private int port;
    private int N;
    private int K;

    public Tracker(int port, int N, int K) {
        this.port = port;
        this.players = new Vector<PlayerImpl>();
        this.N = N;
        this.K = K;

        try {
            System.out.println("Tracker's IP Host Address: " + InetAddress.getLocalHost().getHostAddress());
            registry = LocateRegistry.getRegistry(port);
        } catch (Exception e) {
            System.out.println("Error");
        }
    }

    @Override
    public Bootstrap register(PlayerImpl player) {
        System.out.println("new player registering!");
        players.addElement(player);
        return new Bootstrap(players, N, K);
    }

    @Override
    public void unregister(String name) {
        int i;
        for (i = 0; i < players.size(); i++) {
            if (players.get(i).name == name) {
                break;
            }
        }
        players.remove(i);
    }

    public static void main(String args[]) {
        if(args.length != 3) {
            System.out.println("Wrong number of parameters...exiting");
            System.exit(0);
        }

        int port = Integer.parseInt(args[0]);
        int n = Integer.parseInt(args[1]);
        int k = Integer.parseInt(args[2]);

        TrackerRMI stub = null;
        Registry registry = null;

        try {
            Tracker t = new Tracker(port, n, k);
            stub = (TrackerRMI) UnicastRemoteObject.exportObject(t, 0);
            registry = LocateRegistry.getRegistry(port);
            registry.bind("TrackerRMI", stub);

            System.out.println("Tracker Ready");
        } catch (Exception e1) {
            try {
                registry.unbind("TrackerRMI");
                registry.bind("TrackerRMI", stub);
                System.out.println("Tracker Ready");
            } catch (Exception e2) {
                System.out.println("Tracker exception: " + e2.toString());
                e2.printStackTrace();
            }
        }
    }
}
