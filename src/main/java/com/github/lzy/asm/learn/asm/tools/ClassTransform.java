package com.github.lzy.asm.learn.asm.tools;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

import net.bytebuddy.agent.ByteBuddyAgent;

public class ClassTransform {
    public static void addTransformer(ClassFileTransformer classFileTransformer) {
        Instrumentation instrumentation = ByteBuddyAgent.install();
        instrumentation.addTransformer(classFileTransformer);
    }
}
