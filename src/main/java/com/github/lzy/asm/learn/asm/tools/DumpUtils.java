package com.github.lzy.asm.learn.asm.tools;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javassist.ClassPool;
import javassist.CtClass;

public class DumpUtils {
    public static void dump(byte[] bytes) {
        ClassPool classPool = ClassPool.getDefault();
        try {
            CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(bytes));
            ctClass.debugWriteFile("/tmp");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
