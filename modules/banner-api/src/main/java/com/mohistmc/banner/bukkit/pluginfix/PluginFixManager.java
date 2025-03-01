package com.mohistmc.banner.bukkit.pluginfix;

import java.util.Objects;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class PluginFixManager {


    public static byte[] injectPluginFix(String mainClass, String className, byte[] clazz) {
        if (className.endsWith("PaperLib")) {
            return patch(clazz, PluginFixManager::removePaper);
        }
        if (className.equals("com.comphenix.protocol.ProtocolConfig")) {
            return removeProtocolASM(clazz);
        }
        if (mainClass.equals("com.sk89q.worldedit.bukkit.WorldEditPlugin")) {
            System.setProperty("worldedit.bukkit.adapter", "com.sk89q.worldedit.bukkit.adapter.impl.v1_20_R1.PaperweightAdapter");
        }
        if (mainClass.contains("FastAsyncWorldEdit")) {
            System.setProperty("worldedit.bukkit.adapter", "com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_20_R1.PaperweightFaweAdapter");
        }

        Consumer<ClassNode> patcher = switch (className) {
            case "com.sk89q.worldedit.bukkit.BukkitAdapter" -> WorldEdit::handleBukkitAdapter;
            case "com.sk89q.worldedit.bukkit.adapter.Refraction" -> WorldEdit::handlePickName;
            case "com.sk89q.worldedit.bukkit.adapter.impl.v1_20_R1.PaperweightAdapter$SpigotWatchdog" ->
                    WorldEdit::handleWatchdog;
            case "com.earth2me.essentials.utils.VersionUtil" -> node -> helloWorld(node, 110, 109);
            case "net.ess3.nms.refl.providers.ReflServerStateProvider" -> node -> helloWorld(node, "u", "U");
            case "net.Zrips.CMILib.Reflections" -> node -> helloWorld(node, "bR", "field_7512");
            case "io.lumine.mythic.core.volatilecode.v1_20_R1.VolatileEntityHandlerImpl" -> node -> helloWorld(node, "c", "d"); // mythicmobs-5.8
            default -> null;
        };
        return patcher == null ? clazz : patch(clazz, patcher);
    }

    private static byte[] patch(byte[] basicClass, Consumer<ClassNode> handler) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        handler.accept(node);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] removeProtocolASM(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        for (MethodNode method : node.methods) {
            if (Objects.equals(method.name, "isBackgroundCompilerEnabled") && Objects.equals(method.desc, "()Z")) {
                method.instructions.clear();
                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                method.instructions.add(new InsnNode(Opcodes.IRETURN));
            }
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void removePaper(ClassNode node) {
        for (MethodNode methodNode : node.methods) {
            if (methodNode.name.equals("isPaper") && methodNode.desc.equals("()Z")) {
                InsnList toInject = new InsnList();
                toInject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(PluginFixManager.class), "isPaper", "()Z"));
                toInject.add(new InsnNode(Opcodes.IRETURN));
                methodNode.instructions = toInject;
            }
        }
    }

    public static boolean isPaper() {
        return false;
    }

    private static void helloWorld(ClassNode node, String a, String b) {
        node.methods.forEach(method -> {
            for (AbstractInsnNode next : method.instructions) {
                if (next instanceof LdcInsnNode ldcInsnNode) {
                    if (ldcInsnNode.cst instanceof String str) {
                        if (a.equals(str)) {
                            ldcInsnNode.cst = b;
                        }
                    }
                }
            }
        });
    }

    private static void helloWorld(ClassNode node, int a, int b) {
        node.methods.forEach(method -> {
            for (AbstractInsnNode next : method.instructions) {
                if (next instanceof IntInsnNode ldcInsnNode) {
                    if (ldcInsnNode.operand == a) {
                        ldcInsnNode.operand = b;
                    }
                }
            }
        });
    }
}
