package com.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.PropertiesUtil;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);
    public static void main(String[] args) {
        PropertiesUtil props = PropertiesUtil.getProperties();
        System.out.println("Testing Log4j...");
        try {
            Class<?> clazz = Class.forName("aQute.bnd.annotation.BaselineIgnore");
            System.out.println("DEBUG: Bnd Annotation JAR Location: " +
                    clazz.getProtectionDomain().getCodeSource().getLocation());
        } catch (ClassNotFoundException e) {
            System.out.println("DEBUG: BaselineIgnore class not found on classpath");
        }
    }
}