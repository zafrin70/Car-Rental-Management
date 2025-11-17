import java.sql.*;

public class CustomerDAO {

    // Save customer using raw fields (we use this because Customer class has no password getter)
    public boolean saveCustomer(String name, String email, String password, String phone, String location) {
        String sql = "INSERT INTO customers (name, email, password, phone, location) VALUES (?,?,?,?,?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, password);
            ps.setString(4, phone);
            ps.setString(5, location);
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            // duplicate email
            System.out.println("CustomerDAO: Email already exists -> " + email);
            return false;
        } catch (Exception e) {
            System.out.println("CustomerDAO save error: " + e.getMessage());
            return false;
        }
    }

    // Find customer by email and build a Customer object (compatible with your Customer constructor)
    public Customer findCustomer(String email) {
        String sql = "SELECT name,email,password,phone,location FROM customers WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    String em = rs.getString("email");
                    String pwd = rs.getString("password");
                    String phone = rs.getString("phone");
                    String loc = rs.getString("location");
                    // Use your existing Customer constructor: Customer(name, email, password, phone, location)
                    return new Customer(name, em, pwd, phone, loc);
                }
            }
        } catch (Exception e) {
            System.out.println("CustomerDAO find error: " + e.getMessage());
        }
        return null;
    }
}
