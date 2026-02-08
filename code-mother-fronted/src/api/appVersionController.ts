// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /app/version/download */
export async function downloadVersion(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.downloadVersionParams,
  options?: { [key: string]: any }
) {
  return request<string[]>('/app/version/download', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/version/list/${param0} */
export async function listVersions(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listVersionsParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, ...queryParams } = params
  return request<API.BaseResponseListAppVersionVO>(`/app/version/list/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/version/rollback */
export async function rollback(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.rollbackParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/app/version/rollback', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
