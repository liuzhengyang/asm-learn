package com.github.lzy.asm.learn.asm.practice;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASM5;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import com.github.lzy.asm.learn.TestClass;
import com.github.lzy.asm.learn.asm.tools.DumpUtils;

import net.bytebuddy.agent.ByteBuddyAgent;

public class AddGetterSetter {
    public static void main(String[] args) {
        Instrumentation instrumentation = ByteBuddyAgent.install();
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                if (!className.startsWith("com/github/lzy")) {
                    return null;
                } else {
                    try {
                        ClassReader classReader = new ClassReader(classfileBuffer);
                        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                        classReader.accept(new SimpleAddGetterSetterVisitor(classWriter), 0);
                        DumpUtils.dump(classWriter.toByteArray());
                        return classWriter.toByteArray();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }
        });
        Method[] declaredMethods = TestClass.class.getDeclaredMethods();
        System.out.println(Arrays.toString(declaredMethods));
    }

    static class SimpleAddGetterSetterVisitor extends ClassVisitor {
        private String className;
        private List<FieldStruct> fieldStructList = new ArrayList<>();

        public SimpleAddGetterSetterVisitor(ClassWriter cw) {
            super(ASM5, cw);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            fieldStructList.add(new FieldStruct(name, descriptor, className));
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public void visitEnd() {
            fieldStructList.forEach(fieldStruct -> {
                generateGetters(fieldStruct);
                generateSetters(fieldStruct);
            });
            super.visitEnd();
        }

        void generateSetters(FieldStruct fieldStruct) {
            String propertyName = fieldStruct.fieldName;
            String fieldDesc = fieldStruct.fieldDesc;
            String methodName = "set" + propertyName.substring(0, 1).toUpperCase()
                    + propertyName.substring(1);
            MethodVisitor mv =
                    cv.visitMethod(ACC_PUBLIC, methodName, "(" + fieldDesc + ")V", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(Type.getType(fieldDesc).getOpcode(ILOAD), 1);
            mv.visitFieldInsn(PUTFIELD, fieldStruct.owner, propertyName, fieldDesc);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        void generateGetters(FieldStruct fieldStruct) {
            String propertyName = fieldStruct.fieldName;
            String returnType = fieldStruct.fieldDesc;
            String owner = fieldStruct.owner;
            String methodName = "get" + propertyName.substring(0, 1).toUpperCase()
                    + propertyName.substring(1);
            MethodVisitor mv =
                    cv.visitMethod(ACC_PUBLIC, methodName, "()" + returnType, null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, owner, propertyName, returnType);
            mv.visitInsn(Type.getType(returnType).getOpcode(IRETURN));
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    static class FieldStruct {
        private String fieldName;
        private String fieldDesc;
        private String owner;

        public FieldStruct(String fieldName, String fieldDesc, String owner) {
            this.fieldName = fieldName;
            this.fieldDesc = fieldDesc;
            this.owner = owner;
        }
    }
}
