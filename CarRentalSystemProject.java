/*
 * File: CarRentalSystemProject.java
 * Single-file, runnable Java Swing project for "Car Rental Management System"
 *
 * Features:
 * - Single .java file (all classes included)
 * - Swing GUI with Tabs (Home, Cars, Booking, Admin)
 * - OOP: Encapsulation, Abstraction, Inheritance (simple Person->Customer),
 *   Composition (Booking has Car+Customer), Polymorphism (toString())
 * - Persistent storage using object serialization (cars.dat, bookings.dat)
 * - Booking flow with 30% advance calculation & simulated payment confirmation
 * - Date validation (dd-MM-yyyy)
 *
 * How to run:
 * javac CarRentalSystemProject.java
 * java CarRentalSystemProject
 *
 * Notes:
 * - No database required. Files stored next to the program.
 * - If data files missing, program creates sample cars.
 */

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Font;
import java.awt.Color;
import java.awt.event.*;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/* ============================
   MODEL CLASSES (Domain)
   ============================ */

/**
 * Person - a minimal superclass for future extension.
 */
class Person implements Serializable {
    private static final long serialVersionUID = 1L;
    protected String name;

    public Person(String name) {
        this.name = name;
    }

    public String getName() { return name; }
}

/**
 * Customer extends Person - stores contact info.
 */
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

/**
 * Car class - encapsulates car data and hourly pricing tiers.
 */
class Car implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String model;
    private double price6h, price12h, price24h, price48h;
    private boolean active; // whether this car is active for renting

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
        return price48h;
    }

    public double getPrice6h() { return price6h; }
    public double getPrice12h() { return price12h; }
    public double getPrice24h() { return price24h; }
    public double getPrice48h() { return price48h; }

    @Override
    public String toString() {
        return String.format("ID:%d | %s | Active:%s", id, model, (active ? "Yes" : "No"));
    }

    public String detailedString() {
        return toString()
                + "\n 6h: " + price6h
                + " | 12h: " + price12h
                + " | 24h: " + price24h
                + " | 48h: " + price48h;
    }
}

/**
 * Booking class - composition of Car + Customer + date + hours
 */
class Booking implements Serializable {
    private static final long serialVersionUID = 1L;
    private Car car;
    private Customer customer;
    private String date; // dd-MM-yyyy
    private int hours;
    private double total;     // total price based on hours
    private double advance;   // 30% of total
    private boolean advancePaid; // if simulated advance paid

    public Booking(Car car, Customer customer, String date, int hours, boolean advancePaid) {
        this.car = car;
        this.customer = customer;
        this.date = date;
        this.hours = hours;
        this.total = car.getPriceForHours(hours);
        this.advance = Math.round(this.total * 0.3 * 100.0) / 100.0; // round to 2 decimals
        this.advancePaid = advancePaid;
    }

    public Car getCar() { return car; }
    public Customer getCustomer() { return customer; }
    public String getDate() { return date; }
    public int getHours() { return hours; }
    public double getTotal() { return total; }
    public double getAdvance() { return advance; }
    public boolean isAdvancePaid() { return advancePaid; }

    @Override
    public String toString() {
        return String.format("Booking -> Car: %s | Date: %s | %dh | Customer: %s | Total: %.2f | Advance: %.2f | Paid: %s",
                car.getModel(), date, hours, customer.getName(), total, advance, (advancePaid ? "Yes" : "No"));
    }
}

/* ============================
   BACKEND: CarRentalSystem
   - stores lists, performs operations
   - persists to files
   ============================ */

class CarRentalSystem {
    private List<Car> cars;
    private List<Booking> bookings;
    private int nextCarId;

    // filenames for persistence
    private static final String CARS_FILE = "cars.dat";
    private static final String BOOKINGS_FILE = "bookings.dat";

    public CarRentalSystem() {
        cars = new ArrayList<>();
        bookings = new ArrayList<>();
        nextCarId = 1;
        loadState();
    }

    /* ---------- CRUD for Cars ---------- */
    public Car addCar(String model, double p6, double p12, double p24, double p48) {
        Car c = new Car(nextCarId++, model, p6, p12, p24, p48);
        cars.add(c);
        saveCars();
        return c;
    }

    public boolean removeCarById(int id) {
        boolean removed = cars.removeIf(c -> c.getId() == id);
        if (removed) {
            // optionally remove bookings for that car
            bookings.removeIf(b -> b.getCar().getId() == id);
            saveCars();
            saveBookings();
        }
        return removed;
    }

    public List<Car> listCars() { return new ArrayList<>(cars); }

    public Car findCarById(int id) {
        for (Car c : cars) if (c.getId() == id) return c;
        return null;
    }

    public Car findCarByModel(String model) {
        for (Car c : cars) if (c.getModel().equalsIgnoreCase(model)) return c;
        return null;
    }

    /* ---------- Booking logic ---------- */

    /**
     * Check availability of a car on a given date.
     * Simple rule: one booking per car per date.
     */
    public boolean isCarAvailableOnDate(int carId, String date) {
        for (Booking b : bookings) {
            if (b.getCar().getId() == carId && b.getDate().equals(date)) {
                return false;
            }
        }
        Car c = findCarById(carId);
        return c != null && c.isActive();
    }

    /**
     * Book a car. Returns Booking if success, null if not.
     * advancePaid parameter simulates whether 30% advance was paid (true/false).
     */
    public Booking bookCar(int carId, Customer customer, String date, int hours, boolean advancePaid) {
        Car car = findCarById(carId);
        if (car == null) return null;
        if (!isCarAvailableOnDate(carId, date)) return null;

        Booking booking = new Booking(car, customer, date, hours, advancePaid);
        bookings.add(booking);
        // we can leave car active flag; but to prevent double-booking we rely on date check.
        saveBookings();
        saveCars(); // in case we change car state later
        return booking;
    }

    public List<Booking> listBookings() { return new ArrayList<>(bookings); }

    /* ---------- Persistence (serialization) ---------- */

    private void loadState() {
        // load cars
        File fCars = new File(CARS_FILE);
        if (fCars.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fCars))) {
                Object obj = ois.readObject();
                if (obj instanceof List) {
                    //noinspection unchecked
                    cars = (List<Car>) obj;
                    // set nextCarId based on max id present
                    int maxId = 0;
                    for (Car c : cars) if (c.getId() > maxId) maxId = c.getId();
                    nextCarId = maxId + 1;
                }
            } catch (Exception e) {
                System.err.println("Failed to load cars: " + e.getMessage());
            }
        } else {
            // no file: create sample cars
            cars.add(new Car(nextCarId++, "Toyota Corolla", 500, 900, 1600, 3000));
            cars.add(new Car(nextCarId++, "Honda Civic", 600, 1000, 1800, 3500));
            cars.add(new Car(nextCarId++, "Suzuki Swift", 400, 750, 1300, 2500));
            saveCars();
        }

        // load bookings
        File fBookings = new File(BOOKINGS_FILE);
        if (fBookings.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fBookings))) {
                Object obj = ois.readObject();
                if (obj instanceof List) {
                    //noinspection unchecked
                    bookings = (List<Booking>) obj;
                }
            } catch (Exception e) {
                System.err.println("Failed to load bookings: " + e.getMessage());
            }
        } else {
            bookings = new ArrayList<>();
            saveBookings();
        }
    }

    private void saveCars() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CARS_FILE))) {
            oos.writeObject(cars);
        } catch (IOException e) {
            System.err.println("Failed to save cars: " + e.getMessage());
        }
    }

    private void saveBookings() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(BOOKINGS_FILE))) {
            oos.writeObject(bookings);
        } catch (IOException e) {
            System.err.println("Failed to save bookings: " + e.getMessage());
        }
    }
}

/* ============================
   GUI: CarRentalGUI (Swing)
   - Uses tabs for separation
   - Connects to CarRentalSystem backend
   ============================ */

public class CarRentalSystemProject extends JFrame {
    private CarRentalSystem backend;

    // UI components reused across methods
    private JTextArea infoArea;
    private DefaultListModel<String> carListModel;
    private JList<String> carJList;

    // date formatter
    private SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy");

    public CarRentalSystemProject() {
        super("Car Rental Management System (Swing)");
        backend = new CarRentalSystem();
        DATE_FORMAT.setLenient(false);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top title
        JLabel title = new JLabel("Car Rental Management System", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setBorder(new EmptyBorder(12, 12, 12, 12));
        add(title, BorderLayout.NORTH);

        // Main tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Home", createHomePanel());
        tabs.addTab("Cars", createCarsPanel());
        tabs.addTab("Booking", createBookingPanel());
        tabs.addTab("Admin", createAdminPanel());
        add(tabs, BorderLayout.CENTER);

        // Bottom info area
        infoArea = new JTextArea(5, 20);
        infoArea.setEditable(false);
        infoArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(infoArea), BorderLayout.SOUTH);

        // initial message
        log("System started. Loaded " + backend.listCars().size() + " cars and " + backend.listBookings().size() + " bookings.");

        setVisible(true);
    }

    /* -----------------------
       Home Panel
       ----------------------- */
    private JPanel createHomePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel welcome = new JLabel("<html><center>Welcome to Car Rental System<br/><small>Use tabs to manage cars, make bookings, and use admin tools.</small></center></html>", SwingConstants.CENTER);
        welcome.setFont(new Font("SansSerif", Font.PLAIN, 18));
        panel.add(welcome, BorderLayout.NORTH);

        JTextArea tips = new JTextArea();
        tips.setEditable(false);
        tips.setText("Tips:\n" +
                "- Go to 'Cars' to view all cars and prices.\n" +
                "- Go to 'Booking' to check availability and book a car.\n" +
                "- Admin tab allows adding/removing cars and viewing all bookings.\n" +
                "- Date format is dd-MM-yyyy (e.g., 25-12-2025).");
        tips.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(tips, BorderLayout.CENTER);
        return panel;
    }

    /* -----------------------
       Cars Panel: show list + details
       ----------------------- */
    private JPanel createCarsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        carListModel = new DefaultListModel<>();
        refreshCarListModel();

        carJList = new JList<>(carListModel);
        carJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(carJList);
        listScroll.setPreferredSize(new Dimension(380, 400));

        JPanel left = new JPanel(new BorderLayout());
        left.add(new JLabel("Cars:"), BorderLayout.NORTH);
        left.add(listScroll, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextArea carDetails = new JTextArea(12, 40);
        carDetails.setEditable(false);
        carDetails.setFont(new Font("Monospaced", Font.PLAIN, 12));
        right.add(new JScrollPane(carDetails));

        JButton showDetailsBtn = new JButton("Show Details");
        JButton refreshBtn = new JButton("Refresh List");
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.add(showDetailsBtn);
        btnRow.add(refreshBtn);
        right.add(btnRow);

        panel.add(left, BorderLayout.WEST);
        panel.add(right, BorderLayout.CENTER);

        showDetailsBtn.addActionListener(e -> {
            int idx = carJList.getSelectedIndex();
            if (idx < 0) {
                JOptionPane.showMessageDialog(this, "Please select a car from the list.");
                return;
            }
            // parse id from line "1 - ModelName"
            String line = carListModel.get(idx);
            int id = parseIdFromListLine(line);
            Car c = backend.findCarById(id);
            if (c == null) {
                carDetails.setText("Car not found!");
                return;
            }
            carDetails.setText(c.detailedString());
        });

        refreshBtn.addActionListener(e -> {
            refreshCarListModel();
            log("Car list refreshed.");
        });

        return panel;
    }

    /* -----------------------
       Booking Panel
       - Choose car -> date -> hours
       - Show price & advance
       - Simulate advance payment
       ----------------------- */
    private JPanel createBookingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel form = new JPanel(new GridLayout(10, 2, 8, 8));
        form.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Widgets
        form.add(new JLabel("Choose Car (by ID):"));
        JComboBox<String> carCombo = new JComboBox<>();
        populateCarCombo(carCombo);
        form.add(carCombo);

        form.add(new JLabel("Booking Date (dd-MM-yyyy):"));
        JTextField dateField = new JTextField(DATE_FORMAT.format(new Date()));
        form.add(dateField);

        form.add(new JLabel("Hours (6 / 12 / 24 / 48):"));
        JComboBox<Integer> hoursCombo = new JComboBox<>(new Integer[]{6, 12, 24, 48});
        form.add(hoursCombo);

        form.add(new JLabel("Customer Name:"));
        JTextField nameField = new JTextField();
        form.add(nameField);

        form.add(new JLabel("Phone:"));
        JTextField phoneField = new JTextField();
        form.add(phoneField);

        form.add(new JLabel("Email:"));
        JTextField emailField = new JTextField();
        form.add(emailField);

        form.add(new JLabel("Location:"));
        JTextField locField = new JTextField();
        form.add(locField);

        form.add(new JLabel("Total Price:"));
        JLabel totalPriceLabel = new JLabel("-");
        form.add(totalPriceLabel);

        form.add(new JLabel("30% Advance:"));
        JLabel advanceLabel = new JLabel("-");
        form.add(advanceLabel);

        form.add(new JLabel("Simulate Advance Paid?"));
        JCheckBox advancePaidBox = new JCheckBox("Yes");
        form.add(advancePaidBox);

        // Buttons
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton checkBtn = new JButton("Check Availability");
        JButton bookBtn = new JButton("Confirm Booking");
        bookBtn.setEnabled(false);
        bottom.add(checkBtn);
        bottom.add(bookBtn);

        panel.add(form, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        // When car or hours changed -> update price/advance display automatically
        ActionListener priceUpdater = e -> {
            String item = (String) carCombo.getSelectedItem();
            if (item == null || item.trim().isEmpty()) {
                totalPriceLabel.setText("-");
                advanceLabel.setText("-");
                return;
            }
            int id = Integer.parseInt(item.split(":")[0]);
            Car c = backend.findCarById(id);
            if (c == null) return;
            int hrs = (Integer) hoursCombo.getSelectedItem();
            double tot = c.getPriceForHours(hrs);
            double adv = Math.round(tot * 0.3 * 100.0) / 100.0;
            totalPriceLabel.setText(String.format("%.2f", tot));
            advanceLabel.setText(String.format("%.2f", adv));
        };
        carCombo.addActionListener(priceUpdater);
        hoursCombo.addActionListener(priceUpdater);

        // Check availability
        checkBtn.addActionListener(e -> {
            try {
                String item = (String) carCombo.getSelectedItem();
                if (item == null) throw new Exception("No car selected");
                int id = Integer.parseInt(item.split(":")[0]);
                String date = dateField.getText().trim();
                validateDate(date);
                boolean avail = backend.isCarAvailableOnDate(id, date);
                if (avail) {
                    JOptionPane.showMessageDialog(this, "Car is AVAILABLE on " + date);
                    bookBtn.setEnabled(true);
                    log("Availability: Car ID " + id + " available on " + date);
                } else {
                    JOptionPane.showMessageDialog(this, "Car is NOT available on " + date);
                    bookBtn.setEnabled(false);
                    log("Availability: Car ID " + id + " NOT available on " + date);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });

        // Confirm booking
        bookBtn.addActionListener(e -> {
            try {
                String item = (String) carCombo.getSelectedItem();
                int id = Integer.parseInt(item.split(":")[0]);
                String date = dateField.getText().trim();
                validateDate(date);
                int hrs = (Integer) hoursCombo.getSelectedItem();
                String name = nameField.getText().trim();
                String phone = phoneField.getText().trim();
                String email = emailField.getText().trim();
                String loc = locField.getText().trim();

                if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || loc.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please fill all customer fields.");
                    return;
                }

                // final availability check
                if (!backend.isCarAvailableOnDate(id, date)) {
                    JOptionPane.showMessageDialog(this, "Sorry! Car is no longer available.");
                    bookBtn.setEnabled(false);
                    return;
                }

                // Simulate payment confirmation: user must check the box to indicate they've paid 30%
                boolean advPaid = advancePaidBox.isSelected();
                if (!advPaid) {
                    int confirmNow = JOptionPane.showConfirmDialog(this, "Advance is not marked as paid. Do you still want to continue and store booking as unpaid?", "Advance Not Paid", JOptionPane.YES_NO_OPTION);
                    if (confirmNow != JOptionPane.YES_OPTION) {
                        return;
                    }
                }

                Customer cust = new Customer(name, phone, email, loc);
                Booking b = backend.bookCar(id, cust, date, hrs, advPaid);
                if (b == null) {
                    JOptionPane.showMessageDialog(this, "Booking failed. Possibly car was booked by someone else.");
                    return;
                }
                JOptionPane.showMessageDialog(this, "Booking successful!\n" +
                        "Car: " + b.getCar().getModel() + "\nDate: " + b.getDate() + "\nHours: " + b.getHours() +
                        "\nTotal: " + b.getTotal() + "\nAdvance (30%): " + b.getAdvance() + (b.isAdvancePaid() ? " (paid)" : " (not paid)\nPlease pay remaining on pickup."));
                log("Booked: " + b);
                // refresh UI states
                refreshCarCombo(carCombo);
                refreshCarListModel();
                bookBtn.setEnabled(false);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });

        return panel;
    }

    /* -----------------------
       Admin Panel
       - Add / Remove car
       - View bookings
       ----------------------- */
    private JPanel createAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new BorderLayout());
        left.setPreferredSize(new Dimension(420, 400));

        JTextArea adminInfo = new JTextArea();
        adminInfo.setEditable(false);
        adminInfo.setFont(new Font("Monospaced", Font.PLAIN, 12));
        left.add(new JScrollPane(adminInfo), BorderLayout.CENTER);

        JPanel adminBtns = new JPanel(new GridLayout(5,1,8,8));
        JButton btnAddCar = new JButton("Add Car");
        JButton btnRemoveCar = new JButton("Remove Car");
        JButton btnViewBookings = new JButton("View All Bookings");
        JButton btnExportBookings = new JButton("Export Bookings to CSV");
        JButton btnRefresh = new JButton("Refresh");
        adminBtns.add(btnAddCar);
        adminBtns.add(btnRemoveCar);
        adminBtns.add(btnViewBookings);
        adminBtns.add(btnExportBookings);
        adminBtns.add(btnRefresh);

        panel.add(left, BorderLayout.CENTER);
        panel.add(adminBtns, BorderLayout.EAST);

        // Actions
        btnAddCar.addActionListener(e -> {
            JTextField modelFld = new JTextField();
            JTextField p6Fld = new JTextField();
            JTextField p12Fld = new JTextField();
            JTextField p24Fld = new JTextField();
            JTextField p48Fld = new JTextField();

            Object[] message = {
                    "Model:", modelFld,
                    "Price 6h:", p6Fld,
                    "Price 12h:", p12Fld,
                    "Price 24h:", p24Fld,
                    "Price 48h:", p48Fld
            };

            int option = JOptionPane.showConfirmDialog(this, message, "Add New Car", JOptionPane.OK_CANCEL_OPTION);
            if (option == JOptionPane.OK_OPTION) {
                try {
                    String model = modelFld.getText().trim();
                    double p6 = Double.parseDouble(p6Fld.getText().trim());
                    double p12 = Double.parseDouble(p12Fld.getText().trim());
                    double p24 = Double.parseDouble(p24Fld.getText().trim());
                    double p48 = Double.parseDouble(p48Fld.getText().trim());
                    Car c = backend.addCar(model, p6, p12, p24, p48);
                    adminInfo.append("Added: " + c + "\n");
                    log("Admin: added car " + c);
                    refreshCarListModel();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Invalid input. Car not added.");
                }
            }
        });

        btnRemoveCar.addActionListener(e -> {
            String idStr = JOptionPane.showInputDialog(this, "Enter Car ID to remove:");
            if (idStr == null || idStr.trim().isEmpty()) return;
            try {
                int id = Integer.parseInt(idStr.trim());
                boolean removed = backend.removeCarById(id);
                if (removed) {
                    adminInfo.append("Removed car with ID: " + id + "\n");
                    log("Admin: removed car ID " + id);
                    refreshCarListModel();
                } else {
                    JOptionPane.showMessageDialog(this, "No car with ID " + id);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid ID.");
            }
        });

        btnViewBookings.addActionListener(e -> {
            List<Booking> list = backend.listBookings();
            if (list.isEmpty()) {
                adminInfo.append("No bookings yet.\n");
            } else {
                adminInfo.append("Bookings:\n");
                for (Booking b : list) {
                    adminInfo.append(b.toString() + "\n");
                }
            }
        });

        btnExportBookings.addActionListener(e -> {
            List<Booking> list = backend.listBookings();
            if (list.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No bookings to export.");
                return;
            }
            JFileChooser chooser = new JFileChooser();
            int sel = chooser.showSaveDialog(this);
            if (sel == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
                    pw.println("Car,Date,Hours,Customer,Phone,Email,Location,Total,Advance,PaidAdvance");
                    for (Booking b : list) {
                        pw.printf("\"%s\",%s,%d,\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%.2f,%s\n",
                                b.getCar().getModel(), b.getDate(), b.getHours(),
                                b.getCustomer().getName(), b.getCustomer().getPhone(),
                                b.getCustomer().getEmail(), b.getCustomer().getLocation(),
                                b.getTotal(), b.getAdvance(), b.isAdvancePaid() ? "Yes" : "No");
                    }
                    JOptionPane.showMessageDialog(this, "Bookings exported to " + f.getAbsolutePath());
                    log("Exported bookings to " + f.getAbsolutePath());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Failed to export: " + ex.getMessage());
                }
            }
        });

        btnRefresh.addActionListener(e -> {
            adminInfo.append("Refreshed admin view.\n");
            refreshCarListModel();
        });

        return panel;
    }

    /* -----------------------
       Helper UI functions
       ----------------------- */

    private void refreshCarListModel() {
        carListModel.removeAllElements();
        for (Car c : backend.listCars()) {
            carListModel.addElement(c.getId() + ": " + c.getModel() + " (Active:" + (c.isActive() ? "Y" : "N") + ")");
        }
    }

    private void populateCarCombo(JComboBox<String> combo) {
        combo.removeAllItems();
        for (Car c : backend.listCars()) {
            combo.addItem(c.getId() + ":" + c.getModel());
        }
    }

    private void refreshCarCombo(JComboBox<String> combo) {
        populateCarCombo(combo);
    }

    private int parseIdFromListLine(String line) {
        // line format: "<id>: <model> (Active:Y)"
        try {
            String part = line.split(":")[0];
            return Integer.parseInt(part.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private void log(String message) {
        infoArea.append("[" + new Date() + "] " + message + "\n");
        infoArea.setCaretPosition(infoArea.getDocument().getLength());
    }

    private void validateDate(String dateStr) throws ParseException {
        DATE_FORMAT.parse(dateStr); // will throw ParseException if invalid
    }

    /* -----------------------
       Main Method
       ----------------------- */
    public static void main(String[] args) {
        // Set look and feel to system for nicer appearance (optional)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new CarRentalSystemProject());
    }
}
