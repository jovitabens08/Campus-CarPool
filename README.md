# Campus Carpool

Console Java application (Maven + JDBC + MySQL) for sharing rides between students.

## Requirements
- JDK 17+
- Maven 3.8+
- MySQL 8+

## Setup
1. Create the database and tables:
   ```
   mysql -u root -p < sql/schema.sql
   ```
2. (Optional) load sample students: `mysql -u root -p < sql/sample_data.sql`
3. Set credentials (defaults: `root` / `root` on `localhost:3306`):
   ```
   # Linux/macOS
   export CARPOOL_DB_USER=root
   export CARPOOL_DB_PASSWORD=yourpassword
   # Windows PowerShell
   $env:CARPOOL_DB_USER="root"; $env:CARPOOL_DB_PASSWORD="yourpassword"
   ```
   A custom JDBC URL can be set with `CARPOOL_DB_URL`.

## Run
```
mvn compile exec:java
```
or build a runnable jar:
```
mvn clean package
java -jar target/campus-carpool-1.0.0.jar
```

## Project layout
```
src/main/java/com/campuscarpool/
  Main.java                      entry point
  db/DBConnection.java           JDBC connection factory
  model/                         Student, Ride, RideRequest, BusinessException
  dao/                           StudentDAO, RideDAO, RideRequestDAO (all SQL, transactions)
  service/RideMatchingService    search + smart match scoring
  ui/MenuUI.java                 menu-driven console interface
sql/schema.sql                   table definitions
```

## Business rules
- Ride status: AVAILABLE -> FULL (when seats hit 0) / CANCELLED.
- Request status: PENDING -> APPROVED / REJECTED / CANCELLED.
- Seats are deducted when the creator **approves** (row locks prevent overselling).
- Cancelling an APPROVED request returns its seats and re-opens a FULL ride.
- Cancelling a ride cancels all its PENDING/APPROVED requests.
- Only PENDING and APPROVED requests can be cancelled; only PENDING requests can be approved/rejected.
- You cannot request your own ride, or hold two active requests on the same ride.

## Smart matching score (0-100)
Pickup (40 exact / 25 partial) + Destination (40 exact / 25 partial) + Time proximity (0-20 within a 120-minute window).
Results are ranked best-first; rides without both locations matching are excluded.
