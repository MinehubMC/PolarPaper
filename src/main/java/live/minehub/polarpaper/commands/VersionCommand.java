package live.minehub.polarpaper.commands;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.PolarPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionCommand extends PolarCmd {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionCommand.class);

    private static final String DISCORD_URL = "https://discord.gg/n7fp52auB7";
    private static final String GITHUB_ISSUES_URL = "https://github.com/MinehubMC/PolarPaper/issues";
    private static final String GITHUB_VERSIONS_URL = "https://api.github.com/repos/MinehubMC/PolarPaper/releases?per_page=2";

    private static final long CHECK_INTERVAL = 3 * 3_600_000L; // 3 hour in millis
    private static long LAST_UPDATED = -1L;
    private static @Nullable GithubRelease CACHED_RELEASE = null;

    public VersionCommand() {
        super("version", "Display the plugin version");
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        return run(ctx);
    }

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        String currentVersion = PolarPaper.getPlugin().getPluginMeta().getVersion();

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("❄ Polar for Paper v", NamedTextColor.AQUA))
                        .append(Component.text(currentVersion, NamedTextColor.AQUA))
        );

        getLatestReleaseCached().thenAccept(release -> {
            if (release == null) return;

            String downloadUrl = release.assets.getFirst().browser_download_url;

            SemVer currentSemVer = SemVer.parse(currentVersion);
            SemVer githubSemVer = SemVer.parse(release.name);

            if (currentSemVer == null || githubSemVer == null) return;

            if (currentSemVer.equals(githubSemVer)) {
                ctx.getSource().getSender().sendMessage(Component.text("You are on the latest version!", NamedTextColor.GREEN));
            } else if (currentSemVer.isNewer(githubSemVer)) {
                ctx.getSource().getSender().sendMessage(Component.text()
                        .append(Component.text("Looks like you're on a dev version!", NamedTextColor.GREEN))
                        .appendNewline()
                        .append(Component.text("Let us know if you encounter any problems on our ", NamedTextColor.GREEN))
                        .append(Component.text("Discord", NamedTextColor.BLUE, TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.openUrl(DISCORD_URL))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to open link"))))
                        .append(Component.text(" or ", NamedTextColor.GREEN))
                        .append(Component.text("Github Issues", NamedTextColor.BLUE, TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.openUrl(GITHUB_ISSUES_URL))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to open link"))))
                        .build());
            } else {
                ctx.getSource().getSender().sendMessage(Component.text()
                        .append(Component.text("You are not on the latest version!\n", NamedTextColor.RED))
                        .append(Component.text("Latest version: v" + release.name, NamedTextColor.AQUA))
                        .append(Component.text(" (Released " + release.updated_at_relative + ") ", NamedTextColor.GRAY))
                        .append(Component.text("Download", NamedTextColor.BLUE, TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.openUrl(downloadUrl))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to go to download"))))
                );
            }
        }).exceptionally(e -> {
            LOGGER.error("Failed to get latest release", e);
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }

    public static String toRelative(String isoTime) {
        Instant pastInstant = Instant.parse(isoTime);
        Instant nowInstant = Instant.now();

        ZoneId zone = ZoneId.systemDefault();

        ZonedDateTime past = pastInstant.atZone(zone);
        ZonedDateTime now = nowInstant.atZone(zone);

        ChronoUnit[] values = ChronoUnit.values();
        for (int i = values.length - 1; i >= 0; i--) {
            ChronoUnit unit = values[i];
            if (unit == ChronoUnit.HALF_DAYS || unit == ChronoUnit.FOREVER) continue;
            long amount = unit.between(past, now);
            if (amount > 0) return amount + " " + unit.toString().toLowerCase() + " ago";
        }

        return "now";
    }

    private static CompletableFuture<@Nullable GithubRelease> getLatestReleaseCached() {
        long lastCheck = System.currentTimeMillis() - LAST_UPDATED;
        if (CACHED_RELEASE == null || lastCheck > CHECK_INTERVAL) {
            return getLatestRelease();
        }
        return CompletableFuture.completedFuture(CACHED_RELEASE);
    }

    private static CompletableFuture<@Nullable GithubRelease> getLatestRelease() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_VERSIONS_URL))
                    .build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        Gson gson = new Gson();
                        Type listOfGithubRelease = new TypeToken<List<GithubRelease>>() {}.getType();
                        List<GithubRelease> releases = gson.fromJson(body, listOfGithubRelease);
                        for (GithubRelease release : releases) {
                            if (!release.prerelease) {
                                release.updated_at_relative = toRelative(release.updated_at);
                                CACHED_RELEASE = release;
                                LAST_UPDATED = System.currentTimeMillis();
                                return release;
                            }
                        }
                        return null;
                    }).exceptionally(e -> {
                        LOGGER.error("Failed to get latest release", e);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Failed to get latest release", e);
            return null;
        }
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {

    }

    private static class GithubRelease {
        String name;
        boolean prerelease;
        String updated_at;
        String updated_at_relative;
        List<GithubAsset> assets;
    }
    private static class GithubAsset {
        String browser_download_url;
    }

    public static final class SemVer implements Comparable<SemVer> {

        private static final Pattern PATTERN = Pattern.compile(
                "^[vV]?" + // optional leading 'v'
                        "(0|[1-9]\\d*)\\." + // major
                        "(0|[1-9]\\d*)\\." + // minor
                        "(0|[1-9]\\d*)" + // patch
                        "(?:-([0-9A-Za-z.-]+))?" + // optional pre-release
                        "(?:\\+([0-9A-Za-z.-]+))?$"// optional build metadata
        );

        private final int major;
        private final int minor;
        private final int patch;
        private final String preRelease;   // null if none
        private final String buildMetadata; // null if none, ignored in comparisons
        private final String raw;

        private SemVer(int major, int minor, int patch, String preRelease, String buildMetadata, String raw) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.preRelease = preRelease;
            this.buildMetadata = buildMetadata;
            this.raw = raw;
        }

        /**
         * Parses a version string into a SemVer instance.
         *
         * @return null if the string is not valid semver
         */
        public static SemVer parse(String version) {
            if (version == null) return null;
            String trimmed = version.trim();
            Matcher m = PATTERN.matcher(trimmed);
            if (!m.matches()) return null;
            int major = Integer.parseInt(m.group(1));
            int minor = Integer.parseInt(m.group(2));
            int patch = Integer.parseInt(m.group(3));
            String preRelease = m.group(4);
            String build = m.group(5);
            return new SemVer(major, minor, patch, preRelease, build, trimmed);
        }

        /**
         * Compares two version strings.
         * @return negative if v1 < v2, 0 if equal precedence, positive if v1 > v2
         */
        public static int compare(String v1, String v2) {
            return parse(v1).compareTo(parse(v2));
        }

        public boolean isNewer(SemVer other) {
            return this.compareTo(other) > 0;
        }

        public boolean isOlder(SemVer other) {
            return this.compareTo(other) < 0;
        }

        public boolean isEqual(SemVer other) {
            return this.compareTo(other) == 0;
        }

        @Override
        public int compareTo(SemVer other) {
            if (this.major != other.major) return Integer.compare(this.major, other.major);
            if (this.minor != other.minor) return Integer.compare(this.minor, other.minor);
            if (this.patch != other.patch) return Integer.compare(this.patch, other.patch);
            return comparePreRelease(this.preRelease, other.preRelease);
        }

        /**
         * Pre-release precedence per semver spec:
         * - No pre-release > has pre-release (1.0.0 > 1.0.0-rc.1)
         * - Otherwise compare dot-separated identifiers left to right:
         *     numeric identifiers compare numerically,
         *     alphanumeric identifiers compare lexically (ASCII),
         *     numeric identifiers always have lower precedence than alphanumeric,
         *     a larger set of fields has higher precedence if all preceding fields are equal.
         */
        private static int comparePreRelease(String pre1, String pre2) {
            boolean has1 = pre1 != null;
            boolean has2 = pre2 != null;
            if (!has1 && !has2) return 0;
            if (!has1) return 1;   // this has no pre-release => newer
            if (!has2) return -1;  // other has no pre-release => this is older

            String[] parts1 = pre1.split("\\.");
            String[] parts2 = pre2.split("\\.");
            int len = Math.min(parts1.length, parts2.length);

            for (int i = 0; i < len; i++) {
                String a = parts1[i];
                String b = parts2[i];
                boolean aNum = isNumeric(a);
                boolean bNum = isNumeric(b);

                if (aNum && bNum) {
                    int cmp = Long.compare(Long.parseLong(a), Long.parseLong(b));
                    if (cmp != 0) return cmp;
                } else if (aNum) {
                    return -1; // numeric < alphanumeric
                } else if (bNum) {
                    return 1;
                } else {
                    int cmp = a.compareTo(b);
                    if (cmp != 0) return cmp;
                }
            }
            return Integer.compare(parts1.length, parts2.length);
        }

        private static boolean isNumeric(String s) {
            if (s.isEmpty()) return false;
            for (int i = 0; i < s.length(); i++) {
                if (!Character.isDigit(s.charAt(i))) return false;
            }
            return true;
        }

        public int getMajor() { return major; }
        public int getMinor() { return minor; }
        public int getPatch() { return patch; }
        public String getPreRelease() { return preRelease; }
        public String getBuildMetadata() { return buildMetadata; }

        @Override
        public String toString() {
            return raw;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SemVer)) return false;
            return this.compareTo((SemVer) o) == 0;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(major, minor, patch, preRelease);
        }
    }

}
