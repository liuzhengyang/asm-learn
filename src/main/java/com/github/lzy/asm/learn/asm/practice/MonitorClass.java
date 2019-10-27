package com.github.lzy.asm.learn.asm.practice;

import static org.objectweb.asm.Opcodes.ASM5;

import java.util.Stack;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

import com.github.lzy.asm.learn.TestClass;
import com.github.lzy.asm.learn.asm.tools.ClassTransform;
import com.github.lzy.asm.learn.asm.tools.DumpUtils;

public class MonitorClass {
    public static void main(String[] args) {
        ClassTransform.addTransformer((loader, className, classBeingRedefined, protectionDomain, classfileBuffer) -> {
            if (className != null && className.contains("TestClass")) {
                ClassReader classReader = new ClassReader(classfileBuffer);
                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                MonitorVisitor monitorVisitor = new MonitorVisitor(classWriter);
                classReader.accept(monitorVisitor, 0);
                byte[] bytes = classWriter.toByteArray();
                DumpUtils.dump(bytes);
                return bytes;
            } else {
                return classfileBuffer;
            }
        });
        TestClass testClass = new TestClass();
        testClass.hello();
    }

    static class MonitorVisitor extends ClassVisitor {

        private String className;

        public MonitorVisitor(ClassWriter cw) {
            super(ASM5, cw);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);
            return new MonitorMethodVisitor(className, methodVisitor, access, name, descriptor);
        }
    }

    static class MonitorMethodVisitor extends AdviceAdapter {

        private final String className;
        private final String methodName;

        /**
         * Constructs a new {@link AdviceAdapter}.
         *
         * @param methodVisitor the method visitor to which this adapter delegates calls.
         * @param access        the method's access flags (see {@link Opcodes}).
         * @param name          the method's name.
         * @param descriptor    the method's descriptor (see {@link Type Type}).
         */
        protected MonitorMethodVisitor(String className, MethodVisitor methodVisitor, int access, String name, String descriptor) {
            super(ASM5, methodVisitor, access, name, descriptor);
            this.className = className;
            this.methodName = name;
        }

        @Override
        protected void onMethodEnter() {
            Method enterMethod = Method.getMethod("void enter (String,String)");
            push(className);
            push(methodName);
            invokeStatic(Type.getType(ThreadLocalTimer.class), enterMethod);
        }

        @Override
        protected void onMethodExit(int opcode) {
            Method exitMethod = Method.getMethod("void exit (String,String)");
            push(className);
            push(methodName);
            invokeStatic(Type.getType(ThreadLocalTimer.class), exitMethod);
        }
    }

    public static class ThreadLocalTimer {

        private static final ThreadLocal<Stack<Long>> TIMER = ThreadLocal.withInitial(Stack::new);

        /**
         * record method enter time nanoTime
         */
        public static void enter(String className, String methodName) {
            TIMER.get().push(System.nanoTime());
            System.out.println(String.format("%s.%s enter", className, methodName));
        }

        /**
         * return method enter nanoTime
         */
        public static void exit(String className, String methodName) {
            long enterTime = TIMER.get().pop();
            long currentTime = System.nanoTime();
            long cost = currentTime - enterTime;
            System.out.println(String.format("%s.%s exit", className, methodName));
            System.out.println(String.format("Method %s.%s cost %d nanos", className, methodName, cost));
        }

    }
}
