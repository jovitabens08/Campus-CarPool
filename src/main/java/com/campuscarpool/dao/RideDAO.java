package com.campuscarpool.dao;

import com.campuscarpool.db.DBConnection;
import com.campuscarpool.model.BusinessException;
import com.campuscarpool.model.Ride;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class RideDAO {

    private static final String SELECT_RIDE =
            "SELECT r.ride_id, r.student_id, s.student_name, r.pickup_location, r.destination, "
          + "r.travel_date, r.travel_time, r.available_seats, r.status "
          + "FROM rides r JOIN students s ON s.student_id = r.student_id ";

    /** Rides that have not departed yet. */
    private static final String UPCOMING =
            "(r.travel_date > CURDATE() OR (r.travel_date = CURDATE() AND r.travel_time >= CURTIME()))";

    public int create(int studentId, String pickup, String destination,
                      LocalDate date, LocalTime time, int seats) throws SQLException {
        String sql = "INSERT INTO rides (student_id, pickup_location, destination, travel_date, "
                   + "travel_time, available_seats, status) VALUES (?, ?, ?, ?, ?, ?, 'AVAILABLE')";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, studentId);
            ps.setString(2, pickup);
            ps.setString(3, destination);
            ps.setDate(4, Date.valueOf(date));
            ps.setTime(5, Time.valueOf(time));
            ps.setInt(6, seats);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public Ride findById(int rideId) throws SQLException {
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_RIDE + "WHERE r.ride_id = ?")) {
            ps.setInt(1, rideId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<Ride> findByCreator(int studentId) throws SQLException {
        return query(SELECT_RIDE + "WHERE r.student_id = ? ORDER BY r.travel_date DESC, r.travel_time DESC",
                ps -> ps.setInt(1, studentId));
    }

    /** Open rides (AVAILABLE, seats left, not departed) whose locations contain the given text. */
    public List<Ride> findOpen(String pickup, String destination, LocalDate date) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_RIDE)
                .append("WHERE r.status = 'AVAILABLE' AND r.available_seats > 0 AND ").append(UPCOMING)
                .append(" AND LOWER(r.pickup_location) LIKE ? AND LOWER(r.destination) LIKE ? ");
        if (date != null) sql.append("AND r.travel_date = ? ");
        sql.append("ORDER BY r.travel_date, r.travel_time");
        return query(sql.toString(), ps -> {
            ps.setString(1, "%" + pickup.toLowerCase() + "%");
            ps.setString(2, "%" + destination.toLowerCase() + "%");
            if (date != null) ps.setDate(3, Date.valueOf(date));
        });
    }

    /** All open rides on a date - the candidate pool for smart matching. */
    public List<Ride> findOpenOnDate(LocalDate date) throws SQLException {
        return query(SELECT_RIDE + "WHERE r.status = 'AVAILABLE' AND r.available_seats > 0 AND "
                + UPCOMING + " AND r.travel_date = ? ORDER BY r.travel_time",
                ps -> ps.setDate(1, Date.valueOf(date)));
    }

    /** Cancels a ride and every PENDING/APPROVED request on it. Returns number of requests cancelled. */
    public int cancelRide(int rideId, int studentId) throws SQLException, BusinessException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int owner;
                String status;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT student_id, status FROM rides WHERE ride_id = ? FOR UPDATE")) {
                    ps.setInt(1, rideId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BusinessException("Ride not found.");
                        owner = rs.getInt(1);
                        status = rs.getString(2);
                    }
                }
                if (owner != studentId) throw new BusinessException("You can only cancel your own rides.");
                if (status.equals("CANCELLED")) throw new BusinessException("Ride is already cancelled.");

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE rides SET status = 'CANCELLED' WHERE ride_id = ?")) {
                    ps.setInt(1, rideId);
                    ps.executeUpdate();
                }
                int affected;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE ride_requests SET status = 'CANCELLED' "
                      + "WHERE ride_id = ? AND status IN ('PENDING','APPROVED')")) {
                    ps.setInt(1, rideId);
                    affected = ps.executeUpdate();
                }
                c.commit();
                return affected;
            } catch (SQLException | BusinessException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ---- helpers ----
    @FunctionalInterface
    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private List<Ride> query(String sql, Binder binder) throws SQLException {
        List<Ride> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    private static Ride map(ResultSet rs) throws SQLException {
        return new Ride(rs.getInt("ride_id"), rs.getInt("student_id"), rs.getString("student_name"),
                rs.getString("pickup_location"), rs.getString("destination"),
                rs.getDate("travel_date").toLocalDate(), rs.getTime("travel_time").toLocalTime(),
                rs.getInt("available_seats"), rs.getString("status"));
    }
}
