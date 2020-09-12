import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.Duration;
import java.time.Instant;
import java.util.Vector;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class PlayerImpl extends UnicastRemoteObject implements Player, Serializable {
    private final String name;
    public State state;
    private Vector<Player> players;
    private PlayerType playerType;

    private ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public PlayerImpl(String name) throws RemoteException {
        super();
        this.name = name;
    }

    /**
     * Bootstrap is the "real" constructor
     * @param bs
     */
    public void bootstrap(Bootstrap bs) {
        this.players = bs.players;

        // iterate over player list to register with primary
        // since players contact tracker for (1) first joining and (2) crash recovery
        // the primary could have left. Iterating over player list is the safest method.
        // while slow, only THIS player experiences the slowness.
        for (Player player: players) {
            try {
                this.state = player.register(this, name);
                break;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        if (this.state == null) {
            // this is primary - need to initialise State
            this.state = new State(name, bs.N, bs.K);
            startBackgroundPing();
        }

        switch (state.players.size()) {
            case 1:
                this.playerType = PlayerType.Primary;
                break;
            case 2:
                this.playerType = PlayerType.Backup;
                break;
            default:
                this.playerType = PlayerType.Normal;
        }

        System.out.println(name + " is a " + playerType);
    }

    // ping does nothing. if its not contactable, remote exception is thrown
    @Override
    public void ping() {}

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void push(State latest, Vector<Player> players) throws Exception {
        if (playerType == PlayerType.Primary) {
            throw new Exception("why push to me?");
        }

        if (playerType == PlayerType.Normal) {
            // normal player receiving a push means THIS player is now backup
            playerType = PlayerType.Backup;
        }

        try {
            rwLock.writeLock().lock();
            this.state = latest;
            this.players = players;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public State register(Player p, String caller) throws Exception {
        if (p.getName().equals(name)) {
            throw new Exception("cannot register with self");
        }

        if (playerType != PlayerType.Primary) {
            System.out.println("cant reg with me, im " + name + " " + this + ", im a " + playerType);
            throw new Exception("not primary");
        }

        try {
            rwLock.writeLock().lock();
            players.addElement(p); // add reference to primary server's list of player references
            state.addPlayer(caller);
            return state;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public State move(Move move, String caller) throws Exception {
        if (playerType != PlayerType.Primary) {
            throw new Exception("not primary");
        }

        if (caller.equals(name)) {
            throw new Exception("cannot query from self");
        }

        Instant start = Instant.now();

        System.out.println(caller + " asked to make " + move);

        try {
            rwLock.writeLock().lock();
            state.move(move, caller);
            Duration timeElapsed = Duration.between(start, Instant.now());
            System.out.println("Time taken for 1 write: "+ (timeElapsed.toNanos()) +" ns");

            return this.state;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public State get(String caller) throws Exception {
        if (playerType == PlayerType.Primary) {
            throw new Exception("not server");
        }

        if (caller.equals(this.name)) {
            throw new Exception("cannot query from self");
        }

        try {
            rwLock.readLock().lock();
            return this.state;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void leave(String leaver) throws Exception {
        if (playerType != PlayerType.Primary) {
            throw new Exception("not primary");
        }

        int i;
        for (i = 0; i < state.players.size(); i++) {
            if (state.players.get(i).name.equals(name)) {
                break;
            }
        }
        state.players.remove(i);
    }

    // TODO not implemented
    public void quit() {
        // sends a leave call to the primary
    }

    public void sendMove(Move move) {
        if (this.playerType == PlayerType.Primary) {
            // just update own game state, you're the boss here
            System.out.println("im the primary so i change my own state");
            state.move(move, name);
            state.pretty();
            return;
        }

        // send move to primary
        for (Player player: players) {
            try {
                // non-server changes to game state does not need locking
                state = player.move(move, name);
                break;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        state.pretty();
    }

    public void refreshState() {
        if (playerType == PlayerType.Primary) {
            System.out.print("nothing to refresh");
        }

        for (Player player: players) {
            try {
                this.state = player.get(name);
                break;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * startBackgroundPing starts a thread that pings other servers
     * See https://stackoverflow.com/questions/12551514/create-threads-in-java-to-run-in-background
     */
    private void startBackgroundPing() {
        Runnable r = () -> {
            // TODO ping and recovery actions here
            for (Player p: players) {
                try {
                    p.ping();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    // handle error since it means someone died
                }

            }
        };

        new Thread(r).start();
    }
    public JPanel getPanel() {
        return new Maze();
    }
    public class Maze extends JPanel {
        public int spacing = 5;
        private int grid = 350;

        @Override
        public void paintComponent(Graphics g) {
            g.setColor(Color.gray);
            g.fillRect(0, 0, 2*grid, grid);
            g.setColor(Color.DARK_GRAY);
            g.fillRect(grid,0, grid, grid);
            g.setColor(Color.gray);
            int cellSize = Math.round(grid/state.getN());
            for(int i = 0; i < state.getN(); i++) {
                for(int j = 0; j < state.getN(); j++) {
                    g.fillRect(grid + spacing+i*cellSize, spacing+j*cellSize, cellSize-2*spacing, cellSize-2*spacing );
                }
            }
            state.draw(g ,spacing ,cellSize, grid);
        }
    }
}
