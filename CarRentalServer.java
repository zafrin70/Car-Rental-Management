/*
 * File: CarRentalServer.java
 * Runs a self-contained Java web server for the "DriveNow" Car Rental System.
 *
 * VERSION 3:
 * - Implements Car model with description and soft-delete (`active` flag).
 * - Adds Admin functionality to delete (deactivate) cars: /admin/delete-car
 * - Dynamically generates car listing on the root page.
 * - Persistence for Cars, Customers, and Bookings.
 * * To run:
 * 1. Ensure you have the 5 .java files (Person, Customer, Car, Booking, CarRentalSystem)
 * and style.css in the same directory.
 * 2. Create an 'images' folder and place car images inside, named car_1.jpg to car_7.jpg.
 * 3. Compile: javac *.java
 * 4. Run:     java CarRentalServer
 * 5. Open your browser to: http://localhost:8080
 */

// Imports for web server
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CarRentalServer {

    private static final int PORT = 8080;
    private static final SimpleDateFormat HTML_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat STORAGE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    
    // ============================
    // MAIN METHOD
    // ============================

    public static void main(String[] args) throws IOException {
        // 1. Instantiate the Core System
        CarRentalSystem backend = new CarRentalSystem(); 
        
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // 2. Setup Contexts (Endpoints)
        server.createContext("/", new RootHandler(backend));
        server.createContext("/style.css", new StyleHandler());
        server.createContext("/book", new BookHandler(backend));
        server.createContext("/cancel", new CancelHandler(backend));
        server.createContext("/register", new RegisterHandler(backend));
        server.createContext("/login", new LoginHandler(backend));
        server.createContext("/admin/delete-car", new DeleteCarHandler(backend)); // NEW ADMIN ENDPOINT
        server.createContext("/images/", new ImageHandler());
        
        server.setExecutor(null); // creates a default executor
        server.start();

        System.out.println("Server is running on port " + PORT);
        System.out.println("Open http://localhost:" + PORT + " in your browser.");
    }

    // ============================
    // HELPER METHODS
    // ============================

    /**
     * Simple parser for form data.
     */
    private static Map<String, String> parseFormData(InputStream is) throws IOException {
        InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        String formData = br.readLine();
        Map<String, String> params = new HashMap<>();
        if (formData != null) {
            for (String param : formData.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2) {
                    params.put(
                        URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()), 
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name())
                    );
                }
            }
        }
        return params;
    }

    /**
     * Helper to send a complete HTTP response.
     */
    private static void sendResponse(HttpExchange exchange, int code, String contentType, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    /**
     * Generates a common result page HTML.
     */
    private static String generateResultPage(String title, String message) {
        return generateHtmlPage(title,
            "<section class=\"result-section\">" +
                "<h1>" + title + "</h1>" +
                "<p>" + message + "</p>" +
                "<a href=\"/\">Go Back to Home</a>" +
            "</section>"
        );
    }

    /**
     * Generates the complete HTML structure.
     */
    private static String generateHtmlPage(String title, String bodyContent) {
        return "<!DOCTYPE html>" +
            "<html lang=\"en\">" +
            "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<title>DriveNow | " + title + "</title>" +
                "<link rel=\"stylesheet\" href=\"/style.css\">" +
            "</head>" +
            "<body>" +
                "<header>" +
                    "<div class=\"logo\">DriveNow</div>" +
                    "<nav>" +
                        "<ul>" +
                            "<li><a href=\"#home\" class=\"active\">Home</a></li>" +
                            "<li><a href=\"#booking\">Book</a></li>" +
                            "<li><a href=\"#cancel\">Cancel</a></li>" +
                            "<li><a href=\"#register\">Register/Login</a></li>" +
                            "<li><a href=\"#admin\">Admin</a></li>" +
                        "</ul>" +
                    "</nav>" +
                "</header>" +
                "<main>" + bodyContent + "</main>" +
                "<footer id=\"contact\"><p>© 2025 DriveNow. All rights reserved.</p></footer>" +
            "</body></html>";
    }

    // ============================
    // HTTP HANDLERS
    // ============================
    
    // --- Root Handler (Main Page - Car Display, Forms) ---
    static class RootHandler implements HttpHandler {
        private final CarRentalSystem backend;
        
        public RootHandler(CarRentalSystem backend) {
            this.backend = backend;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = generateRootPageContent();
            sendResponse(exchange, 200, "text/html", generateHtmlPage("Home", response));
        }
        
        private String generateRootPageContent() {
            StringBuilder carListHtml = new StringBuilder();
            StringBuilder carOptionsHtml = new StringBuilder();
            carOptionsHtml.append("<option value=\"\">Choose a car</option>");
            
            // Generate Car Cards and Dropdown Options dynamically
            for (Car car : backend.listCars()) { 
                // Car Card
                carListHtml.append("<div class=\"car-card\">");
                carListHtml.append(String.format("<img src=\"images/car_%d.jpg\" alt=\"%s\">", car.getId(), car.getModel()));
                carListHtml.append(String.format("<h3>%s</h3>", car.getModel()));
                carListHtml.append(String.format("<p>Price: Tk %.0f / day</p>", car.getDailyPrice()));
                carListHtml.append(String.format("<p class=\"car-description\">%s</p>", car.getDescription()));
                carListHtml.append("<button onclick=\"document.getElementById('booking').scrollIntoView();\">Book Now</button>");
                carListHtml.append("</div>");

                // Booking Form Dropdown Option
                carOptionsHtml.append(String.format("<option value=\"%d\">%s (Tk %.0f / day)</option>",
                                                    car.getId(), car.getModel(), car.getDailyPrice()));
            }

            // HTML Structure for the main page
            return 
                // HERO SECTION (Car Display)
                "<section id=\"home\" class=\"hero\">" +
                    "<h1>DriveNow Car Rental</h1>" +
                    "<p>Your journey begins here. Explore our fleet.</p>" +
                    "<div class=\"car-list\">" + carListHtml.toString() + "</div>" +
                "</section>" +
                
                // BOOKING SECTION
                "<section id=\"booking\" class=\"booking-section\">" +
                    "<h2>Book a Car</h2>" +
                    "<form action=\"/book\" method=\"post\">" +
                        "<select name=\"car\" required>" + carOptionsHtml.toString() + "</select>" +
                        "<input type=\"text\" name=\"email\" placeholder=\"Your Email (for booking)\" required>" +
                        "<input type=\"text\" name=\"phone\" placeholder=\"Your Phone Number\" required>" +
                        "<input type=\"date\" name=\"date\" placeholder=\"Start Date\" required>" +
                        "<input type=\"number\" name=\"days\" placeholder=\"Duration (Days)\" min=\"1\" required>" +
                        "<label><input type=\"checkbox\" name=\"advancePaid\" value=\"true\"> Pay 20% Advance Now</label>" +
                        "<button type=\"submit\">Confirm Booking</button>" +
                    "</form>" +
                "</section>" +
                
                // CANCELLATION SECTION
                "<section id=\"cancel\" class=\"form-section\">" +
                    "<h2>Cancel Booking</h2>" +
                    "<form action=\"/cancel\" method=\"post\">" +
                        "<input type=\"email\" name=\"email\" placeholder=\"Your Email\" required>" +
                        "<input type=\"date\" name=\"date\" placeholder=\"Booking Start Date\" required>" +
                        "<button type=\"submit\">Cancel Booking</button>" +
                    "</form>" +
                "</section>" +
                
                // REGISTRATION/LOGIN SECTION
                "<section id=\"register\" class=\"form-section register-login\">" +
                    "<div>" +
                        "<h2>New Customer Registration</h2>" +
                        "<form action=\"/register\" method=\"post\">" +
                            "<input type=\"text\" name=\"name\" placeholder=\"Full Name\" required>" +
                            "<input type=\"email\" name=\"email\" placeholder=\"Email\" required>" +
                            "<input type=\"password\" name=\"password\" placeholder=\"Password\" required>" +
                            "<input type=\"text\" name=\"phone\" placeholder=\"Phone (Optional)\">" +
                            "<input type=\"text\" name=\"location\" placeholder=\"Location (Optional)\">" +
                            "<button type=\"submit\">Register</button>" +
                        "</form>" +
                    "</div>" +
                    "<div>" +
                        "<h2>Existing Customer Login</h2>" +
                        "<form action=\"/login\" method=\"post\">" +
                            "<input type=\"email\" name=\"email\" placeholder=\"Email\" required>" +
                            "<input type=\"password\" name=\"password\" placeholder=\"Password\" required>" +
                            "<button type=\"submit\">Login</button>" +
                        "</form>" +
                    "</div>" +
                "</section>" +
                
                // **NEW ADMIN SECTION (Delete Car)**
                "<section id=\"admin\" class=\"form-section admin-section\">" +
                    "<h2>Admin: Delete Car (Soft Delete)</h2>" +
                    "<p>Enter the ID of the car to remove it from the available rental list.</p>" +
                    "<form action=\"/admin/delete-car\" method=\"post\">" +
                        "<input type=\"number\" name=\"carId\" placeholder=\"Car ID (e.g., 1)\" min=\"1\" required>" +
                        "<button type=\"submit\" style=\"background-color: #f44336;\">Deactivate Car</button>" +
                    "</form>" +
                "</section>";
        }
    }
    
    // --- Style Handler ---
    static class StyleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File file = new File("style.css");
            if (file.exists()) {
                exchange.getResponseHeaders().set("Content-Type", "text/css");
                exchange.sendResponseHeaders(200, file.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    Files.copy(file.toPath(), os);
                }
            } else {
                sendResponse(exchange, 404, "text/plain", "/* CSS file not found */");
            }
        }
    }
    
    // --- Image Handler ---
    static class ImageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // The path will be like /images/car_1.jpg
            File file = new File(path.substring(1)); // Remove leading '/'
            
            if (file.exists() && file.isFile()) {
                Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", Files.probeContentType(file.toPath()));
                headers.set("Cache-Control", "max-age=3600"); // Cache images for an hour
                exchange.sendResponseHeaders(200, file.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    Files.copy(file.toPath(), os);
                }
            } else {
                sendResponse(exchange, 404, "text/plain", "Image Not Found");
            }
        }
    }

    // --- Book Handler ---
    static class BookHandler implements HttpHandler {
        private final CarRentalSystem backend;

        public BookHandler(CarRentalSystem backend) {
            this.backend = backend;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            try {
                Map<String, String> params = parseFormData(exchange.getRequestBody());
                
                // Get customer info (assuming logged in or using email for lookup/placeholder)
                String email = params.get("email");
                String carIdStr = params.get("car");
                String dateStr = params.get("date");
                String daysStr = params.get("days");
                boolean advancePaid = "true".equals(params.get("advancePaid"));
                
                if (email == null || carIdStr == null || dateStr == null || daysStr == null) {
                    sendResponse(exchange, 400, "text/html", generateResultPage("Booking Failed", "All required fields must be filled."));
                    return;
                }

                int carId = Integer.parseInt(carIdStr);
                int days = Integer.parseInt(daysStr);

                // Find the customer (simple version: find by email, assumes they registered first)
                Customer customer = backend.findCustomerByEmail(email);
                if (customer == null) {
                    sendResponse(exchange, 404, "text/html", generateResultPage("Booking Failed", "Customer not found. Please register first or use the email you registered with."));
                    return;
                }
                
                // Format the date for internal storage
                Date htmlDate = HTML_DATE_FORMAT.parse(dateStr);
                String storageDate = STORAGE_DATE_FORMAT.format(htmlDate);
                
                boolean success = backend.bookCar(carId, customer, storageDate, days, advancePaid);

                if (success) {
                    Car car = backend.findCarById(carId).get(); // Should exist if booking was successful
                    String message = String.format(
                        "Your booking for **%s** is confirmed! <br>Start Date: **%s** for **%d** day(s). <br>Total Cost: **Tk %.2f** (Advance Paid: %s)",
                        car.getModel(), storageDate, days, car.getDailyPrice() * days, advancePaid ? "Yes" : "No"
                    );
                    sendResponse(exchange, 200, "text/html", generateResultPage("Booking Confirmed!", message));
                    // --- Save booking to DB (non-intrusive)
Booking bookingToSave = new Booking(car, customer, storageDate, days, advancePaid);
BookingDAO bookingDAO = new BookingDAO();
boolean bookingSaved = bookingDAO.saveBooking(bookingToSave);
if (!bookingSaved) {
    System.out.println("Warning: Booking saved in-memory but failed to save to DB for customer: " + customer.getEmail());
}
                } else {
                    sendResponse(exchange, 409, "text/html", generateResultPage("Booking Failed", "The selected car is not available on that date, or the car ID is invalid."));
                }
            } catch (NumberFormatException | ParseException e) {
                sendResponse(exchange, 400, "text/html", generateResultPage("Error", "Invalid data format. Please check Car ID, Days, and Date format."));
            } catch (Exception e) {
                sendResponse(exchange, 500, "text/html", generateResultPage("Error", "There was an error processing your request: " + e.getMessage()));
            }
        }
    }

    // --- Cancel Handler ---
    static class CancelHandler implements HttpHandler {
        private final CarRentalSystem backend;

        public CancelHandler(CarRentalSystem backend) {
            this.backend = backend;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            try {
                Map<String, String> params = parseFormData(exchange.getRequestBody());
                String email = params.get("email");
                String dateStr = params.get("date");
                
                if (email == null || dateStr == null) {
                    sendResponse(exchange, 400, "text/html", generateResultPage("Cancellation Failed", "Email and Booking Date are required."));
                    return;
                }
                
                Date htmlDate = HTML_DATE_FORMAT.parse(dateStr);
                String storageDate = STORAGE_DATE_FORMAT.format(htmlDate);

                boolean success = backend.cancelBooking(email, storageDate);

                if (success) {
                    sendResponse(exchange, 200, "text/html", generateResultPage("Cancellation Successful", 
                        "Your booking for **" + storageDate + "** has been cancelled."));
                        // --- Also remove booking from DB
BookingDAO bookingDAO = new BookingDAO();
boolean dbDeleted = bookingDAO.deleteBookingByCustomerAndDate(email, storageDate);
if (!dbDeleted) {
    System.out.println("Warning: Booking removed in-memory but DB delete returned false for " + email + " on " + storageDate);
}
                } else {
                    sendResponse(exchange, 404, "text/html", generateResultPage("Cancellation Failed", 
                        "No booking was found for email '" + email + "' on " + storageDate + "."));
                }
            } catch (ParseException e) {
                 sendResponse(exchange, 400, "text/html", generateResultPage("Error", "Invalid date format."));
            } catch (Exception e) {
                sendResponse(exchange, 500, "text/html", generateResultPage("Error", "There was an error processing your request: " + e.getMessage()));
            }
        }
    }

    // --- Register Handler ---
    static class RegisterHandler implements HttpHandler {
        private final CarRentalSystem backend;

        public RegisterHandler(CarRentalSystem backend) {
            this.backend = backend;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            try {
                Map<String, String> params = parseFormData(exchange.getRequestBody());
                String name = params.get("name");
                String email = params.get("email");
                String password = params.get("password");
                String phone = params.get("phone");
                String location = params.get("location");
                
                if (name == null || email == null || password == null || name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    sendResponse(exchange, 400, "text/html", generateResultPage("Registration Failed", "Name, Email, and Password are required."));
                    return;
                }

                Customer newCustomer = backend.registerCustomer(name, email, password, phone, location);

                if (newCustomer != null) {
                    // --- Save to DB via DAO (non-intrusive)
CustomerDAO customerDAO = new CustomerDAO();
boolean saved = customerDAO.saveCustomer(name, email, password, phone, location);
if (!saved) {
    System.out.println("Warning: Customer registration saved in memory but failed to save to DB for email: " + email);
}
                    sendResponse(exchange, 201, "text/html", generateResultPage("Registration Successful!", 
                        "Welcome, **" + newCustomer.getName() + "**! You can now log in and book a car."));
                } else {
                    sendResponse(exchange, 409, "text/html", generateResultPage("Registration Failed", 
                        "An account with this email already exists."));
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "text/html", generateResultPage("Error", "There was an error processing your request: " + e.getMessage()));
            }
        }
    }
    
    // --- Login Handler ---
    static class LoginHandler implements HttpHandler {
        private final CarRentalSystem backend;

        public LoginHandler(CarRentalSystem backend) {
            this.backend = backend;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            try {
                Map<String, String> params = parseFormData(exchange.getRequestBody());
                String email = params.get("email");
                String password = params.get("password");

                Customer customer = backend.loginCustomer(email, password);

                if (customer != null) {
                    sendResponse(exchange, 200, "text/html", generateResultPage("Login Successful!", 
                        "Welcome back, **" + customer.getName() + "**! You can now proceed to book a car."));
                } else {
                    sendResponse(exchange, 401, "text/html", generateResultPage("Login Failed", 
                        "Invalid email or password. Please try again."));
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "text/html", generateResultPage("Error", "There was an error processing your request: " + e.getMessage()));
            }
        }
    }
    
    // --- NEW: Delete Car Handler (Admin Function) ---
    static class DeleteCarHandler implements HttpHandler {
        private final CarRentalSystem backend;

        public DeleteCarHandler(CarRentalSystem backend) {
            this.backend = backend;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            try {
                // IMPORTANT: This is a simplistic admin function. In a real system, you would check 
                // for administrator credentials here before proceeding.
                Map<String, String> params = parseFormData(exchange.getRequestBody());
                String carIdStr = params.get("carId");

                if (carIdStr == null || carIdStr.isEmpty()) {
                    sendResponse(exchange, 400, "text/html", generateResultPage("Deletion Failed", "Car ID is required."));
                    return;
                }

                int carId = Integer.parseInt(carIdStr);
                boolean success = backend.deactivateCar(carId);

                if (success) {
                    sendResponse(exchange, 200, "text/html", generateResultPage("Deletion Successful", 
                        "Car ID **" + carId + "** has been successfully removed from the rental list."));
                } else {
                    sendResponse(exchange, 404, "text/html", generateResultPage("Deletion Failed", 
                        "Car ID **" + carId + "** not found or already inactive."));
                }

            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "text/html", generateResultPage("Error", "Invalid Car ID format."));
            } catch (Exception e) {
                sendResponse(exchange, 500, "text/html", generateResultPage("Error", "There was an error processing your request: " + e.getMessage()));
            }
        }
    }
}