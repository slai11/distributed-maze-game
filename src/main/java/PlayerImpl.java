import javafx.util.Pair;

public class PlayerImpl implements Player  {
    public String name;
    public int score;

    // position is ALWAYS determined by the primary
    public Pair<Integer, Integer> position;

    private State state;
    private PlayerType playerType;

    public PlayerImpl(String name) {
        this.name = name;
        this.score = 0;
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
        for (PlayerImpl player: bs.players) {
            try {
                if (player.name == this.name) continue;
                this.state = player.register(this);
                break;
            } catch (Exception e) {
                continue;
            }
        }

        if (this.state == null) {
            // this is primary - need to initialise State
            System.out.println("IM THE BOSS");
            this.state = new State(this, bs.N, bs.K);
        }

        // determine player type
        switch (state.players.size()) {
            case 1:
                playerType = PlayerType.Primary;
                break;
            case 2:
                playerType = PlayerType.Backup;
                break;
            default:
                playerType = PlayerType.Normal;
        }
    }

    public void setPosition(Pair<Integer, Integer> pair) {
        position = pair;
    }

    public void setPosition(int i, int j) {
        position = new Pair<Integer, Integer>(i, j);
    }

    // ping does nothing. if its not contactable, remote exception is thrown
    @Override
    public void ping() {}

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
    public State register(PlayerImpl p) throws Exception {
        if (playerType != PlayerType.Primary) {
            throw new Exception("not primary");
        }

        // TODO lock state when adding player
        state.addPlayer(p);

        return state;
    }

    @Override
    public State move(Move move, String caller) throws Exception {
        if (playerType != PlayerType.Primary) {
            throw new Exception("not primary");
        }
        // TODO NOT IMPLEMENTED
        return state;
    }

    @Override
    public State get() throws Exception {
        if (playerType == PlayerType.Primary) {
            throw new Exception("not server");
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
        }

        // send move to primary
        for (PlayerImpl player: state.players) {
            try {
                if (player.name == this.name) continue;

                // non-server changes to game state does not need locking
                this.state = player.move(move, name);
                break;
            } catch (Exception e) {
                continue;
            }
        }
    };

    // TODO not implemented
    public void refreshState() {
        if (playerType == PlayerType.Primary) {
            System.out.print("nothing to refresh");
        }

        for (PlayerImpl player: state.players) {
            // QUESTION - can you freely reference a remote reference's attributes?
            if (player.playerType == PlayerType.Primary) continue;
            try {
                if (player.name == this.name) continue;
                this.state = player.get();
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
