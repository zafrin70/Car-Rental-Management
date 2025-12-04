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
import java.util.regex.Pattern;

public class CarRentalServer {

    private static final int PORT = 8080;
    private static final SimpleDateFormat HTML_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat STORAGE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    // ============================
    // MAIN METHOD
    // ============================

    public static void main(String[] args) throws IOException {
        CarRentalSystem backend = new CarRentalSystem();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Endpoints
        server.createContext("/", new RootHandler(backend));
        server.createContext("/style.css", new StyleHandler());
        server.createContext("/book", new BookHandler(backend));
        server.createContext("/cancel", new CancelHandler(backend));
        server.createContext("/register", new RegisterHandler(backend));
        server.createContext("/login", new LoginHandler(backend));
        server.createContext("/logout", new LogoutHandler()); // NEW: Logout Handler
        server.createContext("/admin/delete-car", new DeleteCarHandler(backend));
        server.createContext("/images/", new ImageHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("Server is running on port " + PORT);
        System.out.println("Open http://localhost:" + PORT + " in your browser.");
    }

    // ============================
    // HELPER METHODS
    // ============================

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

    private static void sendResponse(HttpExchange exchange, int code, String contentType, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // --- NEW: Email Validation Regex ---
    private static boolean isValidEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        Pattern pat = Pattern.compile(emailRegex);
        return email != null && pat.matcher(email).matches();
    }

    // --- NEW: Get User Email from Cookie ---
    private static String getUserEmailFromCookie(HttpExchange exchange) {
        if (exchange.getRequestHeaders().containsKey("Cookie")) {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            String[] cookies = cookieHeader.split(";");
            for (String cookie : cookies) {
                String[] pair = cookie.trim().split("=");
                if (pair.length == 2 && pair[0].equals("user")) {
                    return pair[1];
                }
            }
        }
        return null;
    }

    private static String generateResultPage(String title, String message, Customer customer) {
        return generateHtmlPage(title,
            "<section class=\"result-section\">" +
                "<h1>" + title + "</h1>" +
                "<p>" + message + "</p>" +
                "<a href=\"/\">Go Back to Home</a>" +
            "</section>",
            customer
        );
    }

    // Overloaded for backward compatibility
    private static String generateResultPage(String title, String message) {
        return generateResultPage(title, message, null);
    }

    // --- UPDATED: HTML Generator with User Profile Logic ---
    private static String generateHtmlPage(String title, String bodyContent, Customer loggedInUser) {
        
        String navLinks;
        
        if (loggedInUser != null) {
            // IF LOGGED IN: Show Name Initial in Circle and Logout Button
            String initial = loggedInUser.getName().substring(0, 1).toUpperCase();
            navLinks = 
                "<li><a href=\"/\" class=\"active\">Home</a></li>" +
                "<li><a href=\"/#booking\">Book</a></li>" +
                "<li><a href=\"/#cancel\">Cancel</a></li>" +
                "<li class=\"user-profile\">" +
                    "<div class=\"profile-icon\" title=\"" + loggedInUser.getName() + "\">" + initial + "</div>" +
                    "<a href=\"/logout\" class=\"logout-btn\">Logout</a>" +
                "</li>";
        } else {
            // IF NOT LOGGED IN: Show Register/Login Link
            navLinks = 
                "<li><a href=\"/#home\" class=\"active\">Home</a></li>" +
                "<li><a href=\"/#booking\">Book</a></li>" +
                "<li><a href=\"/#cancel\">Cancel</a></li>" +
                "<li><a href=\"/#register\">Register/Login</a></li>" +
                "<li><a href=\"/#admin\">Admin</a></li>";
        }

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
                        "<ul>" + navLinks + "</ul>" +
                    "</nav>" +
                "</header>" +
                "<main>" + bodyContent + "</main>" +
                "<footer id=\"contact\"><p>© 2025 DriveNow. All rights reserved.</p></footer>" +
            "</body></html>";
    }

    // Overloaded helper
    private static String generateHtmlPage(String title, String bodyContent) {
        return generateHtmlPage(title, bodyContent, null);
    }

    // ============================
    // HTTP HANDLERS
    // ============================

    // --- Root Handler (Checks Cookie for Auto-Login) ---
    static class RootHandler implements HttpHandler {
        private final CarRentalSystem backend;

        public RootHandler(CarRentalSystem backend) {
            this.backend = backend;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Check for login cookie
            String email = getUserEmailFromCookie(exchange);
            Customer customer = null;
            if (email != null) {
                customer = backend.findCustomerByEmail(email);
            }

            String response = generateRootPageContent(customer);
            // Pass customer object to update Navbar
            sendResponse(exchange, 200, "text/html", generateHtmlPage("Home", response, customer));
        }

        private String generateRootPageContent(Customer customer) {
            StringBuilder carListHtml = new StringBuilder();
            StringBuilder carOptionsHtml = new StringBuilder();
            carOptionsHtml.append("<option value=\"\">Choose a car</option>");

            for (Car car : backend.listCars()) {
                carListHtml.append("<div class=\"car-card\">");
                carListHtml.append(String.format("<img src=\"images/car_%d.jpg\" alt=\"%s\">", car.getId(), car.getModel()));
                carListHtml.append(String.format("<h3>%s</h3>", car.getModel()));
                carListHtml.append(String.format("<p>Price: Tk %.0f / day</p>", car.getDailyPrice()));
                carListHtml.append(String.format("<p class=\"car-description\">%s</p>", car.getDescription()));
                carListHtml.append("<button onclick=\"document.getElementById('booking').scrollIntoView();\">Book Now</button>");
                carListHtml.append("</div>");

                carOptionsHtml.append(String.format("<option value=\"%d\">%s (Tk %.0f / day)</option>",
                        car.getId(), car.getModel(), car.getDailyPrice()));
            }

            // Pre-fill email if logged in
            String userEmail = (customer != null) ? customer.getEmail() : "";
            
            // Hide Login section if already logged in
            String authSection;
            if (customer != null) {
                authSection = "<section id=\"register\" class=\"form-section register-login\">" +
                        "<div><h2>Welcome, " + customer.getName() + "!</h2>" +
                        "<p>You are logged in. Use the form above to book a car.</p></div>" +
                        "</section>";
            } else {
                authSection = "<section id=\"register\" class=\"form-section register-login\">" +
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
                        "</section>";
            }

            return "<section id=\"home\" class=\"hero\">" +
                    "<h1>DriveNow Car Rental</h1>" +
                    "<p>Your journey begins here. Explore our fleet.</p>" +
                    "<div class=\"car-list\">" + carListHtml.toString() + "</div>" +
                    "</section>" +

                    "<section id=\"booking\" class=\"booking-section\">" +
                    "<h2>Book a Car</h2>" +
                    "<form action=\"/book\" method=\"post\">" +
                        "<select name=\"car\" required>" + carOptionsHtml.toString() + "</select>" +
                        "<input type=\"text\" name=\"email\" placeholder=\"Your Email (for booking)\" value=\""+ userEmail +"\" required>" +
                        "<input type=\"text\" name=\"phone\" placeholder=\"Your Phone Number\" required>" +
                        "<input type=\"date\" name=\"date\" placeholder=\"Start Date\" required>" +
                        "<input type=\"number\" name=\"days\" placeholder=\"Duration (Days)\" min=\"1\" required>" +
                        "<label><input type=\"checkbox\" name=\"advancePaid\" value=\"true\"> Pay 20% Advance Now</label>" +
                        "<button type=\"submit\">Confirm Booking</button>" +
                    "</form>" +
                    "</section>" +

                    "<section id=\"cancel\" class=\"form-section\">" +
                    "<h2>Cancel Booking</h2>" +
                    "<form action=\"/cancel\" method=\"post\">" +
                        "<input type=\"email\" name=\"email\" placeholder=\"Your Email\" value=\""+ userEmail +"\" required>" +
                        "<input type=\"date\" name=\"date\" placeholder=\"Booking Start Date\" required>" +
                        "<button type=\"submit\">Cancel Booking</button>" +
                    "</form>" +
                    "</section>" +
                    
                    authSection + 

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

    static class ImageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            File file = new File(path.substring(1));
            if (file.exists() && file.isFile()) {
                Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", Files.probeContentType(file.toPath()));
                headers.set("Cache-Control", "max-age=3600");
                exchange.sendResponseHeaders(200, file.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    Files.copy(file.toPath(), os);
                }
            } else {
                sendResponse(exchange, 404, "text/plain", "Image Not Found");
            }
        }
    }

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
                // Get current user if logged in
                String userEmail = getUserEmailFromCookie(exchange);
                Customer loggedInUser = (userEmail != null) ? backend.findCustomerByEmail(userEmail) : null;

                Map<String, String> params = parseFormData(exchange.getRequestBody());
                String email = params.get("email");
                String carIdStr = params.get("car");
                String dateStr = params.get("date");
                String daysStr = params.get("days");
                boolean advancePaid = "true".equals(params.get("advancePaid"));

                if (email == null || carIdStr == null || dateStr == null || daysStr == null) {
                    sendResponse(exchange, 400, "text/html", generateResultPage("Booking Failed", "All required fields must be filled.", loggedInUser));
                    return;
                }

                int carId = Integer.parseInt(carIdStr);
                int days = Integer.parseInt(daysStr);

                Customer customer = backend.findCustomerByEmail(email);
                if (customer == null) {
                    sendResponse(exchange, 404, "text/html", generateResultPage("Booking Failed", "Customer not found. Please register first.", loggedInUser));
                    return;
                }

                Date htmlDate = HTML_DATE_FORMAT.parse(dateStr);
                String storageDate = STORAGE_DATE_FORMAT.format(htmlDate);

                boolean success = backend.bookCar(carId, customer, storageDate, days, advancePaid);

                if (success) {
                    Car car = backend.findCarById(carId).get();
                    String message = String.format(
                        "Your booking for **%s** is confirmed! <br>Start Date: **%s** for **%d** day(s). <br>Total Cost: **Tk %.2f** (Advance Paid: %s)",
                        car.getModel(), storageDate, days, car.getDailyPrice() * days, advancePaid ? "Yes" : "No"
                    );

                    // Save to DB
                    Booking bookingToSave = new Booking(car, customer, storageDate, days, advancePaid);
                    BookingDAO bookingDAO = new BookingDAO();
                    bookingDAO.saveBooking(bookingToSave);

                    sendResponse(exchange, 200, "text/html", generateResultPage("Booking Confirmed!", message, loggedInUser));
                } else {
                    sendResponse(exchange, 409, "text/html", generateResultPage("Booking Failed", "Car not available or invalid ID.", loggedInUser));
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "text/html", generateResultPage("Error", "Error processing request: " + e.getMessage()));
            }
        }
    }

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
                String userEmail = getUserEmailFromCookie(exchange);
                Customer loggedInUser = (userEmail != null) ? backend.findCustomerByEmail(userEmail) : null;

                Map<String, String> params = parseFormData(exchange.getRequestBody());
                String email = params.get("email");
                String dateStr = params.get("date");

                if (email == null || dateStr == null) {
                    sendResponse(exchange, 400, "text/html", generateResultPage("Cancellation Failed", "Email and Date required.", loggedInUser));
                    return;
                }

                Date htmlDate = HTML_DATE_FORMAT.parse(dateStr);
                String storageDate = STORAGE_DATE_FORMAT.format(htmlDate);

                boolean success = backend.cancelBooking(email, storageDate);

                if (success) {
                    BookingDAO bookingDAO = new BookingDAO();
                    bookingDAO.deleteBookingByCustomerAndDate(email, storageDate);
                    sendResponse(exchange, 200, "text/html", generateResultPage("Cancellation Successful", "Booking for " + storageDate + " cancelled.", loggedInUser));
                } else {
                    sendResponse(exchange, 404, "text/html", generateResultPage("Cancellation Failed", "No booking found.", loggedInUser));
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "text/html", generateResultPage("Error", e.getMessage()));
            }
        }
    }

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
                    sendResponse(exchange, 400, "text/html", generateResultPage("Registration Failed", "Name, Email, Password required."));
                    return;
                }

                // --- FIX: FAKE EMAIL CHECK ---
                if (!isValidEmail(email)) {
                    sendResponse(exchange, 400, "text/html", generateResultPage("Registration Failed", "Invalid Email Format. Please use a real email like example@gmail.com"));
                    return;
                }

                Customer newCustomer = backend.registerCustomer(name, email, password, phone, location);

                if (newCustomer != null) {
                    CustomerDAO customerDAO = new CustomerDAO();
                    customerDAO.saveCustomer(name, email, password, phone, location);
                    
                    sendResponse(exchange, 201, "text/html", generateResultPage("Registration Successful!", 
                        "Welcome, **" + newCustomer.getName() + "**! You can now log in."));
                } else {
                    sendResponse(exchange, 409, "text/html", generateResultPage("Registration Failed", "Email already exists."));
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "text/html", generateResultPage("Error", e.getMessage()));
            }
        }
    }

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
                    // --- FIX: SET COOKIE FOR LOGIN STATE ---
                    Headers headers = exchange.getResponseHeaders();
                    // Cookie lasts for 1 day (86400 seconds)
                    headers.add("Set-Cookie", "user=" + email + "; Path=/; Max-Age=86400");

                    String msg = "Welcome back, **" + customer.getName() + "**!";
                    // Pass customer object to update header
                    sendResponse(exchange, 200, "text/html", generateResultPage("Login Successful!", msg, customer));
                } else {
                    sendResponse(exchange, 401, "text/html", generateResultPage("Login Failed", "Invalid email or password."));
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "text/html", generateResultPage("Error", e.getMessage()));
            }
        }
    }

    // --- NEW: LOGOUT HANDLER ---
    static class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Delete cookie by setting Max-Age to 0
            Headers headers = exchange.getResponseHeaders();
            headers.add("Set-Cookie", "user=; Path=/; Max-Age=0");
            
            // Redirect to home
            headers.set("Location", "/");
            exchange.sendResponseHeaders(302, -1);
        }
    }

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
                // Check if user is logged in (Optional: you can restrict admin access here)
                String userEmail = getUserEmailFromCookie(exchange);
                Customer loggedInUser = (userEmail != null) ? backend.findCustomerByEmail(userEmail) : null;

                Map<String, String> params = parseFormData(exchange.getRequestBody());
                String carIdStr = params.get("carId");

                if (carIdStr == null || carIdStr.isEmpty()) {
                    sendResponse(exchange, 400, "text/html", generateResultPage("Deletion Failed", "Car ID required.", loggedInUser));
                    return;
                }

                int carId = Integer.parseInt(carIdStr);
                boolean success = backend.deactivateCar(carId);

                if (success) {
                    sendResponse(exchange, 200, "text/html", generateResultPage("Deletion Successful", "Car ID " + carId + " removed.", loggedInUser));
                } else {
                    sendResponse(exchange, 404, "text/html", generateResultPage("Deletion Failed", "Car ID " + carId + " not found.", loggedInUser));
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "text/html", generateResultPage("Error", e.getMessage()));
            }
        }
    }
}