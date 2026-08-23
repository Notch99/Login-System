import net.minecraft.resources.ResourceKey;

public class Test {
    public static void main(String[] args) {
        for (java.lang.reflect.Method m : ResourceKey.class.getDeclaredMethods()) {
            System.out.println(m.getName() + " -> " + m.getReturnType().getName());
        }
    }
}
