import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CarRentalSystem {
    private List<Car> cars;
    private List<Booking> bookings;
    private List<Customer> customers;
    private int nextCarId = 1;
    
    // File names for data persistence
    private final String CARS_FILE = "cars.dat";
    private final String CUSTOMERS_FILE = "customers.dat";
    private final String BOOKINGS_FILE = "bookings.dat";

    public CarRentalSystem() {
        cars = new ArrayList<>();
        bookings = new ArrayList<>();
        customers = new ArrayList<>();
        loadState(); // Load data from files on startup
        
        if (cars.isEmpty()) {
            initializeCars();
        }
    }

    // --- INITIALIZATION ---
    private void initializeCars() {
        addCar(new Car(nextCarId++, "Sedan (Toyota)", "A reliable, comfortable car perfect for city travel.", 500, 800, 1500, 2500));
        addCar(new Car(nextCarId++, "SUV (Prado)", "Spacious and powerful, ideal for family trips.", 700, 1200, 2200, 4000));
        addCar(new Car(nextCarId++, "Microbus (Hiace)", "The best option for group travel. Seats up to 14.", 900, 1500, 2800, 5000));
        addCar(new Car(nextCarId++, "Electric Sedan (Tesla)", "Go green with a high-tech electric vehicle.", 800, 1400, 2600, 4500));
        addCar(new Car(nextCarId++, "Sports Coupe (Mustang)", "Experience the thrill of a powerful engine.", 1200, 2000, 3800, 7000));
        addCar(new Car(nextCarId++, "Pickup Truck (Hilux)", "Tough and versatile for heavy items.", 600, 1000, 1900, 3500));
        addCar(new Car(nextCarId++, "Luxury Van (Alphard)", "Premium comfort for VIP guests.", 1500, 2500, 4500, 8000));
        saveCars();
    }

    // --- FILE OPERATIONS ---
    private void loadState() {
        loadData(CARS_FILE, cars);
        loadData(CUSTOMERS_FILE, customers);
        loadData(BOOKINGS_FILE, bookings);
        nextCarId = cars.stream().mapToInt(Car::getId).max().orElse(0) + 1;
    }

    private <T> void loadData(String filename, List<T> list) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename))) {
            list.addAll((Collection<? extends T>) ois.readObject());
            System.out.println("Loaded " + list.size() + " records from " + filename);
        } catch (FileNotFoundException e) {
            System.out.println("Data file not found: " + filename + ". Starting fresh.");
        } catch (Exception e) {
            System.err.println("Error loading data from " + filename + ": " + e.getMessage());
        }
    }

    private <T> void saveData(String filename, List<T> list) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(list);
        } catch (IOException e) {
            System.err.println("Error saving data to " + filename + ": " + e.getMessage());
        }
    }

    private void addCar(Car car) { cars.add(car); }
    private void saveCars() { saveData(CARS_FILE, cars); }
    private void saveCustomers() { saveData(CUSTOMERS_FILE, customers); }
    private void saveBookings() { saveData(BOOKINGS_FILE, bookings); }

    // --- CAR FUNCTIONS ---
    public List<Car> listCars() {
        return cars.stream().filter(Car::isActive).collect(Collectors.toList());
    }

    public Optional<Car> findCarById(int id) {
        return cars.stream().filter(c -> c.getId() == id).findFirst();
    }

    public boolean deactivateCar(int carId) {
        Optional<Car> carOpt = cars.stream().filter(c -> c.getId() == carId).findFirst();
        if (carOpt.isPresent()) {
            Car car = carOpt.get();
            if (car.isActive()) {
                car.setActive(false);
                saveCars();
                return true;
            }
        }
        return false;
    }

    // --- CUSTOMER FUNCTIONS ---
    public Customer findCustomerByEmail(String email) {
        return customers.stream()
                .filter(c -> c.getEmail().equalsIgnoreCase(email))
                .findFirst()
                .orElse(null);
    }

    public Customer registerCustomer(String name, String email, String password, String phone, String location) {
        if (findCustomerByEmail(email) != null) {
            return null;
        }
        Customer newCustomer = new Customer(name, email, password, phone, location);
        customers.add(newCustomer);
        saveCustomers();
        return newCustomer;
    }

    public Customer loginCustomer(String email, String password) {
        Customer customer = findCustomerByEmail(email);
        if (customer != null && customer.checkPassword(password)) {
            return customer;
        }
        return null;
    }

    // --- BOOKING FUNCTIONS ---
    public boolean bookCar(int carId, Customer customer, String date, int days, boolean advancePaid) {
        Optional<Car> carOpt = findCarById(carId);
        if (carOpt.isEmpty() || !carOpt.get().isActive() || isCarBookedOnDate(carId, date)) {
            return false;
        }
        Car car = carOpt.get();
        Booking newBooking = new Booking(car, customer, date, days, advancePaid);
        bookings.add(newBooking);
        saveBookings();
        return true;
    }

    public boolean cancelBooking(String email, String date) {
        Optional<Booking> bookingToCancel = bookings.stream()
                .filter(b -> b.getCustomer().getEmail().equalsIgnoreCase(email) && b.getDate().equals(date))
                .findFirst();

        if (bookingToCancel.isPresent()) {
            bookings.remove(bookingToCancel.get());
            saveBookings();
            return true;
        }
        return false;
    }

    public boolean isCarBookedOnDate(int carId, String date) {
        return bookings.stream()
                .anyMatch(b -> b.getCar().getId() == carId && b.getDate().equals(date));
    }

    // This is the method used by the Profile Page to show history
    public List<Booking> getUserBookings(String email) {
        return bookings.stream()
                .filter(b -> b.getCustomer().getEmail().equalsIgnoreCase(email))
                .collect(Collectors.toList());
    }
}