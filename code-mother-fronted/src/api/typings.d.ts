declare namespace API {
  type AppAdminUpdateRequest = {
    id?: number
    appName?: string
    cover?: string
    priority?: number
  }

  type AppQueryRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    appName?: string
    initPrompt?: string
    codeGenType?: string
    deployKey?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    priority?: number
  }

  type AppUpdateRequest = {
    id?: number
    appName?: string
  }

  type AppVersionVO = {
    id?: number
    appId?: number
    versionNum?: number
    fileUrl?: string
    status?: string
    fileCount?: number
    fileSize?: number
    createTime?: string
    userId?: string
  }

  type AppVO = {
    id?: string
    appName?: string
    cover?: string
    codeGenType?: string
    initPrompt?: string
    deployKey?: string
    createTime?: string
    updateTime?: string
    userId?: string
    totalInputTokens?: number
    totalOutputTokens?: number
    totalConsumeTime?: number
    user?: UserVO
  }

  type BaseResponseAppVO = {
    code?: number
    data?: AppVO
    message?: string
  }

  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseListAppVersionVO = {
    code?: number
    data?: AppVersionVO[]
    message?: string
  }

  type BaseResponseListAppVO = {
    code?: number
    data?: AppVO[]
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponsePageAppVO = {
    code?: number
    data?: PageAppVO
    message?: string
  }

  type BaseResponsePageChatHistory = {
    code?: number
    data?: PageChatHistory
    message?: string
  }

  type BaseResponsePageUserVO = {
    code?: number
    data?: PageUserVO
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO = {
    code?: number
    data?: UserVO
    message?: string
  }

  type BaseResponseVerifyCodeVO = {
    code?: number
    data?: VerifyCodeVO
    message?: string
  }

  type cancelGenerationParams = {
    appId: number
  }

  type ChatHistory = {
    id?: number
    message?: string
    messageType?: string
    appId?: number
    userId?: string[]
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type ChatHistoryQueryRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    message?: string
    messageType?: string
    appId?: number
    lastCreateTime?: string
  }

  type CreatAppRequest = {
    initPrompt: string
  }

  type DeleteRequest = {
    appId?: number
    userId?: string
  }

  type downloadVersionParams = {
    appId: number
    versionNum: number
  }

  type generateAppParams = {
    appId: number
    message: string
  }

  type getAppVOByIdByAdminParams = {
    arg0: number
  }

  type getAppVOParams = {
    id: number
  }

  type getUserByIdParams = {
    userId: string
  }

  type getUserVOByIdParams = {
    userId: string
  }

  type listAppChatHistoryParams = {
    appId: number
    pageSize?: number
    lastCreateTime?: string
  }

  type listVersionsParams = {
    appId: number
  }

  type OrderItem = {
    column?: string
    asc?: boolean
  }

  type PageAppVO = {
    records?: AppVO[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PageAppVO
    searchCount?: PageAppVO
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type PageChatHistory = {
    records?: ChatHistory[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PageChatHistory
    searchCount?: PageChatHistory
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type PageUserVO = {
    records?: UserVO[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PageUserVO
    searchCount?: PageUserVO
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type rollbackParams = {
    appId: number
    versionNum: number
  }

  type SendCodeRequest = {
    email: string
  }

  type ServerSentEventString = true

  type serveStaticResourceParams = {
    deployKey: string
  }

  type streamParams = {
    sessionId: string
    message: string
  }

  type User = {
    id?: string[]
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userEmail?: string
    userRole?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount: string
    userPassword: string
  }

  type UserQueryRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    userName?: string
    userAccount?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    userName: string
    userAccount: string
    userPassword: string
    checkPassword: string
    userEmail: string
  }

  type UserUpdateRequest = {
    userId?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    id?: string
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    editTime?: string
    createTime?: string
    updateTime?: string
  }

  type VerifyCodeRequest = {
    email: string
    code: string
  }

  type VerifyCodeVO = {
    token?: string
    email?: string
  }
}
