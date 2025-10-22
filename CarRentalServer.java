/*
 * File: CarRentalServer.java
 * Runs a self-contained Java web server for the "DriveNow" Car Rental System.
 *
 * VERSION 2:
 * - Added Customer registration (/register) and login (/login)
 * - Persists customers to 'customers.dat'
 * - Changed currency to Bangladeshi Taka (Tk)
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
   ============================ */

class Person implements Serializable {
    private static final long serialVersionUID = 1L;
    protected String name;
    public Person(String name) { this.name = name; }
    public String getName() { return name; }
}

class Customer extends Person implements Serializable {
    private static final long serialVersionUID = 2L; // Version 2
    private String phone;
    private String email;
    private String location;
    private String password; // NEW: for login

    public Customer(String name, String phone, String email, String location, String password) {
        super(name);
        this.phone = phone;
        this.email = email;
        this.location = location;
        this.password = password; // Can be null if not registered
    }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getLocation() { return location; }

    /**
     * Checks if the provided password matches the stored password.
     * Simple string comparison.
     */
    public boolean checkPassword(String providedPassword) {
        if (this.password == null || providedPassword == null) {
            return false;
        }
        return this.password.equals(providedPassword);
    }

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
    
    public Car(int id, String model, double dailyPrice) {
        this.id = id;
        this.model = model;
        this.price24h = dailyPrice;
        this.price6h = dailyPrice * 0.6;
        this.price12h = dailyPrice * 0.8;
        this.price48h = dailyPrice * 2;
        this.active = true;
    }
    
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
    private int days; 
    private double total;
    private double advance;
    private boolean advancePaid;

    public Booking(Car car, Customer customer, String date, int days, boolean advancePaid) {
        this.car = car;
        this.customer = customer;
        this.date = date;
        this.days = days;
        this.total = car.getDailyPrice() * days; 
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
   ============================ */

class CarRentalSystem {
    private List<Car> cars;
    private List<Booking> bookings;
    private List<Customer> customers; // NEW: For user accounts
    private int nextCarId;

    private static final String CARS_FILE = "cars.dat";
    private static final String BOOKINGS_FILE = "bookings.dat";
    private static final String CUSTOMERS_FILE = "customers.dat"; // NEW

    public CarRentalSystem() {
        cars = new ArrayList<>();
        bookings = new ArrayList<>();
        customers = new ArrayList<>(); // NEW
        nextCarId = 1;
        loadState();
    }

    // --- Customer Management ---

    /**
     * Finds a customer by email (case-insensitive).
     */
    public Customer findCustomerByEmail(String email) {
        for (Customer c : customers) {
            if (c.getEmail().equalsIgnoreCase(email)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Registers a new customer if the email is not already taken.
     * @return The new Customer object if successful, null if email is taken.
     */
    public Customer registerCustomer(String name, String phone, String email, String location, String password) {
        if (findCustomerByEmail(email) != null) {
            return null; // Email already taken
        }
        Customer newCustomer = new Customer(name, phone, email, location, password);
        customers.add(newCustomer);
        saveCustomers();
        return newCustomer;
    }

    /**
     * Attempts to log in a customer.
     * @return The Customer object if login is successful, null otherwise.
     */
    public Customer loginCustomer(String email, String password) {
        Customer customer = findCustomerByEmail(email);
        if (customer != null && customer.checkPassword(password)) {
            return customer; // Success
        }
        return null; // Failed
    }


    // --- Car & Booking Management ---
    
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
            saveBookings(); 
            return true;
        }
        return false; 
    }

    // --- Persistence Methods ---
    private void loadState() {
        // Load Cars
        File fCars = new File(CARS_FILE);
        if (fCars.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fCars))) {
                cars = (List<Car>) ois.readObject();
                int maxId = 0;
                for (Car c : cars) if (c.getId() > maxId) maxId = c.getId();
                nextCarId = maxId + 1;
            } catch (Exception e) {
                System.err.println("Failed to load cars, creating defaults: " + e.getMessage());
                createDefaultCars();
            }
        } else {
            createDefaultCars();
        }

        // Load Bookings
        File fBookings = new File(BOOKINGS_FILE);
        if (fBookings.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fBookings))) {
                bookings = (List<Booking>) ois.readObject();
            } catch (Exception e) { System.err.println("Failed to load bookings: " + e.getMessage()); }
        }
        
        // NEW: Load Customers
        File fCustomers = new File(CUSTOMERS_FILE);
        if (fCustomers.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fCustomers))) {
                customers = (List<Customer>) ois.readObject();
            } catch (Exception e) { System.err.println("Failed to load customers: " + e.getMessage()); }
        }
    }
    
    private void createDefaultCars() {
        // Prices are now in Taka
        cars.add(new Car(nextCarId++, "Sedan (Toyota)", 5000)); // 5000 Tk
        cars.add(new Car(nextCarId++, "SUV (Prado)", 8000));    // 8000 Tk
        cars.add(new Car(nextCarId++, "Microbus (Hiace)", 7000)); // 7000 Tk
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
    
    private void saveCustomers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CUSTOMERS_FILE))) {
            oos.writeObject(customers);
        } catch (IOException e) { System.err.println("Failed to save customers: " + e.getMessage()); }
    }
}

/* ============================
   WEB SERVER
   ============================ */

public class CarRentalServer {
    
    private static final SimpleDateFormat HTML_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat STORAGE_DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy");

    public static void main(String[] args) throws IOException {
        int port = 8080;
        CarRentalSystem backend = new CarRentalSystem();
        
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/", new RootHandler(backend));
        server.createContext("/style.css", new StyleHandler());
        server.createContext("/book", new BookHandler(backend));
        server.createContext("/cancel", new CancelHandler(backend));
        server.createContext("/register", new RegisterHandler(backend)); // NEW
        server.createContext("/login", new LoginHandler(backend));       // NEW
        
        server.setExecutor(null); 
        server.start();
        System.out.println("Server started on port " + port);
        System.out.println("Open http://localhost:8080 in your browser.");
    }

    /**
     * Handler for the main page ("/").
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
                        .append("<img src=\"https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=800&q=60\" alt=\""+car.getModel()+"\">") 
                        .append("<h3>").append(car.getModel()).append("</h3>")
                        .append("<p>Tk ").append(String.format("%.0f", car.getDailyPrice())).append(" / day</p>") // CURRENCY CHANGE
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
                        .append(car.getModel()).append(" (Tk ").append(String.format("%.0f", car.getDailyPrice())).append("/day)") // CURRENCY CHANGE
                        .append("</option>\n");
                }
            }
            
            // --- 3. Build the full HTML page using the template ---
            String html = generateHtmlPage(carListHtml.toString(), carOptionsHtml.toString());
            
            sendResponse(exchange, 200, "text/html", html);
        }
    }
    
    /**
     * Handler for the CSS file ("/style.css").
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
                String phone = params.get("phone");
                String location = params.get("location");
                int carId = Integer.parseInt(params.get("car"));
                String dateStrHtml = params.get("date"); // yyyy-MM-dd
                int days = Integer.parseInt(params.get("days")); 
                
                Date d = HTML_DATE_FORMAT.parse(dateStrHtml);
                String dateStrStorage = STORAGE_DATE_FORMAT.format(d);
                
                // Find or create a customer
                Customer cust = backend.findCustomerByEmail(email);
                if (cust == null) {
                    // Create a temporary customer object for this booking
                    // They are not "registered" as they have no password
                    cust = new Customer(name, phone, email, location, null);
                }
                
                Booking b = backend.bookCar(carId, cust, dateStrStorage, days);
                
                String responsePage;
                if (b != null) {
                    responsePage = generateResultPage("Booking Successful!", 
                        "Your booking is confirmed.<br>" +
                        "Car: " + b.getCar().getModel() + "<br>" +
                        "Date: " + b.getDate() + " for " + b.getDays() + " day(s)<br>" +
                        "Total: Tk " + String.format("%.2f", b.getTotal()) + "<br>" + // CURRENCY CHANGE
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

    /**
     * NEW Handler for the registration form POST request ("/register").
     */
    static class RegisterHandler implements HttpHandler {
        private CarRentalSystem backend;
        public RegisterHandler(CarRentalSystem backend) { this.backend = backend; }

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
                String phone = params.get("phone");
                String location = params.get("location");
                String password = params.get("password");

                if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    throw new Exception("Name, Email, and Password are required.");
                }

                Customer newCustomer = backend.registerCustomer(name, phone, email, location, password);
                
                String responsePage;
                if (newCustomer != null) {
                    responsePage = generateResultPage("Registration Successful!", 
                        "Welcome, " + newCustomer.getName() + "! You can now log in.");
                } else {
                    responsePage = generateResultPage("Registration Failed", 
                        "An account with this email already exists.");
                }
                sendResponse(exchange, 200, "text/html", responsePage);
                
            } catch (Exception e) {
                String errorPage = generateResultPage("Error", "There was an error processing your request: " + e.getMessage());
                sendResponse(exchange, 400, "text/html", errorPage);
            }
        }
    }

    /**
     * NEW Handler for the login form POST request ("/login").
     */
    static class LoginHandler implements HttpHandler {
        private CarRentalSystem backend;
        public LoginHandler(CarRentalSystem backend) { this.backend = backend; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }
            
            Map<String, String> params = parseFormData(exchange.getRequestBody());
            
            try {
                String email = params.get("email");
                String password = params.get("password");

                Customer customer = backend.loginCustomer(email, password);
                
                String responsePage;
                if (customer != null) {
                    responsePage = generateResultPage("Login Successful!", 
                        "Welcome back, " + customer.getName() + "!" +
                        "<br>You can now proceed to book a car.");
                } else {
                    responsePage = generateResultPage("Login Failed", 
                        "Invalid email or password. Please try again.");
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
     */
    private static String generateHtmlPage(String carListHtml, String carOptionsHtml) {
        return "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "  <meta charset=\"UTF-8\" />\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "  <title>DriveNow - Car Rental</title>\n" +
            "  <link rel=\"stylesheet\" href=\"style.css\">\n" + 
            "</head>\n" +
            "<body>\n" +
            "  \n" +
            "  <header>\n" +
            "    <div class=\"logo\">DriveNow</div>\n" +
            "    <nav>\n" +
            "      <ul>\n" +
            "        <li><a href=\"#home\" class=\"active\">Home</a></li>\n" +
            "        <li><a href=\"#cars\">Cars</a></li>\n" +
            "        <li><a href=\"#register\">Register</a></li>\n" + // NEW
            "        <li><a href=\"#login\">Login</a></li>\n" +       // NEW
            "        <li><a href=\"#book\">Book</a></li>\n" +
            "        <li><a href=\"#cancel\">Cancel Booking</a></li>\n" +
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
            "      " + carListHtml + "\n" + // DYNAMIC CONTENT
            "    </div>\n" +
            "  </section>\n" +
            "\n" +
            "  \n" +
            "  <section id=\"register\" class=\"booking-section\" style=\"background-color: #f8f9fa; color: #333;\">\n" +
            "    <h2>Create an Account</h2>\n" +
            "    <form action=\"/register\" method=\"POST\">\n" + 
            "      <div class=\"form-group\">\n" +
            "        <label for=\"reg-name\">Full Name</label>\n" +
            "        <input type=\"text\" id=\"reg-name\" name=\"name\" required>\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"reg-email\">Email</label>\n" +
            "        <input type=\"email\" id=\"reg-email\" name=\"email\" required>\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"reg-phone\">Phone</label>\n" +
            "        <input type=\"text\" id=\"reg-phone\" name=\"phone\">\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"reg-location\">Location (e.g., Dhaka)</label>\n" +
            "        <input type=\"text\" id=\"reg-location\" name=\"location\">\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"reg-pass\">Password</label>\n" +
            "        <input type=\"password\" id=\"reg-pass\" name=\"password\" required>\n" +
            "      </div>\n" +
            "      <button type=\"submit\" class=\"btn\">Register</button>\n" +
            "    </form>\n" +
            "  </section>\n" +
            "\n" +
            "  \n" +
            "  <section id=\"login\" class=\"booking-section\">\n" +
            "    <h2>Customer Login</h2>\n" +
            "    <form action=\"/login\" method=\"POST\">\n" + 
            "      <div class=\"form-group\">\n" +
            "        <label for=\"login-email\">Email</label>\n" +
            "        <input type=\"email\" id=\"login-email\" name=\"email\" required>\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"login-pass\">Password</label>\n" +
            "        <input type=\"password\" id=\"login-pass\" name=\"password\" required>\n" +
            "      </div>\n" +
            "      <button type=\"submit\" class=\"btn\">Login</button>\n" +
            "    </form>\n" +
            "  </section>\n" +
            "\n" +
            "  \n" +
            "  <section id=\"book\" class=\"booking-section\" style=\"background-color: #eee; color: #333;\">\n" +
            "    <h2>Book Your Car</h2>\n" +
            "    <p style=\"margin-bottom: 20px;\">You don't need to be logged in to book.</p>\n" +
            "    <form action=\"/book\" method=\"POST\">\n" + 
            "      <div class=\"form-group\">\n" +
            "        <label for=\"book-name\">Full Name</label>\n" +
            "        <input type=\"text\" id=\"book-name\" name=\"name\" required>\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"book-email\">Email</label>\n" +
            "        <input type=\"email\" id=\"book-email\" name=\"email\" required>\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"book-phone\">Phone</label>\n" +
            "        <input type=\"text\" id=\"book-phone\" name=\"phone\" required>\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"book-location\">Location</label>\n" +
            "        <input type=\"text\" id=\"book-location\" name=\"location\" required>\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"car\">Select Car</label>\n" +
            "        <select id=\"car\" name=\"car\" required>\n" +
            "          " + carOptionsHtml + "\n" + // DYNAMIC CONTENT
            "        </select>\n" +
            "      </div>\n" +
            "      <div class=\"form-group\">\n" +
            "        <label for=\"date\">Pick-up Date</label>\n" +
            "        <input type=\"date\" id=\"date\" name=\"date\" required>\n" +
            "      </div>\n" +
             "      <div class=\"form-group\">\n" + 
            "        <label for=\"days\">Number of Days</label>\n" +
            "        <input type=\"number\" id=\"days\" name=\"days\" min=\"1\" value=\"1\" required>\n" +
            "      </div>\n" +
            "      <button type=\"submit\" class=\"btn\">Book Now</button>\n" +
            "    </form>\n" +
            "  </section>\n" +
            "\n" +
            "  \n" +
            "  <section id=\"cancel\" class=\"booking-section\">\n" +
            "    <h2>Cancel Your Booking</h2>\n" +
            "    <p style=\"margin-bottom: 20px;\">Enter your email and pick-up date to cancel.</p>\n" +
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
}