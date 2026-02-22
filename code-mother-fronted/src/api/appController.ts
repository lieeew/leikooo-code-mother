// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /app/add */
export async function createApp(body: API.CreatAppRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseLong>('/app/add', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/admin/delete */
export async function deleteAppByAdmin(body: API.DeleteRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/admin/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/admin/get/vo */
export async function getAppVoByIdByAdmin(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getAppVOByIdByAdminParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseAppVO>('/app/admin/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/admin/list/page/vo */
export async function listAppVoByPageByAdmin(
  body: API.AppQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageAppVO>('/app/admin/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/admin/update */
export async function updateAppByAdmin(
  body: API.AppAdminUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/app/admin/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/cancel/gen */
export async function cancelGeneration(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.cancelGenerationParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/app/cancel/gen', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/chat/gen/code */
export async function generateApp(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.generateAppParams,
  options?: { [key: string]: any }
) {
  return request<API.ServerSentEventString[]>('/app/chat/gen/code', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/deploy */
export async function deployApp(body: API.AppDeployRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseString>('/app/deploy', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/fix/error */
export async function getFixError(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getFixErrorParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>('/app/fix/error', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/get/vo */
export async function getAppVo(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getAppVOParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseAppVO>('/app/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/good/list/page/vo */
export async function listGoodAppVoByPage(
  body: API.AppQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageAppVO>('/app/good/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/my/list/page/vo */
export async function listMyAppVoByPage(
  body: API.AppQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageAppVO>('/app/my/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/runtime-check */
export async function triggerRuntimeCheck(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.triggerRuntimeCheckParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/app/runtime-check', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/runtime-check/result */
export async function getRuntimeCheckResult(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getRuntimeCheckResultParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseRuntimeCheckResultVO>('/app/runtime-check/result', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/screenshot */
export async function getScreenshot(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getScreenshotParams,
  options?: { [key: string]: any }
) {
  return request<any>('/app/screenshot', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/source/file-content */
export async function getFileContent(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getFileContentParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseFileContentVO>('/app/source/file-content', {
    method: 'GET',
    params: {
      // limit has a default value: 1000
      limit: '1000',
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/source/file-tree */
export async function getFileTree(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getFileTreeParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseFileTreeNodeVO>('/app/source/file-tree', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/source/files */
export async function getFileList(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getFileListParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseFileListVO>('/app/source/files', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/update */
export async function updateApp(body: API.AppUpdateRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/app/update', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
