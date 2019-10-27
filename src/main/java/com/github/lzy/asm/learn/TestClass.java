package com.github.lzy.asm.learn;

public class TestClass {
    private int a;
    private String b;
    private long c;

    public void hello() {
        System.out.println("hello");
        foo();
    }

    private void foo() {
        System.out.println("foo");
    }
}
