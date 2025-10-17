package me.eeshe.watchfacescraper.model.scrapers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import me.eeshe.watchfacescraper.model.Watchface;

public class FaceAppsScraper extends Scraper {
  private static final Logger LOGGER = LoggerFactory.getLogger(FaceAppsScraper.class);

  private static final String FACE_APPS_API_URL = "https://api.facesapps.com/api/watchface/";
  private static final int FACE_APPS_API_PAGE = 0;
  private static final int FACE_APPS_API_LIMIT = 100;

  private static boolean USE_HEADLESS_BROWSER = true;

  public List<Watchface> scrape() {
    LOGGER.info("Scraping FaceApps watchfaces...");

    List<Watchface> watchfaces = new ArrayList<>();
    List<String> watchfaceUrls = fetchWatchfaceIds();
    if (watchfaceUrls.isEmpty()) {
      return watchfaces;
    }
    watchfaces.addAll(scrapeWatchfaces(watchfaceUrls));

    LOGGER.info("Finished scraping {} FaceApps watchfaces.", watchfaces.size());
    return watchfaces;
  }

  /**
   * Fetches and filters the URLs of FaceApps' free or coupon watchfaces.
   *
   * @return List of free or coupon watchface URLs.
   */
  private List<String> fetchWatchfaceIds() {
    List<String> watchfaceIds = new ArrayList<>();
    JsonNode watchfacesJson = filterPaidWatchfaces(fetchWathfaceListJson());
    if (watchfacesJson == null) {
      LOGGER.error("FaceApps watchface list is empty.");
      return watchfaceIds;
    }
    for (JsonNode watchfaceJson : watchfacesJson) {
      String watchfaceId = watchfaceJson.get("id").asText();
      if (watchfaceId == null) {
        continue;
      }
      watchfaceIds.add(watchfaceId);
    }
    return watchfaceIds;
  }

  /**
   * Fetches the JSON with the listed FaceApps watchfaces.
   *
   * @return JsonNode with the listed watchfaces. Null if the API call failed.
   */
  private JsonNode fetchWathfaceListJson() {
    LOGGER.info("Fetching FaceApps watchface list from its API...");
    String requestUrl = String.format(
        "%s?page=%d&limit=%d",
        FACE_APPS_API_URL,
        FACE_APPS_API_PAGE,
        FACE_APPS_API_LIMIT);

    JsonNode jsonNode = executeGetRequest(requestUrl);
    LOGGER.info("Fetched {} watchfaces.", jsonNode.size());

    return jsonNode;
  }

  /**
   * Filters the JSON nodes containing paid watchfaces.
   *
   * @param watchfacesJson JSON node to filter.
   * @return Filtered JSON node.
   */
  private JsonNode filterPaidWatchfaces(JsonNode watchfacesJson) {
    LOGGER.info("Filtering paid watchfaces...");
    if (watchfacesJson == null) {
      return null;
    }
    int initialWatchfaces = watchfacesJson.size();
    Iterator<JsonNode> jsonNodeIterator = watchfacesJson.iterator();
    while (jsonNodeIterator.hasNext()) {
      JsonNode jsonNode = jsonNodeIterator.next();

      String distributionType = jsonNode.get("distributionType").asText();
      if (!distributionType.equalsIgnoreCase("PAID")) {
        continue;
      }
      jsonNodeIterator.remove();
    }
    int finalWatchfaces = watchfacesJson.size();

    LOGGER.info("Filtered {} paid watchfaces. New total is {}.",
        (initialWatchfaces - finalWatchfaces), finalWatchfaces);
    return watchfacesJson;
  }

  private List<Watchface> scrapeWatchfaces(List<String> watchfaceIds) {
    LOGGER.info("Starting scraping {} watchfaces...", watchfaceIds.size());

    ExecutorService executorService = Executors.newFixedThreadPool(5);
    List<Future<Watchface>> watchfaceFutures = new ArrayList<>();
    for (String watchfaceUrl : watchfaceIds) {
      watchfaceFutures.add(executorService.submit(() -> scrapeWatchface(watchfaceUrl)));
    }
    List<Watchface> watchfaces = new ArrayList<>();
    for (Future<Watchface> watchfaceFuture : watchfaceFutures) {
      try {
        Watchface watchface = watchfaceFuture.get();
        if (watchface == null) {
          continue;
        }
        watchfaces.add(watchface);
      } catch (InterruptedException | ExecutionException e) {
        LOGGER.error("Error scraping watchface. Message: {}", e.getMessage());
      }
    }
    return watchfaces;
  }

  /**
   * Fetches and scrapes all required data of the passed Watchface ID.
   *
   * @param watchfaceId ID of the watchface to scrape.
   * @return Scraped Watchface. Null if it has invalid data.
   */
  private Watchface scrapeWatchface(String watchfaceId) {
    LOGGER.info("Scraping watchface: {}", watchfaceId);

    JsonNode watchfaceDataJson = fetchWatchfaceData(watchfaceId);
    String playStoreUrl = watchfaceDataJson.get("link").asText();
    boolean isFree = watchfaceDataJson.get("distributionType").asText().equals("FREE");
    int availableCoupons = isFree ? -1 : watchfaceDataJson.get("couponsCount").asInt();
    if (availableCoupons == 0) {
      return null;
    }
    List<String> imageUrls = scrapeWatchfaceImageUrls(playStoreUrl);
    return new Watchface(
        playStoreUrl,
        "https://facesapps.com/watchface/" + watchfaceId,
        imageUrls,
        isFree,
        availableCoupons);
  }

  private JsonNode fetchWatchfaceData(String watchfaceId) {
    return executeGetRequest(FACE_APPS_API_URL + watchfaceId);
  }

  /**
   * Executes a GET request to the specified URL.
   *
   * @param url URL to make the request to.
   * @return JSON response of the request.
   */
  private JsonNode executeGetRequest(String url) {
    HttpRequest request = HttpRequest.newBuilder()
        .GET()
        .uri(URI.create(url))
        .header("Accept", "application/json")
        .timeout(Duration.ofSeconds(15))
        .build();
    try {
      HttpResponse<String> response = HttpClient.newHttpClient().send(
          request,
          HttpResponse.BodyHandlers.ofString());
      int statusCode = response.statusCode();
      if (statusCode != 200) {
        LOGGER.error("FaceApps API call failed with status code {}. Response body: {}",
            statusCode,
            response.body());
        return null;
      }
      return new ObjectMapper().readTree(response.body());
    } catch (IOException e) {
      LOGGER.error("I/O error while calling API: {}. Message: {}", url, e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.error("API call was interruped. Message: {}", e.getMessage());
    }
    return null;
  }

  /**
   * Scrapes the watchface images from the passed PlayStore URL.
   *
   * @param playStoreUrl PlayStore URL to scrape the images from.
   * @return Scraped images.
   */
  private List<String> scrapeWatchfaceImageUrls(String playStoreUrl) {
    List<String> imageUrls = new ArrayList<>();
    try (Playwright playwright = Playwright.create();
        Browser browser = launchBrowser(playwright, USE_HEADLESS_BROWSER);
        Page page = browser.newPage()) {
      page.navigate(playStoreUrl);

      String cssSelector = "img.T75of.B5GQxf";
      page.waitForSelector(cssSelector);

      for (Locator watchfaceImage : page.locator(cssSelector).all()) {
        String imageUrl = watchfaceImage.getAttribute("src");
        imageUrls.add(imageUrl);
      }
    }
    return imageUrls;
  }
}
