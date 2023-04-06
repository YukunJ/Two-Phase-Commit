/**
 * Server.java
 * author: Yukun Jiang
 * Date: April 05, 2023
 *
 * This is the implementation for the Server instance
 * in our Two Phase Commit distributed consensus protocol
 *
 * The Server acts as the single only coordinator for the system
 * and initiate, make decisions on various transaction commits
 */

public class Server implements ProjectLib.CommitServing {
  @Override
  public void startCommit(String filename, byte[] img, String[] sources) {
    System.out.println("Server: Got request to commit " + filename);
  }

  public static void main(String args[]) throws Exception {
    if (args.length != 1)
      throw new Exception("Need 1 arg: <port>");
    Server srv = new Server();
    ProjectLib PL = new ProjectLib(Integer.parseInt(args[0]), srv);

    // main loop
    while (true) {
      ProjectLib.Message msg = PL.getMessage();
      System.out.println("Server: Got message from " + msg.addr);
      System.out.println("Server: Echoing message to " + msg.addr);
      PL.sendMessage(msg);
    }
  }
}
