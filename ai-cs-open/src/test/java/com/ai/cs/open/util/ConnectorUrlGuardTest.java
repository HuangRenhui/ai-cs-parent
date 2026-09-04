package com.ai.cs.open.util;

import com.ai.cs.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectorUrlGuardTest {

    @Test
    void rejectsLoopbackAndMetadata() {
        assertThrows(BusinessException.class, () -> ConnectorUrlGuard.assertSafe("http://127.0.0.1/x"));
        assertThrows(BusinessException.class, () -> ConnectorUrlGuard.assertSafe("http://localhost/x"));
        assertThrows(BusinessException.class, () -> ConnectorUrlGuard.assertSafe("http://169.254.169.254/latest"));
        assertThrows(BusinessException.class, () -> ConnectorUrlGuard.assertSafe("ftp://example.com/x"));
    }
}
