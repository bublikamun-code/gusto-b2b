package by.gusto.company.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = UnpValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Unp {

    String message() default "УНП должен содержать 9 или 10 цифр";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
