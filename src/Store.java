import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 存储层：把人物与故事保存为 data/ 目录下的本地 JSON 文件。
 * 每次写操作先写临时文件再原子替换，避免文件损坏。
 */
public class Store {
    private final File peopleFile;
    private final File storiesFile;
    private final List<Person> people = new ArrayList<>();
    private final List<Story> stories = new ArrayList<>();

    public Store(File dataDir) {
        this.peopleFile = new File(dataDir, "people.json");
        this.storiesFile = new File(dataDir, "stories.json");
        load();
    }

    // ---------------- 人物 ----------------

    public synchronized List<Person> listPeople() {
        return new ArrayList<>(people);
    }

    public synchronized Person savePerson(Person p) {
        if (p.id == null || p.id.isEmpty()) {
            p.id = newId("p");
            people.add(p);
        } else {
            int idx = indexOfPerson(p.id);
            if (idx >= 0) people.set(idx, p); else people.add(p);
        }
        persist(peopleFile, peopleToJson());
        return p;
    }

    /** 删除人物，并解除故事与其的关联（故事本身保留）。 */
    public synchronized boolean deletePerson(String id) {
        boolean removed = people.removeIf(p -> p.id.equals(id));
        if (removed) {
            for (Story s : stories) s.personIds.remove(id);
            persist(peopleFile, peopleToJson());
            persist(storiesFile, storiesToJson());
        }
        return removed;
    }

    // ---------------- 故事 ----------------

    /**
     * 查询故事。
     * @param publicOnly true 时只返回公开故事（浏览端）；false 返回全部（编辑端）
     * @param personId   按人物过滤，null 不过滤
     * @param decade     按年代过滤（如 1970），null 不过滤
     */
    public synchronized List<Story> listStories(boolean publicOnly, String personId, Integer decade) {
        List<Story> out = new ArrayList<>();
        for (Story s : stories) {
            if (publicOnly && !s.isPublic) continue;
            if (personId != null && !s.personIds.contains(personId)) continue;
            if (decade != null && (s.decade() == null || !s.decade().equals(decade))) continue;
            out.add(s);
        }
        return out;
    }

    public synchronized Story saveStory(Story s) {
        long now = System.currentTimeMillis();
        s.updatedAt = now;
        if (s.id == null || s.id.isEmpty()) {
            s.id = newId("s");
            s.createdAt = now;
            stories.add(s);
        } else {
            int idx = indexOfStory(s.id);
            if (idx >= 0) {
                s.createdAt = stories.get(idx).createdAt;
                stories.set(idx, s);
            } else {
                s.createdAt = now;
                stories.add(s);
            }
        }
        persist(storiesFile, storiesToJson());
        return s;
    }

    public synchronized boolean deleteStory(String id) {
        boolean removed = stories.removeIf(s -> s.id.equals(id));
        if (removed) persist(storiesFile, storiesToJson());
        return removed;
    }

    // ---------------- 内部 ----------------

    private int indexOfPerson(String id) {
        for (int i = 0; i < people.size(); i++) if (people.get(i).id.equals(id)) return i;
        return -1;
    }

    private int indexOfStory(String id) {
        for (int i = 0; i < stories.size(); i++) if (stories.get(i).id.equals(id)) return i;
        return -1;
    }

    private List<Map<String, Object>> peopleToJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Person p : people) list.add(p.toMap());
        return list;
    }

    private List<Map<String, Object>> storiesToJson() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Story s : stories) list.add(s.toMap());
        return list;
    }

    private void load() {
        people.clear();
        stories.clear();
        for (Object o : readArray(peopleFile)) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                people.add(Person.fromMap(m));
            }
        }
        for (Object o : readArray(storiesFile)) {
            if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                stories.add(Story.fromMap(m));
            }
        }
        System.out.println("[Store] 已加载 " + people.size() + " 个人物、" + stories.size() + " 个故事");
    }

    private static List<Object> readArray(File f) {
        if (!f.exists()) return new ArrayList<>();
        try {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return new ArrayList<>();
            return Json.parseArray(text);
        } catch (Exception e) {
            System.err.println("[Store] 读取 " + f + " 失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static void persist(File f, Object data) {
        try {
            f.getParentFile().mkdirs();
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            Files.write(tmp.toPath(), (Json.pretty(data) + "\n").getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[Store] 写入 " + f + " 失败: " + e.getMessage());
        }
    }

    private static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
