package com.openorigins.client.util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Appends a per-unit price line to auction listings (content-detected via
 * the server's own "Price:" lore, so no menu tracking is needed).
 * Single items are left untouched.
 */
public final class AuctionUnitPrice {
  private AuctionUnitPrice() {}

  private static final Pattern PRICE =
      Pattern.compile("(?i)^price:\\s*([\\d,]+(?:\\.\\d+)?)");
  private static volatile boolean structureLogged;

  /** @return true when a line was added. */
  public static boolean apply(ItemStack stack, List<Component> lines) {
    if (stack == null || stack.getCount() <= 1 || lines == null || lines.isEmpty()) {
      return false;
    }
    int priceIdx = -1;
    double total = 0;
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = PRICE.matcher(lines.get(i).getString().strip());
      if (m.find()) {
        priceIdx = i;
        try {
          total = Double.parseDouble(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
          return false;
        }
        break;
      }
    }
    if (priceIdx < 0 || total <= 0) {
      return false;
    }
    double unit = total / stack.getCount();
    String formatted;
    if (unit == Math.floor(unit)) {
      formatted = String.format(Locale.US, "%,d", (long) unit);
    } else {
      formatted = String.format(Locale.US, "%,.2f", unit);
    }
    Component priceLine = lines.get(priceIdx);
    logStructureOnce(priceLine);
    List<Component> sibs = priceLine.getSiblings();
    String prefix = Component.translatable("tooltip.openorigins.per_item_prefix").getString();
    MutableComponent out = Component.literal(prefix).withStyle(priceLine.getStyle());
    if (sibs.isEmpty()) {
      out.append(Component.literal(formatted).withStyle(priceLine.getStyle()));
    } else {
      out.append(Component.literal(formatted).withStyle(sibs.get(0).getStyle()));
    }
    lines.add(priceIdx + 1, out);
    return true;
  }

  private static void logStructureOnce(Component priceLine) {
    if (structureLogged) {
      return;
    }
    structureLogged = true;
    try {
      StringBuilder sb = new StringBuilder();
      dump(priceLine, 0, sb);
      DebugLog.info("[ahprice] price line structure:\n{}", sb.toString());
    } catch (Throwable ignored) {
    }
  }

  private static void dump(Component c, int depth, StringBuilder sb) {
    var s = c.getStyle();
    String text;
    try {
      var contents = c.getContents();
      text = contents.toString();
    } catch (Throwable t) {
      text = "?";
    }
    sb.append("  ".repeat(depth)).append("text=").append(text).append(" color=")
        .append(s.getColor()).append(" italic=").append(s.isItalic()).append(" font=")
        .append(s.getFont()).append('\n');
    for (Component sib : c.getSiblings()) {
      dump(sib, depth + 1, sb);
    }
  }
}
