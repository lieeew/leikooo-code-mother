package com.leikooo.codemother.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leikooo.codemother.mapper.AppMapper;
import com.leikooo.codemother.mapper.ObservableRecordMapper;
import com.leikooo.codemother.model.entity.App;
import com.leikooo.codemother.model.entity.ObservableRecord;
import com.leikooo.codemother.model.enums.RankingTypeEnum;
import com.leikooo.codemother.model.vo.AppRankingVO;
import com.leikooo.codemother.model.vo.AppStatisticsVO;
import com.leikooo.codemother.service.ObservableRecordService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author leikooo
 * @description 针对表【observable_record】的数据库操作Service实现
 * @createDate 2026-01-15
 */
@Service
public class ObservableRecordServiceImpl extends ServiceImpl<ObservableRecordMapper, ObservableRecord>
        implements ObservableRecordService {

    private final ObservableRecordMapper observableRecordMapper;
    private final AppMapper appMapper;

    public ObservableRecordServiceImpl(ObservableRecordMapper observableRecordMapper, AppMapper appMapper) {
        this.observableRecordMapper = observableRecordMapper;
        this.appMapper = appMapper;
    }

    @Override
    public AppStatisticsVO getAppStatistics(Long appId) {
        ObservableRecord record = observableRecordMapper.getAppStatistics(appId.toString());
        Long totalChats = observableRecordMapper.countChatsByAppId(appId.toString());
        if (record == null) {
            return AppStatisticsVO.builder()
                    .totalTokens(0L)
                    .totalChats(0L)
                    .totalToolCalls(0L)
                    .avgDurationMs(0L)
                    .build();
        }
        Long totalTokens = (record.getInputTokens() == null ? 0L : record.getInputTokens())
                + (record.getOutputTokens() == null ? 0L : record.getOutputTokens());
        Long avgDuration = totalChats > 0
                ? (record.getDurationMs() == null ? 0L : record.getDurationMs()) / totalChats
                : 0L;
        return AppStatisticsVO.builder()
                .totalTokens(totalTokens)
                .totalChats(totalChats)
                .totalToolCalls(record.getToolCallCount() == null ? 0L : record.getToolCallCount())
                .avgDurationMs(avgDuration)
                .build();
    }

    @Override
    public List<AppRankingVO> getRanking(RankingTypeEnum type, Integer limit) {
        List<AppRankingVO> ranking = observableRecordMapper.getRanking(type.getValue(), limit);
        for (AppRankingVO item : ranking) {
            App app = appMapper.selectById(item.getAppId());
            item.setApp(app);
            if (app != null) {
                item.setAppName(app.getAppName());
            }
        }
        return ranking;
    }
}
