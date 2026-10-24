package com.campuscarpool.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record Ride(int rideId, int studentId, String creatorName, String pickup, String destination,
                   LocalDate travelDate, LocalTime travelTime, int availableSeats, String status) { }
