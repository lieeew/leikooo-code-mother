package com.leikooo.codemother.controller;

import com.leikooo.codemother.commen.BaseResponse;
import com.leikooo.codemother.commen.ResultUtils;
import com.leikooo.codemother.exception.ErrorCode;
import com.leikooo.codemother.exception.ThrowUtils;
import com.leikooo.codemother.model.enums.RankingTypeEnum;
import com.leikooo.codemother.model.vo.AppRankingVO;
import com.leikooo.codemother.model.vo.AppStatisticsVO;
import com.leikooo.codemother.service.ObservableRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author leikooo
 */
@RestController
@RequestMapping("/observable")
public class ObservableRecordController {

    private final ObservableRecordService observableRecordService;

    public ObservableRecordController(ObservableRecordService observableRecordService) {
        this.observableRecordService = observableRecordService;
    }

    @GetMapping("/statistics/{appId}")
    public BaseResponse<AppStatisticsVO> getAppStatistics(@PathVariable(name = "appId") Long appId) {
        ThrowUtils.throwIf(appId == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(observableRecordService.getAppStatistics(appId));
    }

    /**
     * 获取应用排行榜
     * @param type 排序类型：tokens | inputTokens | outputTokens | toolCalls | duration
     *             - tokens: 输入+输出 tokens 总计
     *             - inputTokens: 仅输入 tokens
     *             - outputTokens: 仅输出 tokens
     *             - toolCalls: 工具调用计数
     *             - duration: 执行耗时
     * @param limit 返回数量，范围 1-100，默认 10
     * @return 排行榜列表
     */
    @GetMapping("/ranking")
    public BaseResponse<List<AppRankingVO>> getRanking(
            @RequestParam(defaultValue = "tokens", name = "type") String type,
            @RequestParam(defaultValue = "10", name = "limit") Integer limit
    ) {
        ThrowUtils.throwIf(limit <= 0 || limit > 100, ErrorCode.PARAMS_ERROR);
        RankingTypeEnum rankingTypeEnum = RankingTypeEnum.fromValue(type);
        return ResultUtils.success(observableRecordService.getRanking(rankingTypeEnum, limit));
    }
}
