import javax.swing.*;
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

        PlayerImpl playerRef = new PlayerImpl(id);

        // bootstrap phase
        TrackerRMI trackerRMIRef = (TrackerRMI) LocateRegistry.getRegistry(host, port).lookup("TrackerRMI");
        Bootstrap bs = trackerRMIRef.register(playerRef);
        playerRef.bootstrap(bs);

        System.out.println(bs.players.size());

        System.out.println("Bootstrapped and ready to go");

        // GUI phase
        JFrame frame = new JFrame();
        frame.setTitle("Player "+id);
        frame.setSize(750, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        frame.setResizable(false);
        JPanel panel = playerRef.getPanel();
        frame.getContentPane().add(panel);

        // Input phase
        Scanner s = new Scanner(System.in);
        char c = s.next().charAt(0);
        while(c != '9') {
            switch (c) {
                case '0':
                    playerRef.refreshState();
                    break;
                case '1':
                    playerRef.sendMove(Move.Left); // west
                    break;
                case '2':
                    playerRef.sendMove(Move.Down); // south
                    break;
                case '3':
                    playerRef.sendMove(Move.Right); // east
                    break;
                case '4':
                    playerRef.sendMove(Move.Up); // north
                    break;

                    // aswd backdoor
                case 'a':
                    playerRef.sendMove(Move.Left);
                    break;
                case 's':
                    playerRef.sendMove(Move.Down);
                    break;
                case 'w':
                    playerRef.sendMove(Move.Up);
                    break;
                case 'd':
                    playerRef.sendMove(Move.Right);
                    break;
            }
            panel.repaint();
            c = s.next().charAt(0);
        }

        // Graceful termination
        playerRef.quit();
        s.close();
        System.out.println("Exiting program...");
        System.exit(0);
    }
}
