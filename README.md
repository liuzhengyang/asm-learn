# ASM

## ASM介绍
ASM是一个流行的Java字节码操作和分析工具。可以用来修改已有的class或者动态生成新的class。ASM提供和其他字节码框架一样的功能，但是ASM的特点是[性能](https://asm.ow2.io/performance.html)出色。
ASM已经在很多开源项目中有使用
- Openjdk中，来生成[lambda call site](http://hg.openjdk.java.net/jdk8/jdk8/jdk/file/687fd7c7986d/src/share/classes/java/lang/invoke/InnerClassLambdaMetafactory.java)
- [groovy compiler](https://github.com/apache/groovy/blob/GROOVY_2_4_15/src/main/org/codehaus/groovy/classgen/AsmClassGenerator.java) 和 [kotlin compiler](https://github.com/JetBrains/kotlin/blob/v1.2.30/compiler/backend/src/org/jetbrains/kotlin/codegen/ClassBuilder.java)
- [cglib生成代理](https://github.com/cglib/cglib/blob/RELEASE_3_2_6/cglib/src/main/java/net/sf/cglib/core/ClassEmitter.java)
- gradle [运行时生成一些class](https://github.com/gradle/gradle/blob/v4.6.0/subprojects/core/src/main/java/org/gradle/api/internal/AsmBackedClassGenerator.java)

## ASM 基础

### ASM 核心类

- ClassReader: 读取一个class文件结构，并且可以在之后的accept方法中调用每个字段、方法、字节码指令的visit方法
- ClassVisitor, MethodVisitor, FieldVisitor, AnnotationVisitor: Class,Method,Field,Annotation等各个结构的visitor
- ClassWriter, MethodWriter, FieldWriter, AnnotationWriter: 对应的writer，在完成类修改后，可以通过Writer输出结果

ASM最常用的用法流程，输入为class的byte数组，先通过ClassReader读取，中间经过若干ClassVisitor对Class进行修改，最后通过Classwriter输出一个修改后的byte数组。代码示例

```
ClassReader classReader = new ClassReader(classfileBuffer);
ClassWriter classWriter = new ClassWriter(0);
MyClassVisitor myClassVisitor = new MyClassVisitor(classWriter);
classReader.accept(myClassVisitor, 0);
return classWriter.toByteArray();
```

当然也可以用ClassReader读取完成后进行其他的例如类分析处理。也可以直接通过ClassWriter生成Class

### ASM 代码结构总览

![asm package overview](https://asm.ow2.io/asm-package-overview.svg)

### Visitor

ASM中的ClassVisitor等Visitor使用了[visitor pattern](https://en.wikipedia.org/wiki/Visitor_pattern)。
> 访问者模式是一种将算法与对象结构分离的软件设计模式。
这个模式的基本想法如下：首先我们拥有一个由许多对象构成的对象结构，这些对象的类都拥有一个accept方法用来接受访问者对象；访问者是一个接口，它拥有一个visit方法，这个方法对访问到的对象结构中不同类型的元素作出不同的反应；在对象结构的一次访问过程中，我们遍历整个对象结构，对每一个元素都实施accept方法，在每一个元素的accept方法中回调访问者的visit方法，从而使访问者得以处理对象结构的每一个元素。我们可以针对对象结构设计不同的实在的访问者类来完成不同的操作。

### Writer

ClassWriter的构造方法需要一个int类型的flags参数。

COMPUTE_MAXS: 自动计算maximum stack size和maximum number of local variables of methods。

COMPUTE_FRAMES: 自动计算方法的stack map，并且也会计算max local和max stack，相当于也设置了COMPUTE_MAXS

[stack map是什么](https://stackoverflow.com/questions/25109942/what-is-a-stack-map-frame)


### ASM tree API

默认的visitor API和tree api的区别，类似xml中的sax和dom。用于使用visitor api不太方便实现的场景。


### ASM commons

commons包中对一些常见的用法进行了封装。
例如AdviceAdapter可以在方法、构造体的前后插入代码。GenerateorAdaptor简化了生成方法体code的实现。

## ASM练习

### 动态生成getter setter方法

生成思路，在ClassVisitor中的visitField执行时记录方法有哪些field字段。在visitEnd的时候，调用ClassWriter的visitMethod方法创建新方法。

getter方法的方法名为get+首字母大写的字段名，没有参数，返回字段类型，方法体为先拿到this对象，然后调用getfield指令并返回。
setter方法的方法名为set+首字母大写的字段名，参数为字段类型，没有返回类型，方法体为接收参数，调用setfield执行并返回。

```
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
```


### 打印方法执行耗时

打印耗时，由于方法执行是一个栈结构，我们同样适用一个栈来保存方法进去的时间戳，在返回时计算这个方法的耗时

```
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
```

创建MonitorMethodVisitor，在方法前后增强，分别调用ThreadLocalTimer的enter和exit方法。这里使用到了asm-commons包中的AdviceAdapter来方便在方法前后插入字节码。

```
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
```

MethodVisitor就绪后，就可以包装成ClassVisitor使用了。

```
static class MyClassVisitor extends ClassVisitor {

    public MyClassVisitor(ClassWriter cw) {
        super(ASM5, cw);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
        return new MyMethodVisitor(mv, access, name, descriptor);
    }
}
```

```
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
```

可以看到方法打印出了具体的enter、exit日志和耗时信息
```
com/github/lzy/asm/learn/TestClass.<init> enter
com/github/lzy/asm/learn/TestClass.<init> exit
Method com/github/lzy/asm/learn/TestClass.<init> cost 13874099 nanos
com/github/lzy/asm/learn/TestClass.hello enter
hello
com/github/lzy/asm/learn/TestClass.foo enter
foo
com/github/lzy/asm/learn/TestClass.foo exit
Method com/github/lzy/asm/learn/TestClass.foo cost 134286 nanos
com/github/lzy/asm/learn/TestClass.hello exit
Method com/github/lzy/asm/learn/TestClass.hello cost 559650 nanos
```

## ASM实践

### 实现一个简版的arthas

TBD

### Arthas实现简析

TBD


## 参考资料

- [asm官网](http://asm.ow2.io/)
- [asm user guilde](https://asm.ow2.io/asm4-guide.pdf)
