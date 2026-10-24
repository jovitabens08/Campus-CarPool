package com.campuscarpool.ui;

import com.campuscarpool.dao.RideDAO;
import com.campuscarpool.dao.RideRequestDAO;
import com.campuscarpool.dao.StudentDAO;
import com.campuscarpool.model.BusinessException;
import com.campuscarpool.model.Ride;
import com.campuscarpool.model.RideRequest;
import com.campuscarpool.model.Student;
import com.campuscarpool.service.RideMatchingService;
import com.campuscarpool.service.RideMatchingService.Match;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

/** Console menu that drives every Campus Carpool feature. */
public class MenuUI {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern PHONE = Pattern.compile("^[+]?[0-9]{7,15}$");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Scanner in = new Scanner(System.in);
    private final StudentDAO studentDAO = new StudentDAO();
    private final RideDAO rideDAO = new RideDAO();
    private final RideRequestDAO requestDAO = new RideRequestDAO();
    private final RideMatchingService matching = new RideMatchingService();

    private Student current;

    public void start() {
        System.out.println("=====================================");
        System.out.println("        CAMPUS CARPOOL");
        System.out.println("=====================================");
        boolean running = true;
        while (running) {
            running = (current == null) ? authMenu() : mainMenu();
        }
        System.out.println("Goodbye!");
    }

    // ------------------------------------------------------------------ menus
    private boolean authMenu() {
        System.out.println("\n1. Register student\n2. Login with student ID\n0. Exit");
        switch (readInt("Choice: ")) {
            case 1 -> run(this::registerStudent);
            case 2 -> run(this::login);
            case 0 -> { return false; }
            default -> System.out.println("Invalid choice.");
        }
        return true;
    }

    private boolean mainMenu() {
        System.out.println("\n--- Logged in: " + current.name() + " (ID " + current.studentId() + ") ---");
        System.out.println("1. Create ride");
        System.out.println("2. Search rides");
        System.out.println("3. Smart ride matching");
        System.out.println("4. Request seats");
        System.out.println("5. Manage ride requests (as ride creator)");
        System.out.println("6. View ride details / my rides / my requests");
        System.out.println("7. Cancel ride or request");
        System.out.println("8. Logout");
        System.out.println("0. Exit");
        switch (readInt("Choice: ")) {
            case 1 -> run(this::createRide);
            case 2 -> run(this::searchRides);
            case 3 -> run(this::smartMatch);
            case 4 -> run(this::requestSeats);
            case 5 -> run(this::manageRequests);
            case 6 -> run(this::viewMenu);
            case 7 -> run(this::cancelMenu);
            case 8 -> current = null;
            case 0 -> { return false; }
            default -> System.out.println("Invalid choice.");
        }
        return true;
    }

    // --------------------------------------------------------------- features
    private void registerStudent() throws SQLException, BusinessException {
        System.out.println("\n-- Student Registration --");
        String name = readNonEmpty("Name: ");
        String email = readMatching("Email: ", EMAIL, "Enter a valid email address.");
        String contact = readMatching("Contact number: ", PHONE, "Enter 7-15 digits (optional leading +).");
        int id = studentDAO.register(name, email, contact);
        System.out.println("Registered successfully. Your student ID is " + id + " (use it to login).");
    }

    private void login() throws SQLException {
        int id = readInt("Student ID: ");
        Student s = studentDAO.findById(id);
        if (s == null) {
            System.out.println("No student found with ID " + id + ".");
        } else {
            current = s;
            System.out.println("Welcome, " + s.name() + "!");
        }
    }

    private void createRide() throws SQLException {
        System.out.println("\n-- Create Ride --");
        String pickup = readNonEmpty("Pickup location: ");
        String dest = readNonEmpty("Destination: ");
        if (pickup.equalsIgnoreCase(dest)) {
            System.out.println("Pickup and destination cannot be the same.");
            return;
        }
        LocalDate date = readDate("Travel date (yyyy-MM-dd): ", false);
        LocalTime time = readTime("Travel time (HH:mm, 24h): ", false);
        if (java.time.LocalDateTime.of(date, time).isBefore(java.time.LocalDateTime.now())) {
            System.out.println("Travel date/time must be in the future.");
            return;
        }
        int seats = readInt("Available seats (1-10): ");
        if (seats < 1 || seats > 10) {
            System.out.println("Seats must be between 1 and 10.");
            return;
        }
        int id = rideDAO.create(current.studentId(), pickup, dest, date, time, seats);
        System.out.println("Ride published with ID " + id + ".");
    }

    private void searchRides() throws SQLException {
        System.out.println("\n-- Search Rides --");
        String pickup = readNonEmpty("Pickup location (partial ok): ");
        String dest = readNonEmpty("Destination (partial ok): ");
        LocalDate date = readDate("Travel date (yyyy-MM-dd, blank = any): ", true);
        LocalTime time = readTime("Preferred time (HH:mm, blank = any; +/-60 min): ", true);
        List<Ride> rides = matching.search(pickup, dest, date, time);
        if (rides.isEmpty()) {
            System.out.println("No rides found. Try Smart ride matching for a wider time window.");
            return;
        }
        printRides(rides);
    }

    private void smartMatch() throws SQLException {
        System.out.println("\n-- Smart Ride Matching --");
        String pickup = readNonEmpty("Your pickup location: ");
        String dest = readNonEmpty("Your destination: ");
        LocalDate date = readDate("Travel date (yyyy-MM-dd): ", false);
        LocalTime time = readTime("Preferred time (HH:mm): ", false);
        int seats = readInt("Seats needed: ");
        if (seats < 1) { System.out.println("Seats needed must be at least 1."); return; }

        List<Match> matches = matching.smartMatch(current.studentId(), pickup, dest, date, time, seats);
        if (matches.isEmpty()) {
            System.out.println("No suitable rides within " + RideMatchingService.MATCH_WINDOW_MINUTES
                    + " minutes of your preferred time.");
            return;
        }
        System.out.printf("%-6s %-6s %-18s %-18s %-11s %-6s %-6s %s%n",
                "Match", "RideID", "Pickup", "Destination", "Date", "Time", "Seats", "Driver");
        for (Match m : matches) {
            Ride r = m.ride();
            System.out.printf("%-6s %-6d %-18s %-18s %-11s %-6s %-6d %s%n",
                    m.score() + "%", r.rideId(), cut(r.pickup(), 18), cut(r.destination(), 18),
                    r.travelDate(), r.travelTime(), r.availableSeats(), r.creatorName());
        }
        System.out.println("Use 'Request seats' with the Ride ID of your choice.");
    }

    private void requestSeats() throws SQLException, BusinessException {
        System.out.println("\n-- Request Seats --");
        int rideId = readInt("Ride ID: ");
        int seats = readInt("Seats to request: ");
        int reqId = requestDAO.requestSeats(rideId, current.studentId(), seats);
        System.out.println("Request submitted (Request ID " + reqId + "). Status: PENDING.");
    }

    private void manageRequests() throws SQLException, BusinessException {
        System.out.println("\n-- Pending Requests For Your Rides --");
        List<RideRequest> pending = requestDAO.pendingForCreator(current.studentId());
        if (pending.isEmpty()) {
            System.out.println("No pending requests.");
            return;
        }
        printRequests(pending, true);
        int reqId = readInt("Request ID to act on (0 = back): ");
        if (reqId == 0) return;
        String action = readNonEmpty("(A)pprove or (R)eject: ").toUpperCase();
        switch (action) {
            case "A" -> { requestDAO.approve(reqId, current.studentId()); System.out.println("Request approved. Seats updated."); }
            case "R" -> { requestDAO.reject(reqId, current.studentId()); System.out.println("Request rejected."); }
            default -> System.out.println("Invalid action.");
        }
    }

    private void viewMenu() throws SQLException {
        System.out.println("\n1. Ride details by ID\n2. My rides\n3. My requests\n0. Back");
        switch (readInt("Choice: ")) {
            case 1 -> viewRideDetails();
            case 2 -> {
                List<Ride> mine = rideDAO.findByCreator(current.studentId());
                if (mine.isEmpty()) System.out.println("You have not created any rides.");
                else printRides(mine);
            }
            case 3 -> {
                List<RideRequest> mine = requestDAO.byStudent(current.studentId());
                if (mine.isEmpty()) System.out.println("You have not made any requests.");
                else printRequests(mine, false);
            }
            case 0 -> { }
            default -> System.out.println("Invalid choice.");
        }
    }

    private void viewRideDetails() throws SQLException {
        int rideId = readInt("Ride ID: ");
        Ride r = rideDAO.findById(rideId);
        if (r == null) { System.out.println("Ride not found."); return; }

        System.out.println("\n========== Ride #" + r.rideId() + " ==========");
        System.out.println("Created by      : " + r.creatorName() + " (ID " + r.studentId() + ")");
        System.out.println("Route           : " + r.pickup() + " -> " + r.destination());
        System.out.println("Departure       : " + r.travelDate() + " " + r.travelTime());
        System.out.println("Available seats : " + r.availableSeats());
        System.out.println("Status          : " + r.status());

        boolean isOwner = r.studentId() == current.studentId();
        List<RideRequest> reqs = isOwner ? requestDAO.forRide(rideId)
                                         : requestDAO.forRideAndStudent(rideId, current.studentId());
        System.out.println(isOwner ? "\nAll requests on this ride:" : "\nYour requests on this ride:");
        if (reqs.isEmpty()) System.out.println("  (none)");
        else printRequests(reqs, true);
    }

    private void cancelMenu() throws SQLException, BusinessException {
        System.out.println("\n1. Cancel my ride\n2. Cancel my request\n0. Back");
        switch (readInt("Choice: ")) {
            case 1 -> {
                List<Ride> mine = rideDAO.findByCreator(current.studentId());
                if (mine.isEmpty()) { System.out.println("You have no rides."); return; }
                printRides(mine);
                int id = readInt("Ride ID to cancel (0 = back): ");
                if (id == 0) return;
                if (!confirm("Cancelling also cancels all active requests on it.")) return;
                int n = rideDAO.cancelRide(id, current.studentId());
                System.out.println("Ride cancelled. " + n + " active request(s) were cancelled.");
            }
            case 2 -> {
                List<RideRequest> mine = requestDAO.byStudent(current.studentId());
                if (mine.isEmpty()) { System.out.println("You have no requests."); return; }
                printRequests(mine, false);
                int id = readInt("Request ID to cancel (0 = back): ");
                if (id == 0) return;
                if (!confirm("Cancel this request?")) return;
                requestDAO.cancel(id, current.studentId());
                System.out.println("Request cancelled.");
            }
            case 0 -> { }
            default -> System.out.println("Invalid choice.");
        }
    }

    // ---------------------------------------------------------------- printing
    private void printRides(List<Ride> rides) {
        System.out.printf("%-6s %-18s %-18s %-11s %-6s %-6s %-10s %s%n",
                "RideID", "Pickup", "Destination", "Date", "Time", "Seats", "Status", "Driver");
        for (Ride r : rides) {
            System.out.printf("%-6d %-18s %-18s %-11s %-6s %-6d %-10s %s%n",
                    r.rideId(), cut(r.pickup(), 18), cut(r.destination(), 18), r.travelDate(),
                    r.travelTime(), r.availableSeats(), r.status(), r.creatorName());
        }
    }

    private void printRequests(List<RideRequest> reqs, boolean showPassenger) {
        System.out.printf("%-6s %-6s %-30s %-16s %-6s %-16s %s%n",
                "ReqID", "RideID", "Route", "Passenger", "Seats", "Requested", "Status");
        for (RideRequest q : reqs) {
            System.out.printf("%-6d %-6d %-30s %-16s %-6d %-16s %s%n",
                    q.requestId(), q.rideId(), cut(q.route(), 30),
                    showPassenger ? cut(q.studentName(), 16) : "you",
                    q.seatsRequested(), q.requestTime().format(TS), q.status());
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }

    // ------------------------------------------------------------------- input
    @FunctionalInterface
    private interface Action { void run() throws SQLException, BusinessException; }

    /** Runs a menu action and reports errors without crashing the app. */
    private void run(Action a) {
        try {
            a.run();
        } catch (BusinessException e) {
            System.out.println("Cannot complete: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        if (!in.hasNextLine()) { System.out.println(); System.exit(0); }
        return in.nextLine().trim();
    }

    private String readNonEmpty(String prompt) {
        while (true) {
            String s = readLine(prompt);
            if (!s.isEmpty()) return s;
            System.out.println("Value cannot be empty.");
        }
    }

    private String readMatching(String prompt, Pattern p, String error) {
        while (true) {
            String s = readLine(prompt);
            if (p.matcher(s).matches()) return s;
            System.out.println(error);
        }
    }

    private int readInt(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(readLine(prompt));
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number.");
            }
        }
    }

    private LocalDate readDate(String prompt, boolean optional) {
        while (true) {
            String s = readLine(prompt);
            if (optional && s.isEmpty()) return null;
            try {
                return LocalDate.parse(s);
            } catch (DateTimeParseException e) {
                System.out.println("Use the format yyyy-MM-dd (e.g. 2026-11-05).");
            }
        }
    }

    private LocalTime readTime(String prompt, boolean optional) {
        while (true) {
            String s = readLine(prompt);
            if (optional && s.isEmpty()) return null;
            try {
                return LocalTime.parse(s);
            } catch (DateTimeParseException e) {
                System.out.println("Use the 24-hour format HH:mm (e.g. 07:30).");
            }
        }
    }

    private boolean confirm(String message) {
        return readLine(message + " Continue? (y/n): ").equalsIgnoreCase("y");
    }
}
