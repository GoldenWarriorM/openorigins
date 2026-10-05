package com.openorigins.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class HotkeyFeatures {
  private static KeyMapping navigatorKey;
  private static KeyMapping gestureKey;
  private static KeyMapping spawnKey;
  private static KeyMapping realmKey;
  private static KeyMapping configKey;
  private static KeyMapping scanKey;
  private static KeyMapping teleportMenuKey;
  private static KeyMapping homesMenuKey;
  private static KeyMapping blueBalloonKey;
  private static KeyMapping greenBalloonKey;
  private static KeyMapping yellowBalloonKey;
  private static KeyMapping redBalloonKey;
  private static KeyMapping badgerHudKey;
  private static KeyMapping armsPlusKey;
  private static KeyMapping armsMinusKey;
  private static boolean joinFetchDone;
  private static boolean badgerFetchDone;
  private static boolean worldsFetchDone;
  private static int joinTicks;
  private static final java.util.Set<Float> OPENORIGINS$ARMS_SEEN = new java.util.HashSet<>();
  private static final java.util.Set<Integer> OPENORIGINS$NEAR_SEEN = new java.util.HashSet<>();
  private static final java.util.Set<Integer> OPENORIGINS$ARMS_FILLED = new java.util.HashSet<>();

  /** Reconstructs server-emptied allay arm slots (classic arms, last-known-good model).
   *  Only fills fingerprint-matching empty displays near an interaction hitbox;
   *  backs off the moment the server provides data. */
  private static void maybeFillArms(net.minecraft.client.Minecraft client,
      java.util.List<net.minecraft.world.entity.Entity> all,
      net.minecraft.world.entity.Display.ItemDisplay disp) {
    try {
      net.minecraft.world.entity.SlotAccess slot = disp.getSlot(0);
      net.minecraft.world.item.ItemStack cur = slot == null ? null : slot.get();
      if (cur != null && !cur.isEmpty()) {
        return;
      }
      com.mojang.math.Transformation t = disp.renderState().transformation().get(1.0f);
      org.joml.Vector3fc tr = t.translation();
      org.joml.Vector3fc sc = t.scale();
      float ax = Math.abs(tr.x());
      if (ax < 0.36f || ax > 0.40f || tr.y() < -1.05f || tr.y() > -1.00f
          || tr.z() < -0.15f || tr.z() > -0.09f) {
        return;
      }
      if (Math.abs(sc.x() - 1.0f) > 0.02f || Math.abs(sc.y() - 1.0f) > 0.02f
          || Math.abs(sc.z() - 1.0f) > 0.02f) {
        return;
      }
      boolean hitbox = false;
      for (net.minecraft.world.entity.Entity o : all) {
        if (o instanceof net.minecraft.world.entity.Interaction
            && o.distanceToSqr(disp.getX(), disp.getY(), disp.getZ()) < 3 * 3) {
          hitbox = true;
          break;
        }
      }
      if (!hitbox) {
        return;
      }
      net.minecraft.world.item.Item firework = net.minecraft.core.registries.BuiltInRegistries.ITEM
          .getValue(net.minecraft.resources.Identifier.parse("minecraft:firework_star"));
      net.minecraft.world.item.ItemStack stack =
          new net.minecraft.world.item.ItemStack(firework.builtInRegistryHolder(), 1);
      stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
          new net.minecraft.world.item.component.CustomModelData(
              java.util.List.of(2380.0f), java.util.List.of(), java.util.List.of(),
              java.util.List.of()));
      slot.set(stack);
      if (OPENORIGINS$ARMS_FILLED.add(disp.getId())) {
        org.slf4j.LoggerFactory.getLogger("openorigins")
            .info("[allay-fill] arms reconstructed on #{}", disp.getId());
      }
    } catch (Throwable ignored) {
    }
  }

  /** One-line summary of items an entity carries/shows (stands, displays, living hands). */
  private static void describeCarried(net.minecraft.world.entity.Entity e, StringBuilder sb) {
    try {
      if (e instanceof net.minecraft.world.entity.Display.ItemDisplay disp) {
        net.minecraft.world.item.ItemStack ds = null;
        String slotErr = null;
        try {
          net.minecraft.world.entity.SlotAccess slot = disp.getSlot(0);
          ds = slot == null ? null : slot.get();
        } catch (Throwable t) {
          slotErr = t.toString();
        }
        if (slotErr != null) {
          sb.append(" shown=<err ").append(slotErr.length() > 80 ? slotErr.substring(0, 80) : slotErr)
              .append(">");
        } else if (ds != null && !ds.isEmpty()) {
          sb.append(" shown=").append(
              net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ds.getItem()));
          appendCmd(sb, ds);
          appendPatch(sb, ds);
        } else if (ds != null) {
          sb.append(" shown=<empty-slot>");
        } else {
          sb.append(" shown=<slot-null>");
        }
      } else if (e instanceof net.minecraft.world.entity.decoration.ArmorStand stand) {
        sb.append(stand.isMarker() ? " marker" : "").append(stand.isSmall() ? " small" : "")
            .append(stand.isInvisible() ? " invisible" : "");
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
            net.minecraft.world.entity.EquipmentSlot.MAINHAND,
            net.minecraft.world.entity.EquipmentSlot.OFFHAND,
            net.minecraft.world.entity.EquipmentSlot.HEAD }) {
          net.minecraft.world.item.ItemStack st = stand.getItemBySlot(slot);
          if (st != null && !st.isEmpty()) {
            sb.append(" ").append(slot.getSerializedName()).append("=").append(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()));
            appendCmd(sb, st);
            try {
              String hover = st.getHoverName().getString();
              if (!hover.isEmpty()) {
                sb.append("('").append(hover.length() > 40 ? hover.substring(0, 40) : hover)
                    .append("')");
              }
            } catch (Throwable ignored) {
            }
          }
        }
      } else if (e instanceof net.minecraft.world.entity.LivingEntity living) {
        net.minecraft.world.item.ItemStack mh = living.getMainHandItem();
        if (mh != null && !mh.isEmpty()) {
          sb.append(" hand=").append(
              net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mh.getItem()));
          appendCmd(sb, mh);
        }
      }
    } catch (Throwable ignored) {
    }
  }

  private static void appendCmd(StringBuilder sb, net.minecraft.world.item.ItemStack st) {
    net.minecraft.world.item.component.CustomModelData cmd =
        st.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
    if (cmd != null && !cmd.floats().isEmpty()) {
      sb.append(cmd.floats());
    }
  }

  /** Full component patch of a stack (what the server actually sent). */
  private static void appendPatch(StringBuilder sb, net.minecraft.world.item.ItemStack st) {
    try {
      net.minecraft.resources.Identifier model =
          st.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
      if (model != null) {
        String ms = model.toString();
        sb.append(" imodel=").append(ms.length() > 90 ? ms.substring(0, 90) : ms);
      }
      java.util.Set<net.minecraft.core.component.DataComponentType<?>> types =
          st.getComponents().keySet();
      if (!types.isEmpty()) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (net.minecraft.core.component.DataComponentType<?> t : types) {
          names.add(t.toString());
        }
        java.util.Collections.sort(names);
        String joined = String.join(",", names);
        sb.append(" patch=[")
            .append(joined.length() > 220 ? joined.substring(0, 220) + "..." : joined).append("]");
      }
    } catch (Throwable ignored) {
    }
  }

  private HotkeyFeatures() {}

  /** True when the pressed key is the bound scan key (works inside GUIs too). */
  public static boolean matchesScan(net.minecraft.client.input.KeyEvent event) {
    try {
      return scanKey != null && scanKey.matches(event);
    } catch (Throwable t) {
      return false;
    }
  }

  public static void register() {
    KeyMapping.Category category =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("openorigins", "keys"));
    navigatorKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.navigator", InputConstants.KEY_N, category));
    gestureKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.gesture_menu", InputConstants.KEY_G, category));
    spawnKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.spawn", InputConstants.UNKNOWN.getValue(), category));
    realmKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.realm", InputConstants.UNKNOWN.getValue(), category));
    configKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.config", InputConstants.UNKNOWN.getValue(), category));
    scanKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.scan_cosmetics", InputConstants.UNKNOWN.getValue(), category));
    teleportMenuKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.teleport_menu", InputConstants.UNKNOWN.getValue(), category));
    homesMenuKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.homes_menu", InputConstants.UNKNOWN.getValue(), category));
    blueBalloonKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.balloon_blue", InputConstants.UNKNOWN.getValue(), category));
    greenBalloonKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.balloon_green", InputConstants.UNKNOWN.getValue(), category));
    yellowBalloonKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.balloon_yellow", InputConstants.UNKNOWN.getValue(), category));
    redBalloonKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.balloon_red", InputConstants.UNKNOWN.getValue(), category));
    badgerHudKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.badger_hud", InputConstants.UNKNOWN.getValue(), category));
    armsPlusKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.allay_arms_plus", InputConstants.UNKNOWN.getValue(), category));
    armsMinusKey = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.openorigins.allay_arms_minus", InputConstants.UNKNOWN.getValue(), category));

    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      while (badgerHudKey.consumeClick()) OpenOriginsConfig.getInstance().toggleBadgerHudVisible();
      while (armsPlusKey.consumeClick()) nudgeArms(client, 0.25f);
      while (armsMinusKey.consumeClick()) nudgeArms(client, -0.25f);
      if (joinTicks % 100 == 0) sampleAllayParts(client);
      if (!com.openorigins.client.ConnectionUtil.isConnected()) {
        joinFetchDone = false;
        badgerFetchDone = false;
        worldsFetchDone = false;
        joinTicks = 0;
      } else {
        joinTicks++;
        com.openorigins.client.util.BackgroundMenu.tick();
        if (!badgerFetchDone && joinTicks > 100
            && !com.openorigins.client.util.BackgroundMenu.isArmed()) {
          badgerFetchDone = true;
          com.openorigins.client.util.BackgroundMenu.tryArm("streaks");
        }
        if (!worldsFetchDone && joinTicks > 120
            && !com.openorigins.client.util.BackgroundMenu.isArmed()
            && com.openorigins.client.util.OpenWorldCache.worlds().isEmpty()) {
          worldsFetchDone = true;
          com.openorigins.client.util.BackgroundMenu.tryArm("navigator worlds");
        }
        if (com.openorigins.client.util.BadgerCache.needsDeliveredRefresh()
            && !com.openorigins.client.util.BackgroundMenu.isArmed()
            && com.openorigins.client.util.BackgroundMenu.tryArm("streaks")) {
          com.openorigins.client.util.BadgerCache.markDeliveredRefreshSent();
        }
        if (joinTicks % 100 == 0 && client.player != null
            && !com.openorigins.client.util.BackgroundMenu.isArmed()) {
          if (com.openorigins.client.util.HomeInfoCache.layout().isEmpty()
              && System.currentTimeMillis()
                  - com.openorigins.client.util.BackgroundMenu.lastEmptyFinishMs()
                  > 10 * 60000L) {
            com.openorigins.client.util.HomeFetch.start();
          } else if (com.openorigins.client.util.HomeFetch.consumeIdleRefresh()) {
            com.openorigins.client.util.HomeFetch.start();
          } else if (com.openorigins.client.util.OpenWorldCache.shouldFetch(
              com.openorigins.client.util.HomeInfoCache.names())) {
            com.openorigins.client.util.OpenWorldCache.fetch(() -> null);
          }
        }
      }
      if (client.player != null && com.openorigins.client.ConnectionUtil.isConnected()) {
        com.openorigins.client.util.HelmetCosmeticTracker.offerReal(
            client.player.getMainHandItem(), true);
        com.openorigins.client.util.HelmetCosmeticTracker.tick();
      }
      while (scanKey.consumeClick()) {
        CosmeticScanner.scanAndReport();
        com.openorigins.client.util.ContainerDump.dump(client);
      }
      while (teleportMenuKey.consumeClick()) {
        if (client.gui.screen() == null && ConnectionUtil.isConnected()) {
          client.gui.setScreen(new com.openorigins.client.screen.TeleportMenuScreen());
        }
      }
      while (homesMenuKey.consumeClick()) {
        if (client.gui.screen() == null && ConnectionUtil.isConnected()) {
          client.gui.setScreen(new com.openorigins.client.screen.HomesMenuScreen());
        }
      }
      while (configKey.consumeClick()) {
        if (client.gui.screen() == null) {
          client.gui.setScreen(new com.openorigins.client.screen.ConfigScreen(null));
        }
      }
      if (!ConnectionUtil.isConnected() || client.player == null) return;
      while (navigatorKey.consumeClick()) client.player.connection.sendCommand("navigator");
      while (gestureKey.consumeClick()) client.player.connection.sendCommand("gesture");
      while (spawnKey.consumeClick()) client.player.connection.sendCommand("spawn");
      while (realmKey.consumeClick()) client.player.connection.sendCommand("realm tp");
      while (blueBalloonKey.consumeClick()) client.player.connection.sendCommand("balloon blue-balloon");
      while (greenBalloonKey.consumeClick()) client.player.connection.sendCommand("balloon green-balloon");
      while (yellowBalloonKey.consumeClick()) client.player.connection.sendCommand("balloon yellow-balloon");
      while (redBalloonKey.consumeClick()) client.player.connection.sendCommand("balloon red-balloon");
    });
  }

  /** Background sampler: notices allay part displays whenever they stream in, no keypress needed. */
  private static void sampleAllayParts(net.minecraft.client.Minecraft client) {
    if (client == null || client.level == null || client.player == null) {
      return;
    }
    if (!com.openorigins.client.ConnectionUtil.isConnected()) {
      return;
    }
    try {
      // ALL tracked entities (not just rendered ones): culled parts show up here too.
      java.util.List<net.minecraft.world.entity.Entity> all =
          client.level.getEntities((net.minecraft.world.entity.Entity) null,
              client.player.getBoundingBox().inflate(48));
      for (net.minecraft.world.entity.Entity e : all) {
        if (e == client.player) {
          continue;
        }
        double distSq = client.player.distanceToSqr(e.getX(), e.getY(), e.getZ());
        String type =
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())
                .toString();
        // Full nearby-entity dump (once per entity): catches non-display carriers too.
        // Displays with no synced item yet are retried until their data arrives.
        boolean defers = false;
        if (e instanceof net.minecraft.world.entity.Display.ItemDisplay disp2) {
          try {
            net.minecraft.world.entity.SlotAccess s2 = disp2.getSlot(0);
            net.minecraft.world.item.ItemStack st2 = s2 == null ? null : s2.get();
            defers = st2 == null || st2.isEmpty();
          } catch (Throwable ignored) {
            defers = true;
          }
        }
        if (distSq < 8 * 8 && (defers || OPENORIGINS$NEAR_SEEN.add(e.getId()))) {
          StringBuilder nb = new StringBuilder();
          nb.append(type).append("#").append(e.getId()).append(" ")
              .append(String.format(java.util.Locale.ROOT, "%.0fm", Math.sqrt(distSq)));
          try {
            if (e.hasCustomName() && e.getCustomName() != null) {
              nb.append(" name=").append(e.getCustomName().getString());
            }
            try {
              java.util.Set<String> tags = e.getTags();
              if (tags != null && !tags.isEmpty()) {
                java.util.List<String> sorted = new java.util.ArrayList<>(tags);
                java.util.Collections.sort(sorted);
                String joined = String.join(",", sorted);
                nb.append(" tags=[")
                    .append(joined.length() > 120 ? joined.substring(0, 120) + "..." : joined)
                    .append("]");
              }
            } catch (Throwable ignored) {
            }
            if (e.getVehicle() != null) {
              nb.append(" rides#").append(e.getVehicle().getId());
            }
            if (!e.getPassengers().isEmpty()) {
              nb.append(" carries").append(e.getPassengers().size());
            }
          } catch (Throwable ignored) {
          }
          describeCarried(e, nb);
          if (e instanceof net.minecraft.world.entity.Display display) {
            try {
              com.mojang.math.Transformation t = display.renderState().transformation().get(1.0f);
              org.joml.Vector3fc tr = t.translation();
              org.joml.Vector3fc sc = t.scale();
              nb.append(String.format(java.util.Locale.ROOT, " t=[%.2f,%.2f,%.2f] s=[%.2f,%.2f,%.2f]",
                  tr.x(), tr.y(), tr.z(), sc.x(), sc.y(), sc.z()));
            } catch (Throwable ignored) {
            }
          }
          org.slf4j.LoggerFactory.getLogger("openorigins")
              .info("[allay-scan] near {}", nb);
        }
        if (e instanceof net.minecraft.world.entity.Display.ItemDisplay fillDisp
            && distSq < 8 * 8) {
          maybeFillArms(client, all, fillDisp);
        }
        if (!(e instanceof net.minecraft.world.entity.Display.ItemDisplay disp)) {
          continue;
        }
        net.minecraft.world.item.ItemStack slotStack = null;
        try {
          net.minecraft.world.entity.SlotAccess s = disp.getSlot(0);
          slotStack = s == null ? null : s.get();
        } catch (Throwable t) {
          continue;
        }
        if (slotStack == null || slotStack.isEmpty()) {
          continue;
        }
        if (com.openorigins.client.util.AllayArmsFix.isOwnCosmetic(slotStack)) {
          continue;
        }
        net.minecraft.world.item.component.CustomModelData cmd = slotStack
            .get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null || cmd.floats().isEmpty()) {
          continue;
        }
        for (Float f : cmd.floats()) {
          if (f == null) {
            continue;
          }
          float v = f.floatValue();
          boolean interesting = (v >= 2378f && v <= 2382f) || (v >= 2404f && v <= 2405f)
              || v == 2906f || v == 2909f || v == 2913f || v == 2915f || v == 13035f || v == 13973f
              || v == 14294f;
          if (interesting && OPENORIGINS$ARMS_SEEN.add(v)) {
            double d = Math.sqrt(client.player.distanceToSqr(e.getX(), e.getY(), e.getZ()));
            org.slf4j.LoggerFactory.getLogger("openorigins").info(
                "[allay-scan] part cmd={} dist={}m entity={}#{}", v,
                String.format(java.util.Locale.ROOT, "%.0f", d),
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()),
                e.getId());
          }
        }
      }
    } catch (Throwable ignored) {
    }
  }

  private static void nudgeArms(net.minecraft.client.Minecraft client, float delta) {    java.util.Set<String> out = new java.util.LinkedHashSet<>();
    com.openorigins.client.util.AllayArmsFix.nudge(delta, out);
    if (com.openorigins.client.util.AllayArmsFix.statusLine().contains("seen: 0")) {
      out.add("[OpenOrigins] nearby carriers:");
      java.util.List<String> census = com.openorigins.client.util.AllayArmsFix.census(client);
      if (census.isEmpty()) {
        out.add("  (none within 48m)");
      } else {
        out.addAll(census);
      }
    }
    for (String line : out) {
      if (client.gui != null && client.gui.hud != null && client.gui.hud.getChat() != null) {
        client.gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(line));
      }
    }
  }
}
