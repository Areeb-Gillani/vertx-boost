package io.github.areebgillani.boost.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

public record MethodRecord(Parameter[] declaredParams, Annotation[][] declaredParamAnnotations) {
}
