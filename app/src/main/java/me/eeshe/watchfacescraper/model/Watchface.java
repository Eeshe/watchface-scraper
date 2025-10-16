package me.eeshe.watchfacescraper.model;

import java.util.List;

public class Watchface {
  private final long scrapeTimeMillis;

  private final String playStoreUrl;
  private final String couponsUrl;
  private final List<String> imageUrls;
  private final boolean isFree;
  private final int remainingCoupons;

  public Watchface(
      String playStoreUrl,
      String couponsUrl,
      List<String> imageUrls,
      boolean isFree,
      int remainingCoupons) {
    this.scrapeTimeMillis = System.currentTimeMillis();

    this.playStoreUrl = playStoreUrl;
    this.couponsUrl = couponsUrl;
    this.imageUrls = imageUrls;
    this.isFree = isFree;
    this.remainingCoupons = remainingCoupons;
  }

  public Watchface(
      long scrapeTimeMillis,
      String playStoreUrl,
      String couponsUrl,
      List<String> imageUrls,
      boolean isFree,
      int remainingCoupons) {
    this.scrapeTimeMillis = scrapeTimeMillis;
    this.playStoreUrl = playStoreUrl;
    this.couponsUrl = couponsUrl;
    this.imageUrls = imageUrls;
    this.isFree = isFree;
    this.remainingCoupons = remainingCoupons;
  }

  public long getScrapeTimeMillis() {
    return scrapeTimeMillis;
  }

  public String getPlayStoreUrl() {
    return playStoreUrl;
  }

  public String getCouponsUrl() {
    return couponsUrl;
  }

  public List<String> getImageUrls() {
    return imageUrls;
  }

  public boolean isFree() {
    return isFree;
  }

  public int getRemainingCoupons() {
    return remainingCoupons;
  }
}
