package com.chad.meaninglog.service.community;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 简易敏感词过滤器。启动时加载 classpath:sensitive-words.txt (UTF-8, 每行一个词)。
 * MVP 阶段使用朴素 String.contains 匹配, 后续可替换为 DFA / AC 自动机。
 */
@Component
@Slf4j
public class SensitiveWordFilter {

    private static final String RESOURCE_PATH = "sensitive-words.txt";

    private volatile Set<String> words = Collections.emptySet();

    @PostConstruct
    public void reload() {
        Set<String> loaded = new HashSet<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            log.warn("敏感词文件不存在, 敏感词过滤功能已禁用: classpath:{}", RESOURCE_PATH);
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    loaded.add(trimmed);
                }
            }
            words = Collections.unmodifiableSet(loaded);
            log.info("敏感词加载完成, 词条数: {}", loaded.size());
        } catch (IOException e) {
            log.error("加载敏感词失败", e);
        }
    }

    public boolean containsSensitive(String text) {
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return false;
        }
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    public String firstHit(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (String w : words) {
            if (text.contains(w)) {
                return w;
            }
        }
        return null;
    }
}
