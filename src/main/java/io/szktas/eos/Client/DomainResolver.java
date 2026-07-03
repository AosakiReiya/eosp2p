package io.szktas.eos.Client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.szktas.eos.EOSBinder.EOSNative;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.szktas.eos.Main.LOGGER;

public class DomainResolver {
    private static final String DOH_ENDPOINT = "https://cloudflare-dns.com/dns-query";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 300_000; // 5 minutes

    private record CachedEntry(String key, long timestamp) {}

    public static String resolveDomain(String domain) {
        String txtName = "_eos." + domain;

        CachedEntry cached = cache.get(txtName);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            LOGGER.debug("DomainResolver: cache hit for {}", txtName);
            return cached.key;
        }

        try {
            String encodedName = URLEncoder.encode(txtName, StandardCharsets.UTF_8);
            URI uri = URI.create(DOH_ENDPOINT + "?name=" + encodedName + "&type=TXT");
            LOGGER.debug("DomainResolver: querying {} for {}", DOH_ENDPOINT, txtName);

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/dns-json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOGGER.warn("DomainResolver: DoH returned status {}", response.statusCode());
                return null;
            }

            String key = parseResponse(response.body());
            if (key != null) {
                cache.put(txtName, new CachedEntry(key, System.currentTimeMillis()));
            }
            return key;
        } catch (Exception e) {
            LOGGER.error("DomainResolver: failed to resolve {}", txtName, e);
            return null;
        }
    }

    private static String parseResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (!root.has("Answer")) {
                LOGGER.debug("DomainResolver: no Answer in DNS response");
                return null;
            }

            JsonArray answers = root.getAsJsonArray("Answer");
            for (int i = 0; i < answers.size(); i++) {
                JsonObject answer = answers.get(i).getAsJsonObject();
                String data = answer.get("data").getAsString();

                if (data.startsWith("\"") && data.endsWith("\"")) {
                    data = data.substring(1, data.length() - 1);
                }

                if (data.startsWith("eos-v1=")) {
                    String base64Key = data.substring(7);
                    String[] decoded = EOSNative.decodeConnectionKey(base64Key);
                    if (decoded != null) {
                        LOGGER.info("DomainResolver: resolved EOS key for domain");
                        return base64Key;
                    } else {
                        LOGGER.warn("DomainResolver: found eos-v1 record but invalid key");
                    }
                }
            }

            LOGGER.debug("DomainResolver: no eos-v1 TXT record found");
            return null;
        } catch (Exception e) {
            LOGGER.error("DomainResolver: failed to parse DNS response", e);
            return null;
        }
    }

    public static boolean isValidDomain(String domain) {
        if (domain == null || domain.isEmpty()) return false;
        return domain.contains(".") && !domain.contains(" ") && !domain.contains("/") && !domain.contains(":");
    }

    public static void clearCache() {
        cache.clear();
    }
}
