package tech.icc.filesrv.core;

import org.junit.jupiter.api.Tag;

/**
 * Base class for pure unit tests.
 * <p>
 * Unit tests should:
 * - Test a single component in isolation
 * - Use mocks for dependencies
 * - Run fast (< 100ms per test)
 * - Not require Spring context or external resources
 * </p>
 *
 * <p>Example:</p>
 * <pre>
 * {@code
 * class FileNameValidatorTest extends BaseUnitTest {
 *     private FileNameValidator validator;
 *
 *     @BeforeEach
 *     void setUp() {
 *         validator = new FileNameValidator();
 *     }
 *
 *     @Test
 *     void shouldRejectInvalidChars() {
 *         assertFalse(validator.isValid("file<name>.txt"));
 *     }
 * }
 * }
 * </pre>
 */
@Tag("unit")
public abstract class BaseUnitTest {
}
