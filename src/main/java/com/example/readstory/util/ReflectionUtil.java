package com.example.readstory.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
@Slf4j
public class ReflectionUtil {
    private static final ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);


    @SuppressWarnings("unchecked")
    public static Set<Class<?>> getClassByAnnotation(Class<?> queryClazz) {
        if (!queryClazz.isAnnotation()) {
            throw new  IllegalArgumentException("Class must be Annotation");
        }
        final Class<? extends Annotation> queryAnnotation = (Class<? extends Annotation>) queryClazz;
        scanner.addIncludeFilter(new AnnotationTypeFilter(queryAnnotation));
        return scanner.findCandidateComponents("com.example.readstory")
                .stream()
                .map(BeanDefinition::getBeanClassName)
                .map(ReflectionUtil::loadClassSafely)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Class<?> loadClassSafely(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            log.warn("Failed to load class {}", className, e);
            return null;
        }
    }
}
