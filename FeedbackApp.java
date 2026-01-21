import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Scanner;

public class FeedbackApp {

    // The URL of our Python Microservice
    private static final String PYTHON_SERVICE_URL = "http://127.0.0.1:8000/analyze";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        HttpClient client = HttpClient.newHttpClient();

        System.out.println("=== USER FEEDBACK SYSTEM ===");
        System.out.println("Type 'exit' to quit.\n");

        while (true) {
            System.out.print("Enter your product review: ");
            String input = scanner.nextLine();

            if ("exit".equalsIgnoreCase(input)) {
                break;
            }

            // 1. Create JSON Payload (Manually formatting for simplicity)
            // Ideally, you would use a library like Jackson or Gson
            String jsonPayload = String.format("{\"text\": \"%s\"}", input);

            try {
                // 2. Build the HTTP POST Request
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(PYTHON_SERVICE_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .timeout(Duration.ofSeconds(5))
                        .build();

                System.out.println("Sending to AI Service...");

                // 3. Send and Receive Response
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // 4. Print Result
                if (response.statusCode() == 200) {
                    System.out.println("AI Response: " + response.body());
                } else {
                    System.out.println("Error: Service returned " + response.statusCode());
                }
                System.out.println("--------------------------------------------------");

            } catch (Exception e) {
                System.out.println("Failed to connect to Python Service. Is it running?");
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}