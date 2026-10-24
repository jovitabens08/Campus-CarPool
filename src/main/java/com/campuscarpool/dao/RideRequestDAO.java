package com.campuscarpool.dao;

import com.campuscarpool.db.DBConnection;
import com.campuscarpool.model.BusinessException;
import com.campuscarpool.model.RideRequest;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class RideRequestDAO {

    private static final String SELECT_REQ =
            "SELECT rr.request_id, rr.ride_id, rr.student_id, s.student_name, "
          + "CONCAT(r.pickup_location, ' -> ', r.destination) AS route, "
          + "rr.seats_requested, rr.request_time, rr.status "
          + "FROM ride_requests rr "
          + "JOIN students s ON s.student_id = rr.student_id "
          + "JOIN rides r ON r.ride_id = rr.ride_id ";

    /** Passenger asks for seats. Seats are only deducted when the creator approves. */
    public int requestSeats(int rideId, int studentId, int seats) throws SQLException, BusinessException {
        if (seats <= 0) throw new BusinessException("You must request at least 1 seat.");
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int owner, available;
                String status;
                LocalDate date;
                LocalTime time;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT student_id, available_seats, status, travel_date, travel_time "
                      + "FROM rides WHERE ride_id = ? FOR UPDATE")) {
                    ps.setInt(1, rideId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BusinessException("Ride not found.");
                        owner = rs.getInt(1);
                        available = rs.getInt(2);
                        status = rs.getString(3);
                        date = rs.getDate(4).toLocalDate();
                        time = rs.getTime(5).toLocalTime();
                    }
                }
                if (owner == studentId) throw new BusinessException("You cannot request a seat on your own ride.");
                if (!status.equals("AVAILABLE")) throw new BusinessException("Ride is not available (status: " + status + ").");
                if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now()))
                    throw new BusinessException("This ride has already departed.");
                if (seats > available)
                    throw new BusinessException("Only " + available + " seat(s) available.");

                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM ride_requests WHERE ride_id = ? AND student_id = ? "
                      + "AND status IN ('PENDING','APPROVED')")) {
                    ps.setInt(1, rideId);
                    ps.setInt(2, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0)
                            throw new BusinessException("You already have an active request on this ride.");
                    }
                }

                int id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ride_requests (ride_id, student_id, seats_requested, status) "
                      + "VALUES (?, ?, ?, 'PENDING')", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, rideId);
                    ps.setInt(2, studentId);
                    ps.setInt(3, seats);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        id = rs.getInt(1);
                    }
                }
                c.commit();
                return id;
            } catch (SQLException | BusinessException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** PENDING requests across all rides created by this student. */
    public List<RideRequest> pendingForCreator(int creatorId) throws SQLException {
        return query(SELECT_REQ + "WHERE r.student_id = ? AND rr.status = 'PENDING' ORDER BY rr.request_time",
                ps -> ps.setInt(1, creatorId));
    }

    public List<RideRequest> forRide(int rideId) throws SQLException {
        return query(SELECT_REQ + "WHERE rr.ride_id = ? ORDER BY rr.request_time",
                ps -> ps.setInt(1, rideId));
    }

    public List<RideRequest> forRideAndStudent(int rideId, int studentId) throws SQLException {
        return query(SELECT_REQ + "WHERE rr.ride_id = ? AND rr.student_id = ? ORDER BY rr.request_time",
                ps -> { ps.setInt(1, rideId); ps.setInt(2, studentId); });
    }

    public List<RideRequest> byStudent(int studentId) throws SQLException {
        return query(SELECT_REQ + "WHERE rr.student_id = ? ORDER BY rr.request_time DESC",
                ps -> ps.setInt(1, studentId));
    }

    /** Approve: validates seats, deducts them, marks ride FULL when none are left. */
    public void approve(int requestId, int creatorId) throws SQLException, BusinessException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                Locked l = lock(c, requestId);
                if (l.rideOwner != creatorId) throw new BusinessException("Only the ride creator can approve requests.");
                if (!l.requestStatus.equals("PENDING")) throw new BusinessException("Request is not PENDING (status: " + l.requestStatus + ").");
                if (!l.rideStatus.equals("AVAILABLE")) throw new BusinessException("Ride is not available (status: " + l.rideStatus + ").");
                if (l.seats > l.availableSeats) throw new BusinessException("Not enough seats left (" + l.availableSeats + " available).");

                int remaining = l.availableSeats - l.seats;
                updateRide(c, l.rideId, remaining, remaining == 0 ? "FULL" : "AVAILABLE");
                setRequestStatus(c, requestId, "APPROVED");
                c.commit();
            } catch (SQLException | BusinessException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void reject(int requestId, int creatorId) throws SQLException, BusinessException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                Locked l = lock(c, requestId);
                if (l.rideOwner != creatorId) throw new BusinessException("Only the ride creator can reject requests.");
                if (!l.requestStatus.equals("PENDING")) throw new BusinessException("Request is not PENDING (status: " + l.requestStatus + ").");
                setRequestStatus(c, requestId, "REJECTED");
                c.commit();
            } catch (SQLException | BusinessException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Passenger cancels own request. If it was APPROVED the seats are returned to the ride. */
    public void cancel(int requestId, int studentId) throws SQLException, BusinessException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                Locked l = lock(c, requestId);
                if (l.requesterId != studentId) throw new BusinessException("You can only cancel your own requests.");
                if (!(l.requestStatus.equals("PENDING") || l.requestStatus.equals("APPROVED")))
                    throw new BusinessException("A " + l.requestStatus + " request cannot be cancelled.");

                if (l.requestStatus.equals("APPROVED") && !l.rideStatus.equals("CANCELLED")) {
                    updateRide(c, l.rideId, l.availableSeats + l.seats, "AVAILABLE");
                }
                setRequestStatus(c, requestId, "CANCELLED");
                c.commit();
            } catch (SQLException | BusinessException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ---- helpers ----
    private record Locked(int rideId, int rideOwner, int requesterId, int seats,
                          String requestStatus, int availableSeats, String rideStatus) { }

    /** Locks the request and its ride row so concurrent approvals cannot oversell seats. */
    private Locked lock(Connection c, int requestId) throws SQLException, BusinessException {
        String sql = "SELECT rr.ride_id, r.student_id AS owner, rr.student_id AS requester, "
                   + "rr.seats_requested, rr.status AS req_status, r.available_seats, r.status AS ride_status "
                   + "FROM ride_requests rr JOIN rides r ON r.ride_id = rr.ride_id "
                   + "WHERE rr.request_id = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new BusinessException("Request not found.");
                return new Locked(rs.getInt("ride_id"), rs.getInt("owner"), rs.getInt("requester"),
                        rs.getInt("seats_requested"), rs.getString("req_status"),
                        rs.getInt("available_seats"), rs.getString("ride_status"));
            }
        }
    }

    private void updateRide(Connection c, int rideId, int seats, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE rides SET available_seats = ?, status = ? WHERE ride_id = ?")) {
            ps.setInt(1, seats);
            ps.setString(2, status);
            ps.setInt(3, rideId);
            ps.executeUpdate();
        }
    }

    private void setRequestStatus(Connection c, int requestId, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE ride_requests SET status = ? WHERE request_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, requestId);
            ps.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private List<RideRequest> query(String sql, Binder binder) throws SQLException {
        List<RideRequest> list = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RideRequest(rs.getInt("request_id"), rs.getInt("ride_id"),
                            rs.getInt("student_id"), rs.getString("student_name"), rs.getString("route"),
                            rs.getInt("seats_requested"), rs.getTimestamp("request_time").toLocalDateTime(),
                            rs.getString("status")));
                }
            }
        }
        return list;
    }
}
