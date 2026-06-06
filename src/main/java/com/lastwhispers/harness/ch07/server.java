package com.lastwhispers.harness.ch07;

public class server {
    public static void main(String user) {
        System.out.println("Server is starting on port 8080...");

        // TODO: 增加鉴权逻辑
        if (user == null) {
            System.out.println("Forbidden! 嘻嘻嘻嘻嘻嘻嘻嘻嘻");
            return;
        }
    }
}
