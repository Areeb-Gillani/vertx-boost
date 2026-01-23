package io.github.areebgillani.boost.aspects;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a method parameter should be bound to a URI path variable.
 * Example: @GetMapping("/users/:id") with @PathVariable("id") String userId
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PathVariable {
    String value() default "";
}
