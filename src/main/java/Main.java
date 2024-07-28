import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Main {

  public static void main(String[] args) {
    Socket clientSocket;
    try (ServerSocket serverSocket = new ServerSocket(4221)) {

      serverSocket.setReuseAddress(true);
      clientSocket = serverSocket.accept(); // Wait for connection from the client.

      BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      List<String> requestData = readRequest(in);
      String path = requestData.get(0);
      String[] uriInfo = path.split(" ");
      HttpRequest httpRequest = new HttpRequest(uriInfo[0], uriInfo[1]);
      PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
      writeResponseByPath(out, httpRequest);
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }

  private static void writeResponseByPath(PrintWriter out, HttpRequest httpRequest) {
    if ("/".equals(httpRequest.uri)) {
      out.println("HTTP/1.1 200 OK\r\n\r\n");
    } else if (httpRequest.uri.startsWith("/echo/")) {
      String pathVariable = httpRequest.uri.replace("/echo/", "");
      out.println("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + pathVariable.length() + "\r\n\r\n" + pathVariable);
    } else {
      out.println("HTTP/1.1 404 Not Found\r\n\r\n");
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

  private static class HttpRequest {

    String httpMethod;
    String uri;

    HttpRequest(String httpMethod, String uri) {
      this.httpMethod = httpMethod;
      this.uri = uri;
    }

  }

}