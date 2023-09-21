package io.github.areebgillani.boost.cache;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

public record MethodRecord(Parameter[] declaredParams, Annotation[][] declaredParamAnnotations) {
}
