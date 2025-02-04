import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

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
    createResponse(clientSocket.getOutputStream(), httpRequest);
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

  private static void createResponse(OutputStream out, HttpRequest httpRequest)
      throws IOException {
    if ("/".equals(httpRequest.uri)) {
      out.write("HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.UTF_8));
    } else if (httpRequest.uri.startsWith("/echo/")) {
      var pathVariable = httpRequest.uri.replace("/echo/", "");
      if (httpRequest.httpHeaders.containsKey(ACCEPT_ENCODING_HEADER)
          && httpRequest.httpHeaders.get(ACCEPT_ENCODING_HEADER).contains("gzip")) {
        byte[] gzipData = compressAsGzip(pathVariable);
        var response =
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Encoding: gzip\r\nContent-Length: "
                + gzipData.length
                + "\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.write(gzipData);
        out.flush();
        out.close();
      } else {
        String httpResponse = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: "
            + pathVariable.length()
            + "\r\n\r\n" + pathVariable;
        out.write(
            httpResponse.getBytes(StandardCharsets.UTF_8));
      }
    } else if (httpRequest.uri.startsWith("/user-agent")) {
      var headerValue = httpRequest.httpHeaders.get(USER_AGENT_HEADER);
      String httpResponse =
          "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + headerValue.length()
              + "\r\n\r\n" + headerValue;
      out.write(
          httpResponse.getBytes(StandardCharsets.UTF_8));
    } else if ("GET".equals(httpRequest.httpMethod) && httpRequest.uri.startsWith("/files/")) {
      String fileName = httpRequest.uri.replace("/files/", "");
      File file = new File(directory, fileName);
      if (file.exists()) {
        byte[] fileContent = Files.readAllBytes(file.toPath());
        var content = new String(fileContent);
        String httpResponse =
            "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: "
                + content.length() + "\r\n\r\n" + content;
        out.write(httpResponse.getBytes());
      } else {
        out.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
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
        out.write("HTTP/1.1 201 Created\r\n\r\n".getBytes());
      }
    } else {
      out.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
    }
  }

  private static byte[] compressAsGzip(String data) {
    byte[] dataAsBytes = data.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(dataAsBytes.length);
    try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
      gzipOutputStream.write(dataAsBytes);
      gzipOutputStream.finish();
      return byteArrayOutputStream.toByteArray();
    } catch (IOException exception) {
      System.out.println(exception.getMessage());
    }
    return new byte[] {};
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
        String header = requestData.get(i);
        String headerKey = header.split(": ")[0];
        String headerValue = header.split(": ")[1];
        headersMap.put(headerKey, headerValue);
      }
      return headersMap;
    }

    private String readBody(BufferedReader in) throws IOException {
      var bodyBuffer = new StringBuilder();
      while (in.ready()) {
        bodyBuffer.append((char) in.read());
      }
      var body = bodyBuffer.toString();
      System.out.println(body);
      return bodyBuffer.toString();
    }
  }
}