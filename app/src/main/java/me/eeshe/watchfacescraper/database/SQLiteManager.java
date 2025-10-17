package me.eeshe.watchfacescraper.database;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SQLiteManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(SQLiteManager.class);
  private static final String DATABASE_FILE_PATH = "database.db";

  public SQLiteManager() {
    File file = new File(DATABASE_FILE_PATH);
    if (file.exists()) {
      return;
    }
    try {
      file.createNewFile();
      LOGGER.info("Successfully created database file '{}'", DATABASE_FILE_PATH);
    } catch (IOException e) {
      LOGGER.error("Couldn't create database file. Message: {}", e.getMessage());
    }
  }

  public Connection getConnection() throws SQLException {
    try {
      return DriverManager.getConnection("jdbc:sqlite:" + DATABASE_FILE_PATH);
    } catch (SQLException e) {
      LOGGER.error("Failed to connect to the SQLite database. Message: {}", e.getMessage());
      throw e;
    }
  }
}
