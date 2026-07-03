package io.szktas.eos.Client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
    private static final String[] DOH_ENDPOINTS = {
            "https://cloudflare-dns.com/dns-query",
            "https://dns.google/dns-query",
            "https://dns.alidns.com/resolve",
            "https://doh.dns.sb/dns-query"
    };
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 300_000;

    private record CachedEntry(String key, long timestamp) {}

    public static String resolveDomain(String domain) {
        return resolveTxtRecord(domain);
    }

    private static String resolveTxtRecord(String txtName) {
        CachedEntry cached = cache.get(txtName);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            LOGGER.debug("DomainResolver: cache hit for {}", txtName);
            return cached.key;
        }

        String key = resolveViaDoH(txtName);
        if (key != null) {
            cache.put(txtName, new CachedEntry(key, System.currentTimeMillis()));
        }
        return key;
    }

    private static String resolveViaDoH(String txtName) {
        String encodedName = URLEncoder.encode(txtName, StandardCharsets.UTF_8);

        for (String endpoint : DOH_ENDPOINTS) {
            try {
                URI uri = URI.create(endpoint + "?name=" + encodedName + "&type=TXT");
                LOGGER.debug("DomainResolver: querying {} for {}", endpoint, txtName);

                HttpRequest request = HttpRequest.newBuilder(uri)
                        .header("Accept", "application/dns-json")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    LOGGER.debug("DomainResolver: {} returned status {}", endpoint, response.statusCode());
                    continue;
                }

                String key = parseDoHResponse(response.body());
                if (key != null) {
                    LOGGER.info("DomainResolver: resolved via {} for {}", endpoint, txtName);
                    return key;
                }
            } catch (Exception e) {
                LOGGER.debug("DomainResolver: {} failed for {}: {}", endpoint, txtName, e.toString());
            }
        }
        return null;
    }

    private static String parseDoHResponse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement answerElement = root.get("Answer");
            if (answerElement == null) {
                LOGGER.debug("DomainResolver: no Answer in DNS response");
                return null;
            }

            JsonArray answers;
            if (answerElement.isJsonArray()) {
                answers = answerElement.getAsJsonArray();
            } else if (answerElement.isJsonObject()) {
                answers = new JsonArray();
                answers.add(answerElement.getAsJsonObject());
            } else {
                return null;
            }

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
                        return base64Key;
                    } else {
                        LOGGER.warn("DomainResolver: found eos-v1 record but invalid key");
                    }
                }
            }

            LOGGER.debug("DomainResolver: no eos-v1 TXT record found");
            return null;
        } catch (Exception e) {
            LOGGER.debug("DomainResolver: failed to parse DNS response: {}", e.toString());
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
