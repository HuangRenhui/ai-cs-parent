package com.ai.cs.base.service;

import com.ai.cs.base.entity.Customer;
import com.ai.cs.base.mapper.CustomerMapper;
import com.ai.cs.base.support.DefaultAvatarPicker;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.util.ValidateUtil;
import com.ai.cs.common.result.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户服务
 *
 * @author huangrenhui
 * @date 2026/6/11 18:14
 */
@Service
public class CustomerService extends ServiceImpl<CustomerMapper, Customer> {

    /**
     * 按关键字搜索客户（手机号/邮箱/昵称/标签模糊匹配），按创建时间倒序
     */
    public List<Customer> listByKeyword(String keyword) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // and 包裹保证多个 or 条件作为一个整体参与查询
            wrapper.and(w -> w.like(Customer::getPhone, keyword)
                    .or().like(Customer::getEmail, keyword)
                    .or().like(Customer::getNickname, keyword)
                    .or().like(Customer::getCustomerTag, keyword));
        }
        wrapper.orderByDesc(Customer::getCreateTime);
        // 旧接口兜底上限，避免全表扫描拖垮数据库
        wrapper.last("limit 200");
        return this.list(wrapper);
    }

    /**
     * 分页查询客户，支持关键字与创建时间范围，避免全表拉取。
     *
     * @param pageNum   页码（从 1 开始）
     * @param pageSize  每页条数（上限 100）
     * @param keyword   手机号/邮箱/昵称/标签模糊匹配
     * @param startTime 创建时间起（含）
     * @param endTime   创建时间止（含）
     */
    public PageResult<Customer> pageByKeyword(int pageNum, int pageSize, String keyword,
                                              LocalDateTime startTime, LocalDateTime endTime) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int page = Math.max(pageNum, 1);
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Customer::getPhone, keyword)
                    .or().like(Customer::getEmail, keyword)
                    .or().like(Customer::getNickname, keyword)
                    .or().like(Customer::getCustomerTag, keyword));
        }
        if (startTime != null) {
            wrapper.ge(Customer::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(Customer::getCreateTime, endTime);
        }
        wrapper.orderByDesc(Customer::getCreateTime);
        Page<Customer> result = this.page(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    /**
     * 新增或更新客户（id 为空走新增，非空走更新）
     */
    public void saveCustomer(Customer customer) {
        if (customer == null) {
            throw new BusinessException("客户信息不能为空");
        }
        // 字段校验：手机号必填，其余可选但需符合格式
        ValidateUtil.requireMobile(customer.getPhone());
        ValidateUtil.optionalGender(customer.getGender());
        ValidateUtil.optionalEmail(customer.getEmail());
        ValidateUtil.optionalNickname(customer.getNickname());
        ValidateUtil.optionalAvatar(customer.getAvatar());
        ValidateUtil.optionalTag(customer.getCustomerTag());

        // 统一去空格，空串转 null，避免脏数据入库
        customer.setPhone(ValidateUtil.trimToNull(customer.getPhone()));
        customer.setEmail(ValidateUtil.trimToNull(customer.getEmail()));
        customer.setNickname(ValidateUtil.trimToNull(customer.getNickname()));
        customer.setAvatar(ValidateUtil.trimToNull(customer.getAvatar()));
        customer.setCustomerTag(ValidateUtil.trimToNull(customer.getCustomerTag()));
        // 未选性别（null/0）统一按 null 入库
        if (customer.getGender() == null || customer.getGender() == 0) {
            customer.setGender(null);
        }
        // 头像兜底：未上传头像，或头像是系统默认图但与性别不匹配时，按性别重新随机一个默认头像
        if (!StringUtils.hasText(customer.getAvatar())
                || (DefaultAvatarPicker.isSystemDefault(customer.getAvatar())
                && !DefaultAvatarPicker.matchesGender(customer.getAvatar(), customer.getGender()))) {
            customer.setAvatar(DefaultAvatarPicker.pick(customer.getGender()));
        }

        // 手机号唯一校验，更新时排除自身
        LambdaQueryWrapper<Customer> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(Customer::getPhone, customer.getPhone());
        if (customer.getId() != null) {
            phoneWrapper.ne(Customer::getId, customer.getId());
        }
        if (this.count(phoneWrapper) > 0) {
            throw new BusinessException("该手机号已存在");
        }
        // 邮箱唯一校验（仅填写了邮箱时）
        if (StringUtils.hasText(customer.getEmail())) {
            LambdaQueryWrapper<Customer> emailWrapper = new LambdaQueryWrapper<>();
            emailWrapper.eq(Customer::getEmail, customer.getEmail());
            if (customer.getId() != null) {
                emailWrapper.ne(Customer::getId, customer.getId());
            }
            if (this.count(emailWrapper) > 0) {
                throw new BusinessException("该邮箱已存在");
            }
        }
        if (customer.getId() == null) {
            this.save(customer);
        } else {
            this.updateById(customer);
        }
    }
}
