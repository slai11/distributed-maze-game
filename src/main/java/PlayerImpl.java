import javafx.util.Pair;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Vector;

public class PlayerImpl extends UnicastRemoteObject implements Player, Serializable {
    private String name;
    public State state;
    private Vector<Player> players;
    private PlayerType playerType;

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
                this.state = player.register(this);
                break;
            } catch (Exception e) {
                continue;
            }
        }

        if (this.state == null) {
            // this is primary - need to initialise State
            this.state = new State(name, bs.N, bs.K);
        }

        // determine player type
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

        System.out.println(this + " is a " + playerType);
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
        switch (playerType) {
            case Backup:
                // TODO write-lock
                this.state = latest;
                break;
            case Normal:
                // normal player receiving a push means THIS player is now backup
                playerType = PlayerType.Backup;
                // TODO write-lock
                this.state = latest;
                break;
            default:
                throw new Exception("why push to me?");
        }
    }

    @Override
    public State register(Player p) throws Exception {
        if (p.getName() == name) {
            throw new Exception("cannot register with self");
        }
        if (playerType != PlayerType.Primary) {
            System.out.println("cant reg with me, im " + name + " " + this + ", im a " + playerType);
            throw new Exception("not primary");
        }

        // TODO lock state when adding player
        state.addPlayer(p.getName());

        return state;
    }

    @Override
    public State move(Move move, String caller) throws Exception {
        if (playerType != PlayerType.Primary) {
            throw new Exception("not primary");
        }

        if (caller == this.name) {
            throw new Exception("cannot query from self");
        }

        System.out.println(caller + " asked to make " + move);

        state.move(move, caller);

        return this.state;
    }

    @Override
    public State get(String name) throws Exception {
        if (playerType == PlayerType.Primary) {
            throw new Exception("not server");
        }

        if (name == this.name) {
            throw new Exception("cannot query from self");
        }

        // TODO must read-lock
        return state;
    }

    @Override
    public void leave(String leaver) throws Exception {
        if (playerType != PlayerType.Primary) {
            throw new Exception("not primary");
        }

        int i;
        for (i = 0; i < state.players.size(); i++) {
            if (state.players.get(i).name == name) {
                break;
            }
        }
        state.players.remove(i);
    }

    // TODO not implemented
    public void quit() {
        // sends a leave call to the primary
    }

    // TODO not implemented
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
                continue;
            }
        }

        state.pretty();
        System.out.println("DONE");
    };

    // TODO not implemented
    public void refreshState() {
        if (playerType == PlayerType.Primary) {
            System.out.print("nothing to refresh");
        }

        for (Player player: players) {
            try {
                this.state = player.get(name);
                break;
            } catch (Exception e) {
                continue;
            }
        }
    };

    /**
     * startBackgroundPing starts a thread that pings other servers
     * See https://stackoverflow.com/questions/12551514/create-threads-in-java-to-run-in-background
     */
    private void startBackgroundPing() {
        Runnable r = new Runnable() {
            public void run() {
                // TODO ping and recovery actions here
            }
        };

        new Thread(r).start();
    }
}
