import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class RealEstateEstimator extends JFrame {

    // The URL of our Python Microservice
    private static final String PYTHON_SERVICE_URL = "http://127.0.0.1:8000/predict-house-price";

    private JTextField sizeField, bedField, ageField, locField;
    private JLabel resultLabel;

    public RealEstateEstimator() {
        setTitle("AI Real Estate Estimator");
        setSize(400, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(6, 2, 10, 10));

        // --- UI COMPONENTS ---
        add(new JLabel("  Size (Sq Ft):"));
        sizeField = new JTextField("1500");
        add(sizeField);

        add(new JLabel("  Bedrooms:"));
        bedField = new JTextField("3");
        add(bedField);

        add(new JLabel("  House Age (Years):"));
        ageField = new JTextField("10");
        add(ageField);

        add(new JLabel("  Location Score (1-10):"));
        locField = new JTextField("8");
        add(locField);

        JButton calculateBtn = new JButton("Get AI Estimate");
        calculateBtn.setBackground(new Color(100, 149, 237)); // Cornflower Blue
        calculateBtn.setForeground(Color.WHITE);
        add(new JLabel("")); // Spacer
        add(calculateBtn);

        resultLabel = new JLabel("Price: $---", SwingConstants.CENTER);
        resultLabel.setFont(new Font("Arial", Font.BOLD, 16));
        add(new JLabel("  AI Prediction:"));
        add(resultLabel);

        // --- ACTION LISTENER ---
        calculateBtn.addActionListener(e -> getPrediction());
    }

    private void getPrediction() {
        try {
            // 1. Get Data from UI
            int size = Integer.parseInt(sizeField.getText());
            int beds = Integer.parseInt(bedField.getText());
            int age = Integer.parseInt(ageField.getText());
            int loc = Integer.parseInt(locField.getText());

            // 2. Construct JSON Payload Manually
            // Example: {"size_sqft": 1500, "bedrooms": 3, "age_years": 10, "location_score": 8}
            String jsonPayload = String.format(
                    "{\"size_sqft\": %d, \"bedrooms\": %d, \"age_years\": %d, \"location_score\": %d}",
                    size, beds, age, loc
            );

            // 3. Send HTTP Request to Python
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PYTHON_SERVICE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            resultLabel.setText("Connecting...");

            // 4. Get Response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Simple string parsing to extract price (avoiding extra libraries)
                String responseBody = response.body();
                // responseBody looks like: {"estimated_price":350000.0,"currency":"USD"...}

                String pricePart = responseBody.split(":")[1].split(",")[0]; // Extracts 350000.0
                double price = Double.parseDouble(pricePart);

                resultLabel.setText(String.format("$%,.2f", price));
                resultLabel.setForeground(new Color(0, 128, 0)); // Green text
            } else {
                resultLabel.setText("Error: " + response.statusCode());
            }

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid numbers only.");
        } catch (Exception ex) {
            resultLabel.setText("Connection Failed");
            JOptionPane.showMessageDialog(this, "Is the Python server running?\nError: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RealEstateEstimator().setVisible(true));
    }
}