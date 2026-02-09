// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 GET /observable/ranking */
export async function getRanking(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getRankingParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListAppRankingVO>('/observable/ranking', {
    method: 'GET',
    params: {
      // type has a default value: tokens
      type: 'tokens',
      // limit has a default value: 10
      limit: '10',
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /observable/statistics/${param0} */
export async function getAppStatistics(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getAppStatisticsParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, ...queryParams } = params
  return request<API.BaseResponseAppStatisticsVO>(`/observable/statistics/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}
