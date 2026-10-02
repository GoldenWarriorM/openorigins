package com.openorigins.client.discord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discord presence via state file. The game runs sandboxed where Discord's IPC
 * socket is unusable with a matching peer pid, so a host-side daemon
 * (tools/discord-relay/discord_presence_daemon.py) owns the IPC connection
 * and this class only publishes the desired state JSON.
 */
public final class DiscordPresence {
  private static final Logger LOGGER = LoggerFactory.getLogger("openorigins");
  private static volatile String lastWritten = "";

  private DiscordPresence() {}

  public static void setClientId(String id) {
  }

  public static void refresh(String clientId, String details, String state, String largeImage,
      String largeText, long startTimestamp) {
    if (clientId == null || clientId.isEmpty()) {
      return;
    }
    StringBuilder json = new StringBuilder("{\"client_id\":");
    appendJsonFieldValue(json, clientId);
    json.append(",\"details\":");
    appendJsonFieldValue(json, stripCodes(details));
    json.append(",\"state\":");
    appendJsonFieldValue(json, stripCodes(state));
    json.append(",\"large_image\":");
    appendJsonFieldValue(json, largeImage);
    json.append(",\"large_text\":");
    appendJsonFieldValue(json, stripCodes(largeText));
    json.append(",\"start\":").append(startTimestamp > 0 ? startTimestamp / 1000 : 0);
    json.append('}');
    writeState(json.toString());
  }

  public static void clear() {
    writeState("{\"clear\":true}");
  }

  public static void shutdown() {
  }

  private static void appendJsonFieldValue(StringBuilder sb, String value) {
    if (value == null) {
      sb.append("null");
      return;
    }
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '"') {
        sb.append("\\\"");
      } else if (c == '\\') {
        sb.append("\\\\");
      } else if (c == '\n') {
        sb.append("\\n");
      } else if (c < 0x20) {
        sb.append(String.format("\\u%04x", (int) c));
      } else {
        sb.append(c);
      }
    }
    sb.append('"');
  }

  /** Strips Minecraft formatting codes and server icon glyphs (Yi block, PUA). */
  private static String stripCodes(String value) {
    if (value == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '§' && i + 1 < value.length()) {
        i++;
        continue;
      }
      if (c < 0x20) {
        continue;
      }
      if ((c >= 0xA000 && c <= 0xA48F) || (c >= 0xE000 && c <= 0xF8FF)) {
        continue;
      }
      sb.append(c);
    }
    return sb.toString().trim().replace("  ", " ");
  }

  private static Path statePath() {
    String runtime = System.getenv("XDG_RUNTIME_DIR");
    if (runtime == null) {
      runtime = "/tmp";
    }
    return Path.of(runtime, "app", "org.prismlauncher.PrismLauncher", "openorigins-presence.json");
  }

  private static synchronized void writeState(String json) {
    if (json.equals(lastWritten)) {
      return;
    }
    lastWritten = json;
    try {
      Path target = statePath();
      Files.createDirectories(target.getParent());
      Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
      Files.writeString(tmp, json, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      LOGGER.warn("[OpenOrigins] failed to write presence state: {}", e.toString());
    }
  }
}
