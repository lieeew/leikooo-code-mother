// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /deprecated/chat/stream/${param0} */
export async function stream(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.streamParams,
  options?: { [key: string]: any }
) {
  const { sessionId: param0, ...queryParams } = params
  return request<string[]>(`/deprecated/chat/stream/${param0}`, {
    method: 'GET',
    params: {
      ...queryParams,
    },
    ...(options || {}),
  })
}
