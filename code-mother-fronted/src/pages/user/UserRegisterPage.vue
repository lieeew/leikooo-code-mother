<template>
  <div id="userRegisterPage">
    <h2 class="title">鱼皮 AI 应用生成 - 用户注册</h2>
    <div class="desc">不写一行代码，生成完整应用</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userName" :rules="[{ required: true, message: '请输入用户名' }]">
        <a-input v-model:value="formState.userName" placeholder="请输入用户名" />
      </a-form-item>
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <a-form-item name="userEmail" :rules="[{ required: true, message: '请输入邮箱' }]">
        <a-input v-model:value="formState.userEmail" placeholder="请输入邮箱" />
      </a-form-item>
      <a-form-item name="emailCode" :rules="[{ required: true, message: '请输入验证码' }]">
        <a-input-search
          v-model:value="formState.emailCode"
          placeholder="请输入验证码"
          @search="handleSendCode"
        >
          <template #enterButton>
            <a-button :disabled="countdown > 0" :loading="codeLoading">
              {{ countdown > 0 ? `${countdown}秒后重发` : '发送验证码' }}
            </a-button>
          </template>
        </a-input-search>
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码不能小于 8 位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>
      <a-form-item
        name="checkPassword"
        :rules="[
          { required: true, message: '请确认密码' },
          { min: 8, message: '密码不能小于 8 位' },
          { validator: validateCheckPassword },
        ]"
      >
        <a-input-password v-model:value="formState.checkPassword" placeholder="请确认密码" />
      </a-form-item>
      <div class="tips">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">注册</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import {useRouter} from 'vue-router'
import {register, sendCode, verifyCode} from '@/api/userController.ts'
import {message} from 'ant-design-vue'
import {onBeforeUnmount, reactive, ref} from 'vue'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest & { emailCode: string }>({
  userName: '',
  userAccount: '',
  userPassword: '',
  checkPassword: '',
  userEmail: '',
  emailCode: '',
})
const codeLoading = ref(false)
const isCodeSent = ref(false)
const countdown = ref(0)
const verifyToken = ref('')
let timer: number | null = null

const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const handleSendCode = async () => {
  if (!formState.userEmail) {
    message.error('请先输入邮箱')
    return
  }
  codeLoading.value = true
  try {
    const res = await sendCode({ email: formState.userEmail })
    if (res.data.code === 0) {
      message.success('验证码已发送')
      isCodeSent.value = true
      startCountdown()
    } else {
      message.error('发送失败，' + res.data.message)
    }
  } catch (error) {
    message.error('发送验证码失败')
  } finally {
    codeLoading.value = false
  }
}

const startCountdown = () => {
  countdown.value = 180
  timer = window.setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer!)
      timer = null
      isCodeSent.value = false
    }
  }, 1000)
}

const handleSubmit = async (values: API.UserRegisterRequest & { emailCode: string }) => {
  if (!formState.emailCode) {
    message.error('请输入验证码')
    return
  }
  codeLoading.value = true
  try {
    const verifyRes = await verifyCode({
      email: formState.userEmail,
      code: formState.emailCode,
    })
    if (verifyRes.data.code === 0 && verifyRes.data.data?.token) {
      verifyToken.value = verifyRes.data.data.token
      const registerRes = await register(values, {
        headers: {
          Authorization: `Bearer ${verifyToken.value}`,
        },
      })
      if (registerRes.data.code === 0) {
        message.success('注册成功')
        router.push({
          path: '/user/login',
          replace: true,
        })
      } else {
        message.error('注册失败，' + registerRes.data.message)
      }
    } else {
      message.error('验证码验证失败，' + verifyRes.data.message)
    }
  } catch (error) {
    message.error('注册失败')
  } finally {
    codeLoading.value = false
  }
}

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<style scoped>
#userRegisterPage {
  background: white;
  max-width: 720px;
  padding: 24px;
  margin: 24px auto;
}

.title {
  text-align: center;
  margin-bottom: 16px;
}

.desc {
  text-align: center;
  color: #bbb;
  margin-bottom: 16px;
}

.tips {
  margin-bottom: 16px;
  color: #bbb;
  font-size: 13px;
  text-align: right;
}
</style>
