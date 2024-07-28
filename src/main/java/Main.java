import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Main {

  private static final String CRLF = "\r\n";

  public static void main(String[] args) {
    Socket clientSocket = null;
    try (ServerSocket serverSocket = new ServerSocket(4221)) {

      serverSocket.setReuseAddress(true);
      clientSocket = serverSocket.accept(); // Wait for connection from the client.

      BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      List<String> requestData = readRequest(in);
      String path = requestData.get(0);
      PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
      writeResponseByPath(out, path);
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }

  private static void writeResponseByPath(PrintWriter out, String path) {
    switch (path) {
      case "GET / HTTP/1.1" -> out.println("HTTP/1.1 200 OK\r\n\r\n");
      default -> out.println("HTTP/1.1 404 Not Found\r\n\r\n");
    }
  }

  private static List<String> readRequest(BufferedReader in) throws IOException {
    List<String> requestData = new ArrayList<>();
    String inputLine;
    while ((inputLine = in.readLine()) != null) {
      System.out.println(inputLine);
      requestData.add(inputLine);
      if (inputLine.isEmpty()) {
        break;
      }
    }
    return requestData;
  }
}