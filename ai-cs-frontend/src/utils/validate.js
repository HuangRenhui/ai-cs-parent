export const MOBILE = /^1[3-9]\d{9}$/
export const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
export const URL = /^https?:\/\/[\w.-]+(?::\d+)?(?:[/\w.?%&=+-]*)?$/i
export const ACCOUNT = /^[A-Za-z][A-Za-z0-9_]{3,31}$/
export const NICKNAME = /^[\u4e00-\u9fa5A-Za-z0-9_·-]{1,32}$/
export const TAG = /^[\u4e00-\u9fa5A-Za-z0-9_-]{1,20}$/

const blank = (value) => value == null || String(value).trim() === ''

export const rules = {
  requiredMobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: MOBILE, message: '请输入11位大陆手机号', trigger: 'blur' }
  ],
  optionalEmail: [
    {
      validator: (_rule, value, callback) => {
        if (blank(value) || EMAIL.test(String(value).trim())) callback()
        else callback(new Error('邮箱格式不正确'))
      },
      trigger: 'blur'
    }
  ],
  optionalUrl: (label = '链接') => [
    {
      validator: (_rule, value, callback) => {
        if (blank(value) || URL.test(String(value).trim())) callback()
        else callback(new Error(label + '必须是 http/https 地址'))
      },
      trigger: 'blur'
    }
  ],
  optionalNickname: [
    {
      validator: (_rule, value, callback) => {
        if (blank(value) || NICKNAME.test(String(value).trim())) callback()
        else callback(new Error('昵称仅支持中文、字母、数字，1-32 个字符'))
      },
      trigger: 'blur'
    }
  ],
  requiredNickname: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { pattern: NICKNAME, message: '姓名仅支持中文、字母、数字，1-32 个字符', trigger: 'blur' }
  ],
  optionalTag: [
    {
      validator: (_rule, value, callback) => {
        if (blank(value) || TAG.test(String(value).trim())) callback()
        else callback(new Error('标签仅支持中文、字母、数字，最长 20 个字符'))
      },
      trigger: 'blur'
    }
  ],
  requiredAccount: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: ACCOUNT, message: '账号需以字母开头，4-32 位字母数字或下划线', trigger: 'blur' }
  ],
  optionalPassword: (required = false) => [
    {
      validator: (_rule, value, callback) => {
        if (blank(value)) {
          if (required) callback(new Error('请输入密码'))
          else callback()
          return
        }
        const text = String(value)
        if (text.length < 6 || text.length > 32) {
          callback(new Error('密码长度为 6-32 位'))
          return
        }
        if (/^[0-9]+$/.test(text) || /^[A-Za-z]+$/.test(text)) {
          callback(new Error('密码需同时包含字母和数字'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  optionalSessionId: [
    {
      validator: (_rule, value, callback) => {
        if (blank(value) || /^[A-Za-z0-9_-]{4,64}$/.test(String(value).trim())) callback()
        else callback(new Error('会话ID格式不正确'))
      },
      trigger: 'blur'
    }
  ],
  length: (label, min, max, required = true) => [
    ...(required ? [{ required: true, message: `请输入${label}`, trigger: 'blur' }] : []),
    {
      validator: (_rule, value, callback) => {
        if (blank(value)) {
          if (required) callback(new Error(`请输入${label}`))
          else callback()
          return
        }
        const size = String(value).trim().length
        if (size < min || size > max) callback(new Error(`${label}长度需在 ${min}-${max} 个字符之间`))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}
