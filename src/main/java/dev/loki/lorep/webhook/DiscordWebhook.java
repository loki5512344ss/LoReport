package dev.loki.lorep.webhook;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.loki.lorep.database.Report;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DiscordWebhook {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final String webhookUrl;
    private final Logger logger;
    private final OkHttpClient client;

    public DiscordWebhook(String webhookUrl, Logger logger) {
        this.webhookUrl = webhookUrl;
        this.logger = logger;
        this.client = new OkHttpClient();
    }

    public void sendReport(Report report) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            logger.warning("Discord webhook URL is not configured");
            return;
        }

        String json = buildPayload(report);

        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
            .url(webhookUrl)
            .post(body)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.log(Level.WARNING, "Failed to send Discord webhook", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    logger.warning("Discord webhook returned: " + response.code());
                }
                response.close();
            }
        });
    }

    public void sendStatusUpdate(Report report) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            logger.warning("Discord webhook URL is not configured");
            return;
        }

        String json = buildStatusPayload(report);

        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
            .url(webhookUrl)
            .post(body)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.log(Level.WARNING, "Failed to send status update webhook", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    logger.warning("Status update webhook returned: " + response.code());
                }
                response.close();
            }
        });
    }

    public String buildPayload(Report report) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "\uD83D\uDCE2 Новый репорт #" + report.id());
        embed.addProperty("color", getColorForStatus(report.status()));

        JsonObject thumbnail = new JsonObject();
        thumbnail.addProperty("url", "https://mc-heads.net/avatar/" + report.targetUuid());
        embed.add("thumbnail", thumbnail);

        JsonObject author = new JsonObject();
        author.addProperty("name", report.reporterName());
        author.addProperty("icon_url", "https://mc-heads.net/avatar/" + report.reporterUuid());
        embed.add("author", author);

        JsonArray fields = new JsonArray();

        JsonObject targetField = new JsonObject();
        targetField.addProperty("name", "\uD83D\uDC64 Нарушитель");
        targetField.addProperty("value", report.targetName());
        targetField.addProperty("inline", true);
        fields.add(targetField);

        JsonObject reporterField = new JsonObject();
        reporterField.addProperty("name", "\uD83D\uDCDD Отправитель");
        reporterField.addProperty("value", report.reporterName());
        reporterField.addProperty("inline", true);
        fields.add(reporterField);

        JsonObject reasonField = new JsonObject();
        reasonField.addProperty("name", "\uD83D\uDCCB Причина");
        reasonField.addProperty("value", stripLegacyColors(report.reason()));
        reasonField.addProperty("inline", false);
        fields.add(reasonField);

        JsonObject statusField = new JsonObject();
        statusField.addProperty("name", "\uD83C\uDFF7 Статус");
        statusField.addProperty("value", getStatusDisplay(report.status()));
        statusField.addProperty("inline", true);
        fields.add(statusField);

        JsonObject timeField = new JsonObject();
        timeField.addProperty("name", "\uD83D\uDD50 Время");
        timeField.addProperty("value", FORMATTER.format(report.createdAt()));
        timeField.addProperty("inline", true);
        fields.add(timeField);

        JsonObject serverField = new JsonObject();
        serverField.addProperty("name", "\uD83D\uDDA5 \u0421\u0435\u0440\u0432\u0435\u0440");
        serverField.addProperty("value", report.serverName() != null ? report.serverName() : "unknown");
        serverField.addProperty("inline", true);
        fields.add(serverField);

        embed.add("fields", fields);

        if (report.imageUrl() != null && !report.imageUrl().isEmpty()) {
            JsonObject image = new JsonObject();
            image.addProperty("url", report.imageUrl());
            embed.add("image", image);
        }

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "LoReport \u2022 " + FORMATTER.format(report.createdAt()));
        embed.add("footer", footer);

        embed.addProperty("timestamp", report.createdAt().toString());

        JsonObject root = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        root.add("embeds", embeds);

        return new GsonBuilder().create().toJson(root);
    }

    private String buildStatusPayload(Report report) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "\uD83D\uDD04 Статус репорта #" + report.id() + " изменён");
        embed.addProperty("color", getColorForStatus(report.status()));

        JsonObject thumbnail = new JsonObject();
        thumbnail.addProperty("url", "https://mc-heads.net/avatar/" + report.targetUuid());
        embed.add("thumbnail", thumbnail);

        JsonArray fields = new JsonArray();

        JsonObject targetField = new JsonObject();
        targetField.addProperty("name", "\uD83D\uDC64 Нарушитель");
        targetField.addProperty("value", report.targetName());
        targetField.addProperty("inline", true);
        fields.add(targetField);

        JsonObject statusField = new JsonObject();
        statusField.addProperty("name", "\uD83C\uDFF7 Новый статус");
        statusField.addProperty("value", getStatusDisplay(report.status()));
        statusField.addProperty("inline", false);
        fields.add(statusField);

        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "LoReport \u2022 " + FORMATTER.format(report.createdAt()));
        embed.add("footer", footer);

        embed.addProperty("timestamp", report.createdAt().toString());

        JsonObject root = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        root.add("embeds", embeds);

        return new GsonBuilder().create().toJson(root);
    }

    private int getColorForStatus(String status) {
        return switch (status != null ? status.toUpperCase() : "PENDING") {
            case "PENDING" -> 0xFF0000;
            case "REVIEWING" -> 0xFFAA00;
            case "RESOLVED" -> 0x00FF00;
            case "DISMISSED" -> 0x808080;
            default -> 0xFF0000;
        };
    }

    private String getStatusDisplay(String status) {
        return switch (status != null ? status.toUpperCase() : "PENDING") {
            case "PENDING" -> "\u23F3 Ожидает";
            case "REVIEWING" -> "\uD83D\uDD0D На рассмотрении";
            case "RESOLVED" -> "\u2705 Решён";
            case "DISMISSED" -> "\u274C Отклонён";
            default -> "\u2753 Неизвестно";
        };
    }

    /**
     * Strips legacy paragraph-sign color codes so colorized GUI-only text (for
     * example the appended anticheat summary) stays readable in Discord embeds.
     */
    private static String stripLegacyColors(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder stripped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++; // skip the color code character itself
                continue;
            }
            stripped.append(c);
        }
        return stripped.toString();
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
