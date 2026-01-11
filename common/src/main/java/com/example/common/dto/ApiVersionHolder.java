package com.example.common.dto;

public class ApiVersionHolder {
    private static String version = "1.0.0";

    public static void setVersion(String apiVersion) {
        version = apiVersion;
    }

    public static String getVersion() {
        return version;
    }
}
