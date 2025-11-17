import java.io.Serializable;

public class Booking implements Serializable {
    private static final long serialVersionUID = 1L;
    private Car car;
    private Customer customer;
    private String date; 
    private int days;
    private double total;
    private double advance;
    private boolean advancePaid;

    public Booking(Car car, Customer customer, String date, int days, boolean advancePaid) {
        this.car = car;
        this.customer = customer;
        this.date = date; // "yyyy-MM-dd"
        this.days = days;
        this.total = car.getDailyPrice() * days;
        this.advance = Math.ceil(this.total * 0.2); // 20% advance
        this.advancePaid = advancePaid;
    }

    // Getters
    public Car getCar() { return car; }
    public Customer getCustomer() { return customer; }
    public String getDate() { return date; }
    public int getDays() { return days; }
    public double getTotal() { return total; }
    public double getAdvance() { return advance; }
    public boolean isAdvancePaid() { return advancePaid; }

    @Override
    public String toString() {
        String paidStatus = advancePaid ? "Yes" : "No";
        return String.format("Booking -> Car: %s | Date: %s | %d days | Customer: %s | Total: %.2f | Paid: %s",
            car.getModel(), date, days, customer.getName(), total, paidStatus);
    }
}