package com.leetsync.util;

public class FileNameUtil {

    private FileNameUtil() {
    }

    public static String createClassName(String problemTitle) {

        if (problemTitle == null || problemTitle.isBlank()) {
            throw new IllegalArgumentException(
                    "Problem title cannot be empty"
            );
        }

        String[] words = problemTitle
                .replaceAll("[^a-zA-Z0-9]+", " ")
                .trim()
                .split("\\s+");

        StringBuilder className = new StringBuilder();

        for (String word : words) {

            if (!word.isEmpty()) {

                className.append(
                        Character.toUpperCase(word.charAt(0))
                );

                if (word.length() > 1) {
                    className.append(
                            word.substring(1).toLowerCase()
                    );
                }
            }
        }

        return className.toString();
    }
}