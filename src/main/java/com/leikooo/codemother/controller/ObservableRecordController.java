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
