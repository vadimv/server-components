package rsp.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Selects an offered representation using Accept weights, specificity, and parameters. */
public final class AcceptNegotiation {
    private AcceptNegotiation() { }

    /**
     * Offers are in server preference order, used to break equal-quality ties.
     * Missing Accept accepts every offer; an empty or unsupported value accepts none.
     * Invalid ranges are ignored. Matching more-specific ranges override wildcards,
     * including when their quality is zero. Repeated header fields are combined.
     */
    public static Optional<String> select(HttpHeaders headers, String... offers) {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(offers, "offers");
        List<String> fields = headers.all("Accept");
        List<Range> ranges = new ArrayList<>();
        if (fields.isEmpty()) ranges.add(parse("*/*", true));
        for (String field : fields) {
            for (String value : split(field, ',')) {
                Range range = parse(value, true);
                if (range != null) ranges.add(range);
            }
        }
        String selected = null;
        int selectedQuality = 0;
        for (String offer : offers) {
            Range representation = parse(Objects.requireNonNull(offer, "offer"), false);
            if (representation == null || representation.specificity() != 2) {
                throw new IllegalArgumentException("Invalid representation: " + offer);
            }
            Range matching = null;
            for (Range range : ranges) {
                if (!range.matches(representation)) continue;
                if (matching == null || range.specificity() > matching.specificity()
                        || range.specificity() == matching.specificity()
                        && (range.parameters.size() > matching.parameters.size()
                        || range.parameters.size() == matching.parameters.size() && range.quality > matching.quality)) {
                    matching = range;
                }
            }
            int quality = matching == null ? 0 : matching.quality;
            if (quality > selectedQuality) {
                selected = offer;
                selectedQuality = quality;
            }
        }
        return Optional.ofNullable(selected);
    }

    private static Range parse(String value, boolean accept) {
        List<String> parts = split(value, ';');
        if (parts.isEmpty()) return null;
        String[] type = parts.getFirst().trim().toLowerCase(Locale.ROOT).split("/", -1);
        if (type.length != 2 || !token(type[0]) || !token(type[1])
                || type[0].equals("*") && !type[1].equals("*")) return null;
        Map<String, String> parameters = new LinkedHashMap<>();
        int quality = 1000;
        boolean weighted = false;
        for (int i = 1; i < parts.size(); i++) {
            String part = parts.get(i).trim();
            int equals = part.indexOf('=');
            if (equals <= 0) return null;
            String name = part.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String raw = part.substring(equals + 1).trim();
            String parameter = parameter(raw);
            if (!token(name) || parameter == null) return null;
            if (accept && name.equals("q")) {
                if (weighted || !raw.matches("(?:0(?:\\.\\d{0,3})?|1(?:\\.0{0,3})?)")) return null;
                weighted = true;
                quality = (int) Math.round(Double.parseDouble(raw) * 1000);
            } else {
                if (name.equals("charset")) parameter = parameter.toLowerCase(Locale.ROOT);
                if (parameters.putIfAbsent(name, parameter) != null) return null;
            }
        }
        return new Range(type[0], type[1], Map.copyOf(parameters), quality);
    }

    private static boolean token(String value) {
        return value.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    }

    private static String parameter(String value) {
        if (token(value)) return value;
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') return null;
        StringBuilder decoded = new StringBuilder();
        for (int i = 1; i < value.length() - 1; i++) {
            char ch = value.charAt(i);
            if (ch == '\\') {
                if (++i >= value.length() - 1) return null;
                ch = value.charAt(i);
            } else if (ch == '"') return null;
            if (ch < 32 && ch != '\t' || ch == 127) return null;
            decoded.append(ch);
        }
        return decoded.toString();
    }

    private static List<String> split(String value, char separator) {
        List<String> parts = new ArrayList<>();
        boolean quoted = false;
        boolean escaped = false;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) escaped = false;
            else if (quoted && ch == '\\') escaped = true;
            else if (ch == '"') quoted = !quoted;
            else if (ch == separator && !quoted) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        if (quoted || escaped) return List.of();
        parts.add(value.substring(start));
        return parts;
    }

    private record Range(String type, String subtype, Map<String, String> parameters, int quality) {
        int specificity() { return type.equals("*") ? 0 : subtype.equals("*") ? 1 : 2; }

        boolean matches(Range representation) {
            return (type.equals("*") || type.equals(representation.type))
                    && (subtype.equals("*") || subtype.equals(representation.subtype))
                    && representation.parameters.entrySet().containsAll(parameters.entrySet());
        }
    }
}
