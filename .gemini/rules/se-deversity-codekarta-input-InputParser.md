<!-- VIBETAGS-START -->
# Rules for InputParser

## Contract-Frozen Signature
- **Constraint**: You may change internal logic, but MUST NOT modify the method name, parameters, return type, or checked exceptions.
- **Reason**: parse(Path) must never throw — all parsers wrap JavaParser calls in try/catch, log a warning, and return a partial (possibly empty) Graph. The fault-tolerance contract ensures the pipeline always produces some output.

## Polymorphic Extension Pattern
- **Pattern**: STRATEGY_PATTERN
- **Rule**: Open for extension, closed for modification. Use strategy or visitor subclasses instead of changing this file.
<!-- VIBETAGS-END -->
