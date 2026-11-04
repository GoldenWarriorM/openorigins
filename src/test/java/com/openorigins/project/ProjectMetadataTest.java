package com.openorigins.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.openorigins.client.FeatureCategory;
import com.openorigins.client.OriginFeature;
import com.openorigins.client.screen.ConfigTab;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards project-wide invariants that are easy to break and expensive to spot
 * at runtime: missing translations, an unregistered mixin, lost license
 * attribution, or a new outbound URL.
 */
class ProjectMetadataTest {
  private static final Path ROOT = Path.of("").toAbsolutePath();
  private static final Path CLIENT_JAVA = ROOT.resolve("src/client/java");
  private static final Path RESOURCES = ROOT.resolve("src/client/resources");
  private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>)]+");

  @Test
  void everyFeatureHasDescriptionAndNothingLeftover() throws IOException {
    JsonObject lang = readJson(RESOURCES.resolve("assets/openorigins/lang/en_us.json"));

    Set<String> expected = new TreeSet<>();
    for (OriginFeature feature : OriginFeature.values()) {
      expected.add("feature.openorigins." + feature.name().toLowerCase(Locale.ROOT) + ".desc");
    }
    Set<String> actual = new TreeSet<>();
    for (String key : lang.keySet()) {
      if (key.startsWith("feature.openorigins.") && key.endsWith(".desc")) {
        actual.add(key);
      }
    }

    assertEquals(expected, actual);
  }

  @Test
  void everyCategoryAndConfigTabHasTitle() throws IOException {
    JsonObject lang = readJson(RESOURCES.resolve("assets/openorigins/lang/en_us.json"));

    List<String> missing = new ArrayList<>();
    for (FeatureCategory category : FeatureCategory.values()) {
      String key = category.getTranslationKey();
      if (!lang.has(key)) {
        missing.add(key);
      }
    }
    for (ConfigTab tab : ConfigTab.values()) {
      if (!lang.has(tab.getTitleKey())) {
        missing.add(tab.getTitleKey());
      }
    }

    assertTrue(missing.isEmpty(), "missing language keys: " + missing);
  }

  @Test
  void mixinConfigMatchesMixinSources() throws IOException {
    Path config = RESOURCES.resolve("openorigins.client.mixins.json");
    JsonObject root = readJson(config);
    JsonArray client = root.getAsJsonArray("client");
    assertTrue(root.get("required").getAsBoolean(), "mixins must stay required");

    Path mixinDir = CLIENT_JAVA.resolve("com/openorigins/client/mixin");
    Set<String> listed = new TreeSet<>();
    for (JsonElement element : client) {
      listed.add(element.getAsString());
    }
    Set<String> files;
    try (Stream<Path> stream = Files.list(mixinDir)) {
      files = new TreeSet<>(stream
          .map(p -> p.getFileName().toString())
          .filter(n -> n.endsWith(".java"))
          .map(n -> n.substring(0, n.length() - ".java".length()))
          .toList());
    }

    Set<String> notListed = new TreeSet<>(files);
    notListed.removeAll(listed);
    Set<String> missingFiles = new TreeSet<>(listed);
    missingFiles.removeAll(files);

    assertTrue(missingFiles.isEmpty(), "listed but no source file: " + missingFiles);
    assertTrue(notListed.isEmpty(), "source file not registered in the mixin config: " + notListed);
  }

  @Test
  void modMetadataMatchesLicenseFile() throws IOException {
    JsonObject mod = readJson(ROOT.resolve("src/main/resources/fabric.mod.json"));

    assertEquals("MIT", mod.get("license").getAsString());
    assertEquals("${version}", mod.get("version").getAsString());
    assertEquals("client", mod.get("environment").getAsString());

    Path license = ROOT.resolve("LICENSE");
    assertTrue(Files.exists(license), "LICENSE file is missing");
    assertTrue(Files.readString(license).startsWith("MIT License"), "LICENSE is not MIT");

    String icon = mod.get("icon").getAsString();
    assertTrue(Files.exists(RESOURCES.resolve(icon)), "declared icon does not exist: " + icon);
  }

  @Test
  void thirdPartyAssetsAreAttributed() throws IOException {
    Path notices = ROOT.resolve("THIRD_PARTY_NOTICES.md");
    assertTrue(Files.exists(notices));
    String text = Files.readString(notices);

    Path originAddons = RESOURCES.resolve("assets/originaddons");
    if (Files.exists(originAddons)) {
      assertTrue(text.contains("OriginAddons"), "OriginAddons assets are not attributed");
      assertTrue(text.contains("MIT License"), "OriginAddons MIT text is missing");
    }
    assertTrue(text.contains("Origins Enhanced"),
        "notices must state that Origins Enhanced content is not bundled");

    assertFalse(Files.exists(RESOURCES.resolve("assets/openorigins/textures/gui/toasts.png")),
        "the ARR toast texture must never come back");
  }

  @Test
  void readmeReferencesLicenseAndNotices() throws IOException {
    String readme = Files.readString(ROOT.resolve("README.md"));
    assertTrue(readme.contains("THIRD_PARTY_NOTICES.md"));
    assertTrue(readme.contains("LICENSE"));
  }

  @Test
  void onlyAllowlistedRemoteUrlsInJavaSources() throws IOException {
    String allowed = "https://raw.githubusercontent.com/OriginsEnhanced/assets/";
    List<String> offenders = new ArrayList<>();
    for (Path file : javaFiles()) {
      Matcher matcher = URL.matcher(Files.readString(file));
      while (matcher.find()) {
        if (!matcher.group().startsWith(allowed)) {
          offenders.add(ROOT.relativize(file) + " -> " + matcher.group());
        }
      }
    }
    assertTrue(offenders.isEmpty(), "unexpected outbound URL: " + offenders);
  }

  @Test
  void noDebugLeftoversInSources() throws IOException {
    List<String> forbidden = List.of("System.out.print", "printStackTrace", "TEMP_");
    List<String> offenders = new ArrayList<>();
    for (Path file : javaFiles()) {
      String text = Files.readString(file);
      for (String needle : forbidden) {
        if (text.contains(needle)) {
          offenders.add(ROOT.relativize(file) + " -> " + needle);
        }
      }
    }
    assertTrue(offenders.isEmpty(), "debug leftovers: " + offenders);
  }

  private static List<Path> javaFiles() throws IOException {
    try (Stream<Path> stream = Files.walk(CLIENT_JAVA)) {
      return stream.filter(p -> p.toString().endsWith(".java")).toList();
    }
  }

  private static JsonObject readJson(Path path) throws IOException {
    if (!Files.exists(path)) {
      fail("missing file: " + path);
    }
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }
}
