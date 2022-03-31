package de.geolykt.starloader.deobf.access;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

import de.geolykt.starloader.deobf.access.AccessFlagModifier.Type;

public final class AccessTransformInfo {

    final List<AccessFlagModifier> modifiers = new ArrayList<>();

    public List<AccessFlagModifier> getModifiers() {
        return Collections.unmodifiableList(modifiers);
    }

    public void apply(Map<String, ClassNode> nodes, Consumer<String> warnLogger) {
        Map<String, List<AccessFlagModifier>> innerClasses = new HashMap<>();

        for (AccessFlagModifier flag : modifiers) {
            ClassNode node = nodes.get(flag.clazz);
            if (node == null) {
                warnLogger.accept("Cannot locate class: " + flag.clazz + " required by " + flag.toAccessWidenerString());
                continue;
            }
            if (flag.type == Type.CLASS) {
                node.access = flag.apply(node.access);
                for (InnerClassNode icn : node.innerClasses) {
                    if (icn.name.equals(node.name)) {
                        icn.access = flag.apply(icn.access);
                        innerClasses.computeIfAbsent(node.name, key -> new ArrayList<>()).add(flag);
                    }
                }
                continue;
            } else if (flag.type == Type.FIELD) {
                boolean found = false;
                for (FieldNode field : node.fields) {
                    if (field.name.equals(flag.name.get()) && field.desc.equals(flag.descriptor.get())) {
                        found = true;
                        field.access = flag.apply(field.access);
                    }
                }
                if (!found) {
                    warnLogger.accept("Cannot find field required by access widener: " + flag.toAccessWidenerString());
                }
            } else if (flag.type == Type.METHOD) {
                boolean found = false;
                for (MethodNode method : node.methods) {
                    if (method.name.equals(flag.name.get()) && method.desc.equals(flag.descriptor.get())) {
                        found = true;
                        method.access = flag.apply(method.access);
                    }
                }
                if (!found) {
                    warnLogger.accept("Cannot find method required by access widener: " + flag.toAccessWidenerString());
                }
            } else {
                throw new IllegalStateException("Unkown type: " + Objects.toString(flag.type));
            }
        }

        if (!innerClasses.isEmpty()) {
            for (ClassNode node : nodes.values()) {
                for (InnerClassNode icn : node.innerClasses) {
                    List<AccessFlagModifier> flags = innerClasses.get(icn.name);
                    if (flags != null) {
                        for (AccessFlagModifier modifier : flags) {
                            icn.access = modifier.apply(icn.access);
                        }
                    }
                }
            }
        }
    }
}
