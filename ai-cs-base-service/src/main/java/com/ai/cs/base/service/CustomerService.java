package com.ai.cs.base.service;

import com.ai.cs.base.entity.Customer;
import com.ai.cs.base.mapper.CustomerMapper;
import com.ai.cs.base.support.DefaultAvatarPicker;
import com.ai.cs.common.exception.BusinessException;
import com.ai.cs.common.util.ValidateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 客户服务
 *
 * @author huangrenhui
 * @date 2026/6/11 18:14
 */
@Service
public class CustomerService extends ServiceImpl<CustomerMapper, Customer> {

    public List<Customer> listByKeyword(String keyword) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Customer::getPhone, keyword)
                    .or().like(Customer::getEmail, keyword)
                    .or().like(Customer::getNickname, keyword)
                    .or().like(Customer::getCustomerTag, keyword));
        }
        wrapper.orderByDesc(Customer::getCreateTime);
        return this.list(wrapper);
    }

    public void saveCustomer(Customer customer) {
        if (customer == null) {
            throw new BusinessException("客户信息不能为空");
        }
        ValidateUtil.requireMobile(customer.getPhone());
        ValidateUtil.optionalGender(customer.getGender());
        ValidateUtil.optionalEmail(customer.getEmail());
        ValidateUtil.optionalNickname(customer.getNickname());
        ValidateUtil.optionalAvatar(customer.getAvatar());
        ValidateUtil.optionalTag(customer.getCustomerTag());

        customer.setPhone(ValidateUtil.trimToNull(customer.getPhone()));
        customer.setEmail(ValidateUtil.trimToNull(customer.getEmail()));
        customer.setNickname(ValidateUtil.trimToNull(customer.getNickname()));
        customer.setAvatar(ValidateUtil.trimToNull(customer.getAvatar()));
        customer.setCustomerTag(ValidateUtil.trimToNull(customer.getCustomerTag()));
        if (customer.getGender() == null || customer.getGender() == 0) {
            customer.setGender(null);
        }
        if (!StringUtils.hasText(customer.getAvatar())
                || (DefaultAvatarPicker.isSystemDefault(customer.getAvatar())
                && !DefaultAvatarPicker.matchesGender(customer.getAvatar(), customer.getGender()))) {
            customer.setAvatar(DefaultAvatarPicker.pick(customer.getGender()));
        }

        LambdaQueryWrapper<Customer> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(Customer::getPhone, customer.getPhone());
        if (customer.getId() != null) {
            phoneWrapper.ne(Customer::getId, customer.getId());
        }
        if (this.count(phoneWrapper) > 0) {
            throw new BusinessException("该手机号已存在");
        }
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
