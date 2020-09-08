import java.rmi.registry.LocateRegistry;
import java.util.Scanner;

/**
 * Game is the main function that handles user's input
 * it bootstraps start of the game from the tracker
 */
public class Game {
    public static void main(String args[]) throws Exception {
         // Arg parsing
        if(args.length != 3) {
            System.out.print("Wrong number of parameters...exiting");
            System.exit(1);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String id = args[2];

        // bootstrap phase
        TrackerRMI trackerRMIRef = (TrackerRMI) LocateRegistry.getRegistry(host, port).lookup("TrackerRMI");
        PlayerImpl playerRef = new PlayerImpl(id);
        Bootstrap bs = trackerRMIRef.register(playerRef);
        playerRef.bootstrap(bs);

        // Input phase
        Scanner s = new Scanner(System.in);
        char c = s.next().charAt(0);
        while(c != '9') {
            switch (c) {
                case '0':
                    playerRef.refreshState();
                case '1':
                    playerRef.sendMove(Move.Up);
                case '2':
                    playerRef.sendMove(Move.Down);
                case '3':
                    playerRef.sendMove(Move.Left);
                case '4':
                    playerRef.sendMove(Move.Right);
            }

            // TODO update game GUI

            c = s.next().charAt(0);
        }

        // Graceful termination
        playerRef.quit();
        s.close();
        System.out.println("Exiting program...");
        System.exit(0);
    }
}
