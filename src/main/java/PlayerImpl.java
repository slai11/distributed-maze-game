import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class PlayerImpl extends UnicastRemoteObject implements Player, Serializable {
    public State state;

    private final String name;
    private PlayerType playerType;
    private ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public PlayerImpl(String name) throws RemoteException {
        super();
        this.name = name;
    }

    /**
     * Bootstrap is the "real" constructor
     * @param bs
     */
    public void bootstrap(Bootstrap bs) {
        // iterate over player list to register with primary
        // since players contact tracker for (1) first joining and (2) crash recovery
        // the primary could have left. Iterating over player list is the safest method.
        // while slow, only THIS player experiences the slowness.
        for (Player player: bs.players) {
            try {
                this.state = player.register(this, name);
                break;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        if (this.state == null) {
            // this is primary - need to initialise State
            this.state = new State(this, name, bs.N, bs.K);
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
        startBackgroundPing();
    }

    // ping does nothing. if its not contactable, remote exception is thrown
    @Override
    public void ping() {}

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void push(State latest) throws Exception {
        if (playerType == PlayerType.Primary) {
            throw new Exception("cannot push to primary");
        }

        if (playerType == PlayerType.Normal) {
            // normal player receiving a push means THIS player is now backup
            playerType = PlayerType.Backup;
        }

        try {
            System.out.println("Received state " + latest.count);
            rwLock.writeLock().lock();
            this.state = latest;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public State register(Player p, String caller) throws Exception {
        if (caller.equals(name)) {
            throw new Exception("cannot register with self");
        }

        if (playerType != PlayerType.Primary) {
            System.out.println("can't reg with me, im " + name + " " + this + ", im a " + playerType);
            throw new Exception("not primary");
        }

        try {
            rwLock.writeLock().lock();
            state.addPlayer(p, caller);
            pushToBackup();
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
            pushToBackup();
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
    public State leave(String leaver) throws Exception {
        if (playerType != PlayerType.Primary) {
            throw new Exception("not primary");
        }

        System.out.println("Removing " + leaver);

        try {
            rwLock.writeLock().lock();
            state.removePlayer(leaver);
            pushToBackup();
            System.out.println("Removed" + leaver);
            return this.state;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void quit() {
        // TODO handle primary
        if (playerType == PlayerType.Primary) {
            return;
        }

        // TODO handle backup
        if (playerType == PlayerType.Backup) {
            return;
        }

        // sends a leave call to the primary
        for (Player player: state.playerRefs) {
            try {
                player.leave(name);
                break;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public void sendMove(Move move) {
        if (this.playerType == PlayerType.Primary) {
            // just update own game state, you're the boss here
            System.out.println("im the primary so i change my own state");
            try {
                rwLock.writeLock().lock();
                state.move(move, name);
            } finally {
                rwLock.writeLock().unlock();
            }

            state.pretty();
            return;
        }

        for (Player player: state.playerRefs) {
            try {
                // needs to be assigned to a new variable to prevent deadlock if `this` is Backup
                State newState = player.move(move, name);

                rwLock.writeLock().lock();
                state = newState;
                break;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        state.pretty();
    }

    public void refreshState() {
        if (playerType == PlayerType.Primary || playerType == PlayerType.Backup) {
            System.out.print("nothing to refresh");
            return;
        }

        for (Player player: state.playerRefs) {
            try {
                rwLock.writeLock().lock();
                this.state = player.get(name);
                break;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }

    /**
     * pushToBackup should occur on EVERY write to state
     * @throws Exception
     */
    private void pushToBackup() throws Exception {
        if (state.playerRefs.size() == 1) return;

        int i = 0;
        for (PlayerInfo player: state.players) {
            if (player.name.equals(name)) break;
            i++;
        }

        if (state.playerRefs.size() < i + 1) {
            // no backup. some grave mistake has happened
            throw new Exception("no backup can be found!");
        }

        state.playerRefs.get(i+1).push(state);
    }

    /**
     * startBackgroundPing starts a thread that pings other servers
     * See https://stackoverflow.com/questions/12551514/create-threads-in-java-to-run-in-background
     */
    private void startBackgroundPing() {
        Runnable r = () -> {
            int pos = 0;
            for (int i = 0; i < state.players.size(); i++) {
                if (state.players.get(i).name.equals(this.name)) {
                    pos = i;
                    break;
                }
            }
            switch (playerType) {
                case Primary:
                    pingBackup(pos);
                    break;
                case Backup:
                    pingPrimary(pos);
                    pingNormal(pos);
                    break;
                case Normal:
                    pingNormal(pos);
                    break;
            }
        };

        scheduler.scheduleAtFixedRate(r, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void reportCrash(String leaver) {
        for (Player player: state.playerRefs) {
            if (this.playerType == PlayerType.Backup) {
                // backup should not lock when reporting a crash to primary
                // since primary will push the updated state to backup.
                try {
                    player.leave(leaver);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            } else {
                try {
                    rwLock.writeLock().lock();
                    this.state = player.leave(leaver);
                    break;
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                } finally {
                    rwLock.writeLock().unlock();
                }
            }
        }
    }

    private void pingBackup(int pos) {
        // TODO
    }

    private void pingPrimary(int pos) {
        // TODO
    }

    /**
     * pingNormal pings the next player in game state's playerRef
     */
    private void pingNormal(int pos) {
        // TODO might need read lock here but its complicated since reportCrash will need to mutate the state
        try {
            if (state.playerRefs.size() <= pos + 1) return;
            state.playerRefs.get(pos + 1).ping();
        } catch (RemoteException e) {
            System.out.println("player at " + (pos+1) + " is gone!");
            reportCrash(state.players.get(pos+1).name);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
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
