package flightapp;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs queries against a back-end database
 */
public class Query extends QueryAbstract {
  //
  // Canned queries
  //
  private static final String FLIGHT_CAPACITY_SQL = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement flightCapacityStmt;

  private static final String USER_CLEAR_SQL = "DELETE FROM users_aydenc16";
  private PreparedStatement userClearStmt;

  private static final String RESERVATION_CLEAR_SQL = "DELETE FROM reservations_aydenc16";
  private PreparedStatement reservationClearStmt;

  private static final String FETCH_USER_SQL = "SELECT * FROM users_aydenc16 WHERE username = ?";
  private PreparedStatement fetchUserStmt;

  private static final String CREATE_USER_SQL = "INSERT INTO users_aydenc16 VALUES(?, ?, ?)";
  private PreparedStatement createUserStmt;


  private static final String DIRECT_FLIGHT_SEARCH_SQL =
  "SELECT DISTINCT * FROM FLIGHTS AS f " +
  "WHERE f.origin_city = ? AND f.dest_city = ? " +
  "AND f.day_of_month = ? AND f.canceled = 0 " +
  "ORDER BY f.actual_time, f.fid";
  private PreparedStatement directFlightSearchStmt;

  private static final String INDIRECT_FLIGHT_SEARCH_SQL =
  "SELECT DISTINCT *, f1.actual_time + f2.actual_time AS total_actual_time " +
  "FROM FLIGHTS AS f1, FLIGHTS AS f2 " +
  "WHERE f1.origin_city = ? AND f2.dest_city = ? " +
  "AND f1.dest_city = f2.origin_city AND f1.dest_city != f2.dest_city " +
  "AND f1.canceled = 0 AND f2.canceled = 0 " +
  "AND f1.day_of_month = ? AND f2.day_of_month = ? " +
  "ORDER BY total_actual_time, f1.fid, f2.fid";
  private PreparedStatement indirectFlightSearchStmt;

  private static final String FETCH_USER_RESERVATIONS_SQL =
  "SELECT * FROM reservations_aydenc16 AS r, FLIGHTS AS f " +
  "WHERE r.username = ? AND f.fid = r.flight1";
  private PreparedStatement fetchUserReservationsStmt;

  private static final String FLIGHT_RESERVATION_AMNT_SQL =
  "SELECT COUNT(*) AS count " +
  "FROM reservations_aydenc16 AS r " +
  "WHERE r.flight1 = ? OR r.flight2 = ?";
  private PreparedStatement flightReservationAmntStmt;

  private static final String TOTAL_RESERVATION_AMNT_SQL =
  "SELECT COUNT(*) AS count " +
  "FROM reservations_aydenc16 AS r ";
  private PreparedStatement totalReservationAmntStmt;

  private static final String CREATE_RESERVATION_SQL =
  "INSERT INTO reservations_aydenc16 VALUES(?, ?, ?, ?, ?)";
  private PreparedStatement createReservationStmt;

  private static final String FETCH_USER_BALANCE_SQL =
  "SELECT balance FROM users_aydenc16 WHERE username = ?";
  private PreparedStatement fetchUserBalanceStmt;

  private static final String FETCH_FLIGHT_PRICE_SQL =
  "SELECT price FROM FLIGHTS WHERE fid = ?";
  private PreparedStatement fetchFlightPriceStmt;

  private static final String PAID_RESERVATION_SQL =
  "UPDATE reservations_aydenc16 SET isPaid = 1 WHERE rid = ?";
  private PreparedStatement paidReservationStmt;

  private static final String UPDATE_USER_BALANCE_SQL =
  "UPDATE users_aydenc16 SET balance = ? WHERE username = ?";
  private PreparedStatement updateUserBalanceStmt;

  private static final String FETCH_FLIGHT_SQL =
  "SELECT * FROM FLIGHTS WHERE fid = ?";
  private PreparedStatement fetchFlightStmt;

  //
  // Instance variables
  //
  private boolean loggedIn;
  private List<Itinerary> currItineraries;
  private String currUser;

  protected Query() throws SQLException, IOException {
    prepareStatements();
    this.loggedIn = false;
    this.currItineraries = new ArrayList<>();
    this.currUser = "";
  }

  /**
   * Clear the data in any custom tables created.
   *
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      conn.setAutoCommit(false);
      reservationClearStmt.executeUpdate();
      userClearStmt.executeUpdate();
      conn.commit();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    flightCapacityStmt = conn.prepareStatement(FLIGHT_CAPACITY_SQL);
    userClearStmt = conn.prepareStatement(USER_CLEAR_SQL);
    reservationClearStmt = conn.prepareStatement(RESERVATION_CLEAR_SQL);
    fetchUserStmt = conn.prepareStatement(FETCH_USER_SQL);
    createUserStmt = conn.prepareStatement(CREATE_USER_SQL);
    directFlightSearchStmt = conn.prepareStatement(DIRECT_FLIGHT_SEARCH_SQL);
    indirectFlightSearchStmt = conn.prepareStatement(INDIRECT_FLIGHT_SEARCH_SQL);
    fetchUserReservationsStmt = conn.prepareStatement(FETCH_USER_RESERVATIONS_SQL);
    flightReservationAmntStmt = conn.prepareStatement(FLIGHT_RESERVATION_AMNT_SQL);
    totalReservationAmntStmt = conn.prepareStatement(TOTAL_RESERVATION_AMNT_SQL);
    createReservationStmt = conn.prepareStatement(CREATE_RESERVATION_SQL);
    fetchUserBalanceStmt = conn.prepareStatement(FETCH_USER_BALANCE_SQL);
    fetchFlightPriceStmt = conn.prepareStatement(FETCH_FLIGHT_PRICE_SQL);
    paidReservationStmt = conn.prepareStatement(PAID_RESERVATION_SQL);
    updateUserBalanceStmt = conn.prepareStatement(UPDATE_USER_BALANCE_SQL);
    fetchFlightStmt = conn.prepareStatement(FETCH_FLIGHT_SQL);
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_login(String username, String password) {
    if(username == null && password == null){
      return "Login failed\n";
    }
    if(!loggedIn){
      try{
        conn.setAutoCommit(false);
        fetchUserStmt.clearParameters();
        fetchUserStmt.setString(1, username);
        ResultSet user = fetchUserStmt.executeQuery();
        if(user.next()){
          if(PasswordUtils.plaintextMatchesSaltedHash(password, user.getBytes("Password"))){
            loggedIn = true;
            currUser = username;
            currItineraries.clear();
            conn.commit();
            conn.setAutoCommit(true);
            return "Logged in as " + username + "\n";
          }
        }
        conn.rollback();
        user.close();
        conn.setAutoCommit(true);
        return "Login failed\n";
      }
      catch (SQLException e){
        e.printStackTrace();
      }
    }
    return "User already logged in\n";
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    if(username == null | password == null) {
      return "Failed to create user\n";
    }
    if(username.length() <= 20 && password.length() <= 20 && initAmount >= 0) {
      try {
        conn.setAutoCommit(false);
        fetchUserStmt.clearParameters();
        fetchUserStmt.setString(1, username);
        ResultSet user = fetchUserStmt.executeQuery();
        if(!user.next()) {
          createUserStmt.setString(1, username);
          createUserStmt.setBytes(2, PasswordUtils.saltAndHashPassword(password));
          createUserStmt.setInt(3, initAmount);
          createUserStmt.execute();
          conn.commit();
          conn.setAutoCommit(true);
          return "Created user " + username + "\n";
        }
        conn.rollback();
      }
      catch(SQLException e) {
        e.printStackTrace();
        if(isDeadlock(e)) {
          return transaction_createCustomer(username, password, initAmount);
        }
      }
    }
    return "Failed to create user\n";
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_search(String originCity, String destinationCity,
                                   boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries) {
    StringBuffer sb = new StringBuffer();
    if(originCity != null && destinationCity != null && dayOfMonth > 0 && numberOfItineraries > 0) {
      try {
        currItineraries.clear();
        directFlightSearchStmt.clearParameters();
        directFlightSearchStmt.setString(1, originCity);
        directFlightSearchStmt.setString(2, destinationCity);
        directFlightSearchStmt.setInt(3, dayOfMonth);
        ResultSet directSet = directFlightSearchStmt.executeQuery();
        while(directSet.next() && currItineraries.size() < numberOfItineraries) {
          Flight f = new Flight(
            directSet.getInt(directSet.findColumn("fid")),
            directSet.getInt(directSet.findColumn("day_of_month")),
            directSet.getString(directSet.findColumn("carrier_id")),
            directSet.getString(directSet.findColumn("flight_num")),
            directSet.getString(directSet.findColumn("origin_city")),
            directSet.getString(directSet.findColumn("dest_city")),
            directSet.getInt(directSet.findColumn("actual_time")),
            directSet.getInt(directSet.findColumn("capacity")),
            directSet.getInt(directSet.findColumn("price")));

          currItineraries.add(new Itinerary(f));
        }
        directSet.close();

        if (!directFlight && currItineraries.size() != numberOfItineraries) {
          indirectFlightSearchStmt.clearParameters();
          indirectFlightSearchStmt.setString(1, originCity);
          indirectFlightSearchStmt.setString(2, destinationCity);
          indirectFlightSearchStmt.setInt(3, dayOfMonth);
          indirectFlightSearchStmt.setInt(4, dayOfMonth);
          ResultSet indirectSet = indirectFlightSearchStmt.executeQuery();
          while(indirectSet.next() && currItineraries.size() < numberOfItineraries) {
            Flight f1 = new Flight(
              indirectSet.getInt(indirectSet.findColumn("fid")),
              indirectSet.getInt(indirectSet.findColumn("day_of_month")),
              indirectSet.getString(indirectSet.findColumn("carrier_id")),
              indirectSet.getString(indirectSet.findColumn("flight_num")),
              indirectSet.getString(indirectSet.findColumn("origin_city")),
              indirectSet.getString(indirectSet.findColumn("dest_city")),
              indirectSet.getInt(indirectSet.findColumn("actual_time")),
              indirectSet.getInt(indirectSet.findColumn("capacity")),
              indirectSet.getInt(indirectSet.findColumn("price")));

            Flight f2 = new Flight(
              indirectSet.getInt(indirectSet.findColumn("fid") + 18),
              indirectSet.getInt(indirectSet.findColumn("day_of_month") + 18),
              indirectSet.getString(indirectSet.findColumn("carrier_id") + 18),
              indirectSet.getString(indirectSet.findColumn("flight_num") + 18),
              indirectSet.getString(indirectSet.findColumn("origin_city") + 18),
              indirectSet.getString(indirectSet.findColumn("dest_city") + 18),
              indirectSet.getInt(indirectSet.findColumn("actual_time") + 18),
              indirectSet.getInt(indirectSet.findColumn("capacity") + 18),
              indirectSet.getInt(indirectSet.findColumn("price") + 18));

            currItineraries.add(new Itinerary(f1, f2));
          }
          indirectSet.close();
        }

        if(currItineraries.size() == 0) {
          return "No flights match your selection\n";
        }

        int i = 0;
        currItineraries.sort(new ItineraryComparator());
        while(i < Math.min(numberOfItineraries, currItineraries.size())) {
          Itinerary itinerary = currItineraries.get(i);
          sb.append("Itinerary " + i + ": " + itinerary.num_of_flights
          + " flight(s), " + itinerary.actual_time + " minutes\n");
          if (itinerary.num_of_flights == 1) {
              sb.append(itinerary.f1.toString() + "\n");
          }
          else {
              sb.append(itinerary.f1.toString() + "\n");
              sb.append(itinerary.f2.toString() + "\n");
          }
          i++;
        }

        return sb.toString();
      }
      catch(Exception e) {
        e.printStackTrace();
        return "Failed to search\n";
      }
    }
    return "Failed to search\n";
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_book(int itineraryId) {
    if(!loggedIn) {
      return "Cannot book reservations, not logged in\n";
    }
    if(itineraryId >= currItineraries.size() || itineraryId < 0) {
      return "No such itinerary " + itineraryId + "\n";
    }
    try {
      Itinerary currItinerary = currItineraries.get(itineraryId);
      Flight f1 = currItinerary.getFlight1();
      Flight f2 = currItinerary.getFlight2();

      conn.setAutoCommit(false);
      fetchUserReservationsStmt.clearParameters();
      fetchUserReservationsStmt.setString(1, currUser);
      ResultSet reservationSet = fetchUserReservationsStmt.executeQuery();
      while (reservationSet.next()) {
        int reservationDate = reservationSet.getInt("day_of_month");
        int flightDate = f1.dayOfMonth;
        if (reservationDate == flightDate) {
          reservationSet.close();
          conn.rollback();
          conn.commit();
          conn.setAutoCommit(true);
          return "You cannot book two flights in the same day\n";
        }
      }

      int resAmnt = flightReservationAmnt(f1);
      if (resAmnt >= checkFlightCapacity(f1.fid)) {
        reservationSet.close();
        conn.rollback();
        conn.commit();
        conn.setAutoCommit(true);
        return "Booking failed\n";
      }

      if (f2 != null) {
        resAmnt = flightReservationAmnt(f2);
        if (resAmnt >= checkFlightCapacity(f2.fid)) {
          reservationSet.close();
          conn.rollback();
          conn.commit();
          conn.setAutoCommit(true);
          return "Booking failed\n";
        }
      }

      reservationSet = totalReservationAmntStmt.executeQuery();
      reservationSet.next();
      int totalReservations = reservationSet.getInt("count");
      reservationSet.close();

      createReservationStmt.clearParameters();
      createReservationStmt.setString(1, currUser);
      createReservationStmt.setInt(2, totalReservations + 1);
      createReservationStmt.setInt(3, 0);
      createReservationStmt.setInt(4, f1.fid);
      if (f2 != null) {
        createReservationStmt.setInt(5, f2.fid);
      }
      else {
        createReservationStmt.setNull(5, Types.INTEGER);
      }
      createReservationStmt.execute();

      conn.commit();
      conn.setAutoCommit(true);
      return "Booked flight(s), reservation ID: " + (totalReservations + 1) + "\n";
    }
    catch (SQLException e) {
      try {
        e.printStackTrace();
        conn.rollback();
        conn.commit();
        conn.setAutoCommit(true);
        if (isDeadlock(e)) {
          System.out.println("Deadlock");
          return transaction_book(itineraryId);
        }
      }
      catch (SQLException se) {
        se.printStackTrace();
      }
    }
    return "Booking failed\n";
  }

  private int flightReservationAmnt(Flight f) throws SQLException {
    flightReservationAmntStmt.clearParameters();
    flightReservationAmntStmt.setInt(1, f.fid);
    flightReservationAmntStmt.setInt(2, f.fid);
    ResultSet reservationSet = flightReservationAmntStmt.executeQuery();
    reservationSet.next();
    int count = reservationSet.getInt("count");
    reservationSet.close();
    return count;
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_pay(int reservationId) {
    if (!loggedIn) {
      return "Cannot pay, not logged in\n";
    }
    try {
      conn.setAutoCommit(false);
      fetchUserReservationsStmt.clearParameters();
      fetchUserReservationsStmt.setString(1, currUser);
      ResultSet reservationSet = fetchUserReservationsStmt.executeQuery();
      boolean ridMatch = false;
      while (reservationSet.next()) {
        if (reservationId == reservationSet.getInt("rid")) {
          ridMatch = true;
          if (reservationSet.getInt("isPaid") == 1) {
            reservationSet.close();
            conn.rollback();
            conn.setAutoCommit(true);
            return "Cannot find unpaid reservation " + reservationId + " under user: " + currUser + "\n";
          }
          break;
        }
      }
      if (!ridMatch) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "Cannot find unpaid reservation " + reservationId + " under user: " + currUser + "\n";
      }

      fetchUserBalanceStmt.clearParameters();
      fetchUserBalanceStmt.setString(1, currUser);
      ResultSet result = fetchUserBalanceStmt.executeQuery();
      result.next();

      int fid1 = reservationSet.getInt("flight1");
      int balance = result.getInt("balance");

      fetchFlightPriceStmt.clearParameters();
      fetchFlightPriceStmt.setInt(1, fid1);
      result = fetchFlightPriceStmt.executeQuery();
      result.next();
      int price1 = result.getInt("price");
      int price2 = 0;
      int fid2 = reservationSet.getInt("flight2");
      if (!reservationSet.wasNull()) {
        fetchFlightPriceStmt.clearParameters();
        fetchFlightPriceStmt.setInt(1, fid2);
        result = fetchFlightPriceStmt.executeQuery();
        result.next();
        price2 = result.getInt("price");
      }

      reservationSet.close();
      result.close();

      int totalCost = price1 + price2;
      if (balance - totalCost < 0) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "User has only " + balance + " in account but itinerary costs " + totalCost + "\n";
      }

      paidReservationStmt.clearParameters();
      paidReservationStmt.setInt(1, reservationId);
      paidReservationStmt.executeUpdate();
      updateUserBalanceStmt.clearParameters();
      updateUserBalanceStmt.setInt(1, balance - totalCost);
      updateUserBalanceStmt.setString(2, currUser);
      updateUserBalanceStmt.executeUpdate();
      conn.commit();
      conn.setAutoCommit(true);
      return "Paid reservation: " + reservationId + " remaining balance: " + (balance - totalCost) + "\n";

    }
    catch (SQLException e) {
      try {
        e.printStackTrace();
        conn.rollback();
        conn.setAutoCommit(true);
        if (isDeadlock(e)) {
          System.out.println("Deadlock");
          return transaction_pay(reservationId);
        }
      }
      catch (SQLException se) {
        se.printStackTrace();
      }
    }
    return "Failed to pay for reservation " + reservationId + "\n";
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_reservations() {
    if (!loggedIn) {
      return "Cannot view reservations, not logged in\n";
    }
    try {
      conn.setAutoCommit(false);
      fetchUserReservationsStmt.clearParameters();
      fetchUserReservationsStmt.setString(1, currUser);
      ResultSet reservationSet = fetchUserReservationsStmt.executeQuery();
      if (!reservationSet.next()) {
        reservationSet.close();
        conn.rollback();
        conn.setAutoCommit(true);
        return "No reservations found\n";
      }

      fetchUserReservationsStmt.clearParameters();
      fetchUserReservationsStmt.setString(1, currUser);
      reservationSet = fetchUserReservationsStmt.executeQuery();
      StringBuffer sb = new StringBuffer();
      while (reservationSet.next()) {
        String isPaid = "false";
        if (reservationSet.getInt("isPaid") == 1) {
          isPaid = "true";
        }
        sb.append("Reservation " + reservationSet.getInt("rid") + " paid: " + isPaid + ":\n");

        Flight f1 = new Flight(
          reservationSet.getInt("flight1"),
          reservationSet.getInt("day_of_month"),
          reservationSet.getString("carrier_id"),
          reservationSet.getString("flight_num"),
          reservationSet.getString("origin_city"),
          reservationSet.getString("dest_city"),
          reservationSet.getInt("actual_time"),
          reservationSet.getInt("capacity"),
          reservationSet.getInt("price")
        );
        sb.append(f1.toString() + "\n");

        int fid2 = reservationSet.getInt("flight2");
        Flight f2;
        if (!reservationSet.wasNull()) {
          fetchFlightStmt.clearParameters();
          fetchFlightStmt.setInt(1, fid2);
          ResultSet flightSet = fetchFlightStmt.executeQuery();
          flightSet.next();
          f2 = new Flight(
            flightSet.getInt("fid"),
            flightSet.getInt("day_of_month"),
            flightSet.getString("carrier_id"),
            flightSet.getString("flight_num"),
            flightSet.getString("origin_city"),
            flightSet.getString("dest_city"),
            flightSet.getInt("actual_time"),
            flightSet.getInt("capacity"),
            flightSet.getInt("price")
          );
          flightSet.close();
          sb.append(f2.toString() + "\n");
        }
      }
      reservationSet.close();
      conn.rollback();
      conn.setAutoCommit(true);
      return sb.toString();
    }
    catch (SQLException e) {
      try {
        e.printStackTrace();
        conn.rollback();
        conn.setAutoCommit(true);
        if (isDeadlock(e)) {
          System.out.println("Deadlock");
          return transaction_reservations();
        }
      }
      catch (SQLException se) {
        se.printStackTrace();
      }
    }
    return "Failed to retrieve reservations\n";
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    flightCapacityStmt.clearParameters();
    flightCapacityStmt.setInt(1, fid);

    ResultSet results = flightCapacityStmt.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * Utility function to determine whether an error was caused by a deadlock
   */
  private static boolean isDeadlock(SQLException e) {
    return e.getErrorCode() == 1205;
  }

  /**
   * A class to store information about a single flight
   *
   * TODO(hctang): move this into QueryAbstract
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    Flight(int id, int day, String carrier, String fnum, String origin, String dest, int tm,
           int cap, int pri) {
      fid = id;
      dayOfMonth = day;
      carrierId = carrier;
      flightNum = fnum;
      originCity = origin;
      destCity = dest;
      time = tm;
      capacity = cap;
      price = pri;
    }

    @Override
    public String toString() {
        return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
            + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
            + " Capacity: " + capacity + " Price: " + price;
    }
  }

  private class Itinerary {
    Flight f1;
    Flight f2;
    int actual_time;
    int num_of_flights;

    Itinerary (Flight f1) {
      this.f1 = f1;
      this.actual_time = f1.time;
      this.num_of_flights = 1;
    }

    Itinerary (Flight f1, Flight f2) {
      this.f1 = f1;
      this.f2 = f2;
      this.actual_time = f1.time + f2.time;
      this.num_of_flights = 2;
    }

    public Flight getFlight1() {
      return this.f1;
    }

    public Flight getFlight2() {
      return this.f2;
    }
  }

  public class ItineraryComparator implements Comparator<Itinerary> {
    @Override
    public int compare(Itinerary i1, Itinerary i2) {
      return Integer.compare(i1.actual_time, i2.actual_time);
    }
  }
}
