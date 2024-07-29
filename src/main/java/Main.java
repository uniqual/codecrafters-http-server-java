import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

  private static final String USER_AGENT_HEADER = "User-Agent";
  private static final String ACCEPT_ENCODING_HEADER = "Accept-Encoding";
  private static final ExecutorService executor = Executors.newFixedThreadPool(10);
  private static String directory;

  public static void main(String[] args) {
    try (ServerSocket serverSocket = new ServerSocket(4221)) {
      if (args.length > 1) {
        directory = args[1];
      }

      serverSocket.setReuseAddress(true);
      while (true) {
        Socket clientSocket = serverSocket.accept();
        executor.submit(() -> {
          try {
            handleRequest(clientSocket);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
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
    List<String> requestData = getRequestMetaData(in);
    return new HttpRequest(requestData, in);
  }

  private static List<String> getRequestMetaData(BufferedReader in) throws IOException {
    List<String> requestData = new ArrayList<>();
    String inputLine;
    while ((inputLine = in.readLine()) != null && !inputLine.isEmpty()) {
      System.out.println(inputLine);
      requestData.add(inputLine);
    }
    return requestData;
  }

  private static void createResponse(PrintWriter out, HttpRequest httpRequest)
      throws IOException {
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
    } else if ("GET".equals(httpRequest.httpMethod) && httpRequest.uri.startsWith("/files/")) {
      String fileName = httpRequest.uri.replace("/files/", "");
      File file = new File(directory, fileName);
      if (file.exists()) {
        byte[] fileContent = Files.readAllBytes(file.toPath());
        var content = new String(fileContent);
        out.println("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: "
            + content.length() + "\r\n\r\n" + content);
      } else {
        out.println("HTTP/1.1 404 Not Found\r\n\r\n");
      }
    } else if (httpRequest.httpMethod.equals("POST") && httpRequest.uri.startsWith("/files/")) {
      String fileName = httpRequest.uri.replace("/files/", "");
      File file = new File(directory, fileName);
      if (file.createNewFile()) {
        try (FileWriter fileWriter = new FileWriter(file)) {
          fileWriter.write(httpRequest.body);
        } catch (IOException e) {
          System.out.println("IOException: " + e.getMessage());
        }
        out.println("HTTP/1.1 201 Created\r\n\r\n");
      }
    } else {
      out.println("HTTP/1.1 404 Not Found\r\n\r\n");
    }
  }

  private static class HttpRequest {

    String httpMethod;
    String uri;
    Map<String, String> httpHeaders;
    String body;

    HttpRequest(List<String> requestMetaData, BufferedReader in) throws IOException {
      this.httpMethod = getHttpMethod(requestMetaData);
      this.uri = getUri(requestMetaData);
      this.httpHeaders = processHeaders(requestMetaData);
      this.body = readBody(in);
    }

    private String getHttpMethod(List<String> requestData) {
      if (!requestData.isEmpty()) {
        return requestData.getFirst().split(" ")[0];
      }
      return "";
    }

    private String getUri(List<String> requestData) {
      if (!requestData.isEmpty()) {
        return requestData.getFirst().split(" ")[1];
      }
      return "";
    }

    private Map<String, String> processHeaders(List<String> requestData) {
      Map<String, String> headersMap = new HashMap<>();
      for (int i = 2; i < requestData.size(); i++) {
        String header = requestData.get(2);
        String headerKey = header.split(" ")[0];
        String headerValue = header.split(" ")[1];
        headersMap.put(headerKey, headerValue);
      }
      return headersMap;
    }

    private String readBody(BufferedReader in) throws IOException {
      var bodyBuffer = new StringBuilder();
      while (in.ready()) {
        bodyBuffer.append((char)in.read());
      }
      var body = bodyBuffer.toString();
      System.out.println(body);
      return bodyBuffer.toString();
    }
  }

}