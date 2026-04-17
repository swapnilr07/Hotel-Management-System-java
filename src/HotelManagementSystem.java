import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class HotelManagementSystem {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new LoginFrame().setVisible(true);
        });
    }
}

// ---------- DATABASE MANAGER ----------
class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:hotel.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initializeDatabase() {
        String createUsers = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                role TEXT NOT NULL
            )
        """;
        String createRooms = """
            CREATE TABLE IF NOT EXISTS rooms (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                room_number TEXT UNIQUE NOT NULL,
                type TEXT NOT NULL,
                price REAL NOT NULL
            )
        """;
        String createBookings = """
            CREATE TABLE IF NOT EXISTS bookings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guest_name TEXT NOT NULL,
                phone TEXT NOT NULL,
                room_id INTEGER NOT NULL,
                check_in TEXT NOT NULL,
                check_out TEXT NOT NULL,
                booked_by TEXT NOT NULL,
                FOREIGN KEY (room_id) REFERENCES rooms(id)
            )
        """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createUsers);
            stmt.execute(createRooms);
            stmt.execute(createBookings);

            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE username = 'admin'");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO users (username, password, role) VALUES ('admin', 'admin', 'admin')");
            }

            rs = stmt.executeQuery("SELECT COUNT(*) FROM rooms");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO rooms (room_number, type, price) VALUES ('101', 'Single', 2000.00)");
                stmt.execute("INSERT INTO rooms (room_number, type, price) VALUES ('102', 'Double', 2800.00)");
                stmt.execute("INSERT INTO rooms (room_number, type, price) VALUES ('103', 'Triple', 4000.00)");
            }

            rs = stmt.executeQuery("SELECT COUNT(*) FROM bookings");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO bookings (guest_name, phone, room_id, check_in, check_out, booked_by) VALUES " +
                        "('Swapnil Rathod', '098765', 2, '2026-04-17', '2026-04-21', 'admin')");
                stmt.execute("INSERT INTO bookings (guest_name, phone, room_id, check_in, check_out, booked_by) VALUES " +
                        "('Ananya', '2345678', 1, '2026-04-17', '2026-04-21', 'admin')");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage(), "Fatal", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    public static User authenticate(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new User(rs.getInt("id"), rs.getString("username"), rs.getString("password"), rs.getString("role"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<Room> getAllRooms() {
        List<Room> rooms = new ArrayList<>();
        String sql = "SELECT * FROM rooms ORDER BY id";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                rooms.add(new Room(rs.getInt("id"), rs.getString("room_number"), rs.getString("type"), rs.getDouble("price")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rooms;
    }

    // Get rooms that are available for a given date range (no overlapping booking)
    public static List<Room> getAvailableRooms(LocalDate checkIn, LocalDate checkOut) {
        List<Room> allRooms = getAllRooms();
        List<Room> available = new ArrayList<>();
        for (Room room : allRooms) {
            if (!isRoomBookedInRange(room.getId(), checkIn, checkOut)) {
                available.add(room);
            }
        }
        return available;
    }

    private static boolean isRoomBookedInRange(int roomId, LocalDate checkIn, LocalDate checkOut) {
        String sql = "SELECT COUNT(*) FROM bookings WHERE room_id = ? AND check_in < ? AND check_out > ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, roomId);
            pstmt.setString(2, checkOut.toString());
            pstmt.setString(3, checkIn.toString());
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        }
    }

    public static boolean addRoom(String roomNumber, String type, double price) {
        String sql = "INSERT INTO rooms (room_number, type, price) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, roomNumber);
            pstmt.setString(2, type);
            pstmt.setDouble(3, price);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateRoom(int id, String roomNumber, String type, double price) {
        String sql = "UPDATE rooms SET room_number = ?, type = ?, price = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, roomNumber);
            pstmt.setString(2, type);
            pstmt.setDouble(3, price);
            pstmt.setInt(4, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteRoom(int id) {
        String checkSql = "SELECT COUNT(*) FROM bookings WHERE room_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        String sql = "DELETE FROM rooms WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isRoomBookedOnDate(int roomId, LocalDate date) {
        String sql = "SELECT COUNT(*) FROM bookings WHERE room_id = ? AND check_in <= ? AND check_out >= ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, roomId);
            pstmt.setString(2, date.toString());
            pstmt.setString(3, date.toString());
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static List<Booking> getAllBookings() {
        List<Booking> bookings = new ArrayList<>();
        String sql = "SELECT b.*, r.room_number FROM bookings b JOIN rooms r ON b.room_id = r.id ORDER BY b.id";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                bookings.add(new Booking(
                        rs.getInt("id"),
                        rs.getString("guest_name"),
                        rs.getString("phone"),
                        rs.getInt("room_id"),
                        rs.getString("room_number"),
                        rs.getString("check_in"),
                        rs.getString("check_out"),
                        rs.getString("booked_by")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return bookings;
    }

    public static boolean isOverlappingBooking(int roomId, String checkIn, String checkOut, int excludeBookingId) {
        String sql = "SELECT COUNT(*) FROM bookings WHERE room_id = ? AND id != ? AND check_in < ? AND check_out > ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, roomId);
            pstmt.setInt(2, excludeBookingId);
            pstmt.setString(3, checkOut);
            pstmt.setString(4, checkIn);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean addBooking(String guestName, String phone, int roomId, String checkIn, String checkOut, String bookedBy) {
        if (isOverlappingBooking(roomId, checkIn, checkOut, -1)) return false;
        String sql = "INSERT INTO bookings (guest_name, phone, room_id, check_in, check_out, booked_by) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guestName);
            pstmt.setString(2, phone);
            pstmt.setInt(3, roomId);
            pstmt.setString(4, checkIn);
            pstmt.setString(5, checkOut);
            pstmt.setString(6, bookedBy);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateBooking(int id, String guestName, String phone, int roomId, String checkIn, String checkOut) {
        if (isOverlappingBooking(roomId, checkIn, checkOut, id)) return false;
        String sql = "UPDATE bookings SET guest_name = ?, phone = ?, room_id = ?, check_in = ?, check_out = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, guestName);
            pstmt.setString(2, phone);
            pstmt.setInt(3, roomId);
            pstmt.setString(4, checkIn);
            pstmt.setString(5, checkOut);
            pstmt.setInt(6, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteBooking(int id) {
        String sql = "DELETE FROM bookings WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY id";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(new User(rs.getInt("id"), rs.getString("username"), rs.getString("password"), rs.getString("role")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    public static boolean addUser(String username, String password, String role) {
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, role);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateUser(int id, String username, String password, String role) {
        String sql = "UPDATE users SET username = ?, password = ?, role = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, role);
            pstmt.setInt(4, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteUser(int id) {
        String checkSql = "SELECT COUNT(*) FROM users WHERE role = 'admin'";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) <= 1) {
                User user = getUserById(id);
                if (user != null && user.getRole().equals("admin")) return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static User getUserById(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new User(rs.getInt("id"), rs.getString("username"), rs.getString("password"), rs.getString("role"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int getTotalRooms() {
        String sql = "SELECT COUNT(*) FROM rooms";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static int getTotalBookings() {
        String sql = "SELECT COUNT(*) FROM bookings";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static int getTotalUsers() {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}

// ---------- MODEL CLASSES ----------
class User {
    private int id;
    private String username;
    private String password;
    private String role;
    public User(int id, String username, String password, String role) {
        this.id = id; this.username = username; this.password = password; this.role = role;
    }
    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getRole() { return role; }
}

class Room {
    private int id;
    private String roomNumber;
    private String type;
    private double price;
    public Room(int id, String roomNumber, String type, double price) {
        this.id = id; this.roomNumber = roomNumber; this.type = type; this.price = price;
    }
    public int getId() { return id; }
    public String getRoomNumber() { return roomNumber; }
    public String getType() { return type; }
    public double getPrice() { return price; }
}

class Booking {
    private int id;
    private String guestName;
    private String phone;
    private int roomId;
    private String roomNumber;
    private String checkIn;
    private String checkOut;
    private String bookedBy;
    public Booking(int id, String guestName, String phone, int roomId, String roomNumber, String checkIn, String checkOut, String bookedBy) {
        this.id = id; this.guestName = guestName; this.phone = phone; this.roomId = roomId;
        this.roomNumber = roomNumber; this.checkIn = checkIn; this.checkOut = checkOut; this.bookedBy = bookedBy;
    }
    public int getId() { return id; }
    public String getGuestName() { return guestName; }
    public String getPhone() { return phone; }
    public int getRoomId() { return roomId; }
    public String getRoomNumber() { return roomNumber; }
    public String getCheckIn() { return checkIn; }
    public String getCheckOut() { return checkOut; }
    public String getBookedBy() { return bookedBy; }
}

// ---------- UTILITY: Digit-only DocumentFilter with max length 10 ----------
class DigitPhoneFilter extends DocumentFilter {
    private static final int MAX_LENGTH = 10;

    @Override
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
        if (string != null && string.matches("\\d*")) {
            int newLength = fb.getDocument().getLength() + string.length();
            if (newLength <= MAX_LENGTH) {
                super.insertString(fb, offset, string, attr);
            }
        }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
        if (text != null && text.matches("\\d*")) {
            int newLength = fb.getDocument().getLength() - length + text.length();
            if (newLength <= MAX_LENGTH) {
                super.replace(fb, offset, length, text, attrs);
            }
        }
    }
}

// ---------- LOGIN FRAME ----------
class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JCheckBox showPasswordCheckBox;

    public LoginFrame() {
        setTitle("Hotel Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 350);
        setLocationRelativeTo(null);
        setResizable(false);

        DatabaseManager.initializeDatabase();

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(new Color(240, 248, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel titleLabel = new JLabel("Hotel Management System");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(new Color(25, 25, 112));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        mainPanel.add(titleLabel, gbc);

        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        mainPanel.add(userLabel, gbc);

        usernameField = new JTextField(15);
        gbc.gridx = 1; gbc.gridy = 1;
        mainPanel.add(usernameField, gbc);

        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 0; gbc.gridy = 2;
        mainPanel.add(passLabel, gbc);

        passwordField = new JPasswordField(15);
        gbc.gridx = 1; gbc.gridy = 2;
        mainPanel.add(passwordField, gbc);

        showPasswordCheckBox = new JCheckBox("Show Password");
        showPasswordCheckBox.addActionListener(e -> {
            if (showPasswordCheckBox.isSelected()) {
                passwordField.setEchoChar((char) 0);
            } else {
                passwordField.setEchoChar('*');
            }
        });
        gbc.gridx = 1; gbc.gridy = 3;
        mainPanel.add(showPasswordCheckBox, gbc);

        JButton loginButton = new JButton("Login");
        loginButton.setBackground(new Color(70, 130, 200)); // blue background
        loginButton.setForeground(Color.BLACK);            // black text
        loginButton.setFocusPainted(false);
        loginButton.addActionListener(this::performLogin);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        mainPanel.add(loginButton, gbc);

        JLabel footerLabel = new JLabel("© 2026 Created by Swapnil Rathod");
        footerLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        footerLabel.setForeground(Color.GRAY);
        gbc.gridx = 0; gbc.gridy = 5;
        mainPanel.add(footerLabel, gbc);

        add(mainPanel);
    }

    private void performLogin(ActionEvent e) {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter username and password", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            User user = DatabaseManager.authenticate(username, password);
            if (user != null) {
                dispose();
                if (user.getRole().equals("admin"))
                    new AdminDashboardFrame(user).setVisible(true);
                else
                    new StaffDashboardFrame(user).setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "Invalid username or password", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Login error:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

// ---------- ADMIN DASHBOARD ----------
class AdminDashboardFrame extends JFrame {
    private User currentUser;

    public AdminDashboardFrame(User user) {
        this.currentUser = user;
        setTitle("Hotel System - Admin Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(25, 25, 112));
        header.setBorder(new EmptyBorder(10, 20, 10, 20));
        JLabel titleLabel = new JLabel("Hotel System");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        header.add(titleLabel, BorderLayout.WEST);

        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        userPanel.setOpaque(false);
        JLabel userLabel = new JLabel(currentUser.getUsername());
        userLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        userLabel.setForeground(Color.WHITE);
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
        userPanel.add(userLabel);
        userPanel.add(logoutBtn);
        header.add(userPanel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Dashboard", new AdminDashboardPanel(this));
        tabbedPane.addTab("Rooms", new RoomsPanel(this));
        tabbedPane.addTab("Bookings", new BookingsPanel(this, currentUser));
        tabbedPane.addTab("Users", new UsersPanel(this));
        add(tabbedPane, BorderLayout.CENTER);

        JLabel footer = new JLabel("© 2026 Created by Swapnil Rathod", SwingConstants.CENTER);
        footer.setFont(new Font("Arial", Font.PLAIN, 10));
        footer.setForeground(Color.GRAY);
        footer.setBorder(new EmptyBorder(5, 0, 5, 0));
        add(footer, BorderLayout.SOUTH);
    }

    public void refreshDashboard() { ((AdminDashboardPanel)((JTabbedPane)getContentPane().getComponent(1)).getComponentAt(0)).refreshStats(); }
    public void refreshRooms() { ((RoomsPanel)((JTabbedPane)getContentPane().getComponent(1)).getComponentAt(1)).refreshTable(); }
    public void refreshBookings() { ((BookingsPanel)((JTabbedPane)getContentPane().getComponent(1)).getComponentAt(2)).refreshTable(); refreshDashboard(); }
    public void refreshUsers() { ((UsersPanel)((JTabbedPane)getContentPane().getComponent(1)).getComponentAt(3)).refreshTable(); refreshDashboard(); }
}

// ---------- STAFF DASHBOARD ----------
class StaffDashboardFrame extends JFrame {
    private User currentUser;

    public StaffDashboardFrame(User user) {
        this.currentUser = user;
        setTitle("Hotel System - Staff Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(25, 25, 112));
        header.setBorder(new EmptyBorder(10, 20, 10, 20));
        JLabel titleLabel = new JLabel("Hotel System");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        header.add(titleLabel, BorderLayout.WEST);

        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        userPanel.setOpaque(false);
        JLabel userLabel = new JLabel(currentUser.getUsername());
        userLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        userLabel.setForeground(Color.WHITE);
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
        userPanel.add(userLabel);
        userPanel.add(logoutBtn);
        header.add(userPanel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Dashboard", new StaffDashboardPanel(this));
        tabbedPane.addTab("Bookings", new BookingsPanel(this, currentUser));
        add(tabbedPane, BorderLayout.CENTER);

        JLabel footer = new JLabel("© 2026 Created by Swapnil Rathod", SwingConstants.CENTER);
        footer.setFont(new Font("Arial", Font.PLAIN, 10));
        footer.setForeground(Color.GRAY);
        footer.setBorder(new EmptyBorder(5, 0, 5, 0));
        add(footer, BorderLayout.SOUTH);
    }

    public void refreshDashboard() { ((StaffDashboardPanel)((JTabbedPane)getContentPane().getComponent(1)).getComponentAt(0)).refreshStats(); }
    public void refreshBookings() { ((BookingsPanel)((JTabbedPane)getContentPane().getComponent(1)).getComponentAt(1)).refreshTable(); refreshDashboard(); }
}

// ---------- ADMIN DASHBOARD PANEL (stats with different colors) ----------
class AdminDashboardPanel extends JPanel {
    private JLabel totalRoomsLabel, totalBookingsLabel, totalUsersLabel;
    private AdminDashboardFrame parent;

    public AdminDashboardPanel(AdminDashboardFrame parent) {
        this.parent = parent;
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Dashboard Overview");
        title.setFont(new Font("Arial", Font.BOLD, 22));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        add(title, gbc);

        // Cards with different background colors
        totalRoomsLabel = createStatCard("Total Rooms", "0", "Manage", new Color(173, 216, 230), gbc, 1);   // light blue
        totalBookingsLabel = createStatCard("Total Bookings", "0", "View", new Color(144, 238, 144), gbc, 2); // light green
        totalUsersLabel = createStatCard("Total Users", "0", "Manage", new Color(255, 255, 153), gbc, 3);     // light yellow
        refreshStats();
    }

    private JLabel createStatCard(String title, String value, String btnText, Color bgColor, GridBagConstraints gbc, int col) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(bgColor);
        card.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));
        card.setPreferredSize(new Dimension(200, 120));

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titleLabel.setBorder(new EmptyBorder(10, 0, 5, 0));
        card.add(titleLabel, BorderLayout.NORTH);

        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(new Font("Arial", Font.BOLD, 28));
        valueLabel.setForeground(new Color(70, 130, 200));
        card.add(valueLabel, BorderLayout.CENTER);

        JButton actionBtn = new JButton(btnText);
        actionBtn.addActionListener(e -> {
            JTabbedPane tabs = (JTabbedPane) parent.getContentPane().getComponent(1);
            switch (title) {
                case "Total Rooms": tabs.setSelectedIndex(1); break;
                case "Total Bookings": tabs.setSelectedIndex(2); break;
                case "Total Users": tabs.setSelectedIndex(3); break;
            }
        });
        card.add(actionBtn, BorderLayout.SOUTH);

        gbc.gridx = col; gbc.gridy = 1;
        add(card, gbc);
        return valueLabel;
    }

    public void refreshStats() {
        totalRoomsLabel.setText(String.valueOf(DatabaseManager.getTotalRooms()));
        totalBookingsLabel.setText(String.valueOf(DatabaseManager.getTotalBookings()));
        totalUsersLabel.setText(String.valueOf(DatabaseManager.getTotalUsers()));
    }
}

// ---------- STAFF DASHBOARD PANEL ----------
class StaffDashboardPanel extends JPanel {
    private JLabel totalRoomsLabel, totalBookingsLabel;
    private StaffDashboardFrame parent;

    public StaffDashboardPanel(StaffDashboardFrame parent) {
        this.parent = parent;
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Staff Dashboard");
        title.setFont(new Font("Arial", Font.BOLD, 22));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        add(title, gbc);

        totalRoomsLabel = createStatCard("Total Rooms", "0", "View", new Color(173, 216, 230), gbc, 1);
        totalBookingsLabel = createStatCard("Total Bookings", "0", "Manage", new Color(144, 238, 144), gbc, 2);
        refreshStats();
    }

    private JLabel createStatCard(String title, String value, String btnText, Color bgColor, GridBagConstraints gbc, int col) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(bgColor);
        card.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));
        card.setPreferredSize(new Dimension(200, 120));

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titleLabel.setBorder(new EmptyBorder(10, 0, 5, 0));
        card.add(titleLabel, BorderLayout.NORTH);

        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(new Font("Arial", Font.BOLD, 28));
        valueLabel.setForeground(new Color(70, 130, 200));
        card.add(valueLabel, BorderLayout.CENTER);

        JButton actionBtn = new JButton(btnText);
        actionBtn.addActionListener(e -> {
            if (title.equals("Total Bookings")) {
                JTabbedPane tabs = (JTabbedPane) parent.getContentPane().getComponent(1);
                tabs.setSelectedIndex(1);
            }
        });
        card.add(actionBtn, BorderLayout.SOUTH);

        gbc.gridx = col; gbc.gridy = 1;
        add(card, gbc);
        return valueLabel;
    }

    public void refreshStats() {
        totalRoomsLabel.setText(String.valueOf(DatabaseManager.getTotalRooms()));
        totalBookingsLabel.setText(String.valueOf(DatabaseManager.getTotalBookings()));
    }
}

// ---------- ROOMS PANEL ----------
class RoomsPanel extends JPanel {
    private JTable roomTable;
    private RoomTableModel tableModel;
    private JFrame parentFrame;

    public RoomsPanel(JFrame parent) {
        this.parentFrame = parent;
        setLayout(new BorderLayout(10, 10));
        setBackground(Color.WHITE);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Add New Room"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(new JLabel("Room Number:"), gbc);
        JTextField roomNumberField = new JTextField(10);
        gbc.gridx = 1; formPanel.add(roomNumberField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; formPanel.add(new JLabel("Room Type:"), gbc);
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Single", "Double", "Triple", "Suite"});
        gbc.gridx = 1; formPanel.add(typeCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2; formPanel.add(new JLabel("Price:"), gbc);
        JTextField priceField = new JTextField(10);
        gbc.gridx = 1; formPanel.add(priceField, gbc);

        JButton addButton = new JButton("Add Room");
        addButton.addActionListener(e -> {
            try {
                String number = roomNumberField.getText().trim();
                String type = (String) typeCombo.getSelectedItem();
                double price = Double.parseDouble(priceField.getText().trim());
                if (DatabaseManager.addRoom(number, type, price)) {
                    refreshTable();
                    roomNumberField.setText("");
                    priceField.setText("");
                    JOptionPane.showMessageDialog(this, "Room added");
                    if (parentFrame instanceof AdminDashboardFrame) ((AdminDashboardFrame) parentFrame).refreshDashboard();
                } else JOptionPane.showMessageDialog(this, "Failed to add room");
            } catch (NumberFormatException ex) { JOptionPane.showMessageDialog(this, "Invalid price"); }
        });
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; formPanel.add(addButton, gbc);
        add(formPanel, BorderLayout.NORTH);

        tableModel = new RoomTableModel();
        roomTable = new JTable(tableModel);
        roomTable.setFillsViewportHeight(true);
        roomTable.setRowHeight(25);
        JScrollPane scrollPane = new JScrollPane(roomTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Room List"));
        add(scrollPane, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout());
        JButton editBtn = new JButton("Edit Selected");
        JButton deleteBtn = new JButton("Delete Selected");
        editBtn.addActionListener(e -> editRoom());
        deleteBtn.addActionListener(e -> deleteRoom());
        btnPanel.add(editBtn);
        btnPanel.add(deleteBtn);
        add(btnPanel, BorderLayout.SOUTH);
        refreshTable();
    }

    private void editRoom() {
        int row = roomTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a room to edit"); return; }
        Room room = tableModel.getRoomAt(row);
        JDialog dialog = new JDialog(parentFrame, "Edit Room", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("Room Number:"), gbc);
        JTextField numberField = new JTextField(room.getRoomNumber(), 10);
        gbc.gridx = 1; dialog.add(numberField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("Room Type:"), gbc);
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Single", "Double", "Triple", "Suite"});
        typeCombo.setSelectedItem(room.getType());
        gbc.gridx = 1; dialog.add(typeCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("Price:"), gbc);
        JTextField priceField = new JTextField(String.valueOf(room.getPrice()), 10);
        gbc.gridx = 1; dialog.add(priceField, gbc);

        JButton updateBtn = new JButton("Update Room");
        updateBtn.addActionListener(e -> {
            try {
                String number = numberField.getText().trim();
                String type = (String) typeCombo.getSelectedItem();
                double price = Double.parseDouble(priceField.getText().trim());
                if (DatabaseManager.updateRoom(room.getId(), number, type, price)) {
                    refreshTable();
                    dialog.dispose();
                    JOptionPane.showMessageDialog(this, "Room updated");
                    if (parentFrame instanceof AdminDashboardFrame) ((AdminDashboardFrame) parentFrame).refreshDashboard();
                } else JOptionPane.showMessageDialog(dialog, "Update failed");
            } catch (NumberFormatException ex) { JOptionPane.showMessageDialog(dialog, "Invalid price"); }
        });
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; dialog.add(updateBtn, gbc);
        dialog.pack();
        dialog.setLocationRelativeTo(parentFrame);
        dialog.setVisible(true);
    }

    private void deleteRoom() {
        int row = roomTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a room to delete"); return; }
        Room room = tableModel.getRoomAt(row);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete room " + room.getRoomNumber() + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            if (DatabaseManager.deleteRoom(room.getId())) {
                refreshTable();
                if (parentFrame instanceof AdminDashboardFrame) ((AdminDashboardFrame) parentFrame).refreshDashboard();
            } else JOptionPane.showMessageDialog(this, "Cannot delete room with existing bookings");
        }
    }

    public void refreshTable() { tableModel.refresh(); }

    class RoomTableModel extends AbstractTableModel {
        private String[] columns = {"ID", "Number", "Type", "Price", "Status"};
        private List<Room> rooms;
        private LocalDate today = LocalDate.now();
        public RoomTableModel() { refresh(); }
        public void refresh() { rooms = DatabaseManager.getAllRooms(); fireTableDataChanged(); }
        public Room getRoomAt(int row) { return rooms.get(row); }
        public int getRowCount() { return rooms.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int col) { return columns[col]; }
        public Object getValueAt(int row, int col) {
            Room room = rooms.get(row);
            switch (col) {
                case 0: return room.getId();
                case 1: return room.getRoomNumber();
                case 2: return room.getType();
                case 3: return String.format("₹%,.2f", room.getPrice());
                case 4: return DatabaseManager.isRoomBookedOnDate(room.getId(), today) ? "Booked" : "Available";
                default: return "";
            }
        }
    }
}

// ---------- BOOKINGS PANEL (with dynamic room filtering & phone max 10 digits) ----------
class BookingsPanel extends JPanel {
    private JTable bookingTable;
    private BookingTableModel tableModel;
    private JFrame parentFrame;
    private User currentUser;

    private JTextField nameField;
    private JTextField phoneField;
    private JComboBox<String> roomCombo;
    private JTextField checkInField;
    private JTextField checkOutField;

    public BookingsPanel(JFrame parent, User user) {
        this.parentFrame = parent;
        this.currentUser = user;
        setLayout(new BorderLayout(10, 10));
        setBackground(Color.WHITE);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Manage Bookings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(new JLabel("Guest Name:"), gbc);
        nameField = new JTextField(15);
        gbc.gridx = 1; formPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; formPanel.add(new JLabel("Phone:"), gbc);
        phoneField = new JTextField(15);
        ((AbstractDocument) phoneField.getDocument()).setDocumentFilter(new DigitPhoneFilter());
        gbc.gridx = 1; formPanel.add(phoneField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; formPanel.add(new JLabel("Select Room:"), gbc);
        roomCombo = new JComboBox<>();
        gbc.gridx = 1; formPanel.add(roomCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 3; formPanel.add(new JLabel("Check-in (yyyy-mm-dd):"), gbc);
        checkInField = new JTextField(LocalDate.now().toString(), 15);
        gbc.gridx = 1; formPanel.add(checkInField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; formPanel.add(new JLabel("Check-out (yyyy-mm-dd):"), gbc);
        checkOutField = new JTextField(LocalDate.now().plusDays(1).toString(), 15);
        gbc.gridx = 1; formPanel.add(checkOutField, gbc);

        checkInField.addFocusListener(new FocusAdapter() { public void focusLost(FocusEvent e) { refreshAvailableRooms(); } });
        checkOutField.addFocusListener(new FocusAdapter() { public void focusLost(FocusEvent e) { refreshAvailableRooms(); } });
        checkInField.addActionListener(e -> refreshAvailableRooms());
        checkOutField.addActionListener(e -> refreshAvailableRooms());

        JButton bookButton = new JButton("Book Room");
        bookButton.addActionListener(e -> addBooking());
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2; formPanel.add(bookButton, gbc);
        add(formPanel, BorderLayout.NORTH);

        tableModel = new BookingTableModel();
        bookingTable = new JTable(tableModel);
        bookingTable.setFillsViewportHeight(true);
        bookingTable.setRowHeight(25);
        JScrollPane scrollPane = new JScrollPane(bookingTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Booking List"));
        add(scrollPane, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout());
        JButton editBtn = new JButton("Edit Selected");
        JButton deleteBtn = new JButton("Delete Selected");
        editBtn.addActionListener(e -> editBooking());
        deleteBtn.addActionListener(e -> deleteBooking());
        btnPanel.add(editBtn);
        btnPanel.add(deleteBtn);
        add(btnPanel, BorderLayout.SOUTH);

        refreshTable();
        refreshAvailableRooms();
    }

    private void refreshAvailableRooms() {
        roomCombo.removeAllItems();
        LocalDate checkIn, checkOut;
        try {
            checkIn = LocalDate.parse(checkInField.getText().trim());
            checkOut = LocalDate.parse(checkOutField.getText().trim());
            if (checkOut.isBefore(checkIn) || checkOut.equals(checkIn)) {
                roomCombo.addItem("Invalid dates");
                return;
            }
        } catch (DateTimeParseException e) {
            roomCombo.addItem("Invalid date format");
            return;
        }
        List<Room> available = DatabaseManager.getAvailableRooms(checkIn, checkOut);
        if (available.isEmpty()) {
            roomCombo.addItem("No rooms available");
        } else {
            for (Room room : available) {
                roomCombo.addItem(room.getId() + " - " + room.getRoomNumber() + " (" + room.getType() + ")");
            }
        }
    }

    private void addBooking() {
        String guestName = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        if (guestName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Guest name cannot be empty");
            return;
        }
        if (phone.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Phone number cannot be empty");
            return;
        }
        String selected = (String) roomCombo.getSelectedItem();
        if (selected == null || selected.startsWith("Invalid") || selected.startsWith("No rooms")) {
            JOptionPane.showMessageDialog(this, "Please select a valid room");
            return;
        }
        int roomId = Integer.parseInt(selected.split(" - ")[0]);
        String checkIn = checkInField.getText().trim();
        String checkOut = checkOutField.getText().trim();

        try {
            LocalDate in = LocalDate.parse(checkIn);
            LocalDate out = LocalDate.parse(checkOut);
            if (out.isBefore(in) || out.equals(in)) {
                JOptionPane.showMessageDialog(this, "Check-out must be after check-in");
                return;
            }
            if (DatabaseManager.addBooking(guestName, phone, roomId, checkIn, checkOut, currentUser.getUsername())) {
                refreshTable();
                nameField.setText("");
                phoneField.setText("");
                checkInField.setText(LocalDate.now().toString());
                checkOutField.setText(LocalDate.now().plusDays(1).toString());
                refreshAvailableRooms();
                JOptionPane.showMessageDialog(this, "Booking added");
                if (parentFrame instanceof AdminDashboardFrame) ((AdminDashboardFrame) parentFrame).refreshBookings();
                else if (parentFrame instanceof StaffDashboardFrame) ((StaffDashboardFrame) parentFrame).refreshBookings();
            } else {
                JOptionPane.showMessageDialog(this, "Room is already booked for these dates");
                refreshAvailableRooms();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid date format. Use yyyy-mm-dd");
        }
    }

    private void editBooking() {
        int row = bookingTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a booking to edit"); return; }
        Booking booking = tableModel.getBookingAt(row);
        JDialog dialog = new JDialog(parentFrame, "Edit Booking", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("Guest Name:"), gbc);
        JTextField nameField = new JTextField(booking.getGuestName(), 15);
        gbc.gridx = 1; dialog.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("Phone:"), gbc);
        JTextField phoneField = new JTextField(booking.getPhone(), 15);
        ((AbstractDocument) phoneField.getDocument()).setDocumentFilter(new DigitPhoneFilter());
        gbc.gridx = 1; dialog.add(phoneField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("Select Room:"), gbc);
        JComboBox<String> roomCombo = new JComboBox<>();
        List<Room> allRooms = DatabaseManager.getAllRooms();
        int selectedIndex = 0;
        for (int i = 0; i < allRooms.size(); i++) {
            Room r = allRooms.get(i);
            roomCombo.addItem(r.getId() + " - " + r.getRoomNumber() + " (" + r.getType() + ")");
            if (r.getId() == booking.getRoomId()) selectedIndex = i;
        }
        roomCombo.setSelectedIndex(selectedIndex);
        gbc.gridx = 1; dialog.add(roomCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 3; dialog.add(new JLabel("Check-in (yyyy-mm-dd):"), gbc);
        JTextField checkInField = new JTextField(booking.getCheckIn(), 15);
        gbc.gridx = 1; dialog.add(checkInField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; dialog.add(new JLabel("Check-out (yyyy-mm-dd):"), gbc);
        JTextField checkOutField = new JTextField(booking.getCheckOut(), 15);
        gbc.gridx = 1; dialog.add(checkOutField, gbc);

        JButton updateBtn = new JButton("Update Booking");
        updateBtn.addActionListener(e -> {
            String guestName = nameField.getText().trim();
            String phone = phoneField.getText().trim();
            String selected = (String) roomCombo.getSelectedItem();
            int roomId = Integer.parseInt(selected.split(" - ")[0]);
            String checkIn = checkInField.getText().trim();
            String checkOut = checkOutField.getText().trim();
            try {
                LocalDate in = LocalDate.parse(checkIn);
                LocalDate out = LocalDate.parse(checkOut);
                if (out.isBefore(in) || out.equals(in)) throw new Exception();
                if (DatabaseManager.updateBooking(booking.getId(), guestName, phone, roomId, checkIn, checkOut)) {
                    refreshTable();
                    dialog.dispose();
                    JOptionPane.showMessageDialog(this, "Booking updated");
                    if (parentFrame instanceof AdminDashboardFrame) ((AdminDashboardFrame) parentFrame).refreshBookings();
                    else if (parentFrame instanceof StaffDashboardFrame) ((StaffDashboardFrame) parentFrame).refreshBookings();
                } else JOptionPane.showMessageDialog(dialog, "Room already booked for these dates");
            } catch (Exception ex) { JOptionPane.showMessageDialog(dialog, "Invalid date format or check-out after check-in"); }
        });
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2; dialog.add(updateBtn, gbc);
        dialog.pack();
        dialog.setLocationRelativeTo(parentFrame);
        dialog.setVisible(true);
    }

    private void deleteBooking() {
        int row = bookingTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a booking to delete"); return; }
        Booking booking = tableModel.getBookingAt(row);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete booking for " + booking.getGuestName() + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            DatabaseManager.deleteBooking(booking.getId());
            refreshTable();
            if (parentFrame instanceof AdminDashboardFrame) ((AdminDashboardFrame) parentFrame).refreshBookings();
            else if (parentFrame instanceof StaffDashboardFrame) ((StaffDashboardFrame) parentFrame).refreshBookings();
        }
    }

    public void refreshTable() { tableModel.refresh(); }

    class BookingTableModel extends AbstractTableModel {
        private String[] columns = {"ID", "Guest Name", "Phone", "Room", "Check-in", "Check-out", "Booked By"};
        private List<Booking> bookings;
        public BookingTableModel() { refresh(); }
        public void refresh() { bookings = DatabaseManager.getAllBookings(); fireTableDataChanged(); }
        public Booking getBookingAt(int row) { return bookings.get(row); }
        public int getRowCount() { return bookings.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int col) { return columns[col]; }
        public Object getValueAt(int row, int col) {
            Booking b = bookings.get(row);
            switch (col) {
                case 0: return b.getId();
                case 1: return b.getGuestName();
                case 2: return b.getPhone();
                case 3: return b.getRoomNumber();
                case 4: return b.getCheckIn();
                case 5: return b.getCheckOut();
                case 6: return b.getBookedBy();
                default: return "";
            }
        }
    }
}

// ---------- USERS PANEL (admin only) ----------
class UsersPanel extends JPanel {
    private JTable userTable;
    private UserTableModel tableModel;
    private AdminDashboardFrame parentFrame;

    public UsersPanel(AdminDashboardFrame parent) {
        this.parentFrame = parent;
        setLayout(new BorderLayout(10, 10));
        setBackground(Color.WHITE);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Add New User"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(new JLabel("Username:"), gbc);
        JTextField usernameField = new JTextField(15);
        gbc.gridx = 1; formPanel.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; formPanel.add(new JLabel("Password:"), gbc);
        JPasswordField passwordField = new JPasswordField(15);
        gbc.gridx = 1; formPanel.add(passwordField, gbc);

        JCheckBox showPasswordCheck = new JCheckBox("Show Password");
        showPasswordCheck.addActionListener(e -> {
            if (showPasswordCheck.isSelected()) passwordField.setEchoChar((char) 0);
            else passwordField.setEchoChar('*');
        });
        gbc.gridx = 1; gbc.gridy = 2;
        formPanel.add(showPasswordCheck, gbc);

        gbc.gridx = 0; gbc.gridy = 3; formPanel.add(new JLabel("Role:"), gbc);
        JComboBox<String> roleCombo = new JComboBox<>(new String[]{"staff", "admin"});
        gbc.gridx = 1; formPanel.add(roleCombo, gbc);

        JButton addButton = new JButton("Add User");
        addButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            String role = (String) roleCombo.getSelectedItem();
            if (username.isEmpty() || password.isEmpty()) { JOptionPane.showMessageDialog(this, "Please fill all fields"); return; }
            if (DatabaseManager.addUser(username, password, role)) {
                refreshTable();
                usernameField.setText("");
                passwordField.setText("");
                parentFrame.refreshDashboard();
            } else JOptionPane.showMessageDialog(this, "Username already exists");
        });
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; formPanel.add(addButton, gbc);
        add(formPanel, BorderLayout.NORTH);

        tableModel = new UserTableModel();
        userTable = new JTable(tableModel);
        userTable.setFillsViewportHeight(true);
        userTable.setRowHeight(25);
        JScrollPane scrollPane = new JScrollPane(userTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("User List"));
        add(scrollPane, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout());
        JButton editBtn = new JButton("Edit Selected");
        JButton deleteBtn = new JButton("Delete Selected");
        editBtn.addActionListener(e -> editUser());
        deleteBtn.addActionListener(e -> deleteUser());
        btnPanel.add(editBtn);
        btnPanel.add(deleteBtn);
        add(btnPanel, BorderLayout.SOUTH);
        refreshTable();
    }

    private void editUser() {
        int row = userTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a user to edit"); return; }
        User user = tableModel.getUserAt(row);
        JDialog dialog = new JDialog(parentFrame, "Edit User", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("Username:"), gbc);
        JTextField usernameField = new JTextField(user.getUsername(), 15);
        gbc.gridx = 1; dialog.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("Password:"), gbc);
        JPasswordField passwordField = new JPasswordField(user.getPassword(), 15);
        gbc.gridx = 1; dialog.add(passwordField, gbc);

        JCheckBox showPasswordCheck = new JCheckBox("Show Password");
        showPasswordCheck.addActionListener(e -> {
            if (showPasswordCheck.isSelected()) passwordField.setEchoChar((char) 0);
            else passwordField.setEchoChar('*');
        });
        gbc.gridx = 1; gbc.gridy = 2;
        dialog.add(showPasswordCheck, gbc);

        gbc.gridx = 0; gbc.gridy = 3; dialog.add(new JLabel("Role:"), gbc);
        JComboBox<String> roleCombo = new JComboBox<>(new String[]{"staff", "admin"});
        roleCombo.setSelectedItem(user.getRole());
        gbc.gridx = 1; dialog.add(roleCombo, gbc);

        JButton updateBtn = new JButton("Update User");
        updateBtn.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            String role = (String) roleCombo.getSelectedItem();
            if (username.isEmpty() || password.isEmpty()) { JOptionPane.showMessageDialog(dialog, "Please fill all fields"); return; }
            if (DatabaseManager.updateUser(user.getId(), username, password, role)) {
                refreshTable();
                dialog.dispose();
                parentFrame.refreshDashboard();
            } else JOptionPane.showMessageDialog(dialog, "Update failed");
        });
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; dialog.add(updateBtn, gbc);
        dialog.pack();
        dialog.setLocationRelativeTo(parentFrame);
        dialog.setVisible(true);
    }

    private void deleteUser() {
        int row = userTable.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select a user to delete"); return; }
        User user = tableModel.getUserAt(row);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete user " + user.getUsername() + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            if (DatabaseManager.deleteUser(user.getId())) {
                refreshTable();
                parentFrame.refreshDashboard();
            } else JOptionPane.showMessageDialog(this, "Cannot delete the last admin user");
        }
    }

    public void refreshTable() { tableModel.refresh(); }

    class UserTableModel extends AbstractTableModel {
        private String[] columns = {"ID", "Username", "Role"};
        private List<User> users;
        public UserTableModel() { refresh(); }
        public void refresh() { users = DatabaseManager.getAllUsers(); fireTableDataChanged(); }
        public User getUserAt(int row) { return users.get(row); }
        public int getRowCount() { return users.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int col) { return columns[col]; }
        public Object getValueAt(int row, int col) {
            User u = users.get(row);
            switch (col) {
                case 0: return u.getId();
                case 1: return u.getUsername();
                case 2: return u.getRole();
                default: return "";
            }
        }
    }
}