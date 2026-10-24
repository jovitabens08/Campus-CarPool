package com.campuscarpool.model;

import java.time.LocalDateTime;

public record RideRequest(int requestId, int rideId, int studentId, String studentName, String route,
                          int seatsRequested, LocalDateTime requestTime, String status) { }
