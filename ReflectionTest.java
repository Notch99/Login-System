import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import java.lang.reflect.Method;
public class ReflectionTest {
    public static void main(String[] args) throws Exception {
        System.out.println("ICancellableEvent methods:");
        for (Method m : ICancellableEvent.class.getMethods()) {
            System.out.println(m.getName());
        }
        System.out.println("LivingDamageEvent subclasses:");
        for (Class<?> c : LivingDamageEvent.class.getDeclaredClasses()) {
            System.out.println(c.getName());
        }
        System.out.println("ItemEntityPickupEvent subclasses:");
        for (Class<?> c : ItemEntityPickupEvent.class.getDeclaredClasses()) {
            System.out.println(c.getName());
        }
        System.out.println("ItemStack methods:");
        for (Method m : ItemStack.class.getMethods()) {
            if (m.getName().contains("save") || m.getName().contains("parse")) {
                System.out.println(m.getName() + " " + m.getParameterCount());
            }
        }
    }
}
