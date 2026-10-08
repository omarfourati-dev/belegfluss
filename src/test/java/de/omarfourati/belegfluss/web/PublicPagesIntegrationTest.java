package de.omarfourati.belegfluss.web;

import de.omarfourati.belegfluss.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Landing page, app shell and the files for search engines and AI crawlers are public. Fixtures: src/test/resources/static. */
class PublicPagesIntegrationTest extends IntegrationTest {

    @Test
    void landingPageIsServedAtTheRoot() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void appIsServedUnderAppWithoutLogin() throws Exception {
        mvc.perform(get("/app"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/"));
        mvc.perform(get("/app/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/app/index.html"));
        mvc.perform(get("/app/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("app fixture")));
    }

    @Test
    void pagesAreRevalidatedSoPhonesGetNewVersions() throws Exception {
        mvc.perform(get("/app/index.html"))
                .andExpect(header().string("Cache-Control", "no-cache"));
        mvc.perform(get("/"))
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void crawlerFilesArePublic() throws Exception {
        for (String path : new String[]{"/robots.txt", "/llms.txt", "/sitemap.xml"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void pwaFilesArePublicAndNeverCached() throws Exception {
        // a cached service worker would delay every update by up to a day
        mvc.perform(get("/app/sw.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));
        mvc.perform(get("/app/manifest.webmanifest"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(content().contentTypeCompatibleWith("application/manifest+json"));
        mvc.perform(get("/app/icons/icon-192.png")).andExpect(status().isOk());
    }

    @Test
    void otherAppFilesStillNeedALogin() throws Exception {
        mvc.perform(get("/app/secret.json")).andExpect(status().isUnauthorized());
    }
}
