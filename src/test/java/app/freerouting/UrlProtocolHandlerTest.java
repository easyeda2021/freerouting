package app.freerouting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlProtocolHandlerTest {

  @Test
  void isProtocolUrl_detectsFreeroutingScheme() {
    assertTrue(UrlProtocolHandler.isProtocolUrl("freerouting://v1/start-api"));
    assertTrue(UrlProtocolHandler.isProtocolUrl("freerouting://v1/start-api?gui=false"));
    assertTrue(UrlProtocolHandler.isProtocolUrl("FREEROUTING://v1/start-api"));
  }

  @Test
  void isProtocolUrl_rejectsNonProtocol() {
    assertFalse(UrlProtocolHandler.isProtocolUrl("--gui.enabled=false"));
    assertFalse(UrlProtocolHandler.isProtocolUrl("http://example.com"));
    assertFalse(UrlProtocolHandler.isProtocolUrl(null));
    assertFalse(UrlProtocolHandler.isProtocolUrl(""));
  }

  @Test
  void parseProtocolUrl_fullParams() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?gui=false&api=true&idle_timeout=3600&max_passes=200&max_threads=4&via_costs=42");
    assertTrue(result.isValid());
    assertEquals(1, result.getVersion());
    assertEquals("start-api", result.getAction());
    assertNull(result.getEndpoint());
    String[] args = result.getCliArgs();
    assertEquals(6, args.length);
    assertArrayEquals(new String[]{
        "--gui.enabled=false",
        "--api_server.enabled=true",
        "--api_server.idle_timeout=3600",
        "--router.max_passes=200",
        "--router.max_threads=4",
        "--router.scoring.via_costs=42"
    }, args);
  }

  @Test
  void parseProtocolUrl_guiAndApiParams() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?gui=false&api=true");
    assertTrue(result.isValid());
    String[] args = result.getCliArgs();
    assertEquals(2, args.length);
    assertEquals("--gui.enabled=false", args[0]);
    assertEquals("--api_server.enabled=true", args[1]);
  }

  @Test
  void parseProtocolUrl_subsetOfParams() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?idle_timeout=1800");
    assertTrue(result.isValid());
    assertArrayEquals(new String[]{"--api_server.idle_timeout=1800"}, result.getCliArgs());
    assertNull(result.getEndpoint());
  }

  @Test
  void parseProtocolUrl_noQueryString() {
    var result = UrlProtocolHandler.parseProtocolUrl("freerouting://v1/start-api");
    assertTrue(result.isValid());
    assertEquals(0, result.getCliArgs().length);
    assertNull(result.getEndpoint());
  }

  @Test
  void parseProtocolUrl_unknownParamRejected() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?unknown_param=foo");
    assertFalse(result.isValid());
    assertTrue(result.getErrorMessage().contains("Unknown parameter"));
  }

  @Test
  void parseProtocolUrl_authenticationParamRejected() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?authentication.enabled=false");
    assertFalse(result.isValid());
    assertTrue(result.getErrorMessage().contains("Unknown parameter"));
  }

  @Test
  void parseProtocolUrl_endpointZeroRejected() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?endpoint=0.0.0.0:37864");
    assertFalse(result.isValid());
    assertTrue(result.getErrorMessage().contains("endpoint"));
  }

  @Test
  void parseProtocolUrl_endpointLocalhostAccepted() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?endpoint=127.0.0.1:37864");
    assertTrue(result.isValid());
    assertEquals("http://127.0.0.1:37864", result.getEndpoint());
    assertEquals(0, result.getCliArgs().length);
  }

  @Test
  void parseProtocolUrl_endpointLocalhostNameAccepted() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?endpoint=localhost:37864");
    assertTrue(result.isValid());
    assertEquals("http://localhost:37864", result.getEndpoint());
  }

  @Test
  void parseProtocolUrl_endpointExternalIpRejected() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?endpoint=192.168.1.1:37864");
    assertFalse(result.isValid());
  }

  @Test
  void parseProtocolUrl_portTooLowRejected() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?endpoint=127.0.0.1:80");
    assertFalse(result.isValid());
  }

  @Test
  void parseProtocolUrl_portTooHighRejected() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?endpoint=127.0.0.1:70000");
    assertFalse(result.isValid());
  }

  @Test
  void parseProtocolUrl_maxPassesNegativeRejected() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?max_passes=-1");
    assertFalse(result.isValid());
  }

  @Test
  void parseProtocolUrl_maxPassesValidAccepted() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?max_passes=100");
    assertTrue(result.isValid());
    assertArrayEquals(new String[]{"--router.max_passes=100"}, result.getCliArgs());
  }

  @Test
  void parseProtocolUrl_unknownVersionRejected() {
    var result = UrlProtocolHandler.parseProtocolUrl("freerouting://v99/start-api");
    assertFalse(result.isValid());
    assertTrue(result.getErrorMessage().contains("version"));
  }

  @Test
  void parseProtocolUrl_unknownActionRejected() {
    var result = UrlProtocolHandler.parseProtocolUrl("freerouting://v1/unknown-action");
    assertFalse(result.isValid());
    assertTrue(result.getErrorMessage().contains("action"));
  }

  @Test
  void parseProtocolUrl_corsValidOriginAccepted() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?cors_origins=https://easyeda.com");
    assertTrue(result.isValid());
    assertArrayEquals(new String[]{"--api_server.cors_origins=https://easyeda.com"}, result.getCliArgs());
  }

  @Test
  void parseProtocolUrl_corsWildcardRejected() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?cors_origins=*");
    assertFalse(result.isValid());
  }

  @Test
  void parseProtocolUrl_urlEncodedValues() {
    var result = UrlProtocolHandler.parseProtocolUrl(
        "freerouting://v1/start-api?cors_origins=https%3A%2F%2Fpro.easyeda.com");
    assertTrue(result.isValid());
    assertArrayEquals(new String[]{"--api_server.cors_origins=https://pro.easyeda.com"}, result.getCliArgs());
  }
}
