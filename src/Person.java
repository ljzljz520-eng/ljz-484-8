import java.util.LinkedHashMap;
import java.util.Map;

/** 人物：家谱中的一位家族成员。 */
public class Person {
    public String id;
    public String name = "";
    public String gender = "";        // 男 / 女 / 空
    public int generation = 1;        // 世代（第几世）
    public Integer birthYear;         // 出生年，可空
    public Integer deathYear;         // 卒年，健在则为空
    public String fatherId;           // 父亲 id，可空
    public String motherId;           // 母亲 id，可空
    public String bio = "";           // 简介

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("gender", gender);
        m.put("generation", generation);
        m.put("birthYear", birthYear);
        m.put("deathYear", deathYear);
        m.put("fatherId", emptyToNull(fatherId));
        m.put("motherId", emptyToNull(motherId));
        m.put("bio", bio);
        return m;
    }

    public static Person fromMap(Map<String, Object> m) {
        Person p = new Person();
        p.id = asString(m.get("id"));
        p.name = asString(m.get("name"));
        p.gender = asString(m.get("gender"));
        p.generation = asInt(m.get("generation"), 1);
        p.birthYear = asInteger(m.get("birthYear"));
        p.deathYear = asInteger(m.get("deathYear"));
        p.fatherId = asString(m.get("fatherId"));
        p.motherId = asString(m.get("motherId"));
        p.bio = asString(m.get("bio"));
        return p;
    }

    static String emptyToNull(String s) { return s == null || s.isEmpty() ? null : s; }

    static String asString(Object o) { return o == null ? "" : String.valueOf(o); }

    static Integer asInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        return Integer.valueOf(s);
    }

    static int asInt(Object o, int def) {
        Integer v = asInteger(o);
        return v == null ? def : v;
    }
}
