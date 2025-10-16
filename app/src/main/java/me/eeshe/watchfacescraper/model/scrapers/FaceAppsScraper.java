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
import com.microsoft.playwright.Locator.WaitForOptions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;

import me.eeshe.watchfacescraper.model.Watchface;

public class FaceAppsScraper extends Scraper {
  private static final Logger LOGGER = LoggerFactory.getLogger(FaceAppsScraper.class);

  private static final String FACE_APPS_API_URL = "https://api.facesapps.com/api/watchface";
  private static final int FACE_APPS_API_PAGE = 0;
  private static final int FACE_APPS_API_LIMIT = 10;

  private static boolean USE_HEADLESS_BROWSER = true;

  public List<Watchface> scrape() {
    LOGGER.info("Scraping FaceApps watchfaces...");

    List<Watchface> watchfaces = new ArrayList<>();
    List<String> watchfaceUrls = fetchWatchfaceUrls();
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
  private List<String> fetchWatchfaceUrls() {
    List<String> watchfaceUrls = new ArrayList<>();
    JsonNode watchfacesJson = filterPaidWatchfaces(fetchWathfacesJson());
    if (watchfacesJson == null) {
      LOGGER.error("FaceApps watchface list is empty.");
      return watchfaceUrls;
    }
    final String faceAppsUrl = "https://facesapps.com/watchface/";
    for (JsonNode watchfaceJson : watchfacesJson) {
      String watchfaceId = watchfaceJson.get("id").asText();
      if (watchfaceId == null) {
        continue;
      }
      watchfaceUrls.add(faceAppsUrl + watchfaceId);
    }
    return watchfaceUrls;
  }

  /**
   * Fetches the JSON with the listed FaceApps watchfaces.
   *
   * @return JsonNode with the listed watchfaces. Null if the API call failed.
   */
  private JsonNode fetchWathfacesJson() {
    LOGGER.info("Fetching FaceApps watchfaces from its API...");
    String requestUrl = String.format(
        "%s?page=%d&limit=%d",
        FACE_APPS_API_URL,
        FACE_APPS_API_PAGE,
        FACE_APPS_API_LIMIT);
    HttpRequest request = HttpRequest.newBuilder()
        .GET()
        .uri(URI.create(requestUrl))
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
      JsonNode jsonNode = new ObjectMapper().readTree(response.body());

      LOGGER.info("Fetched {} watchfaces.", jsonNode.size());
      return jsonNode;
    } catch (IOException e) {
      LOGGER.error("I/O error while calling API: {}. Message: {}", FACE_APPS_API_URL, e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.error("API call was interruped. Message: {}", e.getMessage());
    }
    return null;
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

  private List<Watchface> scrapeWatchfaces(List<String> watchfaceUrls) {
    LOGGER.info("Starting scraping {} watchfaces...", watchfaceUrls.size());

    ExecutorService executorService = Executors.newFixedThreadPool(5);
    List<Future<Watchface>> watchfaceFutures = new ArrayList<>();
    for (String watchfaceUrl : watchfaceUrls) {
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

  private Watchface scrapeWatchface(String watchfaceUrl) {
    LOGGER.info("Scraping watchface: {}", watchfaceUrl);
    try (Playwright playwright = Playwright.create()) {
      Browser browser = launchBrowser(playwright, USE_HEADLESS_BROWSER);
      Page page = browser.newPage();
      page.navigate(watchfaceUrl);

      Locator googlePlayButton = page.getByText("Open in Google Play");
      googlePlayButton.waitFor(new WaitForOptions().setState(WaitForSelectorState.VISIBLE));
      page.waitForTimeout(2000);

      Locator getCouponButton = page.getByText("Get coupon");
      boolean isFree = !getCouponButton.isVisible();
      int availableCoupons = -1;
      if (!isFree) {
        Locator availableCouponsParagraph = page.locator("p")
            .filter(new Locator.FilterOptions().setHasText("Available coupons"));
        Locator couponCountParagraph = availableCouponsParagraph.locator("xpath=preceding-sibling::p[1]");

        availableCoupons = Integer.parseInt(couponCountParagraph.innerText().replace(",", ""));
        if (availableCoupons == 0) {
          return null;
        }
      }

      googlePlayButton.click();

      // Page went into Google Play
      String cssSelector = "img.T75of.B5GQxf";
      page.waitForSelector(cssSelector);

      List<String> imageUrls = new ArrayList<>();
      for (Locator watchfaceImage : page.locator(cssSelector).all()) {
        String imageUrl = watchfaceImage.getAttribute("src");
        imageUrls.add(imageUrl);
      }
      page.close();
      browser.close();

      return new Watchface(
          page.url(),
          watchfaceUrl,
          imageUrls,
          isFree,
          availableCoupons);
    }
  }
}
