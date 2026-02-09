package com.leikooo.codemother.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.leikooo.codemother.model.entity.ObservableRecord;
import com.leikooo.codemother.model.enums.RankingTypeEnum;
import com.leikooo.codemother.model.vo.AppRankingVO;
import com.leikooo.codemother.model.vo.AppStatisticsVO;

import java.util.List;

/**
 * @author leikooo
 * @description 针对表【observable_record】的数据库操作Service
 * @createDate 2026-01-15
 */
public interface ObservableRecordService extends IService<ObservableRecord> {

    /**
     * 获取应用统计信息
     * @param appId 应用ID
     * @return 统计信息
     */
    AppStatisticsVO getAppStatistics(Long appId);

    /**
     * 获取全局排行榜
     * @param type 排序类型：tokens | toolCalls | duration
     * @param limit 返回数量
     * @return 排行榜列表
     */
    List<AppRankingVO> getRanking(RankingTypeEnum type, Integer limit);
}
