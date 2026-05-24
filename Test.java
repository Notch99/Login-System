import java.lang.reflect.Constructor;
import java.util.Arrays;
public class Test {
    public static void main(String[] args) throws Exception {
        try {
            Class<?> clazz = Class.forName("net.minecraft.server.BannedPlayerEntry");
            for (Constructor<?> c : clazz.getDeclaredConstructors()) {
                System.out.println(Arrays.toString(c.getParameterTypes()));
            }
        } catch (Exception e) {
            System.out.println("Could not find class. Attempting with intermediaries...");
        }
    }
}
