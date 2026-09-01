package by.gusto.company.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UnpValidator implements ConstraintValidator<Unp, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return value.matches("^\\d{9}$") || value.matches("^\\d{10}$");
    }
}
