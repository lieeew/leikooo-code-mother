# Bug 汇总：ExecuteToolAdvisor 导致 doOnComplete 重复触发

## 概要
在引入 `ExecuteToolAdvisor` 后，其他 StreamAdvisor（如 Build/Version/MessageAggregator/Observable）的 `doOnComplete` 会在每次 tool loop 迭代时触发，造成重复保存/更新与流式输出被误判为“分片独立”。

## 影响
- BuildAdvisor/VersionAdvisor 多次执行，导致重复保存版本或更新构建状态。
- MessageAggregatorAdvisor/ObservableAdvisor 多次记录，产生重复记录或统计偏差。
- 流式输出表现为每个 tool loop 的独立完成阶段。

## 复现步骤
1. 调用代码生成接口（例如 `/app/chat/gen/code`），让模型触发工具调用。
2. 观察日志或数据库记录：`doOnComplete` 在每次 tool loop 结束时都会执行。

## 根因
`ExecuteToolAdvisor` 的 order 过低（优先级最高），并在工具调用循环中使用 `streamAdvisorChain.copy(this)`。
因此所有 order 更高的 advisors 都被包含在每次 tool loop 迭代中，导致 `doOnComplete` 每次迭代都触发。

## 解决思路
将 `ExecuteToolAdvisor` 放到工具循环“内部”，把具有副作用的 advisors 放到其“外部”，确保它们仅在整个请求完成后触发一次。
（如需统计每次 tool loop，可保留 ObservableAdvisor 在内部。）

## 备注
若仍希望某些统计按“每次 tool loop”执行，可单独调整该 advisor 的顺序使其位于 `ExecuteToolAdvisor` 之后。
