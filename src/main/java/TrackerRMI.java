import java.rmi.Remote;
import java.rmi.RemoteException;

public interface TrackerRMI extends Remote {
    Bootstrap register(Player myName) throws RemoteException;
}
