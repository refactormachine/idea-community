package com.siyeh.ig.initialization;

import com.IGInspectionTestCase;

public class StaticVariableInitializationInspectionTest
        extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/initialization/static_variable_initialization",
                new StaticVariableInitializationInspection());
    }
}