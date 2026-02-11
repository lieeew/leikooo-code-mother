// 排行维度枚举
export enum RankingTypeEnum {
  TOKENS = 'tokens',
  TOOL_CALLS = 'toolCalls',
  DURATION = 'duration',
  INPUT_TOKENS = 'inputTokens',
  OUTPUT_TOKENS = 'outputTokens',
}

// 排行维度选项配置
export const RANKING_TYPE_OPTIONS = [
  {
    label: 'Token消耗',
    value: RankingTypeEnum.TOKENS,
  },
  {
    label: '输入Token',
    value: RankingTypeEnum.INPUT_TOKENS,
  },
  {
    label: '输出Token',
    value: RankingTypeEnum.OUTPUT_TOKENS,
  },
  {
    label: '工具调用次数',
    value: RankingTypeEnum.TOOL_CALLS,
  },
  {
    label: '平均耗时',
    value: RankingTypeEnum.DURATION,
  },
]
