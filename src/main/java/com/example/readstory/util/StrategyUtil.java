package com.example.readstory.util;

import com.example.readstory.common.annotation.Strategy;
import com.example.readstory.crawl.service.CrawlStrategy;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
@Slf4j
public class StrategyUtil {

    private static final Map<String, Class<? extends CrawlStrategy>> STRATEGY_MAP = getStrategyClassMap();

    @SneakyThrows
    private static Map<String, Class<? extends CrawlStrategy>> getStrategyClassMap() {
        final Set<Class<?>> classSet = ReflectionUtil.getClassByAnnotation(Strategy.class);
        return classSet.stream()
                .filter(CrawlStrategy.class::isAssignableFrom)
                .map(StrategyUtil::toStrategyClass)
                .collect(Collectors.toUnmodifiableMap(
                        clazz -> clazz.getAnnotation(Strategy.class).baseHost(),
                        clazz -> clazz));
    }

    private static Class<? extends CrawlStrategy> toStrategyClass(Class<?> clazz) {
        return clazz.asSubclass(CrawlStrategy.class);
    }

    public static CrawlStrategy getCrawlStrategy(String storyURL) {
        if (StringUtils.isEmpty(storyURL)) {
            throw new IllegalArgumentException("Story URL is empty");
        }
        final URI url = URI.create(storyURL);
        final String host = url.getHost();

        final Class<? extends CrawlStrategy> strategyClass = STRATEGY_MAP.get(host);
        if (strategyClass == null) {
            throw new IllegalArgumentException("No CrawlStrategy for host: " + host);
        }

        try {
            return strategyClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to instantiate strategy: " + strategyClass.getName(), e);
        }
    }

}
