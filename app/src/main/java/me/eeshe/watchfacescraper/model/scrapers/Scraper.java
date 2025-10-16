package me.eeshe.watchfacescraper.model.scrapers;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.Locator.WaitForOptions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;

public class Scraper {

  public Browser launchBrowser(Playwright playwright, boolean headless) {
    LaunchOptions launchOptions = new LaunchOptions();
    launchOptions.setHeadless(headless);

    return playwright.chromium().launch(launchOptions);
  }

  /**
   * Scrolls down in the passed page until the loading element isn't visible.
   *
   * @param page Page to scroll down in.
   */
  public void scrollPage(Page page, String loadingElementIdentifier) {
    while (true) {
      for (int i = 0; i < 100; i++) {
        page.mouse().wheel(0, 200);
      }
      if (!page.locator(loadingElementIdentifier).isVisible()) {
        break;
      }
      page.locator(loadingElementIdentifier).waitFor(new WaitForOptions().setState(
          WaitForSelectorState.HIDDEN));
    }
  }
}
