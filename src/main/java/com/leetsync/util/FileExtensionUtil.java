package com.leetsync.util;

import java.util.Map;

public class FileExtensionUtil {

    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("java", ".java"),
            Map.entry("python", ".py"),
            Map.entry("python3", ".py"),
            Map.entry("javascript", ".js"),
            Map.entry("js", ".js"),
            Map.entry("typescript", ".ts"),
            Map.entry("cpp", ".cpp"),
            Map.entry("c++", ".cpp"),
            Map.entry("c", ".c"),
            Map.entry("csharp", ".cs"),
            Map.entry("c#", ".cs"),
            Map.entry("kotlin", ".kt"),
            Map.entry("go", ".go"),
            Map.entry("golang", ".go"),
            Map.entry("rust", ".rs"),
            Map.entry("php", ".php"),
            Map.entry("ruby", ".rb"),
            Map.entry("swift", ".swift"),
            Map.entry("scala", ".scala")
    );

    private FileExtensionUtil() {
    }

    public static String getExtension(String language) {

        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("Language cannot be empty");
        }

        String normalizedLanguage = language
                .trim()
                .toLowerCase();

        String extension = EXTENSIONS.get(normalizedLanguage);

        if (extension == null) {
            throw new IllegalArgumentException(
                    "Unsupported programming language: " + language
            );
        }

        return extension;
    }
}