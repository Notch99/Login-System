import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class FixEncoding {
    public static void main(String[] args) throws IOException {
        String path1 = "c:/Users/lenovo/OneDrive/Documents/mods/LOGIN fabric v2/src/main/java/com/example/loginsystem/LoginSystem.java";
        String path2 = "c:/Users/lenovo/OneDrive/Documents/mods/LOGIN fabric v2/src/main/java/com/example/loginsystem/AdminGUIMenu.java";
        
        fixFile(path1);
        fixFile(path2);
        System.out.println("All string corrections completed.");
    }
    
    private static void fixFile(String path) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        
        // Emojis corrupted
        content = content.replace("âœ…", "")
                         .replace("â Œ", "")
                         .replace("â›”", "")
                         .replace("ðŸ”’", "")
                         .replace("ðŸ’¾", "")
                         .replace("ðŸ—ƒï¸ ", "")
                         .replace("ðŸŒ ", "")
                         .replace("ðŸ” ", "")
                         .replace("ðŸš€", "")
                         .replace("âš ï¸ ", "")
                         .replace("Â§", "§"); // Section sign
                         
        // Fix any weird whitespace left behind from emoji removal
        content = content.replace("Component.literal(\" ", "Component.literal(\"");
        content = content.replace("LOGGER.info(\" ", "LOGGER.info(\"");
        content = content.replace("LOGGER.warn(\" ", "LOGGER.warn(\"");
        content = content.replace("LOGGER.error(\" ", "LOGGER.error(\"");
        
        Files.write(Paths.get(path), content.getBytes(StandardCharsets.UTF_8));
    }
}
