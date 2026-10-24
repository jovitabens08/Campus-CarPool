CREATE DATABASE IF NOT EXISTS campus_carpool;
USE campus_carpool;

CREATE TABLE IF NOT EXISTS STUDENTS (
    student_id   INT AUTO_INCREMENT PRIMARY KEY,
    student_name VARCHAR(100) NOT NULL,
    email        VARCHAR(120) NOT NULL UNIQUE,
    contact_info VARCHAR(20)  NOT NULL
);

CREATE TABLE IF NOT EXISTS RIDES (
    ride_id         INT AUTO_INCREMENT PRIMARY KEY,
    student_id      INT NOT NULL,
    pickup_location VARCHAR(100) NOT NULL,
    destination     VARCHAR(100) NOT NULL,
    travel_date     DATE NOT NULL,
    travel_time     TIME NOT NULL,
    available_seats INT  NOT NULL CHECK (available_seats >= 0),
    status          VARCHAR(15) NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE, FULL, CANCELLED
    CONSTRAINT fk_ride_student FOREIGN KEY (student_id) REFERENCES STUDENTS(student_id),
    INDEX idx_ride_search (travel_date, status)
);

CREATE TABLE IF NOT EXISTS RIDE_REQUESTS (
    request_id      INT AUTO_INCREMENT PRIMARY KEY,
    ride_id         INT NOT NULL,
    student_id      INT NOT NULL,
    seats_requested INT NOT NULL CHECK (seats_requested > 0),
    request_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(15) NOT NULL DEFAULT 'PENDING',    -- PENDING, APPROVED, REJECTED, CANCELLED
    CONSTRAINT fk_req_ride    FOREIGN KEY (ride_id)    REFERENCES RIDES(ride_id),
    CONSTRAINT fk_req_student FOREIGN KEY (student_id) REFERENCES STUDENTS(student_id)
);
