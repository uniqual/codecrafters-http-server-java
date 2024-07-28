import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

  private static final String USER_AGENT_HEADER = "User-Agent";

  public static void main(String[] args) {
    Socket clientSocket;
    try (ServerSocket serverSocket = new ServerSocket(4221)) {

      serverSocket.setReuseAddress(true);
      clientSocket = serverSocket.accept();
      while (true) {
        Runnable runnable = () -> {
          try {
            handleRequest(clientSocket);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        };
        serverSocket.close();
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }

  private static void handleRequest(Socket clientSocket) throws IOException {
    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    HttpRequest httpRequest = parseRequest(in);
    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
    createResponse(out, httpRequest);
  }

  private static HttpRequest parseRequest(BufferedReader in) throws IOException {
    List<String> requestData = readRequest(in);
    String path = requestData.get(0);
    String[] uriInfo = path.split(" ");
    Map<String, String> headers = findKnownHeaders(requestData);
    return new HttpRequest(uriInfo[0], uriInfo[1], headers);
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

  private static Map<String, String> findKnownHeaders(List<String> requestData) {
    Map<String, String> headers = new HashMap<>();
    for (var data : requestData) {
      if (data.startsWith(USER_AGENT_HEADER)) {
        var headerValue = data.split(": ")[1];
        headers.put(USER_AGENT_HEADER, headerValue);
      }
    }
    return headers;
  }

  private static void createResponse(PrintWriter out, HttpRequest httpRequest) {
    if ("/".equals(httpRequest.uri)) {
      out.println("HTTP/1.1 200 OK\r\n\r\n");
    } else if (httpRequest.uri.startsWith("/echo/")) {
      String pathVariable = httpRequest.uri.replace("/echo/", "");
      out.println(
          "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + pathVariable.length()
              + "\r\n\r\n" + pathVariable);
    } else if (httpRequest.uri.startsWith("/user-agent")) {
      var headerValue = httpRequest.httpHeaders.get(USER_AGENT_HEADER);
      out.println(
          "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + headerValue.length()
              + "\r\n\r\n" + headerValue);
    } else {
      out.println("HTTP/1.1 404 Not Found\r\n\r\n");
    }
  }

  private static class HttpRequest {

    String httpMethod;
    String uri;
    Map<String, String> httpHeaders;

    HttpRequest(String httpMethod, String uri, Map<String, String> httpHeaders) {
      this.httpMethod = httpMethod;
      this.uri = uri;
      this.httpHeaders = httpHeaders;
    }

  }

}