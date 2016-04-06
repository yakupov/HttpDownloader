package org.iyakupov.downloader.gui;

import javafx.beans.property.SimpleStringProperty;

/**
 * Created by Ilia on 07.04.2016.
 */
public class TestModel {
    private final SimpleStringProperty c1 = new SimpleStringProperty("");
    private final SimpleStringProperty c2 = new SimpleStringProperty("");
    //private final SimpleStringProperty c3 = new SimpleStringProperty("");
    private String c3 = "";
    private TestEnum testEnum = TestEnum.ONE;

    public TestModel() {}

    public TestModel(String c1, String c2, String c3) {
        this.c1.set(c1);
        this.c2.set(c2);
        //this.c3.set(c3);
        this.c3 = c3;
    }

    public String getC1() {
        return c1.get();
    }

    public void setC1(String c1) {
        this.c1.set(c1);
    }

    public String getC2() {
        return c2.get();
    }

    public void setC2(String c2) {
        this.c2.set(c2);
    }

    public String getC3() {
        return c3;//c3.get();
    }

    public void setC3(String c3) {
        //this.c3.set(c3);
        this.c3 = c3;
    }

    public TestEnum getTestEnum() {
        return testEnum;
    }

    public void setTestEnum(TestEnum testEnum) {
        this.testEnum = testEnum;
    }
}
