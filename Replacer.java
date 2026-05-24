import java.nio.file.*;
import java.util.regex.*;
public class Replacer {
    public static void main(String[] args) throws Exception {
        Path p = Paths.get("e:/mods/LOGIN fabric v2/src/main/java/com/example/loginsystem/LoginSystem.java");
        String content = new String(Files.readAllBytes(p), "UTF-8");
        content = content.replaceAll("\\.sendMessage\\((Text\\.literal\\([^;]+?)\\);", ".sendMessage($1, false);");
        Files.write(p, content.getBytes("UTF-8"));
    }
}
