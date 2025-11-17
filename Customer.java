import java.io.Serializable;

public class Customer extends Person implements Serializable {
    private static final long serialVersionUID = 1L;
    private String phone;
    private String email;
    private String location;
    private String password;

    public Customer(String name, String email, String password, String phone, String location) {
        super(name);
        this.email = email;
        this.password = password;
        this.phone = phone;
        this.location = location;
    }

    // Getters
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getLocation() { return location; }
    
    // Check password for login
    public boolean checkPassword(String password) {
        return this.password.equals(password);
    }

    @Override
    public String toString() {
        return String.format("Customer: %s | Email: %s", getName(), email);
    }
}