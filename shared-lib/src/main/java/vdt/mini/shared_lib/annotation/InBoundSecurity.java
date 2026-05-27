package vdt.mini.shared_lib.annotation;

import vdt.mini.shared_lib.enums.EndpointMethod;
import vdt.mini.shared_lib.enums.EndpointProtocol;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InBoundSecurity {
    String name();
    String path() default "";
    String topic() default "";
    EndpointMethod method() default EndpointMethod.POST;
    EndpointProtocol protocol();
    String description() default "";
}
