import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 故事章节：与若干人物相关、发生在某个年代的一段家族故事。 */
public class Story {
    public String id;
    public String title = "";
    public List<String> personIds = new ArrayList<>(); // 相关人物
    public Integer yearStart;                          // 起始年，可空
    public Integer yearEnd;                            // 结束年，可空
    public String content = "";                        // 正文
    public boolean isPublic = true;                    // 公开状态：false = 私密（仅编辑端可见）
    public long createdAt;
    public long updatedAt;

    /** 所属年代（十年为一档），用于“按年代”浏览。 */
    public Integer decade() {
        return yearStart == null ? null : (yearStart / 10) * 10;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("personIds", new ArrayList<>(personIds));
        m.put("yearStart", yearStart);
        m.put("yearEnd", yearEnd);
        m.put("decade", decade());
        m.put("content", content);
        m.put("public", isPublic);
        m.put("createdAt", createdAt);
        m.put("updatedAt", updatedAt);
        return m;
    }

    public static Story fromMap(Map<String, Object> m) {
        Story s = new Story();
        s.id = Person.asString(m.get("id"));
        s.title = Person.asString(m.get("title"));
        Object ids = m.get("personIds");
        if (ids instanceof List) {
            for (Object o : (List<?>) ids) s.personIds.add(Person.asString(o));
        }
        s.yearStart = Person.asInteger(m.get("yearStart"));
        s.yearEnd = Person.asInteger(m.get("yearEnd"));
        s.content = Person.asString(m.get("content"));
        Object pub = m.get("public");
        s.isPublic = pub == null || Boolean.TRUE.equals(pub);
        s.createdAt = asLong(m.get("createdAt"));
        s.updatedAt = asLong(m.get("updatedAt"));
        return s;
    }

    private static long asLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }
}
