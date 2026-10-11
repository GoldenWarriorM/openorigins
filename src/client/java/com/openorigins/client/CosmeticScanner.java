package com.openorigins.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Debug helper for back cosmetics: reports which equipment slots hold items
 * with the server Cosmetic marker (PublicBukkitValues) and their registry keys.
 */
public final class CosmeticScanner {
  private static final org.slf4j.Logger SCAN_LOG = org.slf4j.LoggerFactory.getLogger("openorigins");
  private static final java.util.Set<Integer> loggedStands = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
  private static int tickCounter;

  public static void registerAutoScan() {
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
        .register((handler, client) -> loggedStands.clear());
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
      if (client.level == null || client.player == null) return;
      if (++tickCounter % 100 != 0) return;
      autoScan(client);
    });
  }

  private static void autoScan(net.minecraft.client.Minecraft mc) {
    for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
      if (!(e instanceof net.minecraft.world.entity.decoration.ArmorStand stand)) continue;
      if (!stand.isInvisible() || !stand.isSmall()) continue;
      if (!loggedStands.add(stand.getId())) continue;
      net.minecraft.world.item.ItemStack head =
          stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
      String cmd = "";
      if (!head.isEmpty()) {
        net.minecraft.world.item.component.CustomModelData data = head.get(
            net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        if (data != null) cmd = " cmd=" + data;
      }
      com.openorigins.client.util.DebugLog.info("[OpenOrigins autospawn] stand#{} head={}{}", stand.getId(), head.getItem(), cmd);
    }
  }

  private static final EquipmentSlot[] SLOTS = {
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
      EquipmentSlot.FEET, EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND
  };

  private CosmeticScanner() {}

  public static void scanAndReport() {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return;
    report(mc, String.format(java.util.Locale.ROOT, "cam=%s playerYaw=%.1f playerPitch=%.1f",
        mc.options.getCameraType(), mc.player.getYRot(), mc.player.getXRot()));
    net.minecraft.world.entity.Entity veh = mc.player.getVehicle();
    int depth = 0;
    while (veh != null && depth < 3) {
      report(mc, String.format(java.util.Locale.ROOT, "VEHICLE depth=%d %s#%d yaw=%.1f%s carries%d",
          depth,
          net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(veh.getType()),
          veh.getId(), veh.getYRot(), veh.isInvisible() ? " invisible" : "",
          veh.getPassengers().size()));
      for (net.minecraft.world.entity.Entity p : veh.getPassengers()) {
        if (p == mc.player) {
          continue;
        }
        StringBuilder csb = new StringBuilder(String.format(java.util.Locale.ROOT,
            "CO-RIDER %s#%d yaw=%.0f",
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(p.getType()),
            p.getId(), p.getYRot()));
        if (p.isInvisible()) {
          csb.append(" invisible");
        }
        if (p instanceof net.minecraft.world.entity.Display display) {
          try {
            csb.append(" bb=").append(display.renderState().billboardConstraints());
            net.minecraft.world.item.ItemStack ds = display.getSlot(0).get();
            if (ds != null && !ds.isEmpty()) {
              csb.append(" shown=")
                  .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ds.getItem()));
            }
          } catch (Throwable ignored) {
          }
        }
        report(mc, csb.toString());
      }
      veh = veh.getVehicle();
      depth++;
    }
    boolean found = false;
    for (EquipmentSlot slot : SLOTS) {
      ItemStack stack = mc.player.getItemBySlot(slot);
      if (stack.isEmpty()) continue;
      String marker = describeMarker(stack);
      if (marker != null) {
        found = true;
        report(mc, slot.getName().toUpperCase(Locale.ROOT) + ": " + marker);
      }
    }
    if (!found) {
      report(mc, "no cosmetic markers in equipment (head/chest/legs/feet/offhand/hand)");
    }
    net.minecraft.world.item.ItemStack back =
        com.openorigins.client.server.EntityData.getBackItem(mc.player.getId());
    if (back == null || back.isEmpty()) {
      report(mc, "backitem: none");
    } else {
      StringBuilder sb = new StringBuilder("backitem: ")
          .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(back.getItem()));
      sb.append(" hover=").append(back.getHoverName().getString());
      net.minecraft.world.item.component.CustomData data =
          back.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
      if (data != null) {
        net.minecraft.nbt.CompoundTag tag = data.copyTag();
        net.minecraft.nbt.CompoundTag values = tag.getCompoundOrEmpty("PublicBukkitValues");
        sb.append(" key=").append(values.getStringOr("xcore:item-registry-key", "-"));
      }
      report(mc, sb.toString());
    }
    scanEntities(mc);
  }

  private static void dumpPassengerTree(Minecraft mc, net.minecraft.world.entity.Entity root, int depth) {
    for (net.minecraft.world.entity.Entity p : root.getPassengers()) {
      StringBuilder sb = new StringBuilder("RIDER depth=").append(depth).append(' ')
          .append(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(p.getType()))
          .append('#').append(p.getId());
      if (p.isInvisible()) sb.append(" invisible");
      if (p instanceof net.minecraft.world.entity.decoration.ArmorStand stand) {
        sb.append(stand.isMarker() ? " marker" : "").append(stand.isSmall() ? " small" : "");
      }
      report(mc, sb.toString());
      if (depth < 3) dumpPassengerTree(mc, p, depth + 1);
    }
  }

  private static void scanEntities(Minecraft mc) {
    if (mc.level == null || mc.player == null) return;
    dumpPassengerTree(mc, mc.player, 0);
    int shown = 0;
    for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
      if (e == mc.player) continue;
      if (mc.player.distanceToSqr(e.getX(), e.getY(), e.getZ()) > 24 * 24) continue;
      if (shown >= 40) {
        report(mc, "...truncated, see latest.log");
        break;
      }
      shown++;
      String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
      StringBuilder sb = new StringBuilder(type + "#" + e.getId());
      sb.append(String.format(java.util.Locale.ROOT, " yaw=%.0f @%.1f,%.1f,%.1f",
          e.getYRot(), e.getX(), e.getY(), e.getZ()));
      double dist = Math.sqrt(mc.player.distanceToSqr(e.getX(), e.getY(), e.getZ()));
      if (dist < 2.5) sb.append(" ON-YOU");
      if (e.hasCustomName() && e.getCustomName() != null) {
        String name = e.getCustomName().getString();
        sb.append(" name=").append(name.length() > 24 ? name.substring(0, 24) : name);
      }
      if (e.isInvisible()) sb.append(" invisible");
      if (e.getVehicle() != null) sb.append(" rides#").append(e.getVehicle().getId());
      if (!e.getPassengers().isEmpty()) sb.append(" carries").append(e.getPassengers().size());
      if (e instanceof net.minecraft.world.entity.decoration.ArmorStand stand) {
        sb.append(stand.isMarker() ? " marker" : "").append(stand.isSmall() ? " small" : "");
      }
      if (e instanceof net.minecraft.world.entity.Display display) {
        try {
          sb.append(" bb=").append(display.renderState().billboardConstraints());
          net.minecraft.world.item.ItemStack ds = display.getSlot(0).get();
          if (ds != null && !ds.isEmpty()) {
            sb.append(" shown=")
                .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ds.getItem()));
            String hover = ds.getHoverName().getString();
            if (!hover.isEmpty()) {
              sb.append(" disp=").append(hover.length() > 30 ? hover.substring(0, 30) : hover);
            }
            net.minecraft.resources.Identifier model =
                ds.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
            if (model != null) {
              sb.append(" model=").append(model.toString());
            }
            net.minecraft.world.item.component.CustomModelData cmd =
                ds.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
            if (cmd != null) {
              String cmdStr = cmd.toString();
              sb.append(" cmd=").append(cmdStr.length() > 60 ? cmdStr.substring(0, 60) : cmdStr);
            }
          }
        } catch (Throwable ignored) {
        }
      }
      if (e instanceof net.minecraft.world.entity.LivingEntity living) {
        for (net.minecraft.world.entity.EquipmentSlot slot :
            new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET,
                net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                net.minecraft.world.entity.EquipmentSlot.MAINHAND}) {
          net.minecraft.world.item.ItemStack s = living.getItemBySlot(slot);
          if (!s.isEmpty()) {
            sb.append(" ").append(slot.getName()).append("=")
                .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()));
            String hover = s.getHoverName().getString();
            if (!hover.isEmpty()) {
              sb.append(" disp=").append(hover.length() > 30 ? hover.substring(0, 30) : hover);
            }
            if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) {
              String comps = s.getComponents().keySet().stream()
                  .map(k -> k.toString()).sorted()
                  .reduce((a, b) -> a + "," + b).orElse("-");
              sb.append("{").append(comps.length() > 120 ? comps.substring(0, 120) : comps).append("}");
              net.minecraft.world.item.component.CustomModelData cmd =
                  s.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
              if (cmd != null) {
                String cmdStr = cmd.toString();
                sb.append(" cmd=").append(cmdStr.length() > 60 ? cmdStr.substring(0, 60) : cmdStr);
              }
            }
          }
        }
      }
      report(mc, sb.toString());
    }
    if (shown == 0) {
      report(mc, "no entities within 24 blocks (except you)");
    }
  }

  private static String describeMarker(ItemStack stack) {
    CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
    if (custom == null || custom == CustomData.EMPTY) return stack.getItem().toString();
    CompoundTag tag = custom.copyTag();
    String key = null;
    if (tag.contains("PublicBukkitValues")) {
      CompoundTag values = tag.getCompoundOrEmpty("PublicBukkitValues");
      if (values.getBooleanOr("Cosmetic", false)) {
        key = values.getStringOr("xcore:item-registry-key", "");
        if (key.isEmpty()) key = "cosmetic?";
      }
    } else if (tag.contains("CustomBlock")) {
      key = "block:" + tag.getStringOr("CustomBlock", "?");
    }
    if (key == null) return null;
    return key + " [" + stack.getItem() + "]";
  }

  private static void report(Minecraft mc, String text) {
    Component msg = Component.literal("[OpenOrigins scan] " + text);
    if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
      mc.gui.hud.getChat().addClientSystemMessage(msg);
    }
  }
}
