import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.Registry;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

public class Tracker implements TrackerRMI {
    // players here should not have game state, just player info
    private Vector<Player> players;
    private Vector<String> playerIds;
    private final int N;
    private final int K;
    private TrackerInfo trackerInfo;

    private ReentrantLock lock = new ReentrantLock();

    public Tracker(int port, int N, int K, String name) {
        this.players = new Vector<>();
        this.playerIds = new Vector<>();
        this.N = N;
        this.K = K;

        try {
            System.out.println("Tracker's IP Host Address: " + InetAddress.getLocalHost().getHostAddress());
            this.trackerInfo = new TrackerInfo(InetAddress.getLocalHost().getHostAddress(), port, name);
        } catch (Exception e) {
            System.out.println("Error");
        }
    }

    @Override
    public Bootstrap register(Player player, String name) {
        System.out.println("new player registering!");
        try {
            lock.lock();
            players.addElement(player);
            playerIds.addElement(name);
            return new Bootstrap(players, N, K, trackerInfo);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void unregister(String name) {
        System.out.println("Remove crashed player from tracker " + name);
        int i;
        for (i = 0; i < playerIds.size(); i++) {
            if (playerIds.get(i).equals(name)) {
                break;
            }
        }

        // Prevent same deletion > once
        if (i < playerIds.size()) {
            try {
                lock.lock();
                players.remove(i);
                playerIds.remove(i);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) {
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
            String stubId= "TrackerRMI";
            Tracker t = new Tracker(port, n, k, stubId);
            stub = (TrackerRMI) UnicastRemoteObject.exportObject(t, 0);
            registry = LocateRegistry.createRegistry(port);
            registry.bind(stubId, stub);

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
