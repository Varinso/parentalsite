package com.pa.client.controllers;

import com.pa.client.ClientApp;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Special Children Support: Search special schools / therapy institutes on a map.
 * Data source: OpenStreetMap (Nominatim + Overpass). No API keys needed.
 *
 * This version:
 *  - Uses broader Overpass filters (amenity, healthcare, social_facility, specialties)
 *  - Uses proper case-insensitive regex in Overpass (",i")
 *  - Finds more autism/special-needs centers that previous strict filter missed
 */
public class SpecialSchoolsController {

    @FXML private TextField areaField;
    @FXML private ComboBox<String> typeBox;
    @FXML private VBox resultsBox;
    @FXML private WebView mapView;

    // Try mirrors if the main Overpass endpoint is busy
    private static final String[] OVERPASS_ENDPOINTS = new String[] {
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.openstreetmap.ru/api/interpreter"
    };

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private List<Place> lastResults = new ArrayList<>();

    public record Place(String name, double lat, double lon, String phone, String website, String email, String rawType) {}

    @FXML
    public void initialize() {
        typeBox.getItems().setAll(
                "All (special schools & therapy)",
                "Special Schools",
                "Therapy Centers",
                "Autism Centers",
                "Speech Therapy",
                "Inclusive Schools"
        );
        typeBox.getSelectionModel().selectFirst();

        areaField.setPromptText("Enter area/city (e.g., Dhaka, Gulshan)");
        areaField.setOnAction(e -> onSearch());

        // Default basemap centered on Bangladesh
        loadMapHTML(23.777176, 90.399452, Collections.emptyList(), 11);
    }

    @FXML public void goHome() { ClientApp.setScene("/fxml/home.fxml"); }

    @FXML
    public void onSearch() {
        String area = areaField.getText() == null ? "" : areaField.getText().trim();
        if (area.isEmpty()) {
            info("Please type an area/city (e.g., 'Dhaka' or 'Gulshan').");
            return;
        }
        String type = typeBox.getValue();
        resultsBox.getChildren().setAll(new Label("Searching…"));

        new Thread(() -> performSearch(area, type)).start();
    }

    // ---------------- Core search flow ----------------

    private void performSearch(String area, String type) {
        try {
            GeoResult geo = geocodeArea(area);
            if (geo == null) {
                Platform.runLater(() -> {
                    resultsBox.getChildren().setAll(new Label("Area not found. Try a broader name (e.g., city)."));
                    loadMapHTML(23.777176, 90.399452, Collections.emptyList(), 11);
                });
                return;
            }

            List<Place> places = queryOverpass(geo, type);
            lastResults = places;

            Platform.runLater(() -> {
                renderResultsList(places);
                loadMapHTML(geo.centerLat, geo.centerLon, places, optimalZoom(geo));
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            Platform.runLater(() -> resultsBox.getChildren().setAll(
                    new Label("Error while searching: " + ex.getMessage())));
        }
    }

    // ---------------- HTTP helpers ----------------

    private record GeoResult(double south, double west, double north, double east, double centerLat, double centerLon) {}

    private GeoResult geocodeArea(String area) throws IOException, InterruptedException {
        String url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" +
                URLEncoder.encode(area, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "ParentalAssist/1.0 (contact: example@example.com)")
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return null;

        String body = resp.body();
        if (body.length() < 5 || body.equals("[]")) return null;

        String lat = findJsonString(body, "\"lat\":\"([0-9\\.-]+)\"");
        String lon = findJsonString(body, "\"lon\":\"([0-9\\.-]+)\"");
        String bb1 = findJsonString(body, "\"boundingbox\":\\s*\\[\\s*\"([0-9\\.-]+)\"");
        String bb2 = findJsonString(body, "\"boundingbox\":\\s*\\[\\s*\"[0-9\\.-]+\"\\s*,\\s*\"([0-9\\.-]+)\"");
        String bb3 = findJsonString(body, "\"boundingbox\":\\s*\\[\\s*\"[0-9\\.-]+\"\\s*,\\s*\"[0-9\\.-]+\"\\s*,\\s*\"([0-9\\.-]+)\"");
        String bb4 = findJsonString(body, "\"boundingbox\":\\s*\\[\\s*\"[0-9\\.-]+\"\\s*,\\s*\"[0-9\\.-]+\"\\s*,\\s*\"[0-9\\.-]+\"\\s*,\\s*\"([0-9\\.-]+)\"");

        if (lat == null || lon == null || bb1 == null || bb2 == null || bb3 == null || bb4 == null) return null;

        double south = Double.parseDouble(bb1);
        double north = Double.parseDouble(bb2);
        double west  = Double.parseDouble(bb3);
        double east  = Double.parseDouble(bb4);
        double clat  = Double.parseDouble(lat);
        double clon  = Double.parseDouble(lon);
        return new GeoResult(south, west, north, east, clat, clon);
    }

    private static String findJsonString(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // ---------------- Overpass search ----------------

    private List<Place> queryOverpass(GeoResult g, String type) throws IOException, InterruptedException {
        String bbox = g.south + "," + g.west + "," + g.north + "," + g.east;

        // Keyword buckets
        String kAll      = "(special|autis|disab|inclusive|therapy|speech|occupational|neuro|sensory)";
        String kSchools  = "(special|autis|disab|inclusive)";
        String kTherapy  = "(therapy|speech|occupational|physio|autis)";

        // Build target regex per chosen type
        String nameRe;
        switch (type == null ? "" : type) {
            case "Special Schools"   -> nameRe = kSchools;
            case "Therapy Centers"   -> nameRe = kTherapy;
            case "Autism Centers"    -> nameRe = "(autis)";
            case "Speech Therapy"    -> nameRe = "(speech|speech\\s*therapy)";
            case "Inclusive Schools" -> nameRe = "(inclusive)";
            default                  -> nameRe = kAll;
        }

        // IMPORTANT: this union block MUST end with ");"
        String filter =
                "(" +
                        // Schools with matching names
                        " node[\"amenity\"=\"school\"][\"name\"~\"" + nameRe + "\",i](" + bbox + ");" +
                        " way [\"amenity\"=\"school\"][\"name\"~\"" + nameRe + "\",i](" + bbox + ");" +
                        " relation[\"amenity\"=\"school\"][\"name\"~\"" + nameRe + "\",i](" + bbox + ");" +

                        // Healthcare & clinics with matching names
                        " node[\"healthcare\"][\"name\"~\"" + nameRe + "\",i](" + bbox + ");" +
                        " way [\"healthcare\"][\"name\"~\"" + nameRe + "\",i](" + bbox + ");" +
                        " relation[\"healthcare\"][\"name\"~\"" + nameRe + "\",i](" + bbox + ");" +

                        " node[\"amenity\"~\"clinic|hospital|doctors|social_facility\"][\"name\"~\"" + nameRe + "\",i](" + bbox + ");" +
                        " way [\"amenity\"~\"clinic|hospital|doctors|social_facility\"][\"name\"~\"" + nameRe + "\",i](" + bbox + ");" +
                        " relation[\"amenity\"~\"clinic|hospital|doctors|social_facility\"][\"name\"~\"" + nameRe + "\",i](" + bbox + ");" +

                        // Specialties without name match
                        " node[\"healthcare:speciality\"~\"autism|speech|occupational|physio\",i](" + bbox + ");" +
                        " way [\"healthcare:speciality\"~\"autism|speech|occupational|physio\",i](" + bbox + ");" +
                        " relation[\"healthcare:speciality\"~\"autism|speech|occupational|physio\",i](" + bbox + ");" +

                        // Social facility for autism/disability
                        " node[\"social_facility:for\"~\"autism|disab|special\",i](" + bbox + ");" +
                        " way [\"social_facility:for\"~\"autism|disab|special\",i](" + bbox + ");" +
                        " relation[\"social_facility:for\"~\"autism|disab|special\",i](" + bbox + ");" +

                        // Explicit special-needs school tag
                        " node[\"school:special_needs\"=\"yes\"](" + bbox + ");" +
                        " way [\"school:special_needs\"=\"yes\"](" + bbox + ");" +
                        " relation[\"school:special_needs\"=\"yes\"](" + bbox + ");" +
                        ");";  // <-- the missing semicolon was the cause of HTTP 400

        String overpassQuery = """
            [out:json][timeout:40];
            %FILTER%
            out center tags;
            """.replace("%FILTER%", filter);

        // Try main endpoint, then mirrors
        String body = null;
        int lastStatus = -1;
        for (String endpoint : OVERPASS_ENDPOINTS) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", "ParentalAssist/1.0 (contact: example@example.com)")
                    .POST(HttpRequest.BodyPublishers.ofString("data=" + URLEncoder.encode(overpassQuery, StandardCharsets.UTF_8)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            lastStatus = resp.statusCode();
            if (lastStatus == 200) {
                body = resp.body();
                break;
            }
        }

        if (body == null) {
            int finalLastStatus = lastStatus;
            Platform.runLater(() -> info("Search service is busy (HTTP " + finalLastStatus + "). Please try again in a moment."));
            return Collections.emptyList();
        }

        List<Place> out = new ArrayList<>();
        for (String elem : splitElements(body)) {
            Map<String,String> tags = extractTags(elem);
            String name = tags.getOrDefault("name", "(Unnamed)");

            double lat, lon;
            if (elem.contains("\"lat\":") && elem.contains("\"lon\":")) {
                lat = parseDouble(findJsonString(elem, "\"lat\":\\s*([0-9\\.-]+)"));
                lon = parseDouble(findJsonString(elem, "\"lon\":\\s*([0-9\\.-]+)"));
            } else {
                lat = parseDouble(findJsonString(elem, "\"center\"\\s*:\\s*\\{[^}]*\"lat\"\\s*:\\s*([0-9\\.-]+)"));
                lon = parseDouble(findJsonString(elem, "\"center\"\\s*:\\s*\\{[^}]*\"lat\"\\s*:\\s*[0-9\\.-]+\\s*,\\s*\"lon\"\\s*:\\s*([0-9\\.-]+)"));
            }
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

            String phone   = firstNonEmpty(tags.get("contact:phone"), tags.get("phone"));
            String website = firstNonEmpty(tags.get("contact:website"), tags.get("website"), tags.get("url"));
            String email   = firstNonEmpty(tags.get("contact:email"), tags.get("email"));
            String rawType = firstNonEmpty(tags.get("amenity"), tags.get("healthcare"), tags.get("social_facility"), "");

            out.add(new Place(name, lat, lon, ns(phone), ns(website), ns(email), ns(rawType)));
        }
        return out;
    }


    private static String ns(String s) { return s == null ? "" : s; }
    private static double parseDouble(String s) { try { return s==null?Double.NaN:Double.parseDouble(s); } catch(Exception e){return Double.NaN;} }
    private static String firstNonEmpty(String... ss) { for (String s: ss) if (s != null && !s.isBlank()) return s; return ""; }

    private static List<String> splitElements(String json) {
        int i = json.indexOf("\"elements\"");
        if (i < 0) return Collections.emptyList();
        int start = json.indexOf("[", i); if (start < 0) return Collections.emptyList();
        int depth = 0; StringBuilder cur = new StringBuilder(); List<String> out = new ArrayList<>();
        for (int j = start+1; j < json.length(); j++) {
            char c = json.charAt(j);
            if (c == '{') { depth++; cur.append(c); }
            else if (c == '}') { depth--; cur.append(c); if (depth == 0) { out.add(cur.toString()); cur.setLength(0); } }
            else if (depth > 0) cur.append(c);
            else if (c == ']') break;
        }
        return out;
    }
    private static Map<String,String> extractTags(String elementJson) {
        Map<String,String> map = new HashMap<>();
        int i = elementJson.indexOf("\"tags\"");
        if (i < 0) return map;
        int start = elementJson.indexOf("{", i);
        int end = elementJson.indexOf("}", start);
        if (start < 0 || end < 0) return map;
        String obj = elementJson.substring(start+1, end);
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(obj);
        while (m.find()) map.put(m.group(1), m.group(2));
        return map;
    }

    // ---------------- Map rendering & UI ----------------

    private void renderResultsList(List<Place> places) {
        resultsBox.getChildren().clear();
        if (places.isEmpty()) {
            Label none = new Label("No matching institutes found here.");
            none.setStyle("-fx-text-fill:#666;");
            resultsBox.getChildren().add(none);
            return;
        }

        int idx = 0;
        for (Place p : places) {
            final int index = idx++;
            VBox card = new VBox(4);
            card.setPadding(new Insets(8));
            card.setStyle("-fx-background-color:white; -fx-background-radius:10; -fx-effect:dropshadow(gaussian, rgba(0,0,0,0.05),8,0,0,1);");

            Label name = new Label(p.name());
            name.setStyle("-fx-font-weight:bold; -fx-font-size:14px;");
            Label type = new Label(p.rawType().isBlank() ? "Institution" : p.rawType());
            type.setStyle("-fx-text-fill:#555;");
            Label phone = new Label(p.phone().isBlank() ? "Phone: —" : "Phone: " + p.phone());
            Label email = new Label(p.email().isBlank() ? "Email: —" : "Email: " + p.email());
            Hyperlink website = new Hyperlink(p.website().isBlank() ? "(No website)" : p.website());
            website.setOnAction(ev -> openExternal(p.website()));

            HBox actions = new HBox(8);
            Button focus = new Button("Show on Map");
            focus.setOnAction(ev -> focusMarker(index));
            actions.getChildren().addAll(focus);
            HBox.setHgrow(focus, Priority.NEVER);

            card.getChildren().addAll(name, type, phone, email, website, actions);
            resultsBox.getChildren().add(card);
        }
    }

    private void focusMarker(int index) {
        WebEngine eng = mapView.getEngine();
        eng.executeScript("if(window.focusMarker){focusMarker(" + index + ");}");
    }

    private int optimalZoom(GeoResult g) {
        double latSpan = Math.abs(g.north - g.south);
        double lonSpan = Math.abs(g.east - g.west);
        double span = Math.max(latSpan, lonSpan);
        if (span < 0.02) return 15;
        if (span < 0.05) return 14;
        if (span < 0.1)  return 13;
        if (span < 0.2)  return 12;
        if (span < 0.4)  return 11;
        return 10;
    }

    private void loadMapHTML(double centerLat, double centerLon, List<Place> places, int zoom) {
        StringBuilder data = new StringBuilder("[");
        for (int i = 0; i < places.size(); i++) {
            Place p = places.get(i);
            if (i > 0) data.append(",");
            data.append("{");
            data.append("\"name\":\"").append(jsEscape(p.name())).append("\",");
            data.append("\"lat\":").append(p.lat()).append(",");
            data.append("\"lon\":").append(p.lon()).append(",");
            data.append("\"phone\":\"").append(jsEscape(p.phone())).append("\",");
            data.append("\"website\":\"").append(jsEscape(p.website())).append("\",");
            data.append("\"email\":\"").append(jsEscape(p.email())).append("\"");
            data.append("}");
        }
        data.append("]");

        String html = """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css"/>
                  <style>
                    html, body, #map { height: 100%; margin: 0; padding: 0; }
                    .popup h4 { margin: 0 0 6px 0; font-size: 14px; }
                    .popup .small { color:#555; font-size: 12px; }
                  </style>
                </head>
                <body>
                  <div id="map"></div>
                  <script src="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js"></script>
                  <script>
                    var map = L.map('map').setView([%LAT%, %LON%], %ZOOM%);
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                      maxZoom: 19,
                      attribution: '&copy; OpenStreetMap'
                    }).addTo(map);

                    var data = %DATA%;
                    var markers = [];
                    for (var i=0;i<data.length;i++){
                      var d = data[i];
                      var html = '<div class="popup">'
                               + '<h4>'+escapeHtml(d.name)+'</h4>'
                               + '<div class="small">'
                               + (d.phone ? ('📞 '+escapeHtml(d.phone)+'<br/>') : '')
                               + (d.email ? ('✉️ '+escapeHtml(d.email)+'<br/>') : '')
                               + (d.website ? ('🔗 <a href="'+escapeAttr(d.website)+'" target="_blank">'+escapeHtml(d.website)+'</a>') : '')
                               + '</div></div>';
                      var m = L.marker([d.lat, d.lon]).addTo(map).bindPopup(html);
                      markers.push(m);
                    }

                    window.focusMarker = function(i){
                      if (i>=0 && i<markers.length){
                        var m = markers[i];
                        map.setView(m.getLatLng(), Math.max(map.getZoom(), 15), {animate:true});
                        m.openPopup();
                      }
                    };

                    function escapeHtml(s){ return s ? s.replace(/[&<>'"]/g, function(c){ return ({'&':'&amp;','<':'&lt;','>':'&gt;','\\'':'&#39;','\\"':'&quot;'}[c]); }) : ''; }
                    function escapeAttr(s){ return s ? s.replace(/"/g, '&quot;') : ''; }
                  </script>
                </body>
                </html>
                """
                .replace("%LAT%", String.valueOf(centerLat))
                .replace("%LON%", String.valueOf(centerLon))
                .replace("%ZOOM%", String.valueOf(zoom))
                .replace("%DATA%", data.toString());

        mapView.getEngine().loadContent(html, "text/html");
    }

    private static String jsEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private void openExternal(String url) {
        if (url == null || url.isBlank()) return;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url.startsWith("http") ? url : "http://" + url));
            }
        } catch (Exception ignored) {}
    }

    private void info(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }
}
