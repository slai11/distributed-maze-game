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
    // index 0 is primary, index 1 is backup - when rendering on GUI
    public Vector<PlayerInfo> players;
    public Vector<Position> treasures;

    private int count;
    private int N;
    private int K;

    // TODO function to render state in GUI

    public State(String name, int n, int k) {
        count = 0;
        this.N = n;
        this.K = k;
        this.treasures = new Vector<Position>();
        this.players = new Vector<PlayerInfo>();

        Random random = new Random();
        // assign primary to game state
        players.addElement(new PlayerInfo(randomPosition(), name));

        // randomly generate k treasures
        for(int j = 0; j < K; ++j) {
            Position pos = randomPosition();
            treasures.add(pos);
        }
    }

    public void pretty() {
        System.out.println("COUNT: " + count);
        for (PlayerInfo p: players) {
            System.out.println(p.name + " has score " + p.score + ". Now at "+ p.pos.x + ", " + p.pos.y );
        }
    }

    public void addPlayer(String name) {
        players.add(new PlayerInfo(randomPosition(), name));
    }

    // lookups are done using player's name. we assume unique for now. we can add checks later
    public void move(Move move, String caller) {
        int i;
        for (i = 0; i < players.size(); i++) {
            if (players.get(i).name.equals(caller)){
                break;
            }
        }

        PlayerInfo moving = players.get(i);

        Position newPosition;
        switch (move) {
            case Up:
                newPosition = new Position(moving.pos.x, moving.pos.y + 1);
                break;
            case Down:
                newPosition = new Position(moving.pos.x, moving.pos.y - 1);
                break;
            case Right:
                newPosition = new Position(moving.pos.x + 1, moving.pos.y);
                break;
            default:
                newPosition = new Position(moving.pos.x - 1, moving.pos.y);
        }

        if (isNewPositionValid(newPosition)) {
            boolean foundTreasure = false;
            int j;
            for (j = 0; j < treasures.size(); j++) {
                if (treasures.get(j).x == newPosition.x && treasures.get(j).y == newPosition.y) {
                    moving.score += 1;
                    foundTreasure = true;
                    break;
                }
            }

            if (foundTreasure) {
                treasures.set(j, randomPosition());
            }

            moving.pos = newPosition;
            players.set(i, moving);
            System.out.println(moving.score);

            count += 1;
            return;
        }

        System.out.println("invalid move");
    }

    // isNewPositionValid checks if new position is out of bound and if any other player already occupies the spot.
    private boolean isNewPositionValid(Position pos) {
        if (pos.x < 0 || pos.x >= N || pos.y < 0 || pos.y >= N) {
            return false;
        }

        for (PlayerInfo p: players) {
            if (p.pos.x == pos.x && p.pos.y == pos.y) {
                return false;
            }
        }

        return true;
    }

    private Position randomPosition() {
        boolean isUnique = false;
        int x = 0, y = 0;

        while(!isUnique) {
            Random random = new Random();
            x = random.nextInt(N - 1);
            y = random.nextInt(N - 1);

            isUnique = true;

            for(Position t : treasures) {
                if(x == t.x && y == t.y) {
                    isUnique = false;
                    break;
                }
            }

            for(PlayerInfo p : players) {
                if(x == p.pos.x && y == p.pos.y) {
                    isUnique = false;
                    break;
                }
            }
        }

        return new Position(x, y);
    }
}
