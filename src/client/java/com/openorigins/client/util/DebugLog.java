package com.openorigins.client.util;

import com.openorigins.client.OpenOriginsConfig;
import com.openorigins.client.OriginFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** All diagnostic logging goes through here (toggleable, off by default). */
public final class DebugLog {
  private DebugLog() {}

  private static final Logger LOG = LoggerFactory.getLogger("openorigins");

  public static boolean enabled() {
    try {
      return OpenOriginsConfig.getInstance().isEnabled(OriginFeature.DEBUG_LOGGING);
    } catch (Throwable t) {
      return false;
    }
  }

  public static void info(String format, Object... args) {
    if (enabled()) {
      LOG.info(format, args);
    }
  }

  public static void warn(String format, Object... args) {
    if (enabled()) {
      LOG.warn(format, args);
    }
  }
}
