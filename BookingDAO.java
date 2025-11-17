import java.sql.*;

public class BookingDAO {

    public boolean saveBooking(Booking b) {
        String sql = "INSERT INTO bookings (customer_email, car_id, date, days, total, advance, advance_paid) VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, b.getCustomer().getEmail());
            ps.setInt(2, b.getCar().getId());
            ps.setString(3, b.getDate());
            ps.setInt(4, b.getDays());
            ps.setDouble(5, b.getTotal());
            ps.setDouble(6, b.getAdvance());
            ps.setBoolean(7, b.isAdvancePaid());
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            System.out.println("BookingDAO save error: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteBookingByCustomerAndDate(String customerEmail, String date) {
        String sql = "DELETE FROM bookings WHERE customer_email = ? AND date = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, customerEmail);
            ps.setString(2, date);
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (Exception e) {
            System.out.println("BookingDAO delete error: " + e.getMessage());
            return false;
        }
    }
}
