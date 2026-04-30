package app.freerouting;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class UrlProtocolHandler {

  private static final String PROTOCOL_PREFIX = "freerouting://";
  private static final Set<String> SUPPORTED_ACTIONS = Set.of("start-api");

  private record ParamSpec(String cliKey, Predicate<String> validator, Function<String, String> transformer) {}

  private static final Map<String, ParamSpec> START_API_PARAMS = Map.of(
      "gui", new ParamSpec("gui.enabled", UrlProtocolHandler::isBoolean, null),
      "api", new ParamSpec("api_server.enabled", UrlProtocolHandler::isBoolean, null),
      "endpoint", new ParamSpec(null, UrlProtocolHandler::isValidEndpoint, v -> "http://" + v),
      "idle_timeout", new ParamSpec("api_server.idle_timeout", v -> isIntInRange(v, 0, 86400), null),
      "cors_origins", new ParamSpec("api_server.cors_origins", UrlProtocolHandler::isValidCorsOrigins, null),
      "max_passes", new ParamSpec("router.max_passes", v -> isIntInRange(v, 1, 9999), null),
      "max_threads", new ParamSpec("router.max_threads", v -> isIntInRange(v, 1, 1024), null),
      "via_costs", new ParamSpec("router.scoring.via_costs", v -> isIntInRange(v, 1, Integer.MAX_VALUE), null)
  );

  public static boolean isProtocolUrl(String arg) {
    return arg != null && arg.toLowerCase().startsWith(PROTOCOL_PREFIX);
  }

  public static UrlParseResult parseProtocolUrl(String url) {
    try {
      URI uri = new URI(url);

      // URI parses freerouting://v1/start-api as host=v1, path=/start-api
      // Combine host and path to get the full "v1/start-api" segment
      String host = uri.getHost();
      String uriPath = uri.getPath();
      String path;
      if (host != null && !host.isEmpty()) {
        path = host + (uriPath != null ? uriPath : "");
      } else {
        path = uri.getSchemeSpecificPart();
        if (path != null) {
          int queryIdx = path.indexOf('?');
          if (queryIdx >= 0) {
            path = path.substring(0, queryIdx);
          }
          if (path.startsWith("//")) {
            path = path.substring(2);
          }
        }
      }

      if (path == null || path.isEmpty()) {
        return UrlParseResult.error("Missing version and action in URL");
      }

      String[] pathParts = path.split("/");
      List<String> segments = new ArrayList<>();
      for (String p : pathParts) {
        if (!p.isEmpty()) segments.add(p);
      }

      if (segments.size() < 2) {
        return UrlParseResult.error("URL must contain version and action (e.g. freerouting://v1/start-api)");
      }

      String versionStr = segments.get(0);
      if (!versionStr.matches("v\\d+")) {
        return UrlParseResult.error("Invalid version format: " + versionStr);
      }
      int version = Integer.parseInt(versionStr.substring(1));
      if (version != 1) {
        return UrlParseResult.error("Unsupported version: " + versionStr);
      }

      String action = segments.get(1);
      if (!SUPPORTED_ACTIONS.contains(action)) {
        return UrlParseResult.error("Unknown action: " + action);
      }

      Map<String, ParamSpec> allowedParams = START_API_PARAMS;

      String rawQuery = uri.getRawQuery();
      Map<String, String> queryParams = parseQueryString(rawQuery);

      List<String> cliArgs = new ArrayList<>();
      String endpoint = null;

      for (Map.Entry<String, String> entry : queryParams.entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();

        ParamSpec spec = allowedParams.get(key);
        if (spec == null) {
          return UrlParseResult.error("Unknown parameter: " + key);
        }

        if (!spec.validator.test(value)) {
          return UrlParseResult.error("Invalid value for '" + key + "': " + value);
        }

        String finalValue = spec.transformer != null ? spec.transformer.apply(value) : value;

        if ("endpoint".equals(key)) {
          endpoint = finalValue;
        } else {
          cliArgs.add("--" + spec.cliKey + "=" + finalValue);
        }
      }

      return UrlParseResult.success(version, action, cliArgs.toArray(new String[0]), endpoint);

    } catch (Exception e) {
      return UrlParseResult.error("Failed to parse URL: " + e.getMessage());
    }
  }

  private static Map<String, String> parseQueryString(String rawQuery) {
    Map<String, String> params = new LinkedHashMap<>();
    if (rawQuery == null || rawQuery.isEmpty()) {
      return params;
    }
    for (String part : rawQuery.split("&")) {
      if (part.isEmpty()) continue;
      String decoded = URLDecoder.decode(part, StandardCharsets.UTF_8);
      int eq = decoded.indexOf('=');
      if (eq > 0) {
        params.put(decoded.substring(0, eq), decoded.substring(eq + 1));
      }
    }
    return params;
  }

  private static boolean isBoolean(String value) {
    return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
  }

  private static boolean isIntInRange(String value, int min, int max) {
    try {
      int n = Integer.parseInt(value);
      return n >= min && n <= max;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean isValidEndpoint(String value) {
    int colonIdx = value.lastIndexOf(':');
    if (colonIdx <= 0) return false;

    String host = value.substring(0, colonIdx);
    String portStr = value.substring(colonIdx + 1);

    if (!"127.0.0.1".equals(host) && !"localhost".equals(host)) {
      return false;
    }

    return isIntInRange(portStr, 1024, 65535);
  }

  private static boolean isValidCorsOrigins(String value) {
    if ("*".equals(value)) return false;
    for (String origin : value.split(",")) {
      origin = origin.trim();
      if (!origin.matches("^https?://[a-zA-Z0-9._-]+(:[0-9]+)?$")) {
        return false;
      }
    }
    return true;
  }

  public static class UrlParseResult {
    private final boolean valid;
    private final int version;
    private final String action;
    private final String[] cliArgs;
    private final String endpoint;
    private final String errorMessage;

    private UrlParseResult(boolean valid, int version, String action, String[] cliArgs, String endpoint, String errorMessage) {
      this.valid = valid;
      this.version = version;
      this.action = action;
      this.cliArgs = cliArgs;
      this.endpoint = endpoint;
      this.errorMessage = errorMessage;
    }

    static UrlParseResult success(int version, String action, String[] cliArgs, String endpoint) {
      return new UrlParseResult(true, version, action, cliArgs, endpoint, null);
    }

    static UrlParseResult error(String message) {
      return new UrlParseResult(false, 0, null, null, null, message);
    }

    public boolean isValid() { return valid; }
    public int getVersion() { return version; }
    public String getAction() { return action; }
    public String[] getCliArgs() { return cliArgs; }
    public String getEndpoint() { return endpoint; }
    public String getErrorMessage() { return errorMessage; }
  }
}
