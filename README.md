# Hotel Management System

A complete Java desktop application for managing hotel rooms, bookings, and users. Built with **Java Swing** for the GUI and **SQLite** for the database. Includes separate dashboards for **Admin** and **Staff** roles.

## Folder Structure
```bash

hotel-management-system/
├── HotelManagementSystem.java
│   sqlite-jdbc-3.42.0.0.jar                       
└── README.md
```

## Features

### Admin Dashboard
- **Dashboard Overview** – Total rooms, bookings, users (colour‑coded stat cards: blue, green, yellow).
- **Room Management** – Add, edit, delete rooms. Room status (Available/Booked) is calculated automatically based on current date.
- **Booking Management** – Create, edit, delete bookings.  
  - Room dropdown shows **only rooms available** for the selected date range.  
  - Phone number field accepts **only digits** (max 10 characters).  
  - Prevents double‑booking for overlapping dates.
- **User Management** – Add, edit, delete staff/admin users.  
  - “Show Password” checkbox for password fields.

### Staff Dashboard
- Limited access: Dashboard (room & booking counts) and Bookings management (create, edit, delete bookings).

### Login Screen
- Admin default credentials: `admin` / `admin`
- “Show Password” checkbox.

### Database
- **SQLite** – automatically creates `hotel.db` on first run.
- Sample data (rooms, bookings) is inserted automatically.

## Screenshots
- **Login page** 

![Login page](screenshots/login.png)


- **Admin Dashboard page** 

![Admin dashboard](screenshots/admin_dashboard.png)


- **Manage Users page** 

![Manage Users page](screenshots/admin_dashboard_users.png)


- **Manage Rooms page** 

![Manage Rooms page](screenshots/admin_dashboard_rooms.png)


- **Manage Rooms Edit page** 

![Manage Rooms edit page](screenshots/admin_dashboard_rooms_edit.png)


- **Manage Booking page** 

![Manage Booking page](screenshots/admin_dashboard_bookings.png)


- **Manage Bookings Edit page** 

![admin_dashboard_bookings_edit](screenshots/admin_dashboard_bookings_edits.png)


## **Intro how to setup after download**

[![YouTube Video]([youtube.png](https://i.ibb.co/gM07cZ5w/polotno-1.png))](https://youtu.be/lkSRut0UpIk?si=z_PMDQV2CZz7Chue)




## Requirements

- **Java JDK 8 or higher**
- **SQLite JDBC driver** (included as an external JAR)

## Setup & Installation

### 1. Download the SQLite JDBC Driver

Download `sqlite-jdbc-3.42.0.0.jar` from:  
[https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.42.0.0/sqlite-jdbc-3.42.0.0.jar](https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.42.0.0/sqlite-jdbc-3.42.0.0.jar)

Place the `.jar` file in the **same folder** as `HotelManagementSystem.java`.

### 2. Compile the Code

Open a terminal/command prompt in that folder.

**Path** 
```
C:\Users\swapnil\Downloads\HotelSystem
```

**Windows:**

```cmd
javac -cp ".;sqlite-jdbc-3.42.0.0.jar" HotelManagementSystem.java

java -cp ".;sqlite-jdbc-3.42.0.0.jar" HotelManagementSystem
```

**Path** 
```
/Users/apple/Downloads/HotelSystem
```

**macOS/Linux:**

```bash
javac -cp ".:sqlite-jdbc-3.42.0.0.jar" HotelManagementSystem.java

java -cp ".:sqlite-jdbc-3.42.0.0.jar" HotelManagementSystem

```
