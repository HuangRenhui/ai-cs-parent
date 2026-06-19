# 代码规范

## 命名规范

### 1. 包命名
- 全部小写，使用点号分隔
- 格式：`com.ai.cs.{模块名}.{层级}`
- 示例：`com.ai.cs.base.controller`

### 2. 类命名
- 使用大驼峰命名法（PascalCase）
- 类名应该是名词或名词短语
- 特定后缀：
  - Controller: `XxxController`
  - Service: `XxxService`
  - ServiceImpl: `XxxServiceImpl`
  - Mapper: `XxxMapper`
  - Entity: `Xxx`（直接使用业务名）
  - DTO: `XxxDTO`
  - VO: `XxxVO`
  - Config: `XxxConfig`
  - Util: `XxxUtil`

**示例**:
```java
public class CustomerController { }
public class CustomerService { }
public class CustomerMapper { }
public class Customer { }
public class ChatDTO { }
```

### 3. 方法命名
- 使用小驼峰命名法（camelCase）
- 方法名应该是动词或动词短语
- 常用前缀：
  - 查询：`get`, `find`, `query`, `list`
  - 新增：`add`, `create`, `insert`, `save`
  - 修改：`update`, `modify`, `edit`
  - 删除：`delete`, `remove`
  - 判断：`is`, `has`, `can`, `exists`

**示例**:
```java
public Customer getCustomerById(Long id) { }
public List<Customer> listCustomers() { }
public void createCustomer(Customer customer) { }
public boolean existsByPhone(String phone) { }
```

### 4. 变量命名
- 使用小驼峰命名法（camelCase）
- 变量名应该有意义，避免单字母（除循环变量）
- 常量使用全大写，下划线分隔

**示例**:
```java
private String customerName;
private Long customerId;
private static final String DEFAULT_ENCODING = "UTF-8";
```

### 5. 数据库表命名
- 使用前缀 `cs_` 表示客服系统
- 使用小写字母和下划线
- 表名应该是名词复数或集合概念

**示例**:
```sql
cs_customer        -- 客户表
cs_chat_session    -- 会话表
cs_knowledge_faq   -- FAQ表
```

### 6. 数据库字段命名
- 使用小写字母和下划线
- 布尔类型使用 `is_`, `has_` 前缀
- 时间字段使用 `_time` 后缀

**示例**:
```sql
customer_name      -- 客户姓名
is_deleted         -- 是否删除
create_time        -- 创建时间
```

## 注释规范

### 1. 类注释
每个类必须包含类级别的 Javadoc 注释：

```java
/**
 * 客户服务控制器
 * 
 * @author huangrenhui
 * @date 2026/6/11 18:02
 * @description 提供客户管理的 RESTful API 接口
 */
@RestController
@RequestMapping("/customer")
public class CustomerController {
}
```

### 2. 方法注释
公共方法必须包含 Javadoc 注释：

```java
/**
 * 根据ID查询客户信息
 * 
 * @param id 客户ID
 * @return 客户信息
 */
@GetMapping("/{id}")
public Result<Customer> getCustomerById(@PathVariable Long id) {
    // ...
}
```

### 3. 行内注释
- 使用 `//` 进行单行注释
- 注释应该解释"为什么"而不是"是什么"
- 复杂逻辑必须添加注释说明

```java
// 计算客户的会员等级，基于消费总额
if (totalAmount > 10000) {
    customerLevel = "VIP";
} else if (totalAmount > 5000) {
    customerLevel = "Gold";
} else {
    customerLevel = "Silver";
}
```

### 4. 语言规范
- **代码注释使用英文**
- **特殊业务逻辑可使用中文注释**
- 保持注释简洁明了

## 代码风格

### 1. 缩进和格式
- 使用 4 个空格缩进（不使用 Tab）
- 左花括号 `{` 不换行
- 右花括号 `}` 独占一行

```java
public class Example {
    public void method() {
        if (condition) {
            // code
        }
    }
}
```

### 2. 空行规则
- 方法之间空一行
- 逻辑块之间空一行
- 类成员变量分组，组间空一行

```java
public class Customer {
    // ID
    private Long id;
    
    // 基本信息
    private String name;
    private String phone;
    
    // 扩展信息
    private String avatar;
    private String tag;
    
    public void method1() {
        // code
    }
    
    public void method2() {
        // code
    }
}
```

### 3. 行长度
- 每行代码不超过 120 字符
- 超长语句需要换行

### 4. 导入顺序
```java
// 1. Java 标准库
import java.util.List;
import java.util.Map;

// 2. 第三方库
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;

// 3. 项目内部包
import com.ai.cs.common.result.Result;
import com.ai.cs.base.entity.Customer;
```

## 异常处理规范

### 1. 统一异常处理
使用全局异常处理器 `GlobalExceptionHandler`：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统异常，请联系管理员");
    }
}
```

### 2. 自定义异常
业务异常使用自定义异常类：

```java
public class BusinessException extends RuntimeException {
    private Integer code;
    
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }
}
```

### 3. 异常抛出
```java
public Customer getCustomerById(Long id) {
    Customer customer = customerMapper.selectById(id);
    if (customer == null) {
        throw new BusinessException("客户不存在");
    }
    return customer;
}
```

## 返回值规范

### 1. 统一返回格式
所有接口使用 `Result` 封装返回结果：

```java
@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;
    
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }
    
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }
}
```

### 2. Controller 返回示例
```java
@GetMapping("/{id}")
public Result<Customer> getCustomerById(@PathVariable Long id) {
    Customer customer = customerService.getById(id);
    return Result.success(customer);
}

@PostMapping
public Result<Void> createCustomer(@RequestBody Customer customer) {
    customerService.save(customer);
    return Result.success(null);
}
```

## 日志规范

### 1. 日志级别
- **ERROR**: 错误信息，影响系统正常运行
- **WARN**: 警告信息，潜在问题
- **INFO**: 重要业务流程信息
- **DEBUG**: 调试信息，详细执行过程

### 2. 日志使用
```java
@Slf4j
@RestController
public class CustomerController {
    
    public Result<Customer> getCustomerById(Long id) {
        log.debug("查询客户，ID: {}", id);
        
        Customer customer = customerService.getById(id);
        if (customer == null) {
            log.warn("客户不存在，ID: {}", id);
            throw new BusinessException("客户不存在");
        }
        
        log.info("查询客户成功，ID: {}", id);
        return Result.success(customer);
    }
}
```

### 3. 日志注意事项
- 不要使用 `System.out.println()`
- 敏感信息（密码、身份证等）不记录日志
- 使用占位符 `{}` 而非字符串拼接

## 数据库操作规范

### 1. 使用 MyBatis Plus
优先使用 MyBatis Plus 提供的 CRUD 方法：

```java
@Service
public class CustomerService extends ServiceImpl<CustomerMapper, Customer> {
    
    public Customer getById(Long id) {
        return this.lambdaQuery()
                .eq(Customer::getId, id)
                .eq(Customer::getDelFlag, 0)
                .one();
    }
}
```

### 2. 复杂查询使用 XML
复杂 SQL 写在 Mapper XML 文件中：

```xml
<!-- CustomerMapper.xml -->
<select id="selectCustomerWithStats" resultType="CustomerVO">
    SELECT c.*, COUNT(s.id) as sessionCount
    FROM cs_customer c
    LEFT JOIN cs_chat_session s ON c.id = s.customer_id
    WHERE c.del_flag = 0
    GROUP BY c.id
</select>
```

### 3. 事务管理
涉及多表操作必须添加事务：

```java
@Transactional(rollbackFor = Exception.class)
public void createOrderAndSession(Order order, Session session) {
    orderService.save(order);
    sessionService.save(session);
}
```

## 测试规范

### 1. 单元测试
- 测试类命名：`XxxTest`
- 测试方法命名：`testXxx` 或使用描述性名称
- 使用 JUnit 5 + Mockito

```java
@SpringBootTest
class CustomerServiceTest {
    
    @Autowired
    private CustomerService customerService;
    
    @Test
    void testGetCustomerById() {
        Customer customer = customerService.getById(1L);
        assertNotNull(customer);
        assertEquals("测试用户", customer.getNickname());
    }
}
```

### 2. 覆盖率要求
- 核心业务逻辑：80%+
- 工具类方法：90%+
- Controller 层：70%+

## Git 提交规范

### 1. Commit Message 格式
```
<type>(<scope>): <subject>

<body>

<footer>
```

### 2. Type 类型
- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 文档更新
- `style`: 代码格式调整
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建/工具链相关

### 3. 示例
```
feat(customer): 添加客户标签管理功能

- 支持客户标签的增删改查
- 添加标签搜索功能

Closes #123
```

## 性能优化建议

1. **避免 N+1 查询**: 使用联表查询或批量查询
2. **合理使用缓存**: 热点数据使用 Redis 缓存
3. **分页查询**: 大数据量必须分页
4. **索引优化**: 为常用查询字段添加索引
5. **异步处理**: 耗时操作使用异步
6. **连接池配置**: 合理配置数据库连接池参数

## 安全建议

1. **SQL 注入防护**: 使用参数化查询
2. **XSS 防护**: 前端输入过滤和转义
3. **密码加密**: 使用 BCrypt 加密存储
4. **权限控制**: 接口级别权限验证
5. **敏感数据**: 不在日志中记录敏感信息
6. **HTTPS**: 生产环境使用 HTTPS

## 代码审查清单

在提交代码前，请检查：

- [ ] 代码符合命名规范
- [ ] 添加了必要的注释
- [ ] 使用了统一的返回格式
- [ ] 正确处理了异常情况
- [ ] 添加了适当的日志
- [ ] 没有硬编码的配置
- [ ] 敏感信息已加密
- [ ] 通过了单元测试
- [ ] 没有明显的性能问题
- [ ] Git commit message 规范
