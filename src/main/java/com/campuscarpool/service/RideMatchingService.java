package com.campuscarpool.service;

import com.campuscarpool.dao.RideDAO;
import com.campuscarpool.model.Ride;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Search and smart matching of rides.
 *
 * Smart score (0-100):
 *   pickup match       : 40 exact, 25 partial (one contains the other)
 *   destination match  : 40 exact, 25 partial
 *   time proximity     : 0-20, linear, full marks at the same time, 0 at the edge of the window
 * A ride is only a candidate when both locations match at least partially and
 * the departure is within MATCH_WINDOW_MINUTES of the requested time.
 */
public class RideMatchingService {

    public static final int SEARCH_WINDOW_MINUTES = 60;
    public static final int MATCH_WINDOW_MINUTES = 120;

    public record Match(Ride ride, int score) { }

    private final RideDAO rideDAO = new RideDAO();

    /** Plain search: partial text match on locations, optional date, optional +/-60 min time filter. */
    public List<Ride> search(String pickup, String destination, LocalDate date, LocalTime time)
            throws SQLException {
        List<Ride> rides = rideDAO.findOpen(pickup.trim(), destination.trim(), date);
        if (time == null) return rides;
        List<Ride> filtered = new ArrayList<>();
        for (Ride r : rides) {
            if (minutesBetween(r.travelTime(), time) <= SEARCH_WINDOW_MINUTES) filtered.add(r);
        }
        return filtered;
    }

    /** Ranked suggestions for a student looking for a ride on a given date and time. */
    public List<Match> smartMatch(int studentId, String pickup, String destination,
                                  LocalDate date, LocalTime time, int seatsNeeded) throws SQLException {
        List<Match> matches = new ArrayList<>();
        for (Ride r : rideDAO.findOpenOnDate(date)) {
            if (r.studentId() == studentId || r.availableSeats() < seatsNeeded) continue;

            int pickupScore = locationScore(r.pickup(), pickup);
            int destScore = locationScore(r.destination(), destination);
            long diff = minutesBetween(r.travelTime(), time);
            if (pickupScore == 0 || destScore == 0 || diff > MATCH_WINDOW_MINUTES) continue;

            int timeScore = (int) Math.round(20.0 * (MATCH_WINDOW_MINUTES - diff) / MATCH_WINDOW_MINUTES);
            matches.add(new Match(r, pickupScore + destScore + timeScore));
        }
        matches.sort(Comparator.comparingInt(Match::score).reversed()
                .thenComparing(m -> m.ride().travelTime()));
        return matches;
    }

    static int locationScore(String rideLocation, String wanted) {
        String a = rideLocation.trim().toLowerCase();
        String b = wanted.trim().toLowerCase();
        if (a.equals(b)) return 40;
        if (!b.isEmpty() && (a.contains(b) || b.contains(a))) return 25;
        return 0;
    }

    private static long minutesBetween(LocalTime a, LocalTime b) {
        return Math.abs(Duration.between(a, b).toMinutes());
    }
}
