package com.ai.cs.open.util;

import com.ai.cs.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ConnectorUrlGuard 单元测试：开放连接器回调地址的 SSRF 防护，
 * 环回/localhost/云元数据地址与非 http(s) 协议一律拒绝。
 */
class ConnectorUrlGuardTest {

    /** 环回、localhost、云厂商元数据 IP、非 http 协议必须全部拦截 */
    @Test
    void rejectsLoopbackAndMetadata() {
        assertThrows(BusinessException.class, () -> ConnectorUrlGuard.assertSafe("http://127.0.0.1/x"));
        assertThrows(BusinessException.class, () -> ConnectorUrlGuard.assertSafe("http://localhost/x"));
        assertThrows(BusinessException.class, () -> ConnectorUrlGuard.assertSafe("http://169.254.169.254/latest"));
        assertThrows(BusinessException.class, () -> ConnectorUrlGuard.assertSafe("ftp://example.com/x"));
    }
}
