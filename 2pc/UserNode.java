/**
 * UserNode.java
 * author: Yukun Jiang
 * Date: April 05, 2023
 *
 * This is the implementation for the UserNode instance
 * in our Two Phase Commit distributed consensus protocol
 *
 * The UserNode acts as the participant for the system
 * and responds to transaction requests from the Server coordinator
 */

public class UserNode implements ProjectLib.MessageHandling {
  public final String myId;
  public UserNode(String id) {
    myId = id;
  }

  @Override
  public boolean deliverMessage(ProjectLib.Message msg) {
    System.out.println(myId + ": Got message from " + msg.addr);
    return true;
  }

  public static void main(String args[]) throws Exception {
    if (args.length != 2)
      throw new Exception("Need 2 args: <port> <id>");
    UserNode UN = new UserNode(args[1]);
    ProjectLib PL = new ProjectLib(Integer.parseInt(args[0]), args[1], UN);

    ProjectLib.Message msg = new ProjectLib.Message("Server", "hello".getBytes());
    System.out.println(args[1] + ": Sending message to " + msg.addr);
    PL.sendMessage(msg);
  }
}
