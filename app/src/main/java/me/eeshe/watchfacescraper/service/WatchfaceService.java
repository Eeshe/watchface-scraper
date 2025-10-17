package me.eeshe.watchfacescraper.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import me.eeshe.watchfacescraper.database.SQLiteManager;
import me.eeshe.watchfacescraper.model.Watchface;

public class WatchfaceService {
  private static final Logger LOGGER = LoggerFactory.getLogger(WatchfaceService.class);
  private static final String WATCHFACES_TABLE_NAME = "watchfaces";

  private final SQLiteManager sqLiteManager;

  public WatchfaceService(SQLiteManager sqLiteManager) {
    this.sqLiteManager = sqLiteManager;
  }

  /**
   * Creates all the needed tables within the SQLite database.
   */
  public void createTables() {
    createWatchfacesTable();
  }

  /**
   * Creates the table that will store scraped Watchfaces.
   */
  private void createWatchfacesTable() {
    String createTableSQL = "CREATE TABLE IF NOT EXISTS " + WATCHFACES_TABLE_NAME + " (" +
        "playStoreUrl TEXT PRIMARY KEY NOT NULL," +
        "scrapeTimeMillis INTEGER NOT NULL," +
        "couponsUrl TEXT," +
        "imageUrls TEXT," +
        "isFree INTEGER NOT NULL," +
        "remainingCoupons INTEGER NOT NULL" +
        ");";

    try (Connection connection = sqLiteManager.getConnection();
        PreparedStatement statement = connection.prepareStatement(createTableSQL)) { // Using Statement for DDL
      statement.executeUpdate();
      LOGGER.info("Table '{}' created or already exists.", WATCHFACES_TABLE_NAME);
    } catch (SQLException e) {
      LOGGER.error("Error creating table '{}': {}", WATCHFACES_TABLE_NAME, e.getMessage(), e);
    }
  }

  /**
   * Writes the passed List of watchfaces to the database.
   *
   * @param watchfaces List of Watchfaces to write.
   */
  public void writeWatchfaces(List<Watchface> watchfaces) {
    if (watchfaces == null || watchfaces.isEmpty()) {
      LOGGER.info("No watchfaces provided to write.");
      return;
    }
    LOGGER.info("Writing {} watchfaces to the database...", watchfaces.size());
    String insertSQL = "INSERT OR REPLACE INTO " + WATCHFACES_TABLE_NAME +
        " (playStoreUrl, scrapeTimeMillis, couponsUrl, imageUrls, isFree, remainingCoupons)" +
        " VALUES (?, ?, ?, ?, ?, ?);";

    try (Connection connection = sqLiteManager.getConnection();
        PreparedStatement preparedStatement = connection.prepareStatement(insertSQL)) {

      final int batchSize = 1000; // Define a batch size for efficiency
      int count = 0;

      for (Watchface watchface : watchfaces) {
        preparedStatement.setString(1, watchface.getPlayStoreUrl());
        preparedStatement.setLong(2, watchface.getScrapeTimeMillis());
        preparedStatement.setString(3, watchface.getCouponsUrl());
        preparedStatement.setString(4, serializeImageUrls(watchface.getImageUrls())); // Serialize list to JSON
        preparedStatement.setInt(5, watchface.isFree() ? 1 : 0); // Convert boolean to int (0 or 1)
        preparedStatement.setInt(6, watchface.getRemainingCoupons());

        preparedStatement.addBatch();
        count++;

        // Execute batch and clear if size reached
        if (count % batchSize == 0) {
          preparedStatement.executeBatch();
          LOGGER.debug("Executed batch of {} watchfaces.", batchSize);
          preparedStatement.clearBatch();
        }
      }

      // Execute any remaining items in the batch
      if (count > 0 && count % batchSize != 0) {
        preparedStatement.executeBatch();
        LOGGER.debug("Executed final batch of {} watchfaces.", count % batchSize);
      }
      LOGGER.info("Successfully wrote/updated {} watchfaces in table '{}'.", count, WATCHFACES_TABLE_NAME);
    } catch (SQLException e) {
      LOGGER.error("Error writing watchfaces to table '{}': {}", WATCHFACES_TABLE_NAME, e.getMessage(), e);
    }
  }

  private String serializeImageUrls(List<String> imageUrls) {
    if (imageUrls == null || imageUrls.isEmpty()) {
      return "[]"; // Store an empty JSON array for null/empty lists
    }
    try {
      return new ObjectMapper().writeValueAsString(imageUrls);
    } catch (JsonProcessingException e) {
      LOGGER.error("Error serializing image URLs to JSON: {}", e.getMessage());
      return "[]"; // Fallback to empty JSON array on error
    }
  }
}
