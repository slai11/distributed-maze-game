import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
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
    private TrackerInfo trackerInfo;
    private ReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public PlayerImpl(String name) throws RemoteException {
        super();
        this.name = name;
    }

    /**
     * Bootstrap is the "real" constructor
     * @param bs
     */
    public boolean bootstrap(Bootstrap bs) {
        this.trackerInfo = bs.trackerInfo;
        // iterate over player list to register with primary
        // since players contact tracker for (1) first joining and (2) crash recovery
        // the primary could have left. Iterating over player list is the safest method.
        // while slow, only THIS player experiences the slowness.
        if (bs.players.size() == 1) {
            // this is primary - need to initialise State
            this.state = new State(this, name, bs.N, bs.K);
        } else {
            for (Player player: bs.players) {
                try {
                    this.state = player.register(this, name);
                    break;
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        }

        if (this.state == null) {
            return false;
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
        return true;
    }

    // ping does nothing. if its not contactable, remote exception is thrown
    @Override
    public void ping() {}

    @Override
    public void push(State latest) throws Exception {
        if (playerType == PlayerType.Primary) {
            throw new Exception("cannot push to primary " + name);
        }

        if (playerType == PlayerType.Normal) {
            // normal player receiving a push means THIS player is now backup
            playerType = PlayerType.Backup;
        }

        try {
            System.out.println("Received push COUNT: " + latest.count);
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
            System.out.println("can't register with me, im " + name + " " + this + ", im a " + playerType);
            throw new Exception(name + " not primary");
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
    public State shadowMove(Move move, String caller) throws Exception {
        // To enable other players to move while primary is retiring
        // player in action could still in backup or already be set to primary
        if (playerType == PlayerType.Backup || playerType == PlayerType.Primary) {
            System.out.println("Shadowing in progress");
            try {
                rwLock.writeLock().lock();
                state.move(move, caller);
                if (playerType == PlayerType.Primary) {
                    pushToBackup();
                }
                return this.state;
            } finally {
                rwLock.writeLock().unlock();
            }
        }
        return null;
    }

    @Override
    public State move(Move move, String caller) throws Exception {
        if (playerType == PlayerType.Retiree) {
            throw new RetiringException(name + " retiring");
        }

        if (playerType != PlayerType.Primary) {
            throw new Exception(name + " not primary");
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
            System.out.println("Time taken for 1 write: "+ (timeElapsed.toMillis()) +" ms\n");
            pushToBackup();
            return this.state;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public State get(String caller) throws Exception {
        if (playerType == PlayerType.Primary) {
            throw new Exception("cannot fetch from primary " + name);
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
            throw new Exception(name + " is not a primary server, cannot process `leave` request");
        }

        try {
            rwLock.writeLock().lock();
            state.removePlayer(leaver);
            pushToBackup();
            System.out.println("Removed " + leaver + " from state");
            return this.state;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void setPrimary(String leaver) throws Exception {
        if (playerType != PlayerType.Backup) {
            throw new Exception(name + " is not a backup");
        }

        playerType = PlayerType.Primary;
        System.out.println(name + " is now a primary");
        try {
            leave(leaver);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }


    public void quit() {
        if (playerType == PlayerType.Primary) {
            try {
                int i = getPlayerPos();
                int backupPosition = i+1;
                Player backup = state.playerRefs.get(backupPosition);
                playerType = PlayerType.Retiree;
                backup.setPrimary(name);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
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
            System.out.println(name + " [primary] asked to Move " + move);
            try {
                rwLock.writeLock().lock();
                state.move(move, name);
                pushToBackup();
            } catch (Exception e){
                System.out.println(e.getMessage());
            } finally {
                rwLock.writeLock().unlock();
            }
            //state.pretty();
            return;
        }

        int i = 0;
        for (Player player : state.playerRefs) {
            State newState = null;
            try {
                // increment at the beginning of loop. Need to -1 to get the correct position.
                i += 1;
                // needs to be assigned to a new variable to prevent deadlock if `this` is Backup
                newState = player.move(move, name);
            } catch (RetiringException e) {
                System.out.println("Retiring Exception triggered");
                try {
                    rwLock.writeLock().lock();
                    if (playerType == PlayerType.Backup) {
                        System.out.println("I am backup server");
                        state = shadowMove(move, name);
                    } else {
                        System.out.println("I am normal player");
                        Player backup = state.playerRefs.get(i);
                        state = backup.shadowMove(move, name);
                    }
                } catch (Exception e1) {
                    System.out.println("Something wrong when primary quit");
                    System.out.println(e1.getMessage());
                } finally {
                    rwLock.writeLock().unlock();
                }
                break;
            } catch (Exception e) {
                System.out.println(e.getMessage());
                continue;
            }

            if (newState == null) {
                System.out.println(name + " failed to make a move!");
                return;
            }

            try {
                rwLock.writeLock().lock();
                state = newState;
                break;
            } finally {
                rwLock.writeLock().unlock();
            }
        }
        //state.pretty();
    }

    public void refreshState() {
        if (playerType == PlayerType.Primary || playerType == PlayerType.Backup) {
            System.out.println(name + " am " + playerType + " nothing to refresh");
            return;
        }

        // TODO just start from index 1?
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
     */
    private void pushToBackup() throws Exception {
        // do nothing if only primary
        if (state.playerRefs.size() == 1) return;
        for (int i = 1; i < state.playerRefs.size(); i++) {
            try {
                state.playerRefs.get(i).push(state);
                break;
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    // getPlayerPos is a util function to find `this`'s index list of players
    private int getPlayerPos() {
        int i = 0;
        for (PlayerInfo player: state.players) {
            if (player.name.equals(name)) break;
            i++;
        }
        return i;
    }

    /**
     * startBackgroundPing starts a thread that pings other servers
     * See https://stackoverflow.com/questions/12551514/create-threads-in-java-to-run-in-background
     */
    private void startBackgroundPing() {
        Runnable r = () -> {
            int pos = getPlayerPos();
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
                    break;
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            } else {
                try {
                    rwLock.writeLock().lock();
                    this.state = player.leave(leaver); // assignment is required else player may encounter out-of-bound errors
                    break;
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                } finally {
                    rwLock.writeLock().unlock();
                }
            }
        }
    }

    private void handleBackupCrash(int primaryPosition) {
        try {
            String name = state.players.get(primaryPosition + 1).name;
            leave(name);
            removeFromTracker(name);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void handlePrimaryCrash(int backupPosition) {
        // Step 1: become primary
        this.playerType = PlayerType.Primary;

        // Step 2: remove primary. can be after step 1 since other players will fail anyway
        try {
            // Step 3: appoint new backup is handled in the `leave` function
            String name = state.players.get(backupPosition - 1).name;
            leave(name);
            removeFromTracker(name);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void pingBackup(int pos) {
        try {
            if (state.playerRefs.size() <= pos + 1) return;
            state.playerRefs.get(pos + 1).ping();
        } catch (RemoteException e) {
            System.out.println(state.players.get(pos+1).name + " [backup] at " + (pos+1) + " is gone!");
            handleBackupCrash(pos);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void pingPrimary(int pos) {
        try {
            state.playerRefs.get(pos - 1).ping();
        } catch (RemoteException e) {
            System.out.println(state.players.get(pos-1).name + " [primary] at " + (pos-1) + " is gone!");
            handlePrimaryCrash(pos);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
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
            String name = state.players.get(pos+1).name;
            reportCrash(name);
            removeFromTracker(name);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void removeFromTracker(String leaver) {
        try {
            TrackerRMI trackerRMIRef = (TrackerRMI) LocateRegistry.getRegistry(trackerInfo.host, trackerInfo.port).lookup(trackerInfo.name);
            trackerRMIRef.unregister(leaver);
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
            super.paintComponent(g);
            g.setColor(Color.gray);
            g.fillRect(0, 0, 2*grid, grid);
            g.setColor(Color.DARK_GRAY);
            g.fillRect(grid,0, grid, grid);
            g.setColor(Color.gray);
            int n = state.getN();
            int cellSize = Math.round(grid/n);
            for(int i = 0; i < n; i++) {
                for(int j = 0; j < n; j++) {
                    g.fillRect(grid + spacing+i*cellSize, spacing+j*cellSize, cellSize-2*spacing, cellSize-2*spacing );
                }
            }
            state.draw(g ,spacing ,cellSize, grid);

            Font f = new Font("Arial", Font.BOLD, 14);
            g.setFont(f);
            g.setColor(Color.black);
            g.drawString("Left->1   Down->2  Right->3   Up->4", cellSize, grid+15);
        }
    }
}
