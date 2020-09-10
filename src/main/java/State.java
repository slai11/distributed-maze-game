import javafx.util.Pair;

import java.io.Serializable;
import java.util.Random;
import java.util.Vector;

/**
 * State represents the local game state.
 * players provide information of each player's score and location
 * treasures provide information of each treasure's location
 */
public class State implements Serializable {
    public Vector<PlayerImpl> players;
    public Vector<Pair<Integer, Integer>> treasures;
    private int N;
    private int K;

    // TODO function to render state in GUI

    public State(PlayerImpl primary, int n, int k) {
        this.N = n;
        this.K = k;
        this.treasures = new Vector<Pair<Integer,Integer>>();
        this.players = new Vector<PlayerImpl>();

        Random random = new Random();
        primary.setPosition(random.nextInt(N - 1), random.nextInt(N - 1));

        // assign primary to game state
        players.add(primary);

        // randomly generate k treasures
        for(int j = 0; j < K; ++j) {
            Pair<Integer, Integer> pos = randomPosition();
            treasures.add(pos);
        }
    }

    public void addPlayer(PlayerImpl joiner) {
        joiner.setPosition(randomPosition());
        players.add(joiner);
    }

    private Pair<Integer, Integer> randomPosition() {
        boolean isUnique = false;
        int x = 0, y = 0;

        while(!isUnique) {
            Random random = new Random();
            x = random.nextInt(N - 1);
            y = random.nextInt(N - 1);

            isUnique = true;

            for(Pair<Integer, Integer> t : treasures) {
                if(x == t.getKey() && y == t.getValue()) {
                    isUnique = false;
                    break;
                }
            }

            for(PlayerImpl p : players) {
                if(x == p.position.getKey() && y == p.position.getValue()) {
                    isUnique = false;
                    break;
                }
            }
        }

        return new Pair<Integer, Integer>(x, y);
    }
}
