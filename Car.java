import java.io.Serializable;

public class Car implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String model;
    private String description; // The new field for the car card
    private double price6h, price12h, price24h, price48h;
    private boolean active; // Used for soft-deletion

    // Constructor updated to include description
    public Car(int id, String model, String description, double price6h, double price12h, double price24h, double price48h) {
        this.id = id;
        this.model = model;
        this.description = description;
        this.price6h = price6h;
        this.price12h = price12h;
        this.price24h = price24h;
        this.price48h = price48h;
        this.active = true;
    }

    // Getters and Setters
    public int getId() { return id; }
    public String getModel() { return model; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; } // Setter for soft-delete
    public double getDailyPrice() { return price24h; }

    public double getPriceForHours(int hours) {
        if (hours <= 6) return price6h;
        if (hours <= 12) return price12h;
        if (hours <= 24) return price24h;
        
        int fullDays = hours / 24;
        int remainingHours = hours % 24;
        double cost = fullDays * price24h;
        
        if (remainingHours > 0) {
            if (remainingHours <= 6) cost += price6h;
            else if (remainingHours <= 12) cost += price12h;
            else cost += price24h;
        }
        return cost;
    }

    @Override
    public String toString() {
        return String.format("ID:%d | %s | Active:%s", id, model, active ? "Yes" : "No");
    }
    
}
