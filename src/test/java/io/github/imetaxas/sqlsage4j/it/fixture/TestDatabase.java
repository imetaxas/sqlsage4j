package io.github.imetaxas.sqlsage4j.it.fixture;

import io.github.imetaxas.sqlsage4j.db.SQLiteConnector;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.sqlite.SQLiteDataSource;

/**
 * Reusable SQLite test fixture providing a pre-populated e-commerce database. Create via factory
 * methods, use the connector for end-to-end pipeline tests.
 */
public final class TestDatabase implements AutoCloseable {

  private final File dbFile;
  private final SQLiteDataSource dataSource;
  private final SQLiteConnector connector;

  private TestDatabase(File dbFile, SQLiteDataSource dataSource) {
    this.dbFile = dbFile;
    this.dataSource = dataSource;
    this.connector = new SQLiteConnector(dataSource);
  }

  public SQLiteConnector connector() {
    return connector;
  }

  public SQLiteDataSource dataSource() {
    return dataSource;
  }

  @Override
  public void close() {
    if (dbFile != null && dbFile.exists()) {
      dbFile.delete();
    }
  }

  /** Creates an e-commerce database with products, customers, orders, and order_items tables. */
  public static TestDatabase ecommerce() {
    TestDatabase db = create();
    db.exec(
        """
        CREATE TABLE products (
          product_id INTEGER PRIMARY KEY,
          name TEXT NOT NULL,
          category TEXT NOT NULL,
          price REAL NOT NULL,
          stock_quantity INTEGER DEFAULT 0
        )""",
        """
        CREATE TABLE customers (
          customer_id INTEGER PRIMARY KEY,
          name TEXT NOT NULL,
          email TEXT,
          country TEXT,
          signup_date TEXT
        )""",
        """
        CREATE TABLE orders (
          order_id INTEGER PRIMARY KEY,
          customer_id INTEGER REFERENCES customers(customer_id),
          order_date TEXT NOT NULL,
          total_amount REAL NOT NULL,
          status TEXT DEFAULT 'pending'
        )""",
        """
        CREATE TABLE order_items (
          item_id INTEGER PRIMARY KEY,
          order_id INTEGER REFERENCES orders(order_id),
          product_id INTEGER REFERENCES products(product_id),
          quantity INTEGER NOT NULL,
          unit_price REAL NOT NULL
        )""");
    db.exec(
        "INSERT INTO products VALUES (1, 'Laptop', 'Electronics', 999.99, 50)",
        "INSERT INTO products VALUES (2, 'Mouse', 'Electronics', 29.99, 200)",
        "INSERT INTO products VALUES (3, 'Desk', 'Furniture', 249.00, 30)",
        "INSERT INTO products VALUES (4, 'Notebook', 'Stationery', 4.99, 500)",
        "INSERT INTO products VALUES (5, 'Monitor', 'Electronics', 399.99, 8)",
        "INSERT INTO customers VALUES (1, 'Alice', 'alice@example.com', 'US', '2023-01-15')",
        "INSERT INTO customers VALUES (2, 'Bob', 'bob@example.com', 'UK', '2023-03-22')",
        "INSERT INTO customers VALUES (3, 'Charlie', 'charlie@example.com', 'US', '2023-06-10')",
        "INSERT INTO orders VALUES (1, 1, '2024-01-10', 1029.98, 'completed')",
        "INSERT INTO orders VALUES (2, 1, '2024-02-14', 29.99, 'completed')",
        "INSERT INTO orders VALUES (3, 2, '2024-01-20', 249.00, 'completed')",
        "INSERT INTO orders VALUES (4, 3, '2024-03-01', 4.99, 'pending')",
        "INSERT INTO orders VALUES (5, 2, '2024-03-15', 399.99, 'completed')",
        "INSERT INTO order_items VALUES (1, 1, 1, 1, 999.99)",
        "INSERT INTO order_items VALUES (2, 1, 2, 1, 29.99)",
        "INSERT INTO order_items VALUES (3, 2, 2, 1, 29.99)",
        "INSERT INTO order_items VALUES (4, 3, 3, 1, 249.00)",
        "INSERT INTO order_items VALUES (5, 4, 4, 1, 4.99)",
        "INSERT INTO order_items VALUES (6, 5, 5, 1, 399.99)");
    return db;
  }

  /** Creates a minimal analytics database with events and users. */
  public static TestDatabase analytics() {
    TestDatabase db = create();
    db.exec(
        """
        CREATE TABLE users (
          user_id INTEGER PRIMARY KEY,
          username TEXT NOT NULL,
          status TEXT DEFAULT 'active',
          last_login TEXT,
          signup_date TEXT
        )""",
        """
        CREATE TABLE events (
          event_id INTEGER PRIMARY KEY,
          user_id INTEGER REFERENCES users(user_id),
          event_type TEXT NOT NULL,
          event_date TEXT NOT NULL,
          properties TEXT
        )""");
    db.exec(
        "INSERT INTO users VALUES (1, 'alice', 'active', '2024-03-14', '2023-01-01')",
        "INSERT INTO users VALUES (2, 'bob', 'active', '2024-03-10', '2023-02-15')",
        "INSERT INTO users VALUES (3, 'charlie', 'inactive', '2023-12-01', '2023-03-20')",
        "INSERT INTO events VALUES (1, 1, 'login', '2024-03-14', NULL)",
        "INSERT INTO events VALUES (2, 1, 'purchase', '2024-03-14', '{\"amount\": 49.99}')",
        "INSERT INTO events VALUES (3, 2, 'login', '2024-03-10', NULL)",
        "INSERT INTO events VALUES (4, 2, 'page_view', '2024-03-10', '{\"page\": \"/products\"}')",
        "INSERT INTO events VALUES (5, 3, 'login', '2023-12-01', NULL)");
    return db;
  }

  private static TestDatabase create() {
    try {
      File file = File.createTempFile("sqlsage4j_fixture_", ".db");
      file.deleteOnExit();
      SQLiteDataSource ds = new SQLiteDataSource();
      ds.setUrl("jdbc:sqlite:" + file.getAbsolutePath());
      return new TestDatabase(file, ds);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create test database", e);
    }
  }

  private void exec(String... statements) {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      for (String sql : statements) {
        stmt.execute(sql);
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to execute fixture SQL", e);
    }
  }
}
