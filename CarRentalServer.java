/*
 * File: CarRentalServer.java
 * Runs a self-contained Java web server for the "DriveNow" Car Rental System.
 *
 * Replaces the Swing GUI with a web interface based on user's HTML/CSS.
 *
 * Features:
 * - Runs on http://localhost:8080
 * - Serves dynamic HTML homepage with car listings and booking forms.
 * - Serves the style.css file.
 * - Handles booking submissions via HTTP POST to /book.
 * - Handles cancellation submissions via HTTP POST to /cancel.
 * - No JavaScript. Pure server-side rendering.
 * - Persists data using object serialization (cars.dat, bookings.dat).
 *
 * How to run:
 * 1. Save this file as CarRentalServer.java
 * 2. Save the CSS file in the same directory as style.css
 * 3. Compile: javac CarRentalServer.java
 * 4. Run:     java CarRentalServer
 * 5. Open your browser to: http://localhost:8080
 */

// Imports for web server
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;

// Imports for IO, parsing, and persistence
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/* ============================
   MODEL CLASSES (Domain)
   (Copied from your Swing project)
   ============================ */

class Person implements Serializable {
    private static final long serialVersionUID = 1L;
    protected String name;
    public Person(String name) { this.name = name; }
    public String getName() { return name; }
}

class Customer extends Person implements Serializable {
    private static final long serialVersionUID = 1L;
    private String phone;
    private String email;
    private String location;

    public Customer(String name, String phone, String email, String location) {
        super(name);
        this.phone = phone;
        this.email = email;
        this.location = location;
    }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getLocation() { return location; }
    @Override
    public String toString() {
        return getName() + " | " + phone + " | " + email + " | " + location;
    }
}

class Car implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String model;
    private double price6h, price12h, price24h, price48h; // Prices per day
    private boolean active;
    
    // Updated constructor to match your HTML/CSS (model, price)
    public Car(int id, String model, double dailyPrice) {
        this.id = id;
        this.model = model;
        // Simple mapping: 1 day = price, 2 day = price*2, etc.
        // We'll use the 24h price as the "daily price"
        this.price24h = dailyPrice;
        this.price6h = dailyPrice * 0.6;
        this.price12h = dailyPrice * 0.8;
        this.price48h = dailyPrice * 2;
        this.active = true;
    }
    
    // Kept original constructor for compatibility with .dat file
    public Car(int id, String model, double p6, double p12, double p24, double p48) {
        this.id = id;
        this.model = model;
        this.price6h = p6;
        this.price12h = p12;
        this.price24h = p24;
        this.price48h = p48;
        this.active = true;
    }

    public int getId() { return id; }
    public String getModel() { return model; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }

    public double getPriceForHours(int hours) {
        if (hours <= 6) return price6h;
        if (hours <= 12) return price12h;
        if (hours <= 24) return price24h;
        // Simple logic for > 24h, e.g., daily rate
        if (hours == 48) return price48h;
        return price24h * (Math.ceil(hours / 24.0));
    }
    
    public double getDailyPrice() { return price24h; }
    public double getPrice6h() { return price6h; }
    public double getPrice12h() { return price12h; }
    public double getPrice24h() { return price24h; }
    public double getPrice48h() { return price48h; }
    @Override
    public String toString() {
        return String.format("ID:%d | %s | Active:%s", id, model, (active ? "Yes" : "No"));
    }
}

class Booking implements Serializable {
    private static final long serialVersionUID = 1L;
    private Car car;
    private Customer customer;
    private String date; // dd-MM-yyyy
    private int days; // Changed from hours to match HTML form
    private double total;
    private double advance;
    private boolean advancePaid;

    // Updated constructor for daily booking
    public Booking(Car car, Customer customer, String date, int days, boolean advancePaid) {
        this.car = car;
        this.customer = customer;
        this.date = date;
        this.days = days;
        this.total = car.getDailyPrice() * days; // Total = daily price * days
        this.advance = Math.round(this.total * 0.3 * 100.0) / 100.0;
        this.advancePaid = advancePaid;
    }

    public Car getCar() { return car; }
    public Customer getCustomer() { return customer; }
    public String getDate() { return date; }
    public int getDays() { return days; }
    public double getTotal() { return total; }
    public double getAdvance() { return advance; }
    public boolean isAdvancePaid() { return advancePaid; }
    @Override
    public String toString() {
        return String.format("Booking -> Car: %s | Date: %s | %d days | Customer: %s | Total: %.2f | Paid: %s",
                car.getModel(), date, days, customer.getName(), total, (advancePaid ? "Yes" : "No"));
    }
}

/* ============================
   BACKEND: CarRentalSystem
   (With new cancelBooking method)
   ============================ */

class CarRentalSystem {
    private List<Car> cars;
    private List<Booking> bookings;
    private int nextCarId;
    private static final String CARS_FILE = "cars.dat";
    private static final String BOOKINGS_FILE = "bookings.dat";

    public CarRentalSystem() {
        cars = new ArrayList<>();
        bookings = new ArrayList<>();
        nextCarId = 1;
        loadState();
    }

    public Car addCar(String model, double dailyPrice) {
        Car c = new Car(nextCarId++, model, dailyPrice);
        cars.add(c);
        saveCars();
        return c;
    }

    public List<Car> listCars() { return new ArrayList<>(cars); }
    public List<Booking> listBookings() { return new ArrayList<>(bookings); }

    public Car findCarById(int id) {
        for (Car c : cars) if (c.getId() == id) return c;
        return null;
    }

    public boolean isCarAvailableOnDate(int carId, String date) {
        for (Booking b : bookings) {
            if (b.getCar().getId() == carId && b.getDate().equals(date)) {
                return false;
            }
        }
        Car c = findCarById(carId);
        return c != null && c.isActive();
    }

    public Booking bookCar(int carId, Customer customer, String date, int days) {
        Car car = findCarById(carId);
        if (car == null || !isCarAvailableOnDate(carId, date)) {
            return null;
        }
        // Assume advance is paid for web booking
        Booking booking = new Booking(car, customer, date, days, true);
        bookings.add(booking);
        saveBookings();
        return booking;
    }
    
    /**
     * NEW FEATURE: Cancel a booking.
     * Finds a booking by customer email and pickup date and removes it.
     * @return true if a booking was found and cancelled, false otherwise.
     */
    public boolean cancelBooking(String email, String date) {
        Booking toRemove = null;
        for (Booking b : bookings) {
            if (b.getCustomer().getEmail().equalsIgnoreCase(email) && b.getDate().equals(date)) {
                toRemove = b;
                break;
            }
        }
        
        if (toRemove != null) {
            bookings.remove(toRemove);
            saveBookings(); // Persist the change
            return true;
        }
        return false; // No booking found
    }

    // --- Persistence Methods (unchanged) ---
    private void loadState() {
        File fCars = new File(CARS_FILE);
        if (fCars.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fCars))) {
                Object obj = ois.readObject();
                if (obj instanceof List) {
                    cars = (List<Car>) obj;
                    int maxId = 0;
                    for (Car c : cars) if (c.getId() > maxId) maxId = c.getId();
                    nextCarId = maxId + 1;
                }
            } catch (Exception e) {
                System.err.println("Failed to load cars, creating defaults: " + e.getMessage());
                createDefaultCars();
            }
        } else {
            createDefaultCars();
        }

        File fBookings = new File(BOOKINGS_FILE);
        if (fBookings.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fBookings))) {
                Object obj = ois.readObject();
                if (obj instanceof List) {
                    bookings = (List<Booking>) obj;
                }
            } catch (Exception e) { System.err.println("Failed to load bookings: " + e.getMessage()); }
        }
    }
    
    private void createDefaultCars() {
        // Create cars that match the user's HTML
        cars.add(new Car(nextCarId++, "Sedan", 45));
        cars.add(new Car(nextCarId++, "SUV", 65));
        cars.add(new Car(nextCarId++, "Sports Car", 120));
        saveCars();
    }

    private void saveCars() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CARS_FILE))) {
            oos.writeObject(cars);
        } catch (IOException e) { System.err.println("Failed to save cars: " + e.getMessage()); }
    }

    private void saveBookings() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(BOOKINGS_FILE))) {
            oos.writeObject(bookings);
        } catch (IOException e) { System.err.println("Failed to save bookings: " + e.getMessage()); }
    }
}

/* ============================
   WEB SERVER
   ============================ */

public class CarRentalServer {
    
    // Date formatter for web forms (HTML standard)
    private static final SimpleDateFormat HTML_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    // Date formatter for storage (from your Swing app)
    private static final SimpleDateFormat STORAGE_DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy");

    public static void main(String[] args) throws IOException {
        int port = 8080;
        CarRentalSystem backend = new CarRentalSystem();
        
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // 1. Handler for the main page
        server.createContext("/", new RootHandler(backend));
        // 2. Handler for the CSS file
        server.createContext("/style.css", new StyleHandler());
        // 3. Handler for processing the booking form
        server.createContext("/book", new BookHandler(backend));
        // 4. Handler for processing the cancellation form
        server.createContext("/cancel", new CancelHandler(backend));
        
        server.setExecutor(null); // default executor
        server.start();
        System.out.println("Server started on port " + port);
        System.out.println("Open http://localhost:8080 in your browser.");
    }

    /**
     * Handler for the main page ("/").
     * Generates the complete HTML page dynamically.
     */
    static class RootHandler implements HttpHandler {
        private CarRentalSystem backend;
        public RootHandler(CarRentalSystem backend) { this.backend = backend; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // --- 1. Generate dynamic car list HTML ---
            StringBuilder carListHtml = new StringBuilder();
            List<Car> cars = backend.listCars();
            for (Car car : cars) {
                if (car.isActive()) {
                    carListHtml.append("<div class=\"car-card\">")
                        .append("<img src=\"https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=800&q=60\" alt=\""+car.getModel()+"\">") // Using a placeholder image
                        .append("<h3>").append(car.getModel()).append("</h3>")
                        .append("<p>$").append(String.format("%.0f", car.getDailyPrice())).append(" / day</p>")
                        // This button is just for show as we have a main booking form
                        .append("<button onclick=\"document.getElementById('book').scrollIntoView();\">Book Now</button>") 
                        .append("</div>\n");
                }
            }

            // --- 2. Generate dynamic car options for the dropdown ---
            StringBuilder carOptionsHtml = new StringBuilder();
            carOptionsHtml.append("<option value=\"\">Choose a car</option>");
            for (Car car : cars) {
                if (car.isActive()) {
                    carOptionsHtml.append("<option value=\"").append(car.getId()).append("\">")
                        .append(car.getModel()).append(" ($").append(String.format("%.0f", car.getDailyPrice())).append("/day)")
                        .append("</option>\n");
                }
            }
            
            // --- 3. Build the full HTML page using the template ---
            String html = generateHtmlPage(carListHtml.toString(), carOptionsHtml.toString());
            
            // --- 4. Send the response ---
            sendResponse(exchange, 200, "text/html", html);
        }
    }
    
    /**
     * Handler for the CSS file ("/style.css").
     * Reads the file from disk and serves it.
     */
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
                String error = "/* CSS file not found */";
                sendResponse(exchange, 404, "text/css", error);
            }
        }
    }

    /**
     * Handler for the booking form POST request ("/book").
     */
    static class BookHandler implements HttpHandler {
        private CarRentalSystem backend;
        public BookHandler(CarRentalSystem backend) { this.backend = backend; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }
            
            Map<String, String> params = parseFormData(exchange.getRequestBody());
            
            try {
                String name = params.get("name");
                String email = params.get("email");
                int carId = Integer.parseInt(params.get("car"));
                String dateStrHtml = params.get("date"); // yyyy-MM-dd
                int days = Integer.parseInt(params.get("days")); // New field for days
                
                // Convert date format
                Date d = HTML_DATE_FORMAT.parse(dateStrHtml);
                String dateStrStorage = STORAGE_DATE_FORMAT.format(d);
                
                // Create a minimal customer
                Customer cust = new Customer(name, "N/A", email, "N/A");
                
                Booking b = backend.bookCar(carId, cust, dateStrStorage, days);
                
                String responsePage;
                if (b != null) {
                    responsePage = generateResultPage("Booking Successful!", 
                        "Your booking is confirmed.<br>" +
                        "Car: " + b.getCar().getModel() + "<br>" +
                        "Date: " + b.getDate() + " for " + b.getDays() + " day(s)<br>" +
                        "Total: $" + String.format("%.2f", b.getTotal()) + "<br>" +
                        "Customer: " + b.getCustomer().getName());
                } else {
                    responsePage = generateResultPage("Booking Failed", 
                        "The car is not available on " + dateStrStorage + ". Please try another car or date.");
                }
                sendResponse(exchange, 200, "text/html", responsePage);
                
            } catch (Exception e) {
                String errorPage = generateResultPage("Error", "There was an error processing your request: " + e.getMessage());
                sendResponse(exchange, 400, "text/html", errorPage);
            }
        }
    }
    
    /**
     * Handler for the cancellation form POST request ("/cancel").
     */
    static class CancelHandler implements HttpHandler {
        private CarRentalSystem backend;
        public CancelHandler(CarRentalSystem backend) { this.backend = backend; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }
            
            Map<String, String> params = parseFormData(exchange.getRequestBody());
            
            try {
                String email = params.get("email");
                String dateStrHtml = params.get("date"); // yyyy-MM-dd
                
                // Convert date format
                Date d = HTML_DATE_FORMAT.parse(dateStrHtml);
                String dateStrStorage = STORAGE_DATE_FORMAT.format(d);
                
                boolean success = backend.cancelBooking(email, dateStrStorage);
                
                String responsePage;
                if (success) {
                    responsePage = generateResultPage("Cancellation Successful", 
                        "Your booking for " + dateStrStorage + " has been cancelled.");
                } else {
                    responsePage = generateResultPage("Cancellation Failed", 
                        "No booking was found for email '" + email + "' on " + dateStrStorage + ".");
                }
                sendResponse(exchange, 200, "text/html", responsePage);
                
            } catch (Exception e) {
                String errorPage = generateResultPage("Error", "There was an error processing your request: " + e.getMessage());
                sendResponse(exchange, 400, "text/html", errorPage);
            }
        }
    }
    
    // --- Helper Methods ---

    /**
     * Generates the full HTML page using the user's template.
     * Injects dynamic content.
     */
    private static String generateHtmlPage(String carListHtml, String carOptionsHtml) {
        // We modify the HTML to add the new "Cancel" section and form actions
        return "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "  <meta charset=\"UTF-8\" />\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "  <title>DriveNow - Car Rental</title>\n" +
            "  <link rel=\"stylesheet\" href=\"style.css\">\n" + // Links to our CSS handler
            "</head>\n" +
            "<body>\n" +
            "  \n" +
            "  <header>\n" +
            "    <div class=\"logo\">DriveNow</div>\n" +
            "    <nav>\n" +
            "      <ul>\n" +
            "        <li><a href=\"#home\" class=\"active\">Home</a></li>\n" +
            "        <li><a href=\"#cars\">Cars</a></li>\n" +
            "        <li><a href=\"#book\">Book</a></li>\n" +
            "        <li><a href=\"#cancel\">Cancel Booking</a></li>\n" + // NEW LINK
            "        <li><a href=\"#contact\">Contact</a></li>\n" +
            "      </ul>\n" +
            "    </nav>\n" +
            "  </header>\n" +
            "\n" +
            "  \n" +
            "  <section id=\"home\" class=\"hero\">\n" +
            "    <div class=\"hero-text\">\n" +
            "      <h1>Find Your Perfect Ride</h1>\n" +
            "      <p>Reliable, affordable car rentals for every journey.</p>\n" +
            "      <a href=\"#cars\" class=\"btn\">View Cars</a>\n" +
            "    </div>\n" +
            "  </section>\n" +
            "\n" +
            "  \n" +
            "  <section id=\"cars\" class=\"cars-section\">\n" +
            "    <h2>Available Cars</h2>\n" +
            "    <div class=\"car-list\">\n" +
            "      " + carListHtml + "\n" + // DYNAMIC CONTENT INJECTED HERE
            "    </div>\n" +
            "  </section>\n" +
            "\n" +
            "  \n" +
            "  <section id=\"book\" class=\"booking-section\">\n" +
            "    <h2>Book Your Car</h2>\n" +
            // Form points to our /book handler
            "    <form action=\"/book\" method=\"POST\">\n" + 
            "      <div class=\"form-group\">\n" +
            "        <label for=\"name\">Full Name</label>\n" +
            "        <input type=\"text\" id=\"name\" name=\"name\" required>\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"email\">Email</label>\n" +
            "        <input type=\"email\" id=\"email\" name=\"email\" required>\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"car\">Select Car</label>\n" +
            "        <select id=\"car\" name=\"car\" required>\n" +
            "          " + carOptionsHtml + "\n" + // DYNAMIC CONTENT INJECTED HERE
            "        </select>\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"date\">Pick-up Date</label>\n" +
            "        <input type=\"date\" id=\"date\" name=\"date\" required>\n" +
            "      </div>\n" +
             "      <div class=\"form-group\">\n" + // Added field for days
            "        <label for=\"days\">Number of Days</label>\n" +
            "        <input type=\"number\" id=\"days\" name=\"days\" min=\"1\" value=\"1\" required>\n" +
            "      </div>\n" +
            "      <button type=\"submit\" class=\"btn\">Book Now</button>\n" +
            "    </form>\n" +
            "  </section>\n" +
            "\n" +
            "  \n" +
            "  <section id=\"cancel\" class=\"booking-section\" style=\"background-color: #f8f9fa; color: #333;\">\n" +
            "    <h2>Cancel Your Booking</h2>\n" +
            "    <p style=\"margin-bottom: 20px;\">Need to cancel? Enter your email and pick-up date below.</p>\n" +
            // Form points to our /cancel handler
            "    <form action=\"/cancel\" method=\"POST\">\n" + 
            "      <div class=\"form-group\">\n" +
            "        <label for=\"cancel-email\">Your Email</label>\n" +
            "        <input type=\"email\" id=\"cancel-email\" name=\"email\" required>\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"cancel-date\">Pick-up Date</tabel>\n" +
            "        <input type=\"date\" id=\"cancel-date\" name=\"date\" required>\n" +
            "      </div>\n" +
            "      <button type=\"submit\" class=\"btn\" style=\"background: #d9534f; color: white;\">Cancel Booking</button>\n" +
            "    </form>\n" +
            "  </section>\n" +
            "\n" +
            "  \n" +
            "  <footer id=\"contact\">\n" +
            "    <p> 123 Main St, Cityville |  (123) 456-7890 |  info@drivenow.com</p>\n" +
            "    <p>© 2025 DriveNow. All rights reserved.</p>\n" +
            "  </footer>\n" +
            "</body>\n" +
            "</html>";
    }

    /**
     * Generates a simple HTML page to show a success or failure message.
     */
    private static String generateResultPage(String title, String message) {
        return "<!DOCTYPE html><html lang=\"en\">" +
            "<head><meta charset=\"UTF-8\"><title>" + title + "</title><link rel=\"stylesheet\" href=\"style.css\"></head>" +
            "<body><header><div class=\"logo\">DriveNow</div></header>" +
            "<section class=\"hero\" style=\"height: 80vh;\"><div class=\"hero-text\">" +
            "<h1>" + title + "</h1>" +
            "<p style=\"font-size: 1.2rem;\">" + message + "</p>" +
            "<a href=\"/\" class=\"btn\">Return Home</a>" +
            "</div></section>" +
            "<footer id=\"contact\"><p>© 2025 DriveNow. All rights reserved.</p></footer>" +
            "</body></html>";
    }

    /**
     * Simple parser for form data (e.g., "name=John&email=john@doe.com").
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
}