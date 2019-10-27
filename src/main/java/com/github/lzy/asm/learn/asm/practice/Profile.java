package com.github.lzy.asm.learn.asm.practice;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ASM5;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.LADD;
import static org.objectweb.asm.Opcodes.LSUB;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import com.github.lzy.asm.learn.TestClass;

import net.bytebuddy.agent.ByteBuddyAgent;

public class Profile {
    public static void main(String[] args) {
        instrumentProfile();
    }

    public static void instrumentProfile() {
        Instrumentation install = ByteBuddyAgent.install();
        install.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                if (className.contains("TestClass")) {
                    ClassReader classReader = new ClassReader(classfileBuffer);
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    classReader.accept(new ProfileVisitor(classWriter), ClassReader.SKIP_FRAMES);
                    return classWriter.toByteArray();
                }
                return classfileBuffer;
            }
        });
        TestClass testClass = new TestClass();
        testClass.hello();

    }

    private static class ProfileVisitor extends ClassVisitor {

        public ProfileVisitor(ClassWriter cw) {
            super(ASM5, cw);
        }

        @Override
        public void visitEnd() {
            cv.visitField(ACC_PRIVATE + ACC_STATIC, "timer", "J", null, null);
            super.visitEnd();
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
            if (!name.equals("<init>") && !Modifier.isAbstract(access)) {
                return new MethodProfiler(mv);
            }
            return mv;
        }
    }

    private static class MethodProfiler extends MethodVisitor {

        public MethodProfiler(MethodVisitor mv) {
            super(ASM5, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            mv.visitFieldInsn(GETSTATIC, "com/github/lzy/asm/learn/TestClass", "timer", "J");
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
            mv.visitInsn(LSUB);
            mv.visitFieldInsn(PUTSTATIC, "com/github/lzy/asm/learn/TestClass", "timer", "J");
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode >= IRETURN && opcode <= RETURN) {
                mv.visitFieldInsn(GETSTATIC, "com/github/lzy/asm/learn/TestClass", "timer", "J");
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
                mv.visitInsn(LADD);
                mv.visitFieldInsn(PUTSTATIC, "com/github/lzy/asm/learn/TestClass", "timer", "J");
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                mv.visitLdcInsn("Cost ");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                mv.visitFieldInsn(GETSTATIC, "com/github/lzy/asm/learn/TestClass", "timer", "J");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;", false);
                mv.visitLdcInsn(" ns");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            }
            mv.visitInsn(opcode);
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            mv.visitMaxs(maxStack + 4, maxLocals);
        }
    }
}
