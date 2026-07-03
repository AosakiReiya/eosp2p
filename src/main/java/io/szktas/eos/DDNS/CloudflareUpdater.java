package io.szktas.eos.DDNS;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static io.szktas.eos.Main.LOGGER;

public class CloudflareUpdater {
    private static final String API_BASE = "https://api.cloudflare.com/client/v4";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static String lastUpdatedKey = null;
    private static long lastUpdatedTime = 0;

    public static boolean updateRecord(String apiToken, String zoneId, String recordName, String connectionKey) {
        if (apiToken == null || apiToken.isEmpty() || zoneId == null || zoneId.isEmpty() || recordName == null || recordName.isEmpty()) {
            LOGGER.error("Cloudflare DDNS: missing configuration (api_token, zone_id, ddns_record)");
            return false;
        }
        if (connectionKey == null || connectionKey.isEmpty()) {
            LOGGER.error("Cloudflare DDNS: connection key is null, skipping update");
            return false;
        }

        String value = "eos-v1=" + connectionKey;

        try {
            String existingId = findExistingRecord(apiToken, zoneId, recordName);
            boolean success;
            if (existingId != null) {
                success = updateDnsRecord(apiToken, zoneId, existingId, recordName, value);
            } else {
                success = createDnsRecord(apiToken, zoneId, recordName, value);
            }

            if (success) {
                lastUpdatedKey = connectionKey;
                lastUpdatedTime = System.currentTimeMillis();
                LOGGER.info("Cloudflare DDNS: successfully updated record {} with key", recordName);
            }
            return success;
        } catch (Exception e) {
            LOGGER.error("Cloudflare DDNS: failed to update record", e);
            return false;
        }
    }

    private static String findExistingRecord(String apiToken, String zoneId, String recordName) throws Exception {
        URI uri = URI.create(API_BASE + "/zones/" + zoneId + "/dns_records?type=TXT&name=" + recordName);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            LOGGER.warn("Cloudflare DDNS: list records returned {}", response.statusCode());
            return null;
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.get("success").getAsBoolean()) return null;

        JsonArray result = json.getAsJsonArray("result");
        if (result != null && result.size() > 0) {
            return result.get(0).getAsJsonObject().get("id").getAsString();
        }
        return null;
    }

    private static boolean updateDnsRecord(String apiToken, String zoneId, String recordId, String recordName, String value) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("type", "TXT");
        body.addProperty("name", recordName);
        body.addProperty("content", value);
        body.addProperty("ttl", 120);

        URI uri = URI.create(API_BASE + "/zones/" + zoneId + "/dns_records/" + recordId);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            LOGGER.error("Cloudflare DDNS: update failed with status {}", response.statusCode());
            LOGGER.error("Cloudflare DDNS: response body: {}", response.body());
            return false;
        }
        return JsonParser.parseString(response.body()).getAsJsonObject().get("success").getAsBoolean();
    }

    private static boolean createDnsRecord(String apiToken, String zoneId, String recordName, String value) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("type", "TXT");
        body.addProperty("name", recordName);
        body.addProperty("content", value);
        body.addProperty("ttl", 120);

        URI uri = URI.create(API_BASE + "/zones/" + zoneId + "/dns_records");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            LOGGER.error("Cloudflare DDNS: create failed with status {}", response.statusCode());
            LOGGER.error("Cloudflare DDNS: response body: {}", response.body());
            return false;
        }
        return JsonParser.parseString(response.body()).getAsJsonObject().get("success").getAsBoolean();
    }

    public static String getLastUpdatedKey() {
        return lastUpdatedKey;
    }

    public static long getLastUpdatedTime() {
        return lastUpdatedTime;
    }
}
