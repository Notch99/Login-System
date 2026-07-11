package com.example.loginsystem;

import net.minecraftforge.common.MinecraftForge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionTest {
    public static void main(String[] args) {
        System.out.println("--- Fields in MinecraftForge ---");
        for (Field f : MinecraftForge.class.getDeclaredFields()) {
            System.out.println(f.getType().getName() + " " + f.getName());
        }
        System.out.println("--- Methods in MinecraftForge ---");
        for (Method m : MinecraftForge.class.getDeclaredMethods()) {
            System.out.println(m.getReturnType().getName() + " " + m.getName());
        }
    }
}
